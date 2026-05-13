package com.quizplatform.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizplatform.auth.config.OAuthProperties;
import com.quizplatform.auth.dto.AuthResponse;
import com.quizplatform.auth.model.RefreshToken;
import com.quizplatform.auth.model.User;
import com.quizplatform.auth.model.UserRole;
import com.quizplatform.auth.repository.RefreshTokenRepository;
import com.quizplatform.auth.repository.UserRepository;
import com.quizplatform.common.exception.UnauthorizedException;
import com.quizplatform.common.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private RestTemplate restTemplate;

    private OAuthProperties oAuthProperties;
    private ObjectMapper objectMapper;
    private OAuthService oAuthService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        oAuthProperties = new OAuthProperties();

        // Configure Google provider
        OAuthProperties.ProviderConfig googleConfig = new OAuthProperties.ProviderConfig();
        googleConfig.setClientId("google-client-id");
        googleConfig.setClientSecret("google-client-secret");
        googleConfig.setRedirectUri("http://localhost:3000/auth/callback");
        googleConfig.setAuthUri("https://accounts.google.com/o/oauth2/v2/auth");
        googleConfig.setTokenUri("https://oauth2.googleapis.com/token");
        googleConfig.setUserInfoUri("https://www.googleapis.com/oauth2/v3/userinfo");
        googleConfig.setScope("openid,email,profile");
        oAuthProperties.setGoogle(googleConfig);

        // Configure GitHub provider
        OAuthProperties.ProviderConfig githubConfig = new OAuthProperties.ProviderConfig();
        githubConfig.setClientId("github-client-id");
        githubConfig.setClientSecret("github-client-secret");
        githubConfig.setRedirectUri("http://localhost:3000/auth/callback");
        githubConfig.setAuthUri("https://github.com/login/oauth/authorize");
        githubConfig.setTokenUri("https://github.com/login/oauth/access_token");
        githubConfig.setUserInfoUri("https://api.github.com/user");
        githubConfig.setScope("read:user,user:email");
        oAuthProperties.setGithub(githubConfig);

        oAuthService = new OAuthService(userRepository, refreshTokenRepository, oAuthProperties, restTemplate, objectMapper);
        ReflectionTestUtils.setField(oAuthService, "jwtSecret", "test-secret-key-that-is-at-least-32-characters-long");
        ReflectionTestUtils.setField(oAuthService, "jwtExpirationMs", 86400000L);
        ReflectionTestUtils.setField(oAuthService, "refreshExpirationDays", 7);
    }

    @Test
    void getAuthorizationUrl_google_shouldReturnCorrectUrl() {
        String url = oAuthService.getAuthorizationUrl("google");

        assertThat(url).contains("https://accounts.google.com/o/oauth2/v2/auth");
        assertThat(url).contains("client_id=google-client-id");
        assertThat(url).contains("redirect_uri=http://localhost:3000/auth/callback");
        assertThat(url).contains("scope=openid,email,profile");
        assertThat(url).contains("response_type=code");
        assertThat(url).contains("state=google");
        assertThat(url).contains("access_type=offline");
    }

    @Test
    void getAuthorizationUrl_github_shouldReturnCorrectUrl() {
        String url = oAuthService.getAuthorizationUrl("github");

        assertThat(url).contains("https://github.com/login/oauth/authorize");
        assertThat(url).contains("client_id=github-client-id");
        assertThat(url).contains("redirect_uri=http://localhost:3000/auth/callback");
        assertThat(url).contains("scope=read:user,user:email");
        assertThat(url).contains("response_type=code");
        assertThat(url).contains("state=github");
        assertThat(url).doesNotContain("access_type=offline");
    }

    @Test
    void getAuthorizationUrl_unsupportedProvider_shouldThrowException() {
        assertThatThrownBy(() -> oAuthService.getAuthorizationUrl("facebook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported OAuth provider");
    }

    @Test
    void handleOAuthCallback_google_newUser_shouldCreateUserAndReturnTokens() {
        // Mock token exchange
        String tokenResponse = "{\"access_token\":\"google-access-token\",\"token_type\":\"Bearer\"}";
        when(restTemplate.exchange(
                eq("https://oauth2.googleapis.com/token"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok(tokenResponse));

        // Mock user info fetch
        String userInfoResponse = "{\"sub\":\"google-123\",\"email\":\"user@gmail.com\",\"name\":\"Google User\"}";
        when(restTemplate.exchange(
                eq("https://www.googleapis.com/oauth2/v3/userinfo"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok(userInfoResponse));

        // No existing user
        when(userRepository.findByOauthProviderAndOauthProviderId("google", "google-123"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@gmail.com")).thenReturn(Optional.empty());

        // Save new user
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthResponse response = oAuthService.handleOAuthCallback("google", "auth-code", null);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getRefreshToken()).isNotBlank();
        assertThat(response.getUser()).isNotNull();
        assertThat(response.getUser().getEmail()).isEqualTo("user@gmail.com");
        assertThat(response.getUser().getDisplayName()).isEqualTo("Google User");
        assertThat(response.getUser().getRole()).isEqualTo("HOST");

        // Verify user was created with OAuth fields
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getOauthProvider()).isEqualTo("google");
        assertThat(savedUser.getOauthProviderId()).isEqualTo("google-123");
        assertThat(savedUser.getPasswordHash()).isNull();
    }

    @Test
    void handleOAuthCallback_github_newUser_shouldCreateUserAndReturnTokens() {
        // Mock token exchange
        String tokenResponse = "{\"access_token\":\"github-access-token\",\"token_type\":\"bearer\"}";
        when(restTemplate.exchange(
                eq("https://github.com/login/oauth/access_token"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok(tokenResponse));

        // Mock user info fetch
        String userInfoResponse = "{\"id\":456789,\"login\":\"githubuser\",\"name\":\"GitHub User\",\"email\":\"user@github.com\"}";
        when(restTemplate.exchange(
                eq("https://api.github.com/user"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok(userInfoResponse));

        // No existing user
        when(userRepository.findByOauthProviderAndOauthProviderId("github", "456789"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@github.com")).thenReturn(Optional.empty());

        // Save new user
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthResponse response = oAuthService.handleOAuthCallback("github", "auth-code", null);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getRefreshToken()).isNotBlank();
        assertThat(response.getUser()).isNotNull();
        assertThat(response.getUser().getEmail()).isEqualTo("user@github.com");
        assertThat(response.getUser().getDisplayName()).isEqualTo("GitHub User");

        // Verify user was created with OAuth fields
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getOauthProvider()).isEqualTo("github");
        assertThat(savedUser.getOauthProviderId()).isEqualTo("456789");
    }

    @Test
    void handleOAuthCallback_existingOAuthUser_shouldReturnTokensWithoutCreatingNewUser() {
        // Mock token exchange
        String tokenResponse = "{\"access_token\":\"google-access-token\",\"token_type\":\"Bearer\"}";
        when(restTemplate.exchange(
                eq("https://oauth2.googleapis.com/token"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok(tokenResponse));

        // Mock user info fetch
        String userInfoResponse = "{\"sub\":\"google-123\",\"email\":\"user@gmail.com\",\"name\":\"Google User\"}";
        when(restTemplate.exchange(
                eq("https://www.googleapis.com/oauth2/v3/userinfo"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok(userInfoResponse));

        // Existing OAuth user
        User existingUser = User.builder()
                .id(UUID.randomUUID())
                .email("user@gmail.com")
                .displayName("Google User")
                .role(UserRole.HOST)
                .oauthProvider("google")
                .oauthProviderId("google-123")
                .build();

        when(userRepository.findByOauthProviderAndOauthProviderId("google", "google-123"))
                .thenReturn(Optional.of(existingUser));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthResponse response = oAuthService.handleOAuthCallback("google", "auth-code", null);

        assertThat(response).isNotNull();
        assertThat(response.getUser().getEmail()).isEqualTo("user@gmail.com");

        // Verify no new user was created
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void handleOAuthCallback_existingEmailUser_shouldLinkOAuthProvider() {
        // Mock token exchange
        String tokenResponse = "{\"access_token\":\"google-access-token\",\"token_type\":\"Bearer\"}";
        when(restTemplate.exchange(
                eq("https://oauth2.googleapis.com/token"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok(tokenResponse));

        // Mock user info fetch
        String userInfoResponse = "{\"sub\":\"google-123\",\"email\":\"user@example.com\",\"name\":\"Google User\"}";
        when(restTemplate.exchange(
                eq("https://www.googleapis.com/oauth2/v3/userinfo"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok(userInfoResponse));

        // No existing OAuth user
        when(userRepository.findByOauthProviderAndOauthProviderId("google", "google-123"))
                .thenReturn(Optional.empty());

        // Existing email/password user
        User existingUser = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .passwordHash("$2a$12$hashedpassword")
                .displayName("Existing User")
                .role(UserRole.HOST)
                .build();

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthResponse response = oAuthService.handleOAuthCallback("google", "auth-code", null);

        assertThat(response).isNotNull();
        assertThat(response.getUser().getEmail()).isEqualTo("user@example.com");

        // Verify OAuth provider was linked
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getOauthProvider()).isEqualTo("google");
        assertThat(savedUser.getOauthProviderId()).isEqualTo("google-123");
        // Password hash should remain unchanged
        assertThat(savedUser.getPasswordHash()).isEqualTo("$2a$12$hashedpassword");
    }

    @Test
    void handleOAuthCallback_duplicateProviderLinking_shouldThrowValidationException() {
        // Mock token exchange
        String tokenResponse = "{\"access_token\":\"github-access-token\",\"token_type\":\"bearer\"}";
        when(restTemplate.exchange(
                eq("https://github.com/login/oauth/access_token"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok(tokenResponse));

        // Mock user info fetch
        String userInfoResponse = "{\"id\":789,\"login\":\"githubuser\",\"name\":\"GitHub User\",\"email\":\"user@example.com\"}";
        when(restTemplate.exchange(
                eq("https://api.github.com/user"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok(userInfoResponse));

        // No existing OAuth user with github/789
        when(userRepository.findByOauthProviderAndOauthProviderId("github", "789"))
                .thenReturn(Optional.empty());

        // Existing user already linked to Google
        User existingUser = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .displayName("Existing User")
                .role(UserRole.HOST)
                .oauthProvider("google")
                .oauthProviderId("google-123")
                .build();

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(existingUser));

        assertThatThrownBy(() -> oAuthService.handleOAuthCallback("github", "auth-code", null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("already linked to a different OAuth provider");
    }

    @Test
    void handleOAuthCallback_tokenExchangeError_shouldThrowUnauthorizedException() {
        // Mock token exchange returning error
        String tokenResponse = "{\"error\":\"invalid_grant\",\"error_description\":\"Code has expired\"}";
        when(restTemplate.exchange(
                eq("https://oauth2.googleapis.com/token"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok(tokenResponse));

        assertThatThrownBy(() -> oAuthService.handleOAuthCallback("google", "expired-code", null))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("OAuth authentication failed");
    }

    @Test
    void handleOAuthCallback_deactivatedUser_shouldThrowUnauthorizedException() {
        // Mock token exchange
        String tokenResponse = "{\"access_token\":\"google-access-token\",\"token_type\":\"Bearer\"}";
        when(restTemplate.exchange(
                eq("https://oauth2.googleapis.com/token"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok(tokenResponse));

        // Mock user info fetch
        String userInfoResponse = "{\"sub\":\"google-123\",\"email\":\"user@gmail.com\",\"name\":\"Google User\"}";
        when(restTemplate.exchange(
                eq("https://www.googleapis.com/oauth2/v3/userinfo"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok(userInfoResponse));

        // Existing OAuth user that is soft-deleted
        User deletedUser = User.builder()
                .id(UUID.randomUUID())
                .email("user@gmail.com")
                .displayName("Google User")
                .role(UserRole.HOST)
                .oauthProvider("google")
                .oauthProviderId("google-123")
                .deletedAt(java.time.Instant.now())
                .build();

        when(userRepository.findByOauthProviderAndOauthProviderId("google", "google-123"))
                .thenReturn(Optional.of(deletedUser));

        assertThatThrownBy(() -> oAuthService.handleOAuthCallback("google", "auth-code", null))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Account has been deactivated");
    }

    @Test
    void handleOAuthCallback_withCustomRedirectUri_shouldUseProvidedUri() {
        // Mock token exchange - verify the redirect_uri in the request
        String tokenResponse = "{\"access_token\":\"google-access-token\",\"token_type\":\"Bearer\"}";
        when(restTemplate.exchange(
                eq("https://oauth2.googleapis.com/token"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok(tokenResponse));

        // Mock user info fetch
        String userInfoResponse = "{\"sub\":\"google-123\",\"email\":\"user@gmail.com\",\"name\":\"Google User\"}";
        when(restTemplate.exchange(
                eq("https://www.googleapis.com/oauth2/v3/userinfo"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok(userInfoResponse));

        // Existing OAuth user
        User existingUser = User.builder()
                .id(UUID.randomUUID())
                .email("user@gmail.com")
                .displayName("Google User")
                .role(UserRole.HOST)
                .oauthProvider("google")
                .oauthProviderId("google-123")
                .build();

        when(userRepository.findByOauthProviderAndOauthProviderId("google", "google-123"))
                .thenReturn(Optional.of(existingUser));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Should not throw - custom redirect URI is accepted
        AuthResponse response = oAuthService.handleOAuthCallback("google", "auth-code", "http://custom:3000/callback");

        assertThat(response).isNotNull();
    }
}
