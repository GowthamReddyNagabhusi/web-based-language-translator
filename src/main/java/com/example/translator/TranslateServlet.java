package com.example.translator;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TranslateServlet extends HttpServlet {

    private static final Logger LOG = LoggerFactory.getLogger(TranslateServlet.class);
    private static final Gson GSON = new Gson();
    private static final String JSON_CONTENT_TYPE = "application/json";
    private static final int MAX_TEXT_LENGTH = 5000;

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        // CORS headers
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type, Accept");

        req.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());

        String text = null;
        String lang = null;

        String contentType = req.getContentType();
        boolean hasJsonBody = contentType != null && contentType.toLowerCase().contains(JSON_CONTENT_TYPE);
        if (hasJsonBody) {
            JsonObject obj = readJsonBody(req);
            if (obj != null) {
                if (obj.has("text") && !obj.get("text").isJsonNull()) {
                    text = obj.get("text").getAsString();
                }
                if (obj.has("lang") && !obj.get("lang").isJsonNull()) {
                    lang = obj.get("lang").getAsString();
                }
            }
        }

        // Fallback to form parameters
        if (text == null) text = req.getParameter("text");
        if (lang == null) lang = req.getParameter("lang");

        LOG.info("Translate request: lang={}, textLength={}", lang, text != null ? text.length() : 0);

        // Content negotiation
        String accept = req.getHeader("Accept");
        boolean wantsJson = (accept != null && accept.toLowerCase().contains(JSON_CONTENT_TYPE))
                || hasJsonBody;
        resp.setContentType(wantsJson ? "application/json; charset=UTF-8" : "text/plain; charset=UTF-8");

        try {
            if (text == null || text.trim().isEmpty()) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                writeError(resp, wantsJson, "Please provide text to translate");
                return;
            }

            if (text.length() > MAX_TEXT_LENGTH) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                writeError(resp, wantsJson, "Text exceeds maximum length of " + MAX_TEXT_LENGTH + " characters");
                return;
            }

            if (lang == null || lang.trim().isEmpty()) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                writeError(resp, wantsJson, "Please select a language");
                return;
            }

            // Validate language code (only allow known codes)
            if (!isValidLanguage(lang.trim())) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                writeError(resp, wantsJson, "Unsupported language code: " + lang);
                return;
            }

            Translator.TranslationResult result = Translator.translateWithPronunciation(text, lang);

            if (wantsJson) {
                JsonObject out = new JsonObject();
                out.addProperty("translatedText", result.translatedText);
                if (result.pronunciation != null && !result.pronunciation.isBlank()) {
                    out.addProperty("pronunciation", result.pronunciation);
                }
                out.addProperty("detectedSourceLang", "auto");
                resp.getWriter().write(GSON.toJson(out));
            } else {
                resp.getWriter().write(result.translatedText);
            }

            LOG.info("Translation successful: lang={}, inputLen={}, outputLen={}",
                    lang, text.length(), result.translatedText.length());

        } catch (Exception e) {
            LOG.error("Translation failed: {}", e.getMessage(), e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            writeError(resp, wantsJson, "Translation service temporarily unavailable. Please try again.");
        }
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type, Accept");
        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    private JsonObject readJsonBody(HttpServletRequest req) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return GSON.fromJson(sb.toString(), JsonObject.class);
        } catch (Exception ex) {
            LOG.warn("Failed to parse JSON body: {}", ex.getMessage());
            return null;
        }
    }

    private void writeError(HttpServletResponse resp, boolean wantsJson, String message) throws IOException {
        if (wantsJson) {
            JsonObject out = new JsonObject();
            out.addProperty("error", message);
            resp.getWriter().write(GSON.toJson(out));
        } else {
            resp.getWriter().write("Error: " + message);
        }
    }

    private boolean isValidLanguage(String lang) {
        return switch (lang.toLowerCase()) {
            case "fr", "es", "de", "hi", "ja", "zh", "it", "te" -> true;
            default -> false;
        };
    }
}