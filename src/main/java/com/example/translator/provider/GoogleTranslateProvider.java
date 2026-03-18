package com.example.translator.provider;

import com.example.translator.config.AppConfig;
import com.example.translator.romanize.Romanizer;
import com.google.gson.*;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Unofficial Google Translate endpoint — fast, free, no API key. */
public class GoogleTranslateProvider implements TranslationProvider {

    private final HttpClient httpClient;
    private final Duration requestTimeout;

    public GoogleTranslateProvider(HttpClient httpClient) {
        this.httpClient = httpClient;
        this.requestTimeout = Duration.ofSeconds(AppConfig.REQUEST_TIMEOUT_SECONDS);
    }

    @Override
    public String name() { return "google"; }

    @Override
    public TranslationResult translate(String text, String targetLang) throws TranslationException {
        try {
            String url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl="
                    + URLEncoder.encode(targetLang, StandardCharsets.UTF_8)
                    + "&dt=t&dt=rm&q=" + URLEncoder.encode(text, StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .timeout(requestTimeout)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() >= 500) {
                throw new TranslationException("Google returned HTTP " + response.statusCode(), true);
            }
            if (response.statusCode() != 200) {
                throw new TranslationException("Google returned HTTP " + response.statusCode(), false);
            }

            return parseResponse(response.body(), targetLang);

        } catch (TranslationException e) {
            throw e;
        } catch (java.net.http.HttpTimeoutException e) {
            throw new TranslationException("Google request timed out", e, true);
        } catch (Exception e) {
            throw new TranslationException("Google request failed: " + e.getMessage(), e, true);
        }
    }

    private TranslationResult parseResponse(String body, String targetLang) throws TranslationException {
        try {
            JsonElement parsed = JsonParser.parseString(body);
            if (!parsed.isJsonArray()) {
                throw new TranslationException("Unexpected response format", false);
            }
            JsonArray top = parsed.getAsJsonArray();
            if (top.isEmpty() || !top.get(0).isJsonArray()) {
                throw new TranslationException("Empty response", false);
            }

            JsonArray segments = top.get(0).getAsJsonArray();
            StringBuilder translatedText = new StringBuilder();
            String pronunciation = null;

            for (JsonElement segEl : segments) {
                if (!segEl.isJsonArray()) continue;
                JsonArray seg = segEl.getAsJsonArray();
                if (!seg.isEmpty() && !seg.get(0).isJsonNull()) {
                    translatedText.append(seg.get(0).getAsString());
                }
                // Extract pronunciation from various positions
                pronunciation = extractPronunciation(seg, pronunciation);
            }

            // Try alternate pronunciation locations in top-level array
            if (isBlank(pronunciation)) {
                pronunciation = searchTopLevelPronunciation(top);
            }

            String result = translatedText.toString().trim();
            if (result.isEmpty()) {
                throw new TranslationException("Empty translation result", false);
            }

            if (isBlank(pronunciation)) {
                pronunciation = Romanizer.romanize(result, targetLang);
            }

            return new TranslationResult(result, pronunciation, name());

        } catch (TranslationException e) {
            throw e;
        } catch (Exception e) {
            throw new TranslationException("Failed to parse Google response", e, false);
        }
    }

    private String extractPronunciation(JsonArray seg, String current) {
        // Positions 2 and 3 often hold romanization
        for (int i = 2; i <= 3; i++) {
            if (seg.size() > i && !seg.get(i).isJsonNull()) {
                String candidate = seg.get(i).getAsString();
                if (!isBlank(candidate)) return candidate.trim();
            }
        }
        // Nested arrays at positions 4 and 5
        for (int i = 4; i <= 5; i++) {
            if (isBlank(current) && seg.size() > i && seg.get(i).isJsonArray()) {
                String found = firstStringFromArray(seg.get(i).getAsJsonArray());
                if (!isBlank(found)) return found;
            }
        }
        return current;
    }

    private String searchTopLevelPronunciation(JsonArray top) {
        // Index 1 sometimes has transliteration
        if (top.size() > 1 && top.get(1).isJsonArray()) {
            String found = firstStringFromArray(top.get(1).getAsJsonArray());
            if (!isBlank(found)) return found;
        }
        // Indexes 5 and 6
        for (int i = 5; i <= 6; i++) {
            if (top.size() > i && top.get(i).isJsonArray()) {
                String found = firstStringFromArray(top.get(i).getAsJsonArray());
                if (!isBlank(found)) return found;
            }
        }
        return null;
    }

    static String firstStringFromArray(JsonArray arr) {
        for (JsonElement el : arr) {
            if (el == null) continue;
            if (el.isJsonPrimitive()) {
                String s = el.getAsString();
                if (!isBlank(s)) return s.trim();
            } else if (el.isJsonArray()) {
                String nested = firstStringFromArray(el.getAsJsonArray());
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
