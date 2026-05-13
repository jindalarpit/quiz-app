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

class CorrelationIdFilterTest {

    private CorrelationIdFilter filter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter();
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("Should generate correlation ID when not present in request")
    void generatesCorrelationId_whenMissing() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/quizzes").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain capturingChain = mock(GatewayFilterChain.class);
        when(capturingChain.filter(any(ServerWebExchange.class))).thenAnswer(invocation -> {
            ServerWebExchange mutatedExchange = invocation.getArgument(0);
            String correlationId =
                    mutatedExchange.getRequest().getHeaders().getFirst("X-Correlation-Id");
            assertThat(correlationId).isNotNull().isNotBlank();
            // Verify it's a valid UUID format
            assertThat(correlationId).matches(
                    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
            return Mono.empty();
        });

        StepVerifier.create(filter.filter(exchange, capturingChain))
                .verifyComplete();

        // Verify response also has the correlation ID
        String responseCorrelationId =
                exchange.getResponse().getHeaders().getFirst("X-Correlation-Id");
        assertThat(responseCorrelationId).isNotNull().isNotBlank();
    }

    @Test
    @DisplayName("Should propagate existing correlation ID from request")
    void propagatesExistingCorrelationId() {
        String existingId = "existing-correlation-id-123";
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/quizzes")
                .header("X-Correlation-Id", existingId)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain capturingChain = mock(GatewayFilterChain.class);
        when(capturingChain.filter(any(ServerWebExchange.class))).thenAnswer(invocation -> {
            ServerWebExchange mutatedExchange = invocation.getArgument(0);
            String correlationId =
                    mutatedExchange.getRequest().getHeaders().getFirst("X-Correlation-Id");
            assertThat(correlationId).isEqualTo(existingId);
            return Mono.empty();
        });

        StepVerifier.create(filter.filter(exchange, capturingChain))
                .verifyComplete();

        // Verify response has the same correlation ID
        String responseCorrelationId =
                exchange.getResponse().getHeaders().getFirst("X-Correlation-Id");
        assertThat(responseCorrelationId).isEqualTo(existingId);
    }

    @Test
    @DisplayName("Should generate new correlation ID when header is blank")
    void generatesNewId_whenHeaderIsBlank() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/quizzes")
                .header("X-Correlation-Id", "   ")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain capturingChain = mock(GatewayFilterChain.class);
        when(capturingChain.filter(any(ServerWebExchange.class))).thenAnswer(invocation -> {
            ServerWebExchange mutatedExchange = invocation.getArgument(0);
            String correlationId =
                    mutatedExchange.getRequest().getHeaders().getFirst("X-Correlation-Id");
            assertThat(correlationId).isNotBlank();
            assertThat(correlationId.trim()).isNotEqualTo("");
            return Mono.empty();
        });

        StepVerifier.create(filter.filter(exchange, capturingChain))
                .verifyComplete();
    }
}
