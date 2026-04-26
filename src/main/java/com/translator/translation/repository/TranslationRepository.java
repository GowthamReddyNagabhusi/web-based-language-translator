package com.translator.translation.repository;

import com.translator.translation.model.Translation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public interface TranslationRepository extends JpaRepository<Translation, UUID> {

    Page<Translation> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<Translation> findByUserIdAndTargetLanguageOrderByCreatedAtDesc(UUID userId, String targetLanguage, Pageable pageable);

    Page<Translation> findByUserIdAndIsFavoriteTrueOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<Translation> findByUserIdAndSourceTextContainingIgnoreCaseOrderByCreatedAtDesc(UUID userId, String sourceText, Pageable pageable);

    long countByUserId(UUID userId);

    long countByUserIdAndIsFavoriteTrue(UUID userId);

    long countByUserIdAndCreatedAtAfter(UUID userId, OffsetDateTime date);
}
