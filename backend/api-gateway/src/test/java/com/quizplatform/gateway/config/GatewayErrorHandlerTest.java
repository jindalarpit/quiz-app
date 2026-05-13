package com.quizplatform.gateway.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import reactor.test.StepVerifier;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayErrorHandlerTest {

    private GatewayErrorHandler errorHandler;

    @BeforeEach
    void setUp() {
        errorHandler = new GatewayErrorHandler();
    }

    @Test
    @DisplayName("Should return 504 for TimeoutException")
    void timeoutException_returns504() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/quizzes").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(errorHandler.handle(exchange, new TimeoutException("Timed out")))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(exchange.getResponse().getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    @DisplayName("Should return 503 for ConnectException")
    void connectException_returns503() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/quizzes").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(
                        errorHandler.handle(exchange, new ConnectException("Connection refused")))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("Should return 502 for generic exceptions")
    void genericException_returns502() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/quizzes").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(
                        errorHandler.handle(exchange, new RuntimeException("Something went wrong")))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    @DisplayName("Should handle ResponseStatusException with correct status")
    void responseStatusException_returnsCorrectStatus() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/quizzes").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(errorHandler.handle(exchange,
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found")))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
