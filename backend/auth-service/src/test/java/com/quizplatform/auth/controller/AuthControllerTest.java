package com.quizplatform.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizplatform.auth.dto.AuthResponse;
import com.quizplatform.auth.dto.RefreshRequest;
import com.quizplatform.auth.dto.RegisterRequest;
import com.quizplatform.auth.security.SecurityConfig;
import com.quizplatform.auth.service.AuthService;
import com.quizplatform.common.dto.UserDTO;
import com.quizplatform.common.exception.DuplicateResourceException;
import com.quizplatform.common.exception.ResourceNotFoundException;
import com.quizplatform.common.exception.UnauthorizedException;
import com.quizplatform.common.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @Test
    void register_shouldReturn201WithAuthResponse() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("test@example.com")
                .password("Password1")
                .displayName("Test User")
                .build();

        AuthResponse response = AuthResponse.builder()
                .accessToken("jwt-token")
                .refreshToken("refresh-token")
                .user(UserDTO.builder()
                        .id(UUID.randomUUID())
                        .email("test@example.com")
                        .displayName("Test User")
                        .role("HOST")
                        .build())
                .build();

        when(authService.register(any(RegisterRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("jwt-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.user.email").value("test@example.com"))
                .andExpect(jsonPath("$.user.displayName").value("Test User"))
                .andExpect(jsonPath("$.user.role").value("HOST"));
    }

    @Test
    void register_shouldReturn400WhenEmailIsBlank() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("")
                .password("Password1")
                .displayName("Test User")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_shouldReturn400WhenEmailIsInvalid() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("not-an-email")
                .password("Password1")
                .displayName("Test User")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_shouldReturn400WhenPasswordIsBlank() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("test@example.com")
                .password("")
                .displayName("Test User")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_shouldReturn400WhenDisplayNameIsBlank() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("test@example.com")
                .password("Password1")
                .displayName("")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_shouldReturn409WhenEmailAlreadyExists() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("existing@example.com")
                .password("Password1")
                .displayName("Test User")
                .build();

        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new DuplicateResourceException("User", "existing@example.com"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void register_shouldReturn400WhenPasswordValidationFails() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("test@example.com")
                .password("weak")
                .displayName("Test User")
                .build();

        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new ValidationException("Password does not meet requirements",
                        List.of("Password must be at least 8 characters long")));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refresh_shouldReturn200WithNewTokens() throws Exception {
        RefreshRequest request = RefreshRequest.builder()
                .refreshToken("valid-refresh-token")
                .build();

        AuthResponse response = AuthResponse.builder()
                .accessToken("new-jwt-token")
                .refreshToken("new-refresh-token")
                .user(UserDTO.builder()
                        .id(UUID.randomUUID())
                        .email("test@example.com")
                        .displayName("Test User")
                        .role("HOST")
                        .build())
                .build();

        when(authService.refresh(any(RefreshRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-jwt-token"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh-token"))
                .andExpect(jsonPath("$.user.email").value("test@example.com"));
    }

    @Test
    void refresh_shouldReturn400WhenRefreshTokenIsBlank() throws Exception {
        RefreshRequest request = RefreshRequest.builder()
                .refreshToken("")
                .build();

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refresh_shouldReturn401WhenRefreshTokenIsInvalid() throws Exception {
        RefreshRequest request = RefreshRequest.builder()
                .refreshToken("invalid-token")
                .build();

        when(authService.refresh(any(RefreshRequest.class)))
                .thenThrow(new UnauthorizedException("Invalid or expired refresh token"));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMe_shouldReturn200WithUserProfile() throws Exception {
        UUID userId = UUID.randomUUID();
        Instant createdAt = Instant.now();

        UserDTO userDTO = UserDTO.builder()
                .id(userId)
                .email("test@example.com")
                .displayName("Test User")
                .role("HOST")
                .createdAt(createdAt)
                .build();

        when(authService.getMe(userId)).thenReturn(userDTO);

        mockMvc.perform(get("/api/auth/me")
                        .with(SecurityMockMvcRequestPostProcessors.user(userId.toString())
                                .roles("HOST"))
                        .with(request -> {
                            // Set the authentication principal as UUID (matching our filter behavior)
                            org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth =
                                    new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                                            userId, null,
                                            List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_HOST")));
                            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.displayName").value("Test User"))
                .andExpect(jsonPath("$.role").value("HOST"));
    }

    @Test
    void getMe_shouldReturn401WhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMe_shouldReturn404WhenUserNotFound() throws Exception {
        UUID userId = UUID.randomUUID();

        when(authService.getMe(userId)).thenThrow(new ResourceNotFoundException("User", userId.toString()));

        mockMvc.perform(get("/api/auth/me")
                        .with(request -> {
                            org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth =
                                    new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                                            userId, null,
                                            List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_HOST")));
                            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
                            return request;
                        }))
                .andExpect(status().isNotFound());
    }
}
