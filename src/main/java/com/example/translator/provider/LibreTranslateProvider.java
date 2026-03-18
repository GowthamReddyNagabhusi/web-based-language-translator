package com.example.translator.provider;

import com.example.translator.config.AppConfig;
import com.example.translator.romanize.Romanizer;
import com.google.gson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** LibreTranslate open-source translation provider. Tries multiple public instances. */
public class LibreTranslateProvider implements TranslationProvider {

    private static final Logger LOG = LoggerFactory.getLogger(LibreTranslateProvider.class);

    private static final String[] ENDPOINTS = {
        "https://libretranslate.com/translate",
        "https://translate.argosopentech.com/translate"
    };

    private final HttpClient httpClient;
    private final Duration requestTimeout;

    public LibreTranslateProvider(HttpClient httpClient) {
        this.httpClient = httpClient;
        this.requestTimeout = Duration.ofSeconds(AppConfig.REQUEST_TIMEOUT_SECONDS);
    }

    @Override
    public String name() { return "libretranslate"; }

    @Override
    public TranslationResult translate(String text, String targetLang) throws TranslationException {
        TranslationException lastException = null;

        for (String endpoint : ENDPOINTS) {
            try {
                return tryEndpoint(endpoint, text, targetLang);
            } catch (TranslationException e) {
                LOG.debug("LibreTranslate endpoint {} failed: {}", endpoint, e.getMessage());
                lastException = e;
            }
        }

        throw lastException != null ? lastException
                : new TranslationException("All LibreTranslate endpoints failed", true);
    }

    private TranslationResult tryEndpoint(String endpoint, String text, String targetLang)
            throws TranslationException {
        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("q", text);
            payload.addProperty("source", "auto");
            payload.addProperty("target", targetLang);
            payload.addProperty("format", "text");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                    .timeout(requestTimeout)
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() >= 500) {
                throw new TranslationException("LibreTranslate returned HTTP " + response.statusCode(), true);
            }

            // API key required — skip this instance
            String body = response.body();
            if (body != null && body.contains("portal.libretranslate")) {
                throw new TranslationException("LibreTranslate requires API key", false);
            }

            if (response.statusCode() != 200 && response.statusCode() != 201) {
                throw new TranslationException("LibreTranslate returned HTTP " + response.statusCode(), false);
            }

            String translation = extractTranslation(body);
            if (translation == null || translation.isBlank()) {
                throw new TranslationException("Empty translation from LibreTranslate", false);
            }

            String pronunciation = Romanizer.romanize(translation, targetLang);
            return new TranslationResult(translation, pronunciation, name());

        } catch (TranslationException e) {
            throw e;
        } catch (java.net.http.HttpTimeoutException e) {
            throw new TranslationException("LibreTranslate timed out", e, true);
        } catch (Exception e) {
            throw new TranslationException("LibreTranslate failed: " + e.getMessage(), e, true);
        }
    }

    private String extractTranslation(String body) {
        try {
            JsonElement parsed = JsonParser.parseString(body);
            if (parsed.isJsonObject()) {
                JsonObject obj = parsed.getAsJsonObject();
                if (obj.has("translatedText")) return obj.get("translatedText").getAsString().trim();
                if (obj.has("translation")) return obj.get("translation").getAsString().trim();
                if (obj.has("result")) return obj.get("result").getAsString().trim();
            } else if (parsed.isJsonArray()) {
                JsonArray arr = parsed.getAsJsonArray();
                if (!arr.isEmpty() && arr.get(0).isJsonObject()) {
                    JsonObject first = arr.get(0).getAsJsonObject();
                    if (first.has("translatedText")) return first.get("translatedText").getAsString().trim();
                }
            }
        } catch (Exception e) {
            LOG.debug("Failed to parse LibreTranslate response: {}", e.getMessage());
        }
        return null;
    }
}
