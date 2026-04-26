package com.translator.infrastructure.external;

import org.springframework.stereotype.Component;

@Component
public class LibreTranslateProvider implements TranslationProvider {

    @Override
    public String translate(String text, String sourceLang, String targetLang) {
        // Fallback mock logic since setting up real self-hosted is out of scope for immediate code
        throw new RuntimeException("LibreTranslate simulated failure"); 
    }

    @Override
    public String getProviderName() {
        return "LIBRE_TRANSLATE";
    }

    @Override
    public int getPriority() {
        return 2;
    }
}
