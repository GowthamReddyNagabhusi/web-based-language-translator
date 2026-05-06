package com.translator.translation.repository;

import com.translator.translation.model.Translation;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public interface TranslationRepository extends JpaRepository<Translation, UUID> {

    /** Ownership check that never triggers lazy-loading of the User association. */
    boolean existsByIdAndUserId(UUID id, UUID userId);

    /** Bulk delete without loading records into memory first (fixes L3). */
    @Modifying
    @Transactional
    @Query("DELETE FROM Translation t WHERE t.user.id = :userId")
    void deleteAllByUserId(UUID userId);

    Page<Translation> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<Translation> findByUserIdAndTargetLanguageOrderByCreatedAtDesc(UUID userId, String targetLanguage, Pageable pageable);

    Page<Translation> findByUserIdAndIsFavoriteTrueOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<Translation> findByUserIdAndSourceTextContainingIgnoreCaseOrderByCreatedAtDesc(UUID userId, String sourceText, Pageable pageable);

    long countByUserId(UUID userId);

    long countByUserIdAndIsFavoriteTrue(UUID userId);

    long countByUserIdAndCreatedAtAfter(UUID userId, OffsetDateTime date);

    long countByCreatedAtAfter(OffsetDateTime date);

    @Query("SELECT t.targetLanguage FROM Translation t WHERE t.user.id = :userId GROUP BY t.targetLanguage ORDER BY COUNT(t) DESC")
    java.util.List<String> findMostUsedLanguagesByUserId(UUID userId, Pageable pageable);
}
