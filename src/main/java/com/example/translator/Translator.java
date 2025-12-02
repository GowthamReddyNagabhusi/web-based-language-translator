package com.example.translator;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class Translator {

    public static String translate(String text, String targetLang) throws Exception {

        String encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8.toString());
        String encodedLangPair = URLEncoder.encode("en|" + targetLang, StandardCharsets.UTF_8.toString());

        String url = "https://api.mymemory.translated.net/get?q="
                + encodedText
                + "&langpair="
                + encodedLangPair;

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(url);

            String response = client.execute(request,
                    httpResponse -> EntityUtils.toString(httpResponse.getEntity())
            );

            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            return json.getAsJsonObject("responseData").get("translatedText").getAsString();
        }
    }
}
