package com.translator.presentation.rest;

import com.translator.translation.dto.HistoryStatsDTO;
import com.translator.translation.model.Translation;
import com.translator.translation.repository.TranslationRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/history")
@Tag(name = "History", description = "Browse, filter, favourite, and manage translation history")
@SecurityRequirement(name = "bearerAuth")
public class HistoryController {

    private final TranslationRepository translationRepository;

    public HistoryController(TranslationRepository translationRepository) {
        this.translationRepository = translationRepository;
    }

    @GetMapping
    @Operation(summary = "Get translation history",
               description = "Paginated list of the authenticated user's translations. " +
                       "Can be filtered by target language, full-text search, or favourites-only.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Page of translations"),
        @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ResponseEntity<Page<Translation>> getHistory(
            @AuthenticationPrincipal UUID userId,
            @Parameter(description = "Filter by target language code, e.g. 'hi'")
            @RequestParam(required = false) String targetLanguage,
            @Parameter(description = "Full-text search on source text")
            @RequestParam(required = false) String search,
            @Parameter(description = "Return only favourited translations")
            @RequestParam(required = false, defaultValue = "false") boolean favoritesOnly,
            Pageable pageable) {

        Page<Translation> results;
        if (favoritesOnly) {
            results = translationRepository.findByUserIdAndIsFavoriteTrueOrderByCreatedAtDesc(userId, pageable);
        } else if (search != null && !search.trim().isEmpty()) {
            results = translationRepository.findByUserIdAndSourceTextContainingIgnoreCaseOrderByCreatedAtDesc(userId, search, pageable);
        } else if (targetLanguage != null && !targetLanguage.trim().isEmpty()) {
            results = translationRepository.findByUserIdAndTargetLanguageOrderByCreatedAtDesc(userId, targetLanguage, pageable);
        } else {
            results = translationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        }

        return ResponseEntity.ok(results);
    }

    @GetMapping("/stats")
    @Operation(summary = "Translation stats", description = "Aggregate stats for the authenticated user's history")
    @ApiResponse(responseCode = "200", description = "Stats returned successfully")
    public ResponseEntity<HistoryStatsDTO> getStats(@AuthenticationPrincipal UUID userId) {
        long total = translationRepository.countByUserId(userId);
        long favorites = translationRepository.countByUserIdAndIsFavoriteTrue(userId);
        long thisWeek = translationRepository.countByUserIdAndCreatedAtAfter(userId, OffsetDateTime.now().minusDays(7));

        // Fetch most-used language if any translations exist
        String mostUsed = translationRepository.findMostUsedLanguagesByUserId(userId, Pageable.ofSize(1))
                .stream().findFirst().orElse(null);

        return ResponseEntity.ok(HistoryStatsDTO.builder()
                .totalTranslations(total)
                .favoriteCount(favorites)
                .translationsThisWeek(thisWeek)
                .mostUsedLanguage(mostUsed)
                .cacheHitRate(0.0)
                .build());
    }

    @PatchMapping("/{id}/favorite")
    @Operation(summary = "Toggle favourite", description = "Toggles the favourite flag on a translation record")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Toggled successfully"),
        @ApiResponse(responseCode = "403", description = "Translation belongs to another user"),
        @ApiResponse(responseCode = "404", description = "Translation not found")
    })
    public ResponseEntity<Void> toggleFavorite(
            @Parameter(description = "UUID of the translation")
            @PathVariable UUID id,
            @AuthenticationPrincipal UUID userId) {

        // H1 fix: use existsByIdAndUserId to avoid lazy-loading the User association
        Translation translation = translationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Translation not found"));

        if (!translationRepository.existsByIdAndUserId(id, userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        translation.setFavorite(!translation.isFavorite());
        translationRepository.save(translation);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a translation", description = "Permanently deletes a single translation record")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Deleted"),
        @ApiResponse(responseCode = "403", description = "Not your record"),
        @ApiResponse(responseCode = "404", description = "Translation not found")
    })
    public ResponseEntity<Void> deleteTranslation(
            @PathVariable UUID id,
            @AuthenticationPrincipal UUID userId) {

        // H1 fix: use existsByIdAndUserId to avoid lazy-loading the User association
        Translation translation = translationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Translation not found"));

        if (!translationRepository.existsByIdAndUserId(id, userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        translationRepository.delete(translation);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    @Operation(summary = "Delete all history", description = "Permanently deletes all translations for the authenticated user")
    @ApiResponse(responseCode = "204", description = "All history deleted")
    public ResponseEntity<Void> deleteAllHistory(@AuthenticationPrincipal UUID userId) {
        // L3 fix: single JPQL DELETE — no records loaded into memory
        translationRepository.deleteAllByUserId(userId);
        return ResponseEntity.noContent().build();
    }
}


