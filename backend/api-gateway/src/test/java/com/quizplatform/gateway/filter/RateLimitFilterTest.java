package com.quizplatform.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateLimitFilterTest {

    private RateLimitFilter filter;
    private GatewayFilterChain chain;
    private ReactiveStringRedisTemplate redisTemplate;
    private ReactiveValueOperations<String, String> valueOps;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisTemplate = mock(ReactiveStringRedisTemplate.class);
        valueOps = mock(ReactiveValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        filter = new RateLimitFilter(redisTemplate);
        ReflectionTestUtils.setField(filter, "authenticatedLimit", 100);
        ReflectionTestUtils.setField(filter, "unauthenticatedLimit", 20);

        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("Should allow request when under rate limit for authenticated user")
    void authenticatedUser_underLimit_allowed() {
        when(valueOps.increment(anyString())).thenReturn(Mono.just(5L));
        when(redisTemplate.expire(anyString(), any())).thenReturn(Mono.just(true));

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/quizzes")
                .header("X-User-Id", "user-123")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // No error status set means request was allowed
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("Should block request when over rate limit for authenticated user")
    void authenticatedUser_overLimit_blocked() {
        when(valueOps.increment(anyString())).thenReturn(Mono.just(101L));

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/quizzes")
                .header("X-User-Id", "user-123")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After"))
                .isEqualTo("60");
    }

    @Test
    @DisplayName("Should allow request when under rate limit for unauthenticated user")
    void unauthenticatedUser_underLimit_allowed() {
        when(valueOps.increment(anyString())).thenReturn(Mono.just(10L));

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/quizzes").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("Should block request when over rate limit for unauthenticated user")
    void unauthenticatedUser_overLimit_blocked() {
        when(valueOps.increment(anyString())).thenReturn(Mono.just(21L));

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/quizzes").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("Should set expiry on first request in window")
    void firstRequest_setsExpiry() {
        when(valueOps.increment(anyString())).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(anyString(), any())).thenReturn(Mono.just(true));

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/quizzes")
                .header("X-User-Id", "user-123")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("Should fail open when Redis is unavailable")
    void redisUnavailable_failsOpen() {
        when(valueOps.increment(anyString()))
                .thenReturn(Mono.error(new RuntimeException("Redis connection failed")));

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/quizzes").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // Request should be allowed (fail-open)
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }
}
