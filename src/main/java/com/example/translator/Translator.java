package com.example.translator;

import com.google.gson.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class Translator {

    // Prefer LibreTranslate endpoints; fall back to MyMemory if public instances require keys.
    private static final String[] LIBRE_ENDPOINTS = new String[] {
        "https://libretranslate.com/translate",
        "https://translate.argosopentech.com/translate"
    };
    private static final String MYMEMORY_API = "https://api.mymemory.translated.net/get";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(8);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    public static class TranslationResult {
        public final String translatedText;
        public final String pronunciation;

        public TranslationResult(String translatedText, String pronunciation) {
            this.translatedText = translatedText;
            this.pronunciation = pronunciation;
        }
    }

    public static String translate(String text, String targetLang) throws Exception {
        return translateWithPronunciation(text, targetLang).translatedText;
    }

    public static TranslationResult translateWithPronunciation(String text, String targetLang) throws Exception {

        if (text == null || text.trim().isEmpty()) {
            throw new Exception("Text cannot be empty");
        }

        if (targetLang == null || targetLang.trim().isEmpty()) {
            throw new Exception("Target language code is required");
        }

        String normalizedTarget = targetLang.trim();

        String pronunciation = null;

        // Prepare payload
        JsonObject payload = new JsonObject();
        payload.addProperty("q", text);
        payload.addProperty("source", "auto");
        payload.addProperty("target", normalizedTarget);
        payload.addProperty("format", "text");

        String requestBody = payload.toString();

        // First try the unofficial Google Translate web endpoint (fast, no key).
        try {
            String googleUrl = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl="
                    + java.net.URLEncoder.encode(normalizedTarget, StandardCharsets.UTF_8)
                    + "&dt=t&dt=rm&q=" + java.net.URLEncoder.encode(text, StandardCharsets.UTF_8);
            HttpRequest greq = HttpRequest.newBuilder()
                    .uri(URI.create(googleUrl))
                    .header("Accept", "application/json")
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> gresp = HTTP_CLIENT.send(greq, BodyHandlers.ofString(StandardCharsets.UTF_8));
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
                                    // many responses include transliteration/romanization around index 2 or 3
                                    if (seg.size() > 2 && !seg.get(2).isJsonNull()) {
                                        String candidate = seg.get(2).getAsString();
                                        if (candidate != null && !candidate.trim().isEmpty()) {
                                            pronunciation = candidate.trim();
                                        }
                                    }
                                    if (seg.size() > 3 && !seg.get(3).isJsonNull()) {
                                        String candidate = seg.get(3).getAsString();
                                        if (candidate != null && !candidate.trim().isEmpty()) {
                                            pronunciation = candidate.trim();
                                        }
                                    }
                                    // some responses include transliteration arrays at index 4 or 5
                                    if ((pronunciation == null || pronunciation.isBlank()) && seg.size() > 4 && seg.get(4).isJsonArray()) {
                                        pronunciation = firstStringFromArray(seg.get(4).getAsJsonArray());
                                    }
                                    if ((pronunciation == null || pronunciation.isBlank()) && seg.size() > 5 && seg.get(5).isJsonArray()) {
                                        pronunciation = firstStringFromArray(seg.get(5).getAsJsonArray());
                                    }
                                    // some responses tuck the transliteration under a nested array near the end
                                    if (seg.size() > 4 && seg.get(4).isJsonArray()) {
                                        JsonArray translits = seg.get(4).getAsJsonArray();
                                        for (JsonElement tEl : translits) {
                                            if (tEl != null && tEl.isJsonPrimitive()) {
                                                String candidate = tEl.getAsString();
                                                if (candidate != null && !candidate.trim().isEmpty()) {
                                                    pronunciation = candidate.trim();
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            // another common spot for transliteration is the second top-level array
                            if ((pronunciation == null || pronunciation.isBlank()) && top.size() > 1 && top.get(1).isJsonArray()) {
                                JsonArray alt = top.get(1).getAsJsonArray();
                                for (JsonElement altEl : alt) {
                                    if (altEl != null && altEl.isJsonPrimitive()) {
                                        String candidate = altEl.getAsString();
                                        if (candidate != null && !candidate.trim().isEmpty()) {
                                            pronunciation = candidate.trim();
                                            break;
                                        }
                                    }
                                }
                            }
                            // some payloads place transliteration under the last top-level element (index 5 or 6)
                            if ((pronunciation == null || pronunciation.isBlank()) && top.size() > 5 && top.get(5).isJsonArray()) {
                                pronunciation = firstStringFromArray(top.get(5).getAsJsonArray());
                            }
                            if ((pronunciation == null || pronunciation.isBlank()) && top.size() > 6 && top.get(6).isJsonArray()) {
                                pronunciation = firstStringFromArray(top.get(6).getAsJsonArray());
                            }
                            String googleTranslation = sb.toString().trim();
                            if (!googleTranslation.isEmpty()) {
                                if (pronunciation == null || pronunciation.isBlank()) {
                                    pronunciation = romanize(googleTranslation, normalizedTarget);
                                }
                                return new TranslationResult(googleTranslation, pronunciation);
                            }
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
                        .timeout(REQUEST_TIMEOUT)
                        .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request, BodyHandlers.ofString(StandardCharsets.UTF_8));
                int status = response.statusCode();

                if (status == 200 || status == 201) {
                    String body = response.body();
                    String translation = extractTranslationFromJson(body);
                    if (translation != null && !translation.trim().isEmpty()) {
                        if (pronunciation == null || pronunciation.isBlank()) {
                            pronunciation = romanize(translation, normalizedTarget);
                        }
                        return new TranslationResult(translation, pronunciation);
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
            String url = MYMEMORY_API + "?q=" + encoded + "&langpair=en|" + normalizedTarget;
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .GET()
                    .timeout(REQUEST_TIMEOUT)
                    .build();
            HttpResponse<String> resp = HTTP_CLIENT.send(req, BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() == 200) {
                String body = resp.body();
                JsonObject jsonResponse = JsonParser.parseString(body).getAsJsonObject();
                if (jsonResponse.has("responseData")) {
                    JsonObject responseData = jsonResponse.getAsJsonObject("responseData");
                    if (responseData.has("translatedText")) {
                        String translation = responseData.get("translatedText").getAsString();
                        if (translation != null && !translation.trim().isEmpty()) {
                            if (pronunciation == null || pronunciation.isBlank()) {
                                pronunciation = romanize(translation, normalizedTarget);
                            }
                            return new TranslationResult(translation, pronunciation);
                        }
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
                        String translation = matches.get(bestIdx).getAsJsonObject().get("translation").getAsString();
                        if (pronunciation == null || pronunciation.isBlank()) {
                            pronunciation = romanize(translation, normalizedTarget);
                        }
                        return new TranslationResult(translation, pronunciation);
                    }
                }
            }
        } catch (Exception e) {
            // fall through to final failure
        }

        throw new Exception("No translation returned from available APIs");
    }

    private static String firstStringFromArray(JsonArray arr) {
        for (JsonElement el : arr) {
            if (el == null) continue;
            if (el.isJsonPrimitive()) {
                String s = el.getAsString();
                if (s != null && !s.trim().isEmpty()) return s.trim();
            } else if (el.isJsonArray()) {
                String nested = firstStringFromArray(el.getAsJsonArray());
                if (nested != null && !nested.isEmpty()) return nested;
            }
        }
        return null;
    }

    private static String romanize(String text, String targetLang) {
        if (text == null || text.isBlank()) return null;
        String lang = targetLang == null ? "" : targetLang.toLowerCase();
        switch (lang) {
            case "ja":
                return romanizeJapanese(text);
            case "hi":
            case "hi-in":
                return romanizeHindi(text);
            case "te":
            case "te-in":
                return romanizeTelugu(text);
            default:
                return null;
        }
    }

    private static String romanizeJapanese(String text) {
        if (text == null) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            // small tsu doubles next consonant
            if ((c == 'っ' || c == 'ッ') && i + 1 < text.length()) {
                String next = JAPANESE_ROMAN.get(text.charAt(i + 1));
                if (next != null && !next.isEmpty()) {
                    sb.append(next.charAt(0));
                    continue;
                }
            }
            // digraphs like きゃ / キャ etc. (two-char lookahead)
            if (i + 1 < text.length()) {
                String pair = "" + c + text.charAt(i + 1);
                String mappedPair = JAPANESE_PAIR_ROMAN.get(pair);
                if (mappedPair != null) {
                    sb.append(mappedPair);
                    i++; // consumed extra char
                    continue;
                }
            }
            String mapped = JAPANESE_ROMAN.get(c);
            if (mapped != null) {
                sb.append(mapped);
            } else if (isAscii(c)) {
                sb.append(c);
            }
            // Kanji and other symbols are skipped per requirements
        }
        String out = sb.toString().trim();
        return out.isEmpty() ? null : out;
    }

    private static String romanizeHindi(String text) {
        return transliterateIndic(text, HINDI_CONSONANTS, HINDI_VOWELS, HINDI_MATRAS);
    }

    private static String romanizeTelugu(String text) {
        return transliterateIndic(text, TELUGU_CONSONANTS, TELUGU_VOWELS, TELUGU_MATRAS);
    }

    // Simplified Indic transliterator: consonant carries implicit 'a', matra replaces it, virama removes it.
    private static String transliterateIndic(String text, java.util.Map<Character, String> consonants,
                                             java.util.Map<Character, String> vowels,
                                             java.util.Map<Character, String> matras) {
        if (text == null) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '्' || c == '్') { // virama/halant
                // remove trailing 'a' from implicit vowel
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) == 'a') {
                    sb.deleteCharAt(sb.length() - 1);
                }
                continue;
            }
            String vowel = vowels.get(c);
            if (vowel != null) {
                sb.append(vowel);
                continue;
            }
            String matra = matras.get(c);
            if (matra != null) {
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) == 'a') {
                    sb.deleteCharAt(sb.length() - 1);
                }
                sb.append(matra);
                continue;
            }
            String cons = consonants.get(c);
            if (cons != null) {
                sb.append(cons).append('a'); // implicit 'a'
                continue;
            }
            if (isAscii(c)) {
                sb.append(c);
            }
        }
        String out = sb.toString().trim();
        return out.isEmpty() ? null : out;
    }

    private static boolean isAscii(char c) {
        return c < 128;
    }

    private static final java.util.Map<Character, String> JAPANESE_ROMAN = new java.util.HashMap<>() {{
        put('あ', "a"); put('い', "i"); put('う', "u"); put('え', "e"); put('お', "o");
        put('か', "ka"); put('き', "ki"); put('く', "ku"); put('け', "ke"); put('こ', "ko");
        put('さ', "sa"); put('し', "shi"); put('す', "su"); put('せ', "se"); put('そ', "so");
        put('た', "ta"); put('ち', "chi"); put('つ', "tsu"); put('て', "te"); put('と', "to");
        put('な', "na"); put('に', "ni"); put('ぬ', "nu"); put('ね', "ne"); put('の', "no");
        put('は', "ha"); put('ひ', "hi"); put('ふ', "fu"); put('へ', "he"); put('ほ', "ho");
        put('ま', "ma"); put('み', "mi"); put('む', "mu"); put('め', "me"); put('も', "mo");
        put('や', "ya"); put('ゆ', "yu"); put('よ', "yo");
        put('ら', "ra"); put('り', "ri"); put('る', "ru"); put('れ', "re"); put('ろ', "ro");
        put('わ', "wa"); put('を', "o"); put('ん', "n");
        put('が', "ga"); put('ぎ', "gi"); put('ぐ', "gu"); put('げ', "ge"); put('ご', "go");
        put('ざ', "za"); put('じ', "ji"); put('ず', "zu"); put('ぜ', "ze"); put('ぞ', "zo");
        put('だ', "da"); put('ぢ', "ji"); put('づ', "zu"); put('で', "de"); put('ど', "do");
        put('ば', "ba"); put('び', "bi"); put('ぶ', "bu"); put('べ', "be"); put('ぼ', "bo");
        put('ぱ', "pa"); put('ぴ', "pi"); put('ぷ', "pu"); put('ぺ', "pe"); put('ぽ', "po");
        // Katakana (basic)
        put('ア', "a"); put('イ', "i"); put('ウ', "u"); put('エ', "e"); put('オ', "o");
        put('カ', "ka"); put('キ', "ki"); put('ク', "ku"); put('ケ', "ke"); put('コ', "ko");
        put('サ', "sa"); put('シ', "shi"); put('ス', "su"); put('セ', "se"); put('ソ', "so");
        put('タ', "ta"); put('チ', "chi"); put('ツ', "tsu"); put('テ', "te"); put('ト', "to");
        put('ナ', "na"); put('ニ', "ni"); put('ヌ', "nu"); put('ネ', "ne"); put('ノ', "no");
        put('ハ', "ha"); put('ヒ', "hi"); put('フ', "fu"); put('ヘ', "he"); put('ホ', "ho");
        put('マ', "ma"); put('ミ', "mi"); put('ム', "mu"); put('メ', "me"); put('モ', "mo");
        put('ヤ', "ya"); put('ユ', "yu"); put('ヨ', "yo");
        put('ラ', "ra"); put('リ', "ri"); put('ル', "ru"); put('レ', "re"); put('ロ', "ro");
        put('ワ', "wa"); put('ヲ', "o"); put('ン', "n");
        put('ガ', "ga"); put('ギ', "gi"); put('グ', "gu"); put('ゲ', "ge"); put('ゴ', "go");
        put('ザ', "za"); put('ジ', "ji"); put('ズ', "zu"); put('ゼ', "ze"); put('ゾ', "zo");
        put('ダ', "da"); put('ヂ', "ji"); put('ヅ', "zu"); put('デ', "de"); put('ド', "do");
        put('バ', "ba"); put('ビ', "bi"); put('ブ', "bu"); put('ベ', "be"); put('ボ', "bo");
        put('パ', "pa"); put('ピ', "pi"); put('プ', "pu"); put('ペ', "pe"); put('ポ', "po");
    }};

    private static final java.util.Map<String, String> JAPANESE_PAIR_ROMAN = new java.util.HashMap<>() {{
        put("きゃ", "kya"); put("きゅ", "kyu"); put("きょ", "kyo");
        put("しゃ", "sha"); put("しゅ", "shu"); put("しょ", "sho");
        put("ちゃ", "cha"); put("ちゅ", "chu"); put("ちょ", "cho");
        put("にゃ", "nya"); put("にゅ", "nyu"); put("にょ", "nyo");
        put("ひゃ", "hya"); put("ひゅ", "hyu"); put("ひょ", "hyo");
        put("みゃ", "mya"); put("みゅ", "myu"); put("みょ", "myo");
        put("りゃ", "rya"); put("りゅ", "ryu"); put("りょ", "ryo");
        put("キャ", "kya"); put("キュ", "kyu"); put("キョ", "kyo");
        put("シャ", "sha"); put("シュ", "shu"); put("ショ", "sho");
        put("チャ", "cha"); put("チュ", "chu"); put("チョ", "cho");
        put("ニャ", "nya"); put("ニュ", "nyu"); put("ニョ", "nyo");
        put("ヒャ", "hya"); put("ヒュ", "hyu"); put("ヒョ", "hyo");
        put("ミャ", "mya"); put("ミュ", "myu"); put("ミョ", "myo");
        put("リャ", "rya"); put("リュ", "ryu"); put("リョ", "ryo");
    }};

    private static final java.util.Map<Character, String> HINDI_CONSONANTS = new java.util.HashMap<>() {{
        put('क', "k"); put('ख', "kh"); put('ग', "g"); put('घ', "gh"); put('ङ', "n");
        put('च', "ch"); put('छ', "chh"); put('ज', "j"); put('झ', "jh"); put('ञ', "n");
        put('ट', "t"); put('ठ', "th"); put('ड', "d"); put('ढ', "dh"); put('ण', "n");
        put('त', "t"); put('थ', "th"); put('द', "d"); put('ध', "dh"); put('न', "n");
        put('प', "p"); put('फ', "ph"); put('ब', "b"); put('भ', "bh"); put('म', "m");
        put('य', "y"); put('र', "r"); put('ल', "l"); put('व', "v");
        put('श', "sh"); put('ष', "sh"); put('स', "s"); put('ह', "h");
    }};

    private static final java.util.Map<Character, String> HINDI_VOWELS = new java.util.HashMap<>() {{
        put('अ', "a"); put('आ', "aa"); put('इ', "i"); put('ई', "ii"); put('उ', "u"); put('ऊ', "uu");
        put('ए', "e"); put('ऐ', "ai"); put('ओ', "o"); put('औ', "au");
        put('ऋ', "ri"); put('ॠ', "rri"); put('ऌ', "li");
        put('ं', "n"); put('ः', "h"); put('ँ', "n");
    }};

    private static final java.util.Map<Character, String> HINDI_MATRAS = new java.util.HashMap<>() {{
        put('ा', "aa"); put('ि', "i"); put('ी', "ii"); put('ु', "u"); put('ू', "uu");
        put('े', "e"); put('ै', "ai"); put('ो', "o"); put('ौ', "au");
        put('ृ', "ri"); put('ॄ', "rri"); put('्', ""); put('ं', "n"); put('ः', "h"); put('ँ', "n");
    }};

    private static final java.util.Map<Character, String> TELUGU_CONSONANTS = new java.util.HashMap<>() {{
        put('క', "k"); put('ఖ', "kh"); put('గ', "g"); put('ఘ', "gh"); put('ఙ', "n");
        put('చ', "ch"); put('ఛ', "chh"); put('జ', "j"); put('ఝ', "jh"); put('ఞ', "n");
        put('ట', "t"); put('ఠ', "th"); put('డ', "d"); put('ఢ', "dh"); put('ణ', "n");
        put('త', "t"); put('థ', "th"); put('ద', "d"); put('ధ', "dh"); put('న', "n");
        put('ప', "p"); put('ఫ', "ph"); put('బ', "b"); put('భ', "bh"); put('మ', "m");
        put('య', "y"); put('ర', "r"); put('ల', "l"); put('వ', "v");
        put('శ', "sh"); put('ష', "sh"); put('స', "s"); put('హ', "h");
    }};

    private static final java.util.Map<Character, String> TELUGU_VOWELS = new java.util.HashMap<>() {{
        put('అ', "a"); put('ఆ', "aa"); put('ఇ', "i"); put('ఈ', "ii"); put('ఉ', "u"); put('ఊ', "uu");
        put('ఎ', "e"); put('ఏ', "ee"); put('ఐ', "ai"); put('ఒ', "o"); put('ఓ', "oo"); put('ఔ', "au");
        put('ఋ', "ri"); put('ౠ', "rri"); put('ఌ', "li"); put('ౡ', "lli");
        put('ఁ', "n"); put('ం', "m"); put('ః', "h");
    }};

    private static final java.util.Map<Character, String> TELUGU_MATRAS = new java.util.HashMap<>() {{
        put('ా', "aa"); put('ి', "i"); put('ీ', "ii"); put('ు', "u"); put('ూ', "uu");
        put('ె', "e"); put('ే', "ee"); put('ై', "ai"); put('ొ', "o"); put('ో', "oo"); put('ౌ', "au");
        put('ృ', "ri"); put('ౄ', "rri"); put('ఁ', "n"); put('ం', "m"); put('ః', "h");
        put('్', "");
    }};

    private static String extractTranslationFromJson(String body) {
        try {
            JsonElement parsed = JsonParser.parseString(body);
            if (parsed.isJsonObject()) {
                JsonObject obj = parsed.getAsJsonObject();
                if (obj.has("translatedText")) return obj.get("translatedText").getAsString().trim();
                if (obj.has("translation")) return obj.get("translation").getAsString().trim();
                // some instances return {"result":"..."}
                if (obj.has("result")) return obj.get("result").getAsString().trim();
            } else if (parsed.isJsonArray()) {
                JsonArray arr = parsed.getAsJsonArray();
                if (arr.size() > 0 && arr.get(0).isJsonObject()) {
                    JsonObject first = arr.get(0).getAsJsonObject();
                    if (first.has("translatedText")) return first.get("translatedText").getAsString().trim();
                    if (first.has("translation")) return first.get("translation").getAsString().trim();
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

}