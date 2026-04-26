package com.translator.presentation.rest;

import com.translator.translation.dto.TranslationRequestDTO;
import com.translator.translation.dto.TranslationResponseDTO;
import com.translator.translation.service.TranslationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/translations")
public class TranslationController {

    private final TranslationService translationService;

    public TranslationController(TranslationService translationService) {
        this.translationService = translationService;
    }

    @PostMapping
    public ResponseEntity<TranslationResponseDTO> translate(
            @Valid @RequestBody TranslationRequestDTO request,
            @AuthenticationPrincipal UUID userId) {
        // userId is extracted from JWT by JwtAuthFilter
        return ResponseEntity.ok(translationService.translate(request, userId));
    }
}
