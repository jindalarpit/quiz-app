package com.quizplatform.auth.controller;

import com.quizplatform.auth.dto.AuthResponse;
import com.quizplatform.auth.dto.LoginRequest;
import com.quizplatform.auth.dto.RefreshRequest;
import com.quizplatform.auth.dto.RegisterRequest;
import com.quizplatform.auth.service.AuthService;
import com.quizplatform.common.dto.UserDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        AuthResponse response = authService.refresh(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<UserDTO> getMe(Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        UserDTO user = authService.getMe(userId);
        return ResponseEntity.ok(user);
    }

    /**
     * GDPR data deletion endpoint.
     * Soft-deletes the authenticated user and revokes all refresh tokens.
     * The user will be permanently deleted after 30 days by the DeletionService.
     */
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMe(Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        authService.requestDeletion(userId);
        return ResponseEntity.noContent().build();
    }
}
