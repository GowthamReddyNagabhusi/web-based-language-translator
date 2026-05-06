package com.translator.presentation.rest;

import com.translator.translation.dto.TranslationRequestDTO;
import com.translator.translation.dto.TranslationResponseDTO;
import com.translator.translation.service.TranslationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Translation", description = "Translate text between 75+ language pairs")
@SecurityRequirement(name = "bearerAuth")
public class TranslationController {

    private final TranslationService translationService;

    public TranslationController(TranslationService translationService) {
        this.translationService = translationService;
    }

    @PostMapping
    @Operation(summary = "Translate text",
               description = "Translates source text to target language. Uses L1 Caffeine → L2 Redis → " +
                       "AWS Translate → LibreTranslate fallback chain. Results are cached and persisted asynchronously.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Translation successful"),
        @ApiResponse(responseCode = "400", description = "Missing or invalid fields"),
        @ApiResponse(responseCode = "401", description = "Not authenticated"),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded — 100 requests/day for USER role"),
        @ApiResponse(responseCode = "500", description = "All translation providers failed")
    })
    public ResponseEntity<TranslationResponseDTO> translate(
            @Valid @RequestBody TranslationRequestDTO request,
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(translationService.translate(request, userId));
    }
}
