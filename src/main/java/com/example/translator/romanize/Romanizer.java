package com.example.translator.romanize;

import java.util.Map;

/**
 * Romanization engine for non-Latin scripts.
 * Supports Japanese (Hiragana/Katakana), Hindi (Devanagari), and Telugu.
 */
public final class Romanizer {

    private Romanizer() {}

    public static String romanize(String text, String targetLang) {
        if (text == null || text.isBlank()) return null;
        String lang = targetLang == null ? "" : targetLang.toLowerCase();
        return switch (lang) {
            case "ja" -> romanizeJapanese(text);
            case "hi", "hi-in" -> transliterateIndic(text, HINDI_CONSONANTS, HINDI_VOWELS, HINDI_MATRAS);
            case "te", "te-in" -> transliterateIndic(text, TELUGU_CONSONANTS, TELUGU_VOWELS, TELUGU_MATRAS);
            default -> null;
        };
    }

    // ---- Japanese ----

    static String romanizeJapanese(String text) {
        if (text == null) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            // Small tsu doubles next consonant
            if ((c == '\u3063' || c == '\u30C3') && i + 1 < text.length()) {
                String next = JAPANESE_ROMAN.get(text.charAt(i + 1));
                if (next != null && !next.isEmpty()) {
                    sb.append(next.charAt(0));
                    continue;
                }
            }
            // Digraphs (two-char lookahead)
            if (i + 1 < text.length()) {
                String pair = "" + c + text.charAt(i + 1);
                String mapped = JAPANESE_PAIR_ROMAN.get(pair);
                if (mapped != null) {
                    sb.append(mapped);
                    i++;
                    continue;
                }
            }
            String mapped = JAPANESE_ROMAN.get(c);
            if (mapped != null) {
                sb.append(mapped);
            } else if (c < 128) {
                sb.append(c);
            }
        }
        String out = sb.toString().trim();
        return out.isEmpty() ? null : out;
    }

    // ---- Indic (Hindi, Telugu) ----

    static String transliterateIndic(String text, Map<Character, String> consonants,
                                     Map<Character, String> vowels, Map<Character, String> matras) {
        if (text == null) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u094D' || c == '\u0C4D') { // virama/halant
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) == 'a') {
                    sb.deleteCharAt(sb.length() - 1);
                }
                continue;
            }
            String vowel = vowels.get(c);
            if (vowel != null) { sb.append(vowel); continue; }
            String matra = matras.get(c);
            if (matra != null) {
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) == 'a') {
                    sb.deleteCharAt(sb.length() - 1);
                }
                sb.append(matra);
                continue;
            }
            String cons = consonants.get(c);
            if (cons != null) { sb.append(cons).append('a'); continue; }
            if (c < 128) sb.append(c);
        }
        String out = sb.toString().trim();
        return out.isEmpty() ? null : out;
    }

    // ---- Lookup tables ----

    static final Map<Character, String> JAPANESE_ROMAN = Map.ofEntries(
        // Hiragana
        Map.entry('\u3042', "a"), Map.entry('\u3044', "i"), Map.entry('\u3046', "u"), Map.entry('\u3048', "e"), Map.entry('\u304A', "o"),
        Map.entry('\u304B', "ka"), Map.entry('\u304D', "ki"), Map.entry('\u304F', "ku"), Map.entry('\u3051', "ke"), Map.entry('\u3053', "ko"),
        Map.entry('\u3055', "sa"), Map.entry('\u3057', "shi"), Map.entry('\u3059', "su"), Map.entry('\u305B', "se"), Map.entry('\u305D', "so"),
        Map.entry('\u305F', "ta"), Map.entry('\u3061', "chi"), Map.entry('\u3064', "tsu"), Map.entry('\u3066', "te"), Map.entry('\u3068', "to"),
        Map.entry('\u306A', "na"), Map.entry('\u306B', "ni"), Map.entry('\u306C', "nu"), Map.entry('\u306D', "ne"), Map.entry('\u306E', "no"),
        Map.entry('\u306F', "ha"), Map.entry('\u3072', "hi"), Map.entry('\u3075', "fu"), Map.entry('\u3078', "he"), Map.entry('\u307B', "ho"),
        Map.entry('\u307E', "ma"), Map.entry('\u307F', "mi"), Map.entry('\u3080', "mu"), Map.entry('\u3081', "me"), Map.entry('\u3082', "mo"),
        Map.entry('\u3084', "ya"), Map.entry('\u3086', "yu"), Map.entry('\u3088', "yo"),
        Map.entry('\u3089', "ra"), Map.entry('\u308A', "ri"), Map.entry('\u308B', "ru"), Map.entry('\u308C', "re"), Map.entry('\u308D', "ro"),
        Map.entry('\u308F', "wa"), Map.entry('\u3092', "o"), Map.entry('\u3093', "n"),
        Map.entry('\u304C', "ga"), Map.entry('\u304E', "gi"), Map.entry('\u3050', "gu"), Map.entry('\u3052', "ge"), Map.entry('\u3054', "go"),
        Map.entry('\u3056', "za"), Map.entry('\u3058', "ji"), Map.entry('\u305A', "zu"), Map.entry('\u305C', "ze"), Map.entry('\u305E', "zo"),
        Map.entry('\u3060', "da"), Map.entry('\u3062', "ji"), Map.entry('\u3065', "zu"), Map.entry('\u3067', "de"), Map.entry('\u3069', "do"),
        Map.entry('\u3070', "ba"), Map.entry('\u3073', "bi"), Map.entry('\u3076', "bu"), Map.entry('\u3079', "be"), Map.entry('\u307C', "bo"),
        Map.entry('\u3071', "pa"), Map.entry('\u3074', "pi"), Map.entry('\u3077', "pu"), Map.entry('\u307A', "pe"), Map.entry('\u307D', "po"),
        // Katakana
        Map.entry('\u30A2', "a"), Map.entry('\u30A4', "i"), Map.entry('\u30A6', "u"), Map.entry('\u30A8', "e"), Map.entry('\u30AA', "o"),
        Map.entry('\u30AB', "ka"), Map.entry('\u30AD', "ki"), Map.entry('\u30AF', "ku"), Map.entry('\u30B1', "ke"), Map.entry('\u30B3', "ko"),
        Map.entry('\u30B5', "sa"), Map.entry('\u30B7', "shi"), Map.entry('\u30B9', "su"), Map.entry('\u30BB', "se"), Map.entry('\u30BD', "so"),
        Map.entry('\u30BF', "ta"), Map.entry('\u30C1', "chi"), Map.entry('\u30C4', "tsu"), Map.entry('\u30C6', "te"), Map.entry('\u30C8', "to"),
        Map.entry('\u30CA', "na"), Map.entry('\u30CB', "ni"), Map.entry('\u30CC', "nu"), Map.entry('\u30CD', "ne"), Map.entry('\u30CE', "no"),
        Map.entry('\u30CF', "ha"), Map.entry('\u30D2', "hi"), Map.entry('\u30D5', "fu"), Map.entry('\u30D8', "he"), Map.entry('\u30DB', "ho"),
        Map.entry('\u30DE', "ma"), Map.entry('\u30DF', "mi"), Map.entry('\u30E0', "mu"), Map.entry('\u30E1', "me"), Map.entry('\u30E2', "mo"),
        Map.entry('\u30E4', "ya"), Map.entry('\u30E6', "yu"), Map.entry('\u30E8', "yo"),
        Map.entry('\u30E9', "ra"), Map.entry('\u30EA', "ri"), Map.entry('\u30EB', "ru"), Map.entry('\u30EC', "re"), Map.entry('\u30ED', "ro"),
        Map.entry('\u30EF', "wa"), Map.entry('\u30F2', "o"), Map.entry('\u30F3', "n"),
        Map.entry('\u30AC', "ga"), Map.entry('\u30AE', "gi"), Map.entry('\u30B0', "gu"), Map.entry('\u30B2', "ge"), Map.entry('\u30B4', "go"),
        Map.entry('\u30B6', "za"), Map.entry('\u30B8', "ji"), Map.entry('\u30BA', "zu"), Map.entry('\u30BC', "ze"), Map.entry('\u30BE', "zo"),
        Map.entry('\u30C0', "da"), Map.entry('\u30C2', "ji"), Map.entry('\u30C5', "zu"), Map.entry('\u30C7', "de"), Map.entry('\u30C9', "do"),
        Map.entry('\u30D0', "ba"), Map.entry('\u30D3', "bi"), Map.entry('\u30D6', "bu"), Map.entry('\u30D9', "be"), Map.entry('\u30DC', "bo"),
        Map.entry('\u30D1', "pa"), Map.entry('\u30D4', "pi"), Map.entry('\u30D7', "pu"), Map.entry('\u30DA', "pe"), Map.entry('\u30DD', "po")
    );

    static final Map<String, String> JAPANESE_PAIR_ROMAN = Map.ofEntries(
        Map.entry("\u304D\u3083", "kya"), Map.entry("\u304D\u3085", "kyu"), Map.entry("\u304D\u3087", "kyo"),
        Map.entry("\u3057\u3083", "sha"), Map.entry("\u3057\u3085", "shu"), Map.entry("\u3057\u3087", "sho"),
        Map.entry("\u3061\u3083", "cha"), Map.entry("\u3061\u3085", "chu"), Map.entry("\u3061\u3087", "cho"),
        Map.entry("\u306B\u3083", "nya"), Map.entry("\u306B\u3085", "nyu"), Map.entry("\u306B\u3087", "nyo"),
        Map.entry("\u3072\u3083", "hya"), Map.entry("\u3072\u3085", "hyu"), Map.entry("\u3072\u3087", "hyo"),
        Map.entry("\u307F\u3083", "mya"), Map.entry("\u307F\u3085", "myu"), Map.entry("\u307F\u3087", "myo"),
        Map.entry("\u308A\u3083", "rya"), Map.entry("\u308A\u3085", "ryu"), Map.entry("\u308A\u3087", "ryo"),
        Map.entry("\u30AD\u30E3", "kya"), Map.entry("\u30AD\u30E5", "kyu"), Map.entry("\u30AD\u30E7", "kyo"),
        Map.entry("\u30B7\u30E3", "sha"), Map.entry("\u30B7\u30E5", "shu"), Map.entry("\u30B7\u30E7", "sho"),
        Map.entry("\u30C1\u30E3", "cha"), Map.entry("\u30C1\u30E5", "chu"), Map.entry("\u30C1\u30E7", "cho"),
        Map.entry("\u30CB\u30E3", "nya"), Map.entry("\u30CB\u30E5", "nyu"), Map.entry("\u30CB\u30E7", "nyo"),
        Map.entry("\u30D2\u30E3", "hya"), Map.entry("\u30D2\u30E5", "hyu"), Map.entry("\u30D2\u30E7", "hyo"),
        Map.entry("\u30DF\u30E3", "mya"), Map.entry("\u30DF\u30E5", "myu"), Map.entry("\u30DF\u30E7", "myo"),
        Map.entry("\u30EA\u30E3", "rya"), Map.entry("\u30EA\u30E5", "ryu"), Map.entry("\u30EA\u30E7", "ryo")
    );

    static final Map<Character, String> HINDI_CONSONANTS = Map.ofEntries(
        Map.entry('\u0915', "k"), Map.entry('\u0916', "kh"), Map.entry('\u0917', "g"), Map.entry('\u0918', "gh"), Map.entry('\u0919', "n"),
        Map.entry('\u091A', "ch"), Map.entry('\u091B', "chh"), Map.entry('\u091C', "j"), Map.entry('\u091D', "jh"), Map.entry('\u091E', "n"),
        Map.entry('\u091F', "t"), Map.entry('\u0920', "th"), Map.entry('\u0921', "d"), Map.entry('\u0922', "dh"), Map.entry('\u0923', "n"),
        Map.entry('\u0924', "t"), Map.entry('\u0925', "th"), Map.entry('\u0926', "d"), Map.entry('\u0927', "dh"), Map.entry('\u0928', "n"),
        Map.entry('\u092A', "p"), Map.entry('\u092B', "ph"), Map.entry('\u092C', "b"), Map.entry('\u092D', "bh"), Map.entry('\u092E', "m"),
        Map.entry('\u092F', "y"), Map.entry('\u0930', "r"), Map.entry('\u0932', "l"), Map.entry('\u0935', "v"),
        Map.entry('\u0936', "sh"), Map.entry('\u0937', "sh"), Map.entry('\u0938', "s"), Map.entry('\u0939', "h")
    );

    static final Map<Character, String> HINDI_VOWELS = Map.ofEntries(
        Map.entry('\u0905', "a"), Map.entry('\u0906', "aa"), Map.entry('\u0907', "i"), Map.entry('\u0908', "ii"),
        Map.entry('\u0909', "u"), Map.entry('\u090A', "uu"), Map.entry('\u090F', "e"), Map.entry('\u0910', "ai"),
        Map.entry('\u0913', "o"), Map.entry('\u0914', "au"), Map.entry('\u090B', "ri"), Map.entry('\u0960', "rri"),
        Map.entry('\u090C', "li"), Map.entry('\u0902', "n"), Map.entry('\u0903', "h"), Map.entry('\u0901', "n")
    );

    static final Map<Character, String> HINDI_MATRAS = Map.ofEntries(
        Map.entry('\u093E', "aa"), Map.entry('\u093F', "i"), Map.entry('\u0940', "ii"), Map.entry('\u0941', "u"),
        Map.entry('\u0942', "uu"), Map.entry('\u0947', "e"), Map.entry('\u0948', "ai"), Map.entry('\u094B', "o"),
        Map.entry('\u094C', "au"), Map.entry('\u0943', "ri"), Map.entry('\u0944', "rri"), Map.entry('\u094D', ""),
        Map.entry('\u0902', "n"), Map.entry('\u0903', "h"), Map.entry('\u0901', "n")
    );

    static final Map<Character, String> TELUGU_CONSONANTS = Map.ofEntries(
        Map.entry('\u0C15', "k"), Map.entry('\u0C16', "kh"), Map.entry('\u0C17', "g"), Map.entry('\u0C18', "gh"), Map.entry('\u0C19', "n"),
        Map.entry('\u0C1A', "ch"), Map.entry('\u0C1B', "chh"), Map.entry('\u0C1C', "j"), Map.entry('\u0C1D', "jh"), Map.entry('\u0C1E', "n"),
        Map.entry('\u0C1F', "t"), Map.entry('\u0C20', "th"), Map.entry('\u0C21', "d"), Map.entry('\u0C22', "dh"), Map.entry('\u0C23', "n"),
        Map.entry('\u0C24', "t"), Map.entry('\u0C25', "th"), Map.entry('\u0C26', "d"), Map.entry('\u0C27', "dh"), Map.entry('\u0C28', "n"),
        Map.entry('\u0C2A', "p"), Map.entry('\u0C2B', "ph"), Map.entry('\u0C2C', "b"), Map.entry('\u0C2D', "bh"), Map.entry('\u0C2E', "m"),
        Map.entry('\u0C2F', "y"), Map.entry('\u0C30', "r"), Map.entry('\u0C32', "l"), Map.entry('\u0C35', "v"),
        Map.entry('\u0C36', "sh"), Map.entry('\u0C37', "sh"), Map.entry('\u0C38', "s"), Map.entry('\u0C39', "h")
    );

    static final Map<Character, String> TELUGU_VOWELS = Map.ofEntries(
        Map.entry('\u0C05', "a"), Map.entry('\u0C06', "aa"), Map.entry('\u0C07', "i"), Map.entry('\u0C08', "ii"),
        Map.entry('\u0C09', "u"), Map.entry('\u0C0A', "uu"), Map.entry('\u0C0E', "e"), Map.entry('\u0C0F', "ee"),
        Map.entry('\u0C10', "ai"), Map.entry('\u0C12', "o"), Map.entry('\u0C13', "oo"), Map.entry('\u0C14', "au"),
        Map.entry('\u0C0B', "ri"), Map.entry('\u0C60', "rri"), Map.entry('\u0C0C', "li"), Map.entry('\u0C61', "lli"),
        Map.entry('\u0C01', "n"), Map.entry('\u0C02', "m"), Map.entry('\u0C03', "h")
    );

    static final Map<Character, String> TELUGU_MATRAS = Map.ofEntries(
        Map.entry('\u0C3E', "aa"), Map.entry('\u0C3F', "i"), Map.entry('\u0C40', "ii"), Map.entry('\u0C41', "u"),
        Map.entry('\u0C42', "uu"), Map.entry('\u0C46', "e"), Map.entry('\u0C47', "ee"), Map.entry('\u0C48', "ai"),
        Map.entry('\u0C4A', "o"), Map.entry('\u0C4B', "oo"), Map.entry('\u0C4C', "au"), Map.entry('\u0C43', "ri"),
        Map.entry('\u0C44', "rri"), Map.entry('\u0C01', "n"), Map.entry('\u0C02', "m"), Map.entry('\u0C03', "h"),
        Map.entry('\u0C4D', "")
    );
}
