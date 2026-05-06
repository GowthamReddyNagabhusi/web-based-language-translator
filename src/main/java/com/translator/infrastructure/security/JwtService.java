package com.translator.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class JwtService {

    private final KeyPair keyPair;
    private final StringRedisTemplate redisTemplate;

    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";
    private static final long ACCESS_TOKEN_EXPIRY = 15 * 60 * 1000; // 15 mins
    private static final long REFRESH_TOKEN_EXPIRY = 7L * 24 * 60 * 60 * 1000; // 7 days

    public JwtService(StringRedisTemplate redisTemplate, @Value("${jwt.secret-key-id}") String secretKeyId) {
        this.redisTemplate = redisTemplate;
        this.keyPair = generateOrLoadKeyPair(secretKeyId);
    }

    private KeyPair generateOrLoadKeyPair(String secretKeyId) {
        try {
            // For production, integrate with AWS Secrets Manager here based on secretKeyId.
            // For local/test dev simplicity per requirements, we generate an in-memory RS256 pair.
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load or generate RSA key pair", e);
        }
    }

    public String generateAccessToken(UUID userId, String role) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRY))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    public String generateRefreshToken(UUID userId) {
        return Jwts.builder()
                .subject(userId.toString())
                .id(UUID.randomUUID().toString()) // jti for blacklisting
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + REFRESH_TOKEN_EXPIRY))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }
    
    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(keyPair.getPublic())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isTokenValid(String token) {
        try {
            Claims claims = extractAllClaims(token);
            // check expiry
            if (claims.getExpiration().before(new Date())) {
                return false;
            }
            // check blacklist
            String jti = claims.getId();
            if (jti != null && Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + jti))) {
                return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(extractAllClaims(token).getSubject());
    }

    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
    }

    public void blacklistToken(String token) {
        Claims claims = extractAllClaims(token);
        String jti = claims.getId();
        if (jti != null) {
            long timeToLive = claims.getExpiration().getTime() - System.currentTimeMillis();
            if (timeToLive > 0) {
                redisTemplate.opsForValue().set(BLACKLIST_PREFIX + jti, "blacklisted", timeToLive, TimeUnit.MILLISECONDS);
            }
        }
    }
}
