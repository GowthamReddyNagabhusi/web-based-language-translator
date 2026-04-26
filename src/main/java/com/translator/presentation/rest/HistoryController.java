package com.translator.presentation.rest;

import com.translator.translation.dto.HistoryStatsDTO;
import com.translator.translation.model.Translation;
import com.translator.translation.repository.TranslationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/history")
public class HistoryController {

    private final TranslationRepository translationRepository;

    public HistoryController(TranslationRepository translationRepository) {
        this.translationRepository = translationRepository;
    }

    @GetMapping
    public ResponseEntity<Page<Translation>> getHistory(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(required = false) String targetLanguage,
            @RequestParam(required = false) String search,
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
    public ResponseEntity<HistoryStatsDTO> getStats(@AuthenticationPrincipal UUID userId) {
        long total = translationRepository.countByUserId(userId);
        long favorites = translationRepository.countByUserIdAndIsFavoriteTrue(userId);
        long thisWeek = translationRepository.countByUserIdAndCreatedAtAfter(userId, OffsetDateTime.now().minusDays(7));
        
        return ResponseEntity.ok(HistoryStatsDTO.builder()
                .totalTranslations(total)
                .favoriteCount(favorites)
                .translationsThisWeek(thisWeek)
                .mostUsedLanguage(null) // omitted for brevity without aggregation query
                .cacheHitRate(0.0) // omitted for brevity
                .build());
    }

    @PatchMapping("/{id}/favorite")
    public ResponseEntity<Void> toggleFavorite(@PathVariable UUID id, @AuthenticationPrincipal UUID userId) {
        Translation translation = translationRepository.findById(id).orElseThrow();
        if (!translation.getUser().getId().equals(userId)) {
            return ResponseEntity.status(403).build();
        }
        
        translation.setFavorite(!translation.isFavorite());
        translationRepository.save(translation);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTranslation(@PathVariable UUID id, @AuthenticationPrincipal UUID userId) {
        Translation translation = translationRepository.findById(id).orElseThrow();
        if (!translation.getUser().getId().equals(userId)) {
            return ResponseEntity.status(403).build();
        }
        
        translationRepository.delete(translation);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteAllHistory(@AuthenticationPrincipal UUID userId) {
        // Technically normally done via bulk query or service
        Page<Translation> page = translationRepository.findByUserIdOrderByCreatedAtDesc(userId, Pageable.unpaged());
        translationRepository.deleteAllInBatch(page.getContent());
        return ResponseEntity.noContent().build();
    }
}
