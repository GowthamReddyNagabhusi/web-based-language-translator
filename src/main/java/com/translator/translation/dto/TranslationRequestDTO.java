package com.translator.translation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TranslationRequestDTO {
    @NotBlank
    private String sourceText;
    
    @NotBlank
    private String targetLanguage;
    
    private String sourceLanguage = "auto";
}
