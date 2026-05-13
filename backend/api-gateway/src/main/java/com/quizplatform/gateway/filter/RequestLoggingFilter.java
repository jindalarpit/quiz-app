package com.quizplatform.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global filter that logs request/response details in structured JSON format.
 * Logs: method, path, status code, duration, and correlation ID.
 * Does NOT log request/response bodies for security reasons.
 */
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final String START_TIME_ATTR = "requestStartTime";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String method = request.getMethod().name();
        String path = request.getURI().getPath();
        String correlationId = request.getHeaders().getFirst(CORRELATION_ID_HEADER);

        exchange.getAttributes().put(START_TIME_ATTR, System.currentTimeMillis());

        log.info(
                "{{\"event\":\"request_received\",\"method\":\"{}\",\"path\":\"{}\",\"correlationId\":\"{}\"}}",
                method, path, correlationId
        );

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            Long startTime = exchange.getAttribute(START_TIME_ATTR);
            long duration = startTime != null ? System.currentTimeMillis() - startTime : -1;
            int statusCode = exchange.getResponse().getStatusCode() != null
                    ? exchange.getResponse().getStatusCode().value()
                    : 0;

            log.info(
                    "{{\"event\":\"request_completed\",\"method\":\"{}\",\"path\":\"{}\","
                            + "\"status\":{},\"durationMs\":{},\"correlationId\":\"{}\"}}",
                    method, path, statusCode, duration, correlationId
            );
        }));
    }

    @Override
    public int getOrder() {
        // Run very early to capture full request duration
        return -300;
    }
}
