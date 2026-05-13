package com.quizplatform.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequestLoggingFilterTest {

    private RequestLoggingFilter filter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new RequestLoggingFilter();
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("Should store start time attribute for duration calculation")
    void storesStartTimeAttribute() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/quizzes")
                .header("X-Correlation-Id", "test-correlation-id")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // Verify start time was stored
        Long startTime = exchange.getAttribute("requestStartTime");
        assertThat(startTime).isNotNull().isPositive();
    }

    @Test
    @DisplayName("Should complete successfully without correlation ID")
    void completesWithoutCorrelationId() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/quizzes").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should have highest priority order")
    void hasHighestPriorityOrder() {
        assertThat(filter.getOrder()).isEqualTo(-300);
    }
}
