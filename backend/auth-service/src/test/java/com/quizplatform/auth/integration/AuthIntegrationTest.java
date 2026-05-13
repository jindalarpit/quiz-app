package com.quizplatform.auth.integration;

import com.quizplatform.auth.dto.AuthResponse;
import com.quizplatform.auth.dto.LoginRequest;
import com.quizplatform.auth.dto.RefreshRequest;
import com.quizplatform.auth.dto.RegisterRequest;
import com.quizplatform.auth.repository.RefreshTokenRepository;
import com.quizplatform.auth.repository.UserRepository;
import com.quizplatform.common.dto.UserDTO;
import com.quizplatform.common.exception.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the Auth Service using TestContainers with PostgreSQL.
 * Tests the full flow from HTTP request through to database persistence.
 *
 * Each test uses a unique simulated IP (via X-Forwarded-For) to avoid
 * rate limiter interference between tests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class AuthIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("quizplatform_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Disable Redis auto-configuration for integration tests
        // The rate limiter uses in-memory Bucket4j (no Redis dependency)
        registry.add("spring.autoconfigure.exclude",
                () -> "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private String baseUrl;

    /**
     * Counter to generate unique IPs per test, avoiding rate limiter bucket sharing.
     * The rate limiter allows 5 requests/min/IP — tests that make multiple requests
     * need their own IP to avoid interference.
     */
    private static final AtomicInteger IP_COUNTER = new AtomicInteger(1);
    private String testIp;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/api/auth";
        testIp = "10.0.0." + IP_COUNTER.getAndIncrement();
        // Clean up database between tests (order matters due to FK constraints)
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    // --- Helper Methods ---

    private HttpHeaders headersWithIp() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Forwarded-For", testIp);
        return headers;
    }

    private HttpHeaders authHeaders(String accessToken) {
        HttpHeaders headers = headersWithIp();
        headers.setBearerAuth(accessToken);
        return headers;
    }

    private RegisterRequest createRegisterRequest(String email, String displayName) {
        return RegisterRequest.builder()
                .email(email)
                .password("StrongPass1")
                .displayName(displayName)
                .build();
    }

    private AuthResponse registerUser(String email, String displayName) {
        RegisterRequest request = createRegisterRequest(email, displayName);
        HttpEntity<RegisterRequest> entity = new HttpEntity<>(request, headersWithIp());
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                baseUrl + "/register", entity, AuthResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    // --- Test: Full Registration Flow ---

    @Test
    @DisplayName("POST /api/auth/register - full registration flow returns 201 and persists user")
    void register_shouldReturn201AndPersistUser() {
        RegisterRequest request = createRegisterRequest("newuser@example.com", "New User");
        HttpEntity<RegisterRequest> entity = new HttpEntity<>(request, headersWithIp());

        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                baseUrl + "/register", entity, AuthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        AuthResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getAccessToken()).isNotBlank();
        assertThat(body.getRefreshToken()).isNotBlank();
        assertThat(body.getUser()).isNotNull();
        assertThat(body.getUser().getEmail()).isEqualTo("newuser@example.com");
        assertThat(body.getUser().getDisplayName()).isEqualTo("New User");
        assertThat(body.getUser().getRole()).isEqualTo("HOST");
        assertThat(body.getUser().getId()).isNotNull();

        // Verify user is persisted in the database
        assertThat(userRepository.existsByEmail("newuser@example.com")).isTrue();
    }

    // --- Test: Full Login Flow ---

    @Test
    @DisplayName("POST /api/auth/login - register then login returns tokens")
    void login_afterRegistration_shouldReturnTokens() {
        // Register first
        registerUser("loginuser@example.com", "Login User");

        // Login
        LoginRequest loginRequest = LoginRequest.builder()
                .email("loginuser@example.com")
                .password("StrongPass1")
                .build();
        HttpEntity<LoginRequest> entity = new HttpEntity<>(loginRequest, headersWithIp());

        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                baseUrl + "/login", entity, AuthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        AuthResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getAccessToken()).isNotBlank();
        assertThat(body.getRefreshToken()).isNotBlank();
        assertThat(body.getUser()).isNotNull();
        assertThat(body.getUser().getEmail()).isEqualTo("loginuser@example.com");
        assertThat(body.getUser().getDisplayName()).isEqualTo("Login User");
    }

    // --- Test: Token Refresh Flow ---

    @Test
    @DisplayName("POST /api/auth/refresh - use refresh token to get new tokens")
    void refresh_withValidRefreshToken_shouldReturnNewTokens() {
        // Register to get initial tokens
        AuthResponse registerResponse = registerUser("refreshuser@example.com", "Refresh User");
        String originalRefreshToken = registerResponse.getRefreshToken();
        String originalAccessToken = registerResponse.getAccessToken();

        // Use refresh token
        RefreshRequest refreshRequest = RefreshRequest.builder()
                .refreshToken(originalRefreshToken)
                .build();
        HttpEntity<RefreshRequest> entity = new HttpEntity<>(refreshRequest, headersWithIp());

        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                baseUrl + "/refresh", entity, AuthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        AuthResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getAccessToken()).isNotBlank();
        assertThat(body.getRefreshToken()).isNotBlank();
        // New tokens should be different from original (rotation)
        assertThat(body.getRefreshToken()).isNotEqualTo(originalRefreshToken);
        assertThat(body.getAccessToken()).isNotEqualTo(originalAccessToken);
        assertThat(body.getUser().getEmail()).isEqualTo("refreshuser@example.com");
    }

    // --- Test: Duplicate Email Registration ---

    @Test
    @DisplayName("POST /api/auth/register - duplicate email returns 409")
    void register_withDuplicateEmail_shouldReturn409() {
        // Register first user
        registerUser("duplicate@example.com", "First User");

        // Attempt to register with same email
        RegisterRequest duplicateRequest = createRegisterRequest("duplicate@example.com", "Second User");
        HttpEntity<RegisterRequest> entity = new HttpEntity<>(duplicateRequest, headersWithIp());

        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                baseUrl + "/register", entity, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(409);
        assertThat(body.getErrorCode()).isNotNull();
    }

    // --- Test: Invalid Login ---

    @Test
    @DisplayName("POST /api/auth/login - wrong password returns 401")
    void login_withWrongPassword_shouldReturn401() {
        // Register user
        registerUser("wrongpass@example.com", "Wrong Pass User");

        // Attempt login with wrong password
        LoginRequest loginRequest = LoginRequest.builder()
                .email("wrongpass@example.com")
                .password("WrongPassword1")
                .build();
        HttpEntity<LoginRequest> entity = new HttpEntity<>(loginRequest, headersWithIp());

        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                baseUrl + "/login", entity, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(401);
    }

    // --- Test: Get Me ---

    @Test
    @DisplayName("GET /api/auth/me - with valid token returns user profile")
    void getMe_withValidToken_shouldReturnUserProfile() {
        // Register to get access token
        AuthResponse registerResponse = registerUser("meuser@example.com", "Me User");
        String accessToken = registerResponse.getAccessToken();

        // Call /me with token
        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(accessToken));
        ResponseEntity<UserDTO> response = restTemplate.exchange(
                baseUrl + "/me", HttpMethod.GET, entity, UserDTO.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        UserDTO body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getEmail()).isEqualTo("meuser@example.com");
        assertThat(body.getDisplayName()).isEqualTo("Me User");
        assertThat(body.getRole()).isEqualTo("HOST");
        assertThat(body.getId()).isNotNull();
    }

    // --- Test: Get Me without token ---

    @Test
    @DisplayName("GET /api/auth/me - without token returns 401 or 403")
    void getMe_withoutToken_shouldReturnUnauthorized() {
        HttpEntity<Void> entity = new HttpEntity<>(headersWithIp());
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/me", HttpMethod.GET, entity, String.class);

        // Spring Security returns 403 by default for unauthenticated requests
        // when no custom AuthenticationEntryPoint is configured
        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        assertThat(response.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    // --- Test: Rate Limiting ---

    @Test
    @DisplayName("Rate limiting - 6th request within a minute returns 429")
    void rateLimiting_shouldReturn429OnSixthRequest() {
        // The rate limiter allows 5 requests per minute per IP.
        // Use a dedicated IP for this test to ensure a fresh bucket.
        String rateLimitIp = "192.168.200.1";

        LoginRequest loginRequest = LoginRequest.builder()
                .email("nonexistent@example.com")
                .password("SomePassword1")
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Forwarded-For", rateLimitIp);

        // Send 5 requests (these will get 401 but consume rate limit tokens)
        for (int i = 0; i < 5; i++) {
            HttpEntity<LoginRequest> entity = new HttpEntity<>(loginRequest, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    baseUrl + "/login", entity, String.class);
            // These should be 401 (invalid credentials) but not 429
            assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        }

        // 6th request should be rate limited
        HttpEntity<LoginRequest> entity = new HttpEntity<>(loginRequest, headers);
        ResponseEntity<String> rateLimitedResponse = restTemplate.postForEntity(
                baseUrl + "/login", entity, String.class);

        assertThat(rateLimitedResponse.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    // --- Test: Refresh token reuse after rotation ---

    @Test
    @DisplayName("POST /api/auth/refresh - reusing old refresh token after rotation returns 401")
    void refresh_withRevokedToken_shouldReturn401() {
        // Register to get initial tokens
        AuthResponse registerResponse = registerUser("rotateuser@example.com", "Rotate User");
        String originalRefreshToken = registerResponse.getRefreshToken();

        // Use refresh token (this rotates it)
        RefreshRequest refreshRequest = RefreshRequest.builder()
                .refreshToken(originalRefreshToken)
                .build();
        HttpEntity<RefreshRequest> refreshEntity = new HttpEntity<>(refreshRequest, headersWithIp());
        restTemplate.postForEntity(baseUrl + "/refresh", refreshEntity, AuthResponse.class);

        // Try to reuse the old refresh token — should be rejected
        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                baseUrl + "/refresh", refreshEntity, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // --- Test: Login with non-existent email ---

    @Test
    @DisplayName("POST /api/auth/login - non-existent email returns 401 (no user enumeration)")
    void login_withNonExistentEmail_shouldReturn401() {
        LoginRequest loginRequest = LoginRequest.builder()
                .email("nobody@example.com")
                .password("SomePassword1")
                .build();
        HttpEntity<LoginRequest> entity = new HttpEntity<>(loginRequest, headersWithIp());

        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                baseUrl + "/login", entity, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        // Should not reveal whether email or password was wrong
        assertThat(body.getMessage()).doesNotContainIgnoringCase("email");
    }
}
