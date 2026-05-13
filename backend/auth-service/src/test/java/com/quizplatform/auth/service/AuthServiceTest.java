package com.quizplatform.auth.service;

import com.quizplatform.auth.dto.AuthResponse;
import com.quizplatform.auth.dto.RefreshRequest;
import com.quizplatform.auth.dto.RegisterRequest;
import com.quizplatform.auth.model.RefreshToken;
import com.quizplatform.auth.model.User;
import com.quizplatform.auth.model.UserRole;
import com.quizplatform.auth.repository.RefreshTokenRepository;
import com.quizplatform.auth.repository.UserRepository;
import com.quizplatform.common.exception.DuplicateResourceException;
import com.quizplatform.common.exception.ResourceNotFoundException;
import com.quizplatform.common.exception.UnauthorizedException;
import com.quizplatform.common.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private AuthService authService;

    private BCryptPasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(12);
        authService = new AuthService(userRepository, refreshTokenRepository, passwordEncoder);
        ReflectionTestUtils.setField(authService, "jwtSecret", "test-secret-key-that-is-at-least-32-characters-long");
        ReflectionTestUtils.setField(authService, "jwtExpirationMs", 86400000L);
        ReflectionTestUtils.setField(authService, "refreshExpirationDays", 7);
    }

    @Test
    void register_shouldCreateUserAndReturnTokens() {
        RegisterRequest request = RegisterRequest.builder()
                .email("test@example.com")
                .password("Password1")
                .displayName("Test User")
                .build();

        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthResponse response = authService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getRefreshToken()).isNotBlank();
        assertThat(response.getUser()).isNotNull();
        assertThat(response.getUser().getEmail()).isEqualTo("test@example.com");
        assertThat(response.getUser().getDisplayName()).isEqualTo("Test User");
        assertThat(response.getUser().getRole()).isEqualTo("HOST");
    }

    @Test
    void register_shouldHashPasswordWithBCrypt() {
        RegisterRequest request = RegisterRequest.builder()
                .email("test@example.com")
                .password("Password1")
                .displayName("Test User")
                .build();

        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getPasswordHash()).isNotEqualTo("Password1");
        assertThat(passwordEncoder.matches("Password1", savedUser.getPasswordHash())).isTrue();
    }

    @Test
    void register_shouldAssignHostRole() {
        RegisterRequest request = RegisterRequest.builder()
                .email("test@example.com")
                .password("Password1")
                .displayName("Test User")
                .build();

        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        assertThat(userCaptor.getValue().getRole()).isEqualTo(UserRole.HOST);
    }

    @Test
    void register_shouldThrowDuplicateResourceExceptionWhenEmailExists() {
        RegisterRequest request = RegisterRequest.builder()
                .email("existing@example.com")
                .password("Password1")
                .displayName("Test User")
                .build();

        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("existing@example.com");

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_shouldThrowValidationExceptionForWeakPassword() {
        RegisterRequest request = RegisterRequest.builder()
                .email("test@example.com")
                .password("weak")
                .displayName("Test User")
                .build();

        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Password does not meet requirements");

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_shouldStoreRefreshTokenHashUsingSHA256() {
        RegisterRequest request = RegisterRequest.builder()
                .email("test@example.com")
                .password("Password1")
                .displayName("Test User")
                .build();

        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthResponse response = authService.register(request);

        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(tokenCaptor.capture());

        RefreshToken savedToken = tokenCaptor.getValue();
        assertThat(savedToken.getTokenHash()).isNotBlank();
        // SHA-256 hex digest is 64 characters
        assertThat(savedToken.getTokenHash()).hasSize(64);
        assertThat(savedToken.getTokenHash()).isNotEqualTo(response.getRefreshToken());
        // Verify the hash matches what hashToken produces
        assertThat(savedToken.getTokenHash()).isEqualTo(authService.hashToken(response.getRefreshToken()));
        assertThat(savedToken.getExpiresAt()).isNotNull();
        assertThat(savedToken.getUser()).isNotNull();
    }

    @Test
    void refresh_shouldReturnNewTokensWhenValidRefreshToken() {
        String rawToken = UUID.randomUUID().toString();
        String tokenHash = authService.hashToken(rawToken);

        User user = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .displayName("Test User")
                .role(UserRole.HOST)
                .build();

        RefreshToken existingToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(existingToken));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RefreshRequest request = RefreshRequest.builder().refreshToken(rawToken).build();
        AuthResponse response = authService.refresh(request);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getRefreshToken()).isNotBlank();
        // New refresh token should be different from the old one
        assertThat(response.getRefreshToken()).isNotEqualTo(rawToken);
        assertThat(response.getUser()).isNotNull();
        assertThat(response.getUser().getEmail()).isEqualTo("test@example.com");
    }

    @Test
    void refresh_shouldRevokeOldTokenAndCreateNewOne() {
        String rawToken = UUID.randomUUID().toString();
        String tokenHash = authService.hashToken(rawToken);

        User user = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .displayName("Test User")
                .role(UserRole.HOST)
                .build();

        RefreshToken existingToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(existingToken));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RefreshRequest request = RefreshRequest.builder().refreshToken(rawToken).build();
        authService.refresh(request);

        // Verify old token was revoked
        assertThat(existingToken.getRevokedAt()).isNotNull();

        // Verify save was called twice: once for revoking old, once for new token
        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(2)).save(tokenCaptor.capture());

        RefreshToken newToken = tokenCaptor.getAllValues().get(1);
        assertThat(newToken.getTokenHash()).isNotEqualTo(tokenHash);
        assertThat(newToken.getTokenHash()).hasSize(64); // SHA-256 hex
        assertThat(newToken.getRevokedAt()).isNull();
        assertThat(newToken.getUser()).isEqualTo(user);
    }

    @Test
    void refresh_shouldThrowUnauthorizedWhenTokenNotFound() {
        String rawToken = UUID.randomUUID().toString();
        String tokenHash = authService.hashToken(rawToken);

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.empty());

        RefreshRequest request = RefreshRequest.builder().refreshToken(rawToken).build();

        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid or expired refresh token");
    }

    @Test
    void refresh_shouldThrowUnauthorizedWhenTokenAlreadyRevoked() {
        String rawToken = UUID.randomUUID().toString();
        String tokenHash = authService.hashToken(rawToken);

        User user = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .displayName("Test User")
                .role(UserRole.HOST)
                .build();

        RefreshToken existingToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .revokedAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(existingToken));

        RefreshRequest request = RefreshRequest.builder().refreshToken(rawToken).build();

        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid or expired refresh token");
    }

    @Test
    void refresh_shouldRevokeExpiredTokenAndThrowUnauthorized() {
        String rawToken = UUID.randomUUID().toString();
        String tokenHash = authService.hashToken(rawToken);

        User user = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .displayName("Test User")
                .role(UserRole.HOST)
                .build();

        RefreshToken existingToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS)) // expired
                .build();

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(existingToken));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RefreshRequest request = RefreshRequest.builder().refreshToken(rawToken).build();

        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid or expired refresh token");

        // Verify the expired token was revoked
        assertThat(existingToken.getRevokedAt()).isNotNull();
        verify(refreshTokenRepository).save(existingToken);
    }

    @Test
    void getMe_shouldReturnUserDTOWhenUserExists() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("test@example.com")
                .displayName("Test User")
                .role(UserRole.HOST)
                .createdAt(Instant.now())
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        com.quizplatform.common.dto.UserDTO result = authService.getMe(userId);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(userId);
        assertThat(result.getEmail()).isEqualTo("test@example.com");
        assertThat(result.getDisplayName()).isEqualTo("Test User");
        assertThat(result.getRole()).isEqualTo("HOST");
        assertThat(result.getCreatedAt()).isEqualTo(user.getCreatedAt());
    }

    @Test
    void getMe_shouldThrowResourceNotFoundExceptionWhenUserDoesNotExist() {
        UUID userId = UUID.randomUUID();

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getMe(userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(userId.toString());
    }

    @Test
    void hashToken_shouldProduceConsistentSHA256Hash() {
        String token = "test-token-value";
        String hash1 = authService.hashToken(token);
        String hash2 = authService.hashToken(token);

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64); // SHA-256 produces 32 bytes = 64 hex chars
    }

    @Test
    void hashToken_shouldProduceDifferentHashesForDifferentTokens() {
        String token1 = "token-one";
        String token2 = "token-two";

        assertThat(authService.hashToken(token1)).isNotEqualTo(authService.hashToken(token2));
    }
}
