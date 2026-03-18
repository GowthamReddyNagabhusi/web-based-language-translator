package com.example.translator.provider;

import com.example.translator.config.AppConfig;
import com.example.translator.romanize.Romanizer;
import com.google.gson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** MyMemory translation API — free tier, no key required. */
public class MyMemoryProvider implements TranslationProvider {

    private static final Logger LOG = LoggerFactory.getLogger(MyMemoryProvider.class);
    private static final String API_URL = "https://api.mymemory.translated.net/get";

    private final HttpClient httpClient;
    private final Duration requestTimeout;

    public MyMemoryProvider(HttpClient httpClient) {
        this.httpClient = httpClient;
        this.requestTimeout = Duration.ofSeconds(AppConfig.REQUEST_TIMEOUT_SECONDS);
    }

    @Override
    public String name() { return "mymemory"; }

    @Override
    public TranslationResult translate(String text, String targetLang) throws TranslationException {
        try {
            String url = API_URL + "?q=" + URLEncoder.encode(text, StandardCharsets.UTF_8)
                    + "&langpair=auto|" + URLEncoder.encode(targetLang, StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .GET()
                    .timeout(requestTimeout)
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() >= 500) {
                throw new TranslationException("MyMemory returned HTTP " + response.statusCode(), true);
            }
            if (response.statusCode() != 200) {
                throw new TranslationException("MyMemory returned HTTP " + response.statusCode(), false);
            }

            return parseResponse(response.body(), targetLang);

        } catch (TranslationException e) {
            throw e;
        } catch (java.net.http.HttpTimeoutException e) {
            throw new TranslationException("MyMemory timed out", e, true);
        } catch (Exception e) {
            throw new TranslationException("MyMemory failed: " + e.getMessage(), e, true);
        }
    }

    private TranslationResult parseResponse(String body, String targetLang) throws TranslationException {
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();

            // Try responseData.translatedText first
            if (json.has("responseData")) {
                JsonObject data = json.getAsJsonObject("responseData");
                if (data.has("translatedText")) {
                    String translation = data.get("translatedText").getAsString().trim();
                    if (!translation.isEmpty()) {
                        String pronunciation = Romanizer.romanize(translation, targetLang);
                        return new TranslationResult(translation, pronunciation, name());
                    }
                }
            }

            // Fallback: best quality match from matches array
            if (json.has("matches")) {
                JsonArray matches = json.getAsJsonArray("matches");
                int bestIdx = -1;
                double bestQuality = -1;
                for (int i = 0; i < matches.size(); i++) {
                    JsonObject m = matches.get(i).getAsJsonObject();
                    double q = m.has("quality") ? m.get("quality").getAsDouble() : 0;
                    if (q > bestQuality && m.has("translation")) {
                        bestQuality = q;
                        bestIdx = i;
                    }
                }
                if (bestIdx != -1) {
                    String translation = matches.get(bestIdx).getAsJsonObject()
                            .get("translation").getAsString().trim();
                    String pronunciation = Romanizer.romanize(translation, targetLang);
                    return new TranslationResult(translation, pronunciation, name());
                }
            }

            throw new TranslationException("No translation in MyMemory response", false);

        } catch (TranslationException e) {
            throw e;
        } catch (Exception e) {
            throw new TranslationException("Failed to parse MyMemory response", e, false);
        }
    }
}
