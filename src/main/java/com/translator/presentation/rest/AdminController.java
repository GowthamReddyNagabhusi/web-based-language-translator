package com.translator.presentation.rest;

import com.translator.translation.dto.SystemStatsDTO;
import com.translator.translation.model.Translation;
import com.translator.translation.repository.TranslationRepository;
import com.translator.user.dto.UserSummaryDTO;
import com.translator.user.model.User;
import com.translator.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "Admin-only endpoints for user and system management")
public class AdminController {

    private final UserRepository userRepository;
    private final TranslationRepository translationRepository;

    public AdminController(UserRepository userRepository, TranslationRepository translationRepository) {
        this.userRepository = userRepository;
        this.translationRepository = translationRepository;
    }

    @GetMapping("/users")
    @Operation(summary = "List all users", description = "Returns a paginated list of all registered users with their stats")
    public ResponseEntity<Page<UserSummaryDTO>> listUsers(Pageable pageable) {
        Page<UserSummaryDTO> users = userRepository.findAll(pageable).map(this::toUserSummary);
        return ResponseEntity.ok(users);
    }

    @GetMapping("/users/{userId}/history")
    @Operation(summary = "View user history", description = "Returns paginated translation history for a specific user")
    public ResponseEntity<Page<Translation>> getUserHistory(
            @PathVariable UUID userId, Pageable pageable) {
        Page<Translation> history = translationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return ResponseEntity.ok(history);
    }

    @PatchMapping("/users/{userId}/deactivate")
    @Operation(summary = "Deactivate user", description = "Sets the user account as inactive, preventing login")
    public ResponseEntity<Void> deactivateUser(@PathVariable UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        user.setActive(false);
        user.setUpdatedAt(OffsetDateTime.now());
        userRepository.save(user);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/stats")
    @Operation(summary = "System-wide stats", description = "Returns aggregate stats: users, translations today, cache hit rates, provider breakdown")
    public ResponseEntity<SystemStatsDTO> getSystemStats() {
        long totalUsers = userRepository.count();
        long totalTranslationsToday = translationRepository.countByCreatedAtAfter(OffsetDateTime.now().withHour(0).withMinute(0).withSecond(0));

        return ResponseEntity.ok(SystemStatsDTO.builder()
                .totalUsers(totalUsers)
                .totalTranslationsToday(totalTranslationsToday)
                .cacheHitRate(0.0) // Populated from Micrometer in Phase 9
                .awsTranslateCount(0)
                .libreTranslateCount(0)
                .myMemoryCount(0)
                .build());
    }

    private UserSummaryDTO toUserSummary(User user) {
        long count = translationRepository.countByUserId(user.getId());
        return UserSummaryDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .role(user.getRole().name())
                .isActive(user.isActive())
                .translationCount(count)
                .createdAt(user.getCreatedAt())
                .build();
    }
}
