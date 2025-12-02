package com.example.translator;

import com.google.gson.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class Translator {

    private static final String API_KEY = "";

    public static String translate(String text, String targetLang) throws Exception {

        String prompt = "Translate this to " + targetLang + ":\n" + text;

        URL url = new URL(
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent?key=" + API_KEY
        );

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");

        JsonObject req = new JsonObject();
        JsonArray contents = new JsonArray();
        JsonObject wrapper = new JsonObject();
        JsonArray parts = new JsonArray();

        JsonObject textObj = new JsonObject();
        textObj.addProperty("text", prompt);
        parts.add(textObj);

        wrapper.add("parts", parts);
        contents.add(wrapper);
        req.add("contents", contents);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(req.toString().getBytes());
        }

        BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream())
        );

        StringBuilder response = new StringBuilder();
        String line;

        while ((line = br.readLine()) != null) response.append(line);

        JsonObject json = JsonParser.parseString(response.toString()).getAsJsonObject();

        return json.getAsJsonArray("candidates")
                .get(0).getAsJsonObject()
                .getAsJsonObject("content")
                .getAsJsonArray("parts")
                .get(0).getAsJsonObject()
                .get("text").getAsString();
    }
}
