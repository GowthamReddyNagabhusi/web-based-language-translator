package com.translator.translation.service;

import com.translator.translation.dto.TranslationRequestDTO;
import com.translator.translation.model.Translation;
import com.translator.translation.repository.TranslationRepository;
import com.translator.user.model.User;
import com.translator.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Separate bean so that @Async fires via Spring's AOP proxy.
 * Calling @Async from within the same class bypasses the proxy — moving
 * this logic here fixes that (H3 / M3 audit findings).
 */
@Service
public class TranslationPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(TranslationPersistenceService.class);

    private final TranslationRepository translationRepository;
    private final UserRepository userRepository;

    public TranslationPersistenceService(TranslationRepository translationRepository,
                                         UserRepository userRepository) {
        this.translationRepository = translationRepository;
        this.userRepository = userRepository;
    }

    @Async
    public void persistAsync(TranslationRequestDTO request,
                             String translatedText,
                             String provider,
                             UUID userId,
                             boolean isCached) {
        try {
            User user = userRepository.findById(userId).orElseThrow();
            int wordCount = request.getSourceText().split("\\s+").length;
            Translation translation = Translation.builder()
                    .id(UUID.randomUUID())
                    .user(user)
                    .sourceText(request.getSourceText())
                    .translatedText(translatedText)
                    .sourceLanguage(request.getSourceLanguage())
                    .targetLanguage(request.getTargetLanguage())
                    .providerUsed(provider)
                    .isCached(isCached)
                    .metadata(Map.of("wordCount", wordCount))
                    .build();
            translationRepository.save(translation);
        } catch (Exception e) {
            // Persistence failure must never affect the translation response
            log.error("Async translation persist failed for userId={}: {}", userId, e.getMessage(), e);
        }
    }
}
