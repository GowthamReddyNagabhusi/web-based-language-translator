package com.translator.translation.service;

import com.translator.infrastructure.external.TranslationProvider;
import com.translator.translation.dto.TranslationRequestDTO;
import com.translator.translation.dto.TranslationResponseDTO;
import com.translator.translation.model.Translation;
import com.translator.translation.repository.TranslationRepository;
import com.translator.user.model.User;
import com.translator.user.repository.UserRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.util.DigestUtils;

@Service
@EnableAsync
public class TranslationService {

    private final List<TranslationProvider> providers;
    private final TranslationRepository translationRepository;
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;

    public TranslationService(List<TranslationProvider> providers,
                              TranslationRepository translationRepository,
                              UserRepository userRepository,
                              StringRedisTemplate redisTemplate) {
        this.providers = providers;
        this.translationRepository = translationRepository;
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
        
        // Sort providers by priority
        this.providers.sort(Comparator.comparingInt(TranslationProvider::getPriority));
    }

    @Cacheable(value = "translations-l1-caffeine", key = "#request.sourceText.toLowerCase() + '-' + #request.targetLanguage")
    @CircuitBreaker(name = "translationService", fallbackMethod = "fallbackTranslation")
    @Retry(name = "translationService")
    public TranslationResponseDTO translate(TranslationRequestDTO request, UUID userId) {
        String md5hash = DigestUtils.md5DigestAsHex(request.getSourceText().getBytes());
        String redisKey = "translation:l2:" + md5hash + ":" + request.getTargetLanguage();
        
        // Check L2 Redis Cache
        String cachedResult = redisTemplate.opsForValue().get(redisKey);
        if (cachedResult != null) {
            return buildResponse(cachedResult, request, "REDIS_CACHE", true);
        }

        // Try providers in priority
        for (TranslationProvider provider : providers) {
            try {
                String translated = provider.translate(request.getSourceText(), request.getSourceLanguage(), request.getTargetLanguage());
                
                // Save to Redis (L2) TTL 24h
                redisTemplate.opsForValue().set(redisKey, translated, Duration.ofHours(24));
                
                TranslationResponseDTO response = buildResponse(translated, request, provider.getProviderName(), false);
                
                // Persist async
                persistTranslationAsync(request, translated, provider.getProviderName(), userId, false);
                
                return response;
            } catch (Exception e) {
                // Log and try next provider
            }
        }
        
        throw new RuntimeException("All translation providers failed");
    }
    
    // Fallback for circuit breaker
    public TranslationResponseDTO fallbackTranslation(TranslationRequestDTO request, UUID userId, Throwable t) {
         throw new RuntimeException("Service temporarily unavailable. Circuit breaker open. " + t.getMessage());
    }

    @Async
    public void persistTranslationAsync(TranslationRequestDTO request, String translatedText, String provider, UUID userId, boolean isCached) {
        User user = userRepository.findById(userId).orElseThrow();
        Translation translation = Translation.builder()
                .id(UUID.randomUUID())
                .user(user)
                .sourceText(request.getSourceText())
                .translatedText(translatedText)
                .sourceLanguage(request.getSourceLanguage())
                .targetLanguage(request.getTargetLanguage())
                .providerUsed(provider)
                .isCached(isCached)
                .metadata(Map.of("wordCount", request.getSourceText().split("\\s+").length))
                .build();
                
        translationRepository.save(translation);
    }

    private TranslationResponseDTO buildResponse(String translated, TranslationRequestDTO req, String provider, boolean cached) {
        return TranslationResponseDTO.builder()
                .translationId(UUID.randomUUID()) // Or fetch real from DB if blocking
                .translatedText(translated)
                .sourceLanguageDetected(req.getSourceLanguage() == null ? "en" : req.getSourceLanguage())
                .targetLanguage(req.getTargetLanguage())
                .providerUsed(provider)
                .servedFromCache(cached)
                .wordCount(req.getSourceText().split("\\s+").length)
                .characterCount(req.getSourceText().length())
                .build();
    }
}
