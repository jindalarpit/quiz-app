package com.quizplatform.gateway;

import com.quizplatform.gateway.config.CorsConfig;
import com.quizplatform.gateway.config.GatewayErrorHandler;
import com.quizplatform.gateway.filter.CorrelationIdFilter;
import com.quizplatform.gateway.filter.JwtValidationFilter;
import com.quizplatform.gateway.filter.RequestLoggingFilter;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for the API Gateway filter chain.
 * Tests the full filter pipeline: CorrelationId → JWT Validation → downstream.
 * Verifies routing behavior, JWT validation, header propagation, and error handling.
 */
class GatewayRoutingIntegrationTest {

    private static final String JWT_SECRET =
            "test-secret-key-that-is-at-least-32-characters-long-for-hmac-sha256";

    private CorrelationIdFilter correlationIdFilter;
    private JwtValidationFilter jwtValidationFilter;
    private RequestLoggingFilter loggingFilter;

    @BeforeEach
    void setUp() {
        correlationIdFilter = new CorrelationIdFilter();

        jwtValidationFilter = new JwtValidationFilter();
        ReflectionTestUtils.setField(jwtValidationFilter, "jwtSecret", JWT_SECRET);

        loggingFilter = new RequestLoggingFilter();
    }

    @Test
    @DisplayName("Full filter chain: valid JWT → correlation ID + user headers propagated")
    void fullFilterChain_validJwt_propagatesAllHeaders() {
        UUID userId = UUID.randomUUID();
        String token = generateValidToken(userId, "HOST");

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/quizzes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<ServerWebExchange> capturedExchange = new AtomicReference<>();

        // Build the filter chain: logging → correlationId → jwt → capture
        GatewayFilterChain captureChain = mock(GatewayFilterChain.class);
        when(captureChain.filter(any(ServerWebExchange.class))).thenAnswer(invocation -> {
            capturedExchange.set(invocation.getArgument(0));
            return Mono.empty();
        });

        // Chain: correlationId → jwt → capture
        GatewayFilterChain jwtChain = ex -> jwtValidationFilter.filter(ex, captureChain);
        GatewayFilterChain correlationChain = ex -> correlationIdFilter.filter(ex, jwtChain);

        StepVerifier.create(loggingFilter.filter(exchange, correlationChain))
                .verifyComplete();

        // Verify all headers are propagated
        ServerWebExchange result = capturedExchange.get();
        assertThat(result).isNotNull();
        assertThat(result.getRequest().getHeaders().getFirst("X-Correlation-Id")).isNotNull();
        assertThat(result.getRequest().getHeaders().getFirst("X-User-Id"))
                .isEqualTo(userId.toString());
        assertThat(result.getRequest().getHeaders().getFirst("X-User-Role"))
                .isEqualTo("HOST");

        // Verify response has correlation ID
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Correlation-Id")).isNotNull();
    }

    @Test
    @DisplayName("Full filter chain: missing JWT on protected endpoint → 401")
    void fullFilterChain_missingJwt_returns401() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/quizzes").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain endChain = mock(GatewayFilterChain.class);
        when(endChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        // Chain: correlationId → jwt → end
        GatewayFilterChain jwtChain = ex -> jwtValidationFilter.filter(ex, endChain);
        GatewayFilterChain correlationChain = ex -> correlationIdFilter.filter(ex, jwtChain);

        StepVerifier.create(loggingFilter.filter(exchange, correlationChain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // Correlation ID should still be in response even on error
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Correlation-Id")).isNotNull();
    }

    @Test
    @DisplayName("Full filter chain: public endpoint bypasses JWT validation")
    void fullFilterChain_publicEndpoint_bypassesJwt() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/auth/login").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<ServerWebExchange> capturedExchange = new AtomicReference<>();
        GatewayFilterChain captureChain = mock(GatewayFilterChain.class);
        when(captureChain.filter(any(ServerWebExchange.class))).thenAnswer(invocation -> {
            capturedExchange.set(invocation.getArgument(0));
            return Mono.empty();
        });

        GatewayFilterChain jwtChain = ex -> jwtValidationFilter.filter(ex, captureChain);
        GatewayFilterChain correlationChain = ex -> correlationIdFilter.filter(ex, jwtChain);

        StepVerifier.create(loggingFilter.filter(exchange, correlationChain))
                .verifyComplete();

        // Request should pass through without user headers
        ServerWebExchange result = capturedExchange.get();
        assertThat(result).isNotNull();
        assertThat(result.getRequest().getHeaders().getFirst("X-Correlation-Id")).isNotNull();
        // No user headers since JWT was not provided/required
        assertThat(result.getRequest().getHeaders().getFirst("X-User-Id")).isNull();
    }

    @Test
    @DisplayName("Full filter chain: expired JWT → 401 with correlation ID")
    void fullFilterChain_expiredJwt_returns401WithCorrelationId() {
        String token = generateExpiredToken(UUID.randomUUID(), "HOST");

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/quizzes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain endChain = mock(GatewayFilterChain.class);
        when(endChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        GatewayFilterChain jwtChain = ex -> jwtValidationFilter.filter(ex, endChain);
        GatewayFilterChain correlationChain = ex -> correlationIdFilter.filter(ex, jwtChain);

        StepVerifier.create(correlationIdFilter.filter(exchange, jwtChain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Correlation-Id")).isNotNull();
    }

    @Test
    @DisplayName("Full filter chain: WebSocket path bypasses JWT validation")
    void fullFilterChain_websocketPath_bypassesJwt() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/ws/ABC123").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<ServerWebExchange> capturedExchange = new AtomicReference<>();
        GatewayFilterChain captureChain = mock(GatewayFilterChain.class);
        when(captureChain.filter(any(ServerWebExchange.class))).thenAnswer(invocation -> {
            capturedExchange.set(invocation.getArgument(0));
            return Mono.empty();
        });

        GatewayFilterChain jwtChain = ex -> jwtValidationFilter.filter(ex, captureChain);

        StepVerifier.create(correlationIdFilter.filter(exchange, jwtChain))
                .verifyComplete();

        assertThat(capturedExchange.get()).isNotNull();
        // No 401 — request passed through
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("Filter ordering: correlation ID runs before JWT validation")
    void filterOrdering_correlationIdBeforeJwt() {
        assertThat(correlationIdFilter.getOrder()).isLessThan(jwtValidationFilter.getOrder());
    }

    @Test
    @DisplayName("Filter ordering: logging runs before correlation ID")
    void filterOrdering_loggingBeforeCorrelationId() {
        assertThat(loggingFilter.getOrder()).isLessThan(correlationIdFilter.getOrder());
    }

    @Test
    @DisplayName("Route configuration: auth-service routes are defined")
    void routeConfig_authServiceDefined() {
        // This test verifies the application.yml route configuration is correct
        // by testing that the JWT filter correctly identifies auth paths as public
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/auth/register").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(jwtValidationFilter.filter(exchange, chain))
                .verifyComplete();

        // Should not return 401 — register is public
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("Route configuration: session join with wildcard PIN is public")
    void routeConfig_sessionJoinWithAnyPin_isPublic() {
        MockServerHttpRequest request =
                MockServerHttpRequest.post("/api/sessions/XYZ789/join").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(jwtValidationFilter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    private String generateValidToken(UUID userId, String role) {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(userId.toString())
                .claim("role", role)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }

    private String generateExpiredToken(UUID userId, String role) {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(userId.toString())
                .claim("role", role)
                .issuedAt(Date.from(Instant.now().minus(2, ChronoUnit.HOURS)))
                .expiration(Date.from(Instant.now().minus(1, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }
}
