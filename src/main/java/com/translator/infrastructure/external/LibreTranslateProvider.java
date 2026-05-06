package com.translator.infrastructure.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Free translation provider using the MyMemory API.
 * No API key required. Supports 75+ language pairs.
 * Free tier: 1,000 words/day. Docs: https://mymemory.translated.net/doc/spec.php
 */
@Component
public class LibreTranslateProvider implements TranslationProvider {

    private static final Logger log = LoggerFactory.getLogger(LibreTranslateProvider.class);
    private static final String BASE_URL = "https://api.mymemory.translated.net/get";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public LibreTranslateProvider() {
        this.httpClient  = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String translate(String text, String sourceLang, String targetLang) {
        try {
            String src = (sourceLang == null || sourceLang.isBlank() || "auto".equalsIgnoreCase(sourceLang))
                    ? "en" : sourceLang;

            // URL-encode BOTH params — the pipe in langpair is an illegal URI character
            String encodedText     = URLEncoder.encode(text, StandardCharsets.UTF_8);
            String encodedLangPair = URLEncoder.encode(src + "|" + targetLang, StandardCharsets.UTF_8);
            String urlStr          = BASE_URL + "?q=" + encodedText + "&langpair=" + encodedLangPair;

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(new URI(urlStr))
                    .timeout(TIMEOUT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (resp.statusCode() != 200) {
                throw new RuntimeException("MyMemory returned HTTP " + resp.statusCode());
            }

            JsonNode root        = objectMapper.readTree(resp.body());
            JsonNode responseData = root.path("responseData");
            String translated    = responseData.path("translatedText").asText();

            if (translated.isBlank()) {
                throw new RuntimeException("Empty response from MyMemory");
            }

            // MyMemory returns ALL CAPS when it can't translate — treat as failure
            if (translated.equals(translated.toUpperCase()) && !text.equals(text.toUpperCase())) {
                throw new RuntimeException("MyMemory returned untranslated text");
            }

            return translated;

        } catch (Exception e) {
            log.warn("MyMemory translation failed: {}", e.getMessage());
            throw new RuntimeException("MyMemory provider failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String getProviderName() {
        return "MYMEMORY";
    }

    @Override
    public int getPriority() {
        return 1; // Primary provider — tried before MockTranslationProvider
    }
}

