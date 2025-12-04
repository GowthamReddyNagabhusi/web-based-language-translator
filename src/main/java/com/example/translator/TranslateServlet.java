package com.example.translator;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.io.BufferedReader;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class TranslateServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        String text = null;
        String lang = null;

        String contentType = req.getContentType();
        if (contentType != null && contentType.toLowerCase().contains("application/json")) {
            // parse JSON body
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = req.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            try {
                Gson gson = new Gson();
                JsonObject obj = gson.fromJson(sb.toString(), JsonObject.class);
                if (obj != null) {
                    if (obj.has("text") && !obj.get("text").isJsonNull()) text = obj.get("text").getAsString();
                    if (obj.has("lang") && !obj.get("lang").isJsonNull()) lang = obj.get("lang").getAsString();
                }
            } catch (Exception ex) {
                System.out.println("Failed to parse JSON body: " + ex.getMessage());
            }
        }

        // fallback to form parameters if not provided via JSON
        if (text == null) text = req.getParameter("text");
        if (lang == null) lang = req.getParameter("lang");

        System.out.println("Received - TEXT: " + text + ", LANG: " + lang + ", contentType=" + contentType);

        resp.setContentType("text/plain; charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");

        try {
            if (text == null || text.trim().isEmpty()) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("Error: Please provide text to translate");
                return;
            }

            if (lang == null || lang.trim().isEmpty()) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("Error: Please select a language");
                return;
            }

            String translated = Translator.translate(text, lang);
            resp.getWriter().write(translated);

        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("Error: " + e.getMessage());
        }
    }
}