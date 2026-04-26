package com.translator.translation.repository;

import com.translator.translation.model.Translation;
import com.translator.user.model.Role;
import com.translator.user.model.User;
import com.translator.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TranslationRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
    }

    @Autowired
    private TranslationRepository translationRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(UUID.randomUUID())
                .email("translatortest@test.com")
                .passwordHash("hash")
                .role(Role.USER)
                .build();
        userRepository.saveAndFlush(testUser);
    }

    @Test
    void shouldSaveAndRetrieveTranslationWithJsonb() {
        Translation translation = Translation.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .sourceText("Hello")
                .translatedText("Hola")
                .targetLanguage("es")
                .metadata(Map.of("wordCount", 1))
                .isFavorite(true)
                .build();
        
        translationRepository.saveAndFlush(translation);

        Page<Translation> page = translationRepository.findByUserIdAndIsFavoriteTrueOrderByCreatedAtDesc(testUser.getId(), PageRequest.of(0, 10));
        
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getMetadata()).containsEntry("wordCount", 1);
    }

    @Test
    void shouldSearchBySourceText() {
        Translation t1 = Translation.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .sourceText("The quick brown fox")
                .translatedText("El zorro marron")
                .targetLanguage("es")
                .build();
        translationRepository.saveAndFlush(t1);

        Page<Translation> results = translationRepository.findByUserIdAndSourceTextContainingIgnoreCaseOrderByCreatedAtDesc(
                testUser.getId(), "brown", PageRequest.of(0, 10));

        assertThat(results.getContent()).hasSize(1);
    }

    @Test
    void shouldCountRecentTranslations() {
        Translation t1 = Translation.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .sourceText("Count me")
                .translatedText("Cuentame")
                .targetLanguage("es")
                .createdAt(OffsetDateTime.now())
                .build();
        translationRepository.saveAndFlush(t1);

        long count = translationRepository.countByUserIdAndCreatedAtAfter(testUser.getId(), OffsetDateTime.now().minusDays(1));
        assertThat(count).isEqualTo(1);
    }
}
