package com.example.translator.provider;

/**
 * Contract for a translation provider (Google, LibreTranslate, MyMemory, etc.).
 * Each provider is independently testable and replaceable.
 */
public interface TranslationProvider {

    /**
     * Translate text to the target language.
     * @param text source text
     * @param targetLang ISO 639-1 language code
     * @return result containing translated text and optional pronunciation
     * @throws TranslationException if translation fails
     */
    TranslationResult translate(String text, String targetLang) throws TranslationException;

    /** Human-readable name for logging and metrics. */
    String name();

    /** Provider-level health check. Returns true if the provider is believed to be reachable. */
    default boolean isHealthy() { return true; }
}
