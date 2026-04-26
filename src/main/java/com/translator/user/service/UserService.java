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

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
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
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid email or password");
        }
        
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
                
        // Only return a new access token in a standard flow, but the req says "return new access token".
        // Returning AuthResponseDTO with new access and keeping old refresh token.
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
