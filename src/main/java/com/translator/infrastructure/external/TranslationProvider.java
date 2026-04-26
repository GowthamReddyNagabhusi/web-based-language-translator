package com.translator.infrastructure.external;

public interface TranslationProvider {
    String translate(String text, String sourceLang, String targetLang);
    String getProviderName();
    int getPriority(); // 1 = highest
}
