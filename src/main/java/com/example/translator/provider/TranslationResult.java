package com.example.translator.provider;

/** Immutable result of a translation operation. */
public record TranslationResult(String translatedText, String pronunciation, String provider) {

    public TranslationResult(String translatedText, String pronunciation, String provider) {
        this.translatedText = translatedText != null ? translatedText : "";
        this.pronunciation = pronunciation;
        this.provider = provider != null ? provider : "unknown";
    }
}
