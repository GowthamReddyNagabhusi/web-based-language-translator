package com.example.translator.service;

import com.example.translator.provider.TranslationException;
import com.example.translator.provider.TranslationProvider;
import com.example.translator.provider.TranslationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class TranslationServiceTest {

    private TranslationService service;
    private StubProvider google;
    private StubProvider libre;

    @BeforeEach
    void setUp() {
        google = new StubProvider("google");
        libre = new StubProvider("libretranslate");
        service = new TranslationService(List.of(google, libre));
    }

    @Test
    void translate_returnsResultFromFirstProvider() throws TranslationException {
        google.result = new TranslationResult("Bonjour", null, "google");

        TranslationResult result = service.translate("Hello", "fr");

        assertThat(result.translatedText()).isEqualTo("Bonjour");
        assertThat(result.provider()).isEqualTo("google");
    }

    @Test
    void translate_fallsBackToSecondProvider() throws TranslationException {
        google.exception = new TranslationException("Google down", true);
        libre.result = new TranslationResult("Bonjour", null, "libretranslate");

        TranslationResult result = service.translate("Hello", "fr");

        assertThat(result.translatedText()).isEqualTo("Bonjour");
        assertThat(result.provider()).isEqualTo("libretranslate");
    }

    @Test
    void translate_cachesResults() throws TranslationException {
        google.result = new TranslationResult("Bonjour", null, "google");

        service.translate("Hello", "fr");
        service.translate("Hello", "fr");

        assertThat(google.callCount).isEqualTo(1); // second call hits cache
    }

    @Test
    void translate_emptyText_throwsException() {
        assertThatThrownBy(() -> service.translate("", "fr"))
                .isInstanceOf(TranslationException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void translate_nullLang_throwsException() {
        assertThatThrownBy(() -> service.translate("Hello", null))
                .isInstanceOf(TranslationException.class)
                .hasMessageContaining("required");
    }

    @Test
    void translate_unsupportedLang_throwsException() {
        assertThatThrownBy(() -> service.translate("Hello", "zz"))
                .isInstanceOf(TranslationException.class)
                .hasMessageContaining("Unsupported");
    }

    @Test
    void translate_textTooLong_throwsException() {
        String longText = "a".repeat(6000);
        assertThatThrownBy(() -> service.translate(longText, "fr"))
                .isInstanceOf(TranslationException.class)
                .hasMessageContaining("maximum length");
    }

    @Test
    void translate_allProvidersFail_throwsException() {
        google.exception = new TranslationException("fail", true);
        libre.exception = new TranslationException("fail", true);

        assertThatThrownBy(() -> service.translate("Hello", "fr"))
                .isInstanceOf(TranslationException.class);
    }

    @Test
    void isValidLanguage_supportedLanguages() {
        assertThat(TranslationService.isValidLanguage("fr")).isTrue();
        assertThat(TranslationService.isValidLanguage("es")).isTrue();
        assertThat(TranslationService.isValidLanguage("ja")).isTrue();
        assertThat(TranslationService.isValidLanguage("te")).isTrue();
    }

    @Test
    void isValidLanguage_unsupportedLanguages() {
        assertThat(TranslationService.isValidLanguage("xx")).isFalse();
        assertThat(TranslationService.isValidLanguage(null)).isFalse();
        assertThat(TranslationService.isValidLanguage("")).isFalse();
    }

    @Test
    void cacheStats_returnsValidData() throws TranslationException {
        google.result = new TranslationResult("Bonjour", null, "google");
        service.translate("Hello", "fr");

        var stats = service.cacheStats();
        assertThat(stats.size()).isGreaterThanOrEqualTo(1);
        assertThat(stats.misses()).isGreaterThanOrEqualTo(1);
    }

    // ---- Stub Provider ----

    static class StubProvider implements TranslationProvider {
        final String providerName;
        TranslationResult result;
        TranslationException exception;
        int callCount = 0;

        StubProvider(String name) { this.providerName = name; }

        @Override
        public String name() { return providerName; }

        @Override
        public TranslationResult translate(String text, String targetLang) throws TranslationException {
            callCount++;
            if (exception != null) throw exception;
            return result;
        }
    }
}
