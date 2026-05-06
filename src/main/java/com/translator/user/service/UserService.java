package com.translator.user.service;

import com.translator.infrastructure.security.JwtService;
import com.translator.user.dto.AuthResponseDTO;
import com.translator.user.dto.LoginRequestDTO;
import com.translator.user.dto.RegisterRequestDTO;
import com.translator.user.model.Role;
import com.translator.user.model.User;
import com.translator.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /**
     * Pre-computed BCrypt hash used to ensure constant-time password comparison
     * even when no user is found, preventing timing-based user enumeration (H6 fix).
     */
    private final String dummyHash;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public AuthResponseDTO register(RegisterRequestDTO request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already in use");
        }

        User user = User.builder()
                .id(UUID.randomUUID())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .build();

        userRepository.save(user);

        return buildAuthResponse(user);
    }

    public AuthResponseDTO login(LoginRequestDTO request) {
        Optional<User> maybeUser = userRepository.findByEmail(request.getEmail());

        // Always run BCrypt (cost=12) regardless of whether user exists — prevents
        // timing-based enumeration of registered email addresses (H6 fix).
        String hashToCheck = maybeUser.map(User::getPasswordHash).orElse(dummyHash);
        boolean passwordMatches = passwordEncoder.matches(request.getPassword(), hashToCheck);

        if (maybeUser.isEmpty() || !passwordMatches) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        User user = maybeUser.get();
        if (!user.isActive()) {
            throw new IllegalArgumentException("User account is inactive");
        }

        return buildAuthResponse(user);
    }

    public AuthResponseDTO refresh(String refreshToken) {
        if (!jwtService.isTokenValid(refreshToken)) {
            throw new IllegalArgumentException("Invalid refresh token");
        }

        UUID userId = jwtService.extractUserId(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return AuthResponseDTO.builder()
                .accessToken(jwtService.generateAccessToken(user.getId(), user.getRole().name()))
                .refreshToken(refreshToken)
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }

    public void logout(String refreshToken) {
        if (jwtService.isTokenValid(refreshToken)) {
            jwtService.blacklistToken(refreshToken);
        }
    }

    private AuthResponseDTO buildAuthResponse(User user) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getRole().name());
        String refreshToken = jwtService.generateRefreshToken(user.getId());

        return AuthResponseDTO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }
}


