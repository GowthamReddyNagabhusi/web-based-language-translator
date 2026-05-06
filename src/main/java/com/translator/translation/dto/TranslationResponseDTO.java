package com.translator.translation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranslationResponseDTO {
    private UUID translationId;
    private String translatedText;
    private String pronunciation;
    private String sourceLanguageDetected;
    private String targetLanguage;
    private String providerUsed;
    private boolean servedFromCache;
    private int wordCount;
    private int characterCount;
    private OffsetDateTime createdAt;
}
