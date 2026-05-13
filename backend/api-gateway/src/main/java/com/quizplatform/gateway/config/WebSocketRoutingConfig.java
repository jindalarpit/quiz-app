package com.quizplatform.gateway.config;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

/**
 * Custom gateway filter factory for WebSocket routing with PIN-based sticky session support.
 * Extracts the PIN from the WebSocket path or query parameter and uses it for consistent hashing
 * to ensure all connections for the same session route to the same backend instance.
 */
@Component
public class WebSocketRoutingConfig extends AbstractGatewayFilterFactory<WebSocketRoutingConfig.Config> {

    private static final String PIN_HEADER = "X-Session-Pin";

    public WebSocketRoutingConfig() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String path = request.getURI().getPath();
            String pin = extractPin(path, request);

            if (pin != null && !pin.isBlank()) {
                // Add PIN as header for downstream sticky session routing
                ServerHttpRequest mutatedRequest = request.mutate()
                        .header(PIN_HEADER, pin)
                        .build();
                return chain.filter(exchange.mutate().request(mutatedRequest).build());
            }

            return chain.filter(exchange);
        };
    }

    /**
     * Extract PIN from WebSocket path (e.g., /ws/{pin}) or query parameter.
     */
    private String extractPin(String path, ServerHttpRequest request) {
        // Try to extract from path: /ws/{pin}
        if (path.startsWith("/ws/")) {
            String[] segments = path.split("/");
            if (segments.length >= 3) {
                return segments[2];
            }
        }

        // Fallback: try query parameter
        String pinParam = request.getQueryParams().getFirst("pin");
        if (pinParam != null && !pinParam.isBlank()) {
            return pinParam;
        }

        return null;
    }

    public static class Config {
        // Configuration properties can be added here if needed
    }
}
