package com.example.translator.romanize;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.assertj.core.api.Assertions.assertThat;

class RomanizerTest {

    // ---- Japanese Romanization ----

    @Test
    void romanizeJapanese_hiragana() {
        // こんにちは = konnichiha
        String result = Romanizer.romanize("\u3053\u3093\u306B\u3061\u306F", "ja");
        assertThat(result).isEqualTo("konnichiha");
    }

    @Test
    void romanizeJapanese_katakana() {
        // トキョ = tokyo (approximation — actually "Tokyo" uses トーキョー but this tests basic katakana)
        String result = Romanizer.romanize("\u30C8\u30AD\u30E8", "ja");
        assertThat(result).isEqualTo("tokiyo");
    }

    @Test
    void romanizeJapanese_digraphs() {
        // きゃ = kya
        String result = Romanizer.romanize("\u304D\u3083", "ja");
        assertThat(result).isEqualTo("kya");
    }

    @Test
    void romanizeJapanese_smallTsu() {
        // かった (katta) — っ doubles the next consonant
        String result = Romanizer.romanize("\u304B\u3063\u305F", "ja");
        assertThat(result).isEqualTo("katta");
    }

    @Test
    void romanizeJapanese_mixedWithAscii() {
        String result = Romanizer.romanize("hello\u306F", "ja");
        assertThat(result).isEqualTo("helloha");
    }

    // ---- Hindi Romanization ----

    @Test
    void romanizeHindi_basicWord() {
        // नमस्ते = namaste
        String result = Romanizer.romanize("\u0928\u092E\u0938\u094D\u0924\u0947", "hi");
        assertThat(result).isEqualTo("namaste"); // virama removes implicit 'a' from स before ते
    }

    @Test
    void romanizeHindi_withVirama() {
        // क्ष = ksha (virama removes implicit 'a')
        String result = Romanizer.romanize("\u0915\u094D\u0937", "hi");
        assertThat(result).isEqualTo("ksha");
    }

    // ---- Telugu Romanization ----

    @Test
    void romanizeTelugu_basicWord() {
        // తెలుగు = telugu
        String result = Romanizer.romanize("\u0C24\u0C46\u0C32\u0C41\u0C17\u0C41", "te");
        assertThat(result).isEqualTo("telugu");
    }

    // ---- Unsupported languages return null ----

    @ParameterizedTest
    @CsvSource({"fr", "es", "de", "zh", "it"})
    void romanize_unsupportedLang_returnsNull(String lang) {
        assertThat(Romanizer.romanize("hello", lang)).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void romanize_nullOrEmptyText_returnsNull(String text) {
        assertThat(Romanizer.romanize(text, "ja")).isNull();
    }

    @Test
    void romanize_nullLang_returnsNull() {
        assertThat(Romanizer.romanize("hello", null)).isNull();
    }
}
