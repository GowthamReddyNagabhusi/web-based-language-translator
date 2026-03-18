package com.example.translator.provider;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TranslationResultTest {

    @Test
    void record_storesValues() {
        TranslationResult result = new TranslationResult("Bonjour", "bonjur", "google");
        assertThat(result.translatedText()).isEqualTo("Bonjour");
        assertThat(result.pronunciation()).isEqualTo("bonjur");
        assertThat(result.provider()).isEqualTo("google");
    }

    @Test
    void record_nullText_becomesEmpty() {
        TranslationResult result = new TranslationResult(null, null, null);
        assertThat(result.translatedText()).isEmpty();
        assertThat(result.pronunciation()).isNull();
        assertThat(result.provider()).isEqualTo("unknown");
    }

    @Test
    void record_equalityAndHashCode() {
        TranslationResult a = new TranslationResult("Hello", null, "google");
        TranslationResult b = new TranslationResult("Hello", null, "google");
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
