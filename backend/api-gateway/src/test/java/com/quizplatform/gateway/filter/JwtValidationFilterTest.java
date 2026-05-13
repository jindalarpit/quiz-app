package com.quizplatform.gateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtValidationFilterTest {

    private static final String JWT_SECRET =
            "test-secret-key-that-is-at-least-32-characters-long-for-hmac-sha256";

    private JwtValidationFilter filter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new JwtValidationFilter();
        ReflectionTestUtils.setField(filter, "jwtSecret", JWT_SECRET);
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("Should pass through public endpoints without JWT")
    void publicEndpoints_noJwtRequired() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/auth/login").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should pass through register endpoint without JWT")
    void registerEndpoint_noJwtRequired() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/auth/register").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should pass through refresh endpoint without JWT")
    void refreshEndpoint_noJwtRequired() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/auth/refresh").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should pass through session join endpoint without JWT")
    void sessionJoinEndpoint_noJwtRequired() {
        MockServerHttpRequest request =
                MockServerHttpRequest.post("/api/sessions/ABC123/join").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should pass through WebSocket endpoints without JWT")
    void websocketEndpoint_noJwtRequired() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/ws/ABC123").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return 401 when Authorization header is missing")
    void missingAuthHeader_returns401() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/quizzes").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should return 401 when Authorization header has no Bearer prefix")
    void invalidAuthHeader_returns401() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/quizzes")
                .header(HttpHeaders.AUTHORIZATION, "Basic abc123")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should return 401 for expired token")
    void expiredToken_returns401() {
        String token = generateToken(UUID.randomUUID(), "HOST",
                Instant.now().minus(1, ChronoUnit.HOURS));
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/quizzes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should return 401 for invalid token signature")
    void invalidSignature_returns401() {
        String wrongSecret =
                "wrong-secret-key-that-is-at-least-32-characters-long-for-hmac-sha256";
        SecretKey key = Keys.hmacShaKeyFor(wrongSecret.getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("role", "HOST")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/quizzes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should extract user ID and role from valid token and pass as headers")
    void validToken_extractsClaimsAsHeaders() {
        UUID userId = UUID.randomUUID();
        String token = generateToken(userId, "HOST",
                Instant.now().plus(1, ChronoUnit.HOURS));

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/quizzes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Capture the mutated exchange
        GatewayFilterChain capturingChain = mock(GatewayFilterChain.class);
        when(capturingChain.filter(any(ServerWebExchange.class))).thenAnswer(invocation -> {
            ServerWebExchange mutatedExchange = invocation.getArgument(0);
            assertThat(mutatedExchange.getRequest().getHeaders().getFirst("X-User-Id"))
                    .isEqualTo(userId.toString());
            assertThat(mutatedExchange.getRequest().getHeaders().getFirst("X-User-Role"))
                    .isEqualTo("HOST");
            return Mono.empty();
        });

        StepVerifier.create(filter.filter(exchange, capturingChain))
                .verifyComplete();
    }

    private String generateToken(UUID userId, String role, Instant expiry) {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(userId.toString())
                .claim("role", role)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }
}
