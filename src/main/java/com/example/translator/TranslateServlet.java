package com.example.translator;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class TranslateServlet extends HttpServlet {

    private static final Gson GSON = new Gson();
    private static final String JSON_CONTENT_TYPE = "application/json";

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

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

        // fallback to form parameters if not provided via JSON
        if (text == null) {
            text = req.getParameter("text");
        }
        if (lang == null) {
            lang = req.getParameter("lang");
        }

        System.out.println("Received - TEXT: " + text + ", LANG: " + lang + ", contentType=" + contentType);

        // respond in JSON when client is JSON-aware, otherwise plain text
        String accept = req.getHeader("Accept");
        boolean wantsJson = (accept != null && accept.toLowerCase().contains(JSON_CONTENT_TYPE))
                || (contentType != null && contentType.toLowerCase().contains(JSON_CONTENT_TYPE));
        resp.setContentType(wantsJson ? "application/json; charset=UTF-8" : "text/plain; charset=UTF-8");

        try {
            if (text == null || text.trim().isEmpty()) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                writeError(resp, wantsJson, "Please provide text to translate");
                return;
            }

            if (lang == null || lang.trim().isEmpty()) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                writeError(resp, wantsJson, "Please select a language");
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

        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            String msg = e.getMessage() == null ? "Internal error" : e.getMessage();
            writeError(resp, wantsJson, msg);
        }
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
            System.out.println("Failed to parse JSON body: " + ex.getMessage());
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
}