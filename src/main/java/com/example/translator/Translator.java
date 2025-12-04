package com.example.translator;

import com.google.gson.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;

public class Translator {

    // Prefer LibreTranslate endpoints; fall back to MyMemory if public instances require keys.
    private static final String[] LIBRE_ENDPOINTS = new String[] {
        "https://libretranslate.com/translate",
        "https://translate.argosopentech.com/translate"
    };
    private static final String MYMEMORY_API = "https://api.mymemory.translated.net/get";

    public static String translate(String text, String targetLang) throws Exception {

        if (text == null || text.trim().isEmpty()) {
            throw new Exception("Text cannot be empty");
        }

        if (targetLang == null || targetLang.trim().isEmpty()) {
            throw new Exception("Target language code is required");
        }

        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .build();

        // Prepare payload
        JsonObject payload = new JsonObject();
        payload.addProperty("q", text);
        payload.addProperty("source", "auto");
        payload.addProperty("target", targetLang);
        payload.addProperty("format", "text");

        String requestBody = payload.toString();

        // First try the unofficial Google Translate web endpoint (fast, no key).
        try {
            String googleUrl = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl="
                    + java.net.URLEncoder.encode(targetLang, StandardCharsets.UTF_8)
                    + "&dt=t&q=" + java.net.URLEncoder.encode(text, StandardCharsets.UTF_8);
            HttpRequest greq = HttpRequest.newBuilder()
                    .uri(URI.create(googleUrl))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> gresp = client.send(greq, BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (gresp.statusCode() == 200) {
                // Response format is a nested array; attempt to extract the translated text
                try {
                    JsonElement parsed = JsonParser.parseString(gresp.body());
                    if (parsed.isJsonArray()) {
                        JsonArray top = parsed.getAsJsonArray();
                        if (top.size() > 0 && top.get(0).isJsonArray()) {
                            JsonArray segments = top.get(0).getAsJsonArray();
                            StringBuilder sb = new StringBuilder();
                            for (JsonElement segEl : segments) {
                                if (segEl.isJsonArray()) {
                                    JsonArray seg = segEl.getAsJsonArray();
                                    if (seg.size() > 0 && !seg.get(0).isJsonNull()) {
                                        sb.append(seg.get(0).getAsString());
                                    }
                                }
                            }
                            String googleTranslation = sb.toString().trim();
                            if (!googleTranslation.isEmpty()) return googleTranslation;
                        }
                    }
                } catch (Exception ex) {
                    // ignore and continue to other providers
                }
            }
        } catch (Exception e) {
            // ignore and continue to other providers
        }

        // Try libretranslate endpoints in order
        for (String endpoint : LIBRE_ENDPOINTS) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .header("Content-Type", "application/json")
                        .POST(BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> response = client.send(request, BodyHandlers.ofString(StandardCharsets.UTF_8));
                int status = response.statusCode();

                if (status == 200 || status == 201) {
                    String body = response.body();
                    String translation = extractTranslationFromJson(body);
                    if (translation != null && !translation.trim().isEmpty()) {
                        return translation;
                    }
                } else {
                    // If this instance requires an API key, continue to next
                    String body = response.body();
                    if (body != null && body.contains("Visit") && body.contains("portal.libretranslate")) {
                        // try next endpoint
                        continue;
                    }
                    // otherwise try next endpoint as well
                }

            } catch (Exception e) {
                // try next endpoint
                continue;
            }
        }

        // Fallback: MyMemory (GET)
        try {
            String encoded = java.net.URLEncoder.encode(text, StandardCharsets.UTF_8);
            String url = MYMEMORY_API + "?q=" + encoded + "&langpair=en|" + targetLang;
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> resp = client.send(req, BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() == 200) {
                String body = resp.body();
                JsonObject jsonResponse = JsonParser.parseString(body).getAsJsonObject();
                if (jsonResponse.has("responseData")) {
                    JsonObject responseData = jsonResponse.getAsJsonObject("responseData");
                    if (responseData.has("translatedText")) {
                        String translation = responseData.get("translatedText").getAsString();
                        if (translation != null && !translation.trim().isEmpty()) return translation;
                    }
                }
                // try matches[] fallback
                if (jsonResponse.has("matches")) {
                    JsonArray matches = jsonResponse.getAsJsonArray("matches");
                    int bestIdx = -1; double bestQuality = -1;
                    for (int i = 0; i < matches.size(); i++) {
                        JsonObject m = matches.get(i).getAsJsonObject();
                        double q = m.has("quality") ? m.get("quality").getAsDouble() : 0;
                        if (q > bestQuality && m.has("translation")) { bestQuality = q; bestIdx = i; }
                    }
                    if (bestIdx != -1) {
                        return matches.get(bestIdx).getAsJsonObject().get("translation").getAsString();
                    }
                }
            }
        } catch (Exception e) {
            // fall through to final failure
        }

        throw new Exception("No translation returned from available APIs");
    }

    private static String extractTranslationFromJson(String body) {
        try {
            JsonElement parsed = JsonParser.parseString(body);
            if (parsed.isJsonObject()) {
                JsonObject obj = parsed.getAsJsonObject();
                if (obj.has("translatedText")) return obj.get("translatedText").getAsString();
                if (obj.has("translation")) return obj.get("translation").getAsString();
                // some instances return {"result":"..."}
                if (obj.has("result")) return obj.get("result").getAsString();
            } else if (parsed.isJsonArray()) {
                JsonArray arr = parsed.getAsJsonArray();
                if (arr.size() > 0 && arr.get(0).isJsonObject()) {
                    JsonObject first = arr.get(0).getAsJsonObject();
                    if (first.has("translatedText")) return first.get("translatedText").getAsString();
                    if (first.has("translation")) return first.get("translation").getAsString();
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }
}