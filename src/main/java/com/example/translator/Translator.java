package com.example.translator;

import com.example.translator.provider.*;
import com.example.translator.service.TranslationService;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

/**
 * Backward-compatible facade for CLI usage (Main.java).
 * Delegates to TranslationService with the full provider chain.
 */
public class Translator {

    private static final TranslationService SERVICE;

    static {
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(8))
                .build();
        SERVICE = new TranslationService(List.of(
                new GoogleTranslateProvider(client),
                new LibreTranslateProvider(client),
                new MyMemoryProvider(client)
        ));
    }

    public static String translate(String text, String targetLang) throws Exception {
        try {
            return SERVICE.translate(text, targetLang).translatedText();
        } catch (TranslationException e) {
            throw new Exception(e.getMessage(), e);
        }
    }

    public static TranslationResult translateWithPronunciation(String text, String targetLang) throws Exception {
        try {
            var result = SERVICE.translate(text, targetLang);
            return new TranslationResult(result.translatedText(), result.pronunciation());
        } catch (TranslationException e) {
            throw new Exception(e.getMessage(), e);
        }
    }

    public static class TranslationResult {
        public final String translatedText;
        public final String pronunciation;

        public TranslationResult(String translatedText, String pronunciation) {
            this.translatedText = translatedText;
            this.pronunciation = pronunciation;
        }
    }
}
