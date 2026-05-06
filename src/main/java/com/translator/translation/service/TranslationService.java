package com.translator.translation.service;

import com.translator.infrastructure.external.TranslationProvider;
import com.translator.translation.dto.TranslationRequestDTO;
import com.translator.translation.dto.TranslationResponseDTO;
import com.translator.translation.repository.TranslationRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
// @EnableAsync removed — already declared on CaffeineCacheConfig (H3 fix)
public class TranslationService {

    private final List<TranslationProvider> providers;
    private final TranslationRepository translationRepository;
    private final TranslationPersistenceService persistenceService;
    private final StringRedisTemplate redisTemplate;

    public TranslationService(List<TranslationProvider> providers,
                              TranslationRepository translationRepository,
                              TranslationPersistenceService persistenceService,
                              StringRedisTemplate redisTemplate) {
        this.providers = providers;
        this.translationRepository = translationRepository;
        this.persistenceService = persistenceService;
        this.redisTemplate = redisTemplate;

        // Sort providers by priority ascending
        this.providers.sort(Comparator.comparingInt(TranslationProvider::getPriority));
    }

    /**
     * M4 fix: cache key now includes sourceLanguage so "en→es" and "auto→es"
     * don't collide in Caffeine or Redis.
     */
    @Cacheable(value = "translations-l1-caffeine",
               key = "#request.sourceText.toLowerCase() + ':' + (#request.sourceLanguage ?: 'auto') + ':' + #request.targetLanguage")
    @CircuitBreaker(name = "translationService", fallbackMethod = "fallbackTranslation")
    @Retry(name = "translationService")
    public TranslationResponseDTO translate(TranslationRequestDTO request, UUID userId) {
        String md5hash = DigestUtils.md5DigestAsHex(request.getSourceText().getBytes());
        String redisKey = "translation:l2:" + md5hash + ":" + request.getTargetLanguage();

        // Check L2 Redis Cache
        String cachedResult = redisTemplate.opsForValue().get(redisKey);
        if (cachedResult != null) {
            // M3 fix: persistenceService is a separate bean — @Async fires via proxy
            persistenceService.persistAsync(request, cachedResult, "REDIS_CACHE", userId, true);
            return buildResponse(cachedResult, request, "REDIS_CACHE", true);
        }

        // Try providers in priority order
        for (TranslationProvider provider : providers) {
            try {
                String translated = provider.translate(
                        request.getSourceText(), request.getSourceLanguage(), request.getTargetLanguage());

                // Store in L2 Redis cache with 24h TTL
                redisTemplate.opsForValue().set(redisKey, translated, Duration.ofHours(24));

                TranslationResponseDTO response = buildResponse(translated, request, provider.getProviderName(), false);

                // M3 fix: async persistence via separate bean (not self-call)
                persistenceService.persistAsync(request, translated, provider.getProviderName(), userId, false);

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

    private TranslationResponseDTO buildResponse(String translated, TranslationRequestDTO req, String provider, boolean cached) {
        return TranslationResponseDTO.builder()
                .translationId(UUID.randomUUID())
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


