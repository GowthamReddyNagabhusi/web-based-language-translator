package com.translator.infrastructure.aws;

import com.translator.infrastructure.external.TranslationProvider;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.translate.TranslateClient;
import software.amazon.awssdk.services.translate.model.TranslateTextRequest;
import software.amazon.awssdk.services.translate.model.TranslateTextResponse;

@Component
public class AwsTranslateProvider implements TranslationProvider {

    private final TranslateClient translateClient;

    public AwsTranslateProvider(TranslateClient translateClient) {
        this.translateClient = translateClient;
    }

    @Override
    public String translate(String text, String sourceLang, String targetLang) {
        String source = (sourceLang == null || sourceLang.equalsIgnoreCase("auto")) ? "auto" : sourceLang;
        
        TranslateTextRequest request = TranslateTextRequest.builder()
                .text(text)
                .sourceLanguageCode(source)
                .targetLanguageCode(targetLang)
                .build();

        TranslateTextResponse response = translateClient.translateText(request);
        return response.translatedText();
    }

    @Override
    public String getProviderName() {
        return "AWS_TRANSLATE";
    }

    @Override
    public int getPriority() {
        return 1;
    }
}
