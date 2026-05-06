package com.translator.infrastructure.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    
    @Mock
    private ValueOperations<String, String> valueOperations;

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(redisTemplate, "test-secret");
    }

    @Test
    void shouldGenerateAndValidateAccessToken() {
        UUID userId = UUID.randomUUID();
        String role = "USER";

        String token = jwtService.generateAccessToken(userId, role);

        assertThat(token).isNotNull().isNotEmpty();
        
        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        boolean isValid = jwtService.isTokenValid(token);
        assertThat(isValid).isTrue();

        UUID extractedId = jwtService.extractUserId(token);
        String extractedRole = jwtService.extractRole(token);

        assertThat(extractedId).isEqualTo(userId);
        assertThat(extractedRole).isEqualTo(role);
    }

    @Test
    void shouldGenerateAndValidateRefreshToken() {
        UUID userId = UUID.randomUUID();

        String token = jwtService.generateRefreshToken(userId);

        assertThat(token).isNotNull().isNotEmpty();
        
        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        boolean isValid = jwtService.isTokenValid(token);
        assertThat(isValid).isTrue();

        UUID extractedId = jwtService.extractUserId(token);

        assertThat(extractedId).isEqualTo(userId);
    }
}
