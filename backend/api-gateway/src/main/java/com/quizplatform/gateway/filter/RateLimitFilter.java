package com.quizplatform.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Global rate limiting filter using Redis.
 * Applies different rate limits for authenticated vs unauthenticated users.
 * Key resolver: user ID for authenticated, IP for unauthenticated.
 */
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final String RATE_LIMIT_PREFIX = "rate_limit:";
    private static final String HEADER_USER_ID = "X-User-Id";

    private final ReactiveStringRedisTemplate redisTemplate;

    @Value("${gateway.rate-limit.authenticated-requests-per-minute:100}")
    private int authenticatedLimit;

    @Value("${gateway.rate-limit.unauthenticated-requests-per-minute:20}")
    private int unauthenticatedLimit;

    public RateLimitFilter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String userId = request.getHeaders().getFirst(HEADER_USER_ID);

        boolean isAuthenticated = userId != null && !userId.isBlank();
        String key = resolveKey(exchange, isAuthenticated, userId);
        int limit = isAuthenticated ? authenticatedLimit : unauthenticatedLimit;

        return checkRateLimit(key, limit)
                .flatMap(allowed -> {
                    if (allowed) {
                        return chain.filter(exchange);
                    } else {
                        return rateLimitExceededResponse(exchange, limit);
                    }
                });
    }

    @Override
    public int getOrder() {
        // Run after JWT validation filter so X-User-Id header is available
        return -90;
    }

    private String resolveKey(ServerWebExchange exchange, boolean isAuthenticated, String userId) {
        if (isAuthenticated) {
            return RATE_LIMIT_PREFIX + "user:" + userId;
        }
        String ip = resolveClientIp(exchange);
        return RATE_LIMIT_PREFIX + "ip:" + ip;
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();

        // Check X-Forwarded-For header first (for proxied requests)
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }

        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null) {
            InetAddress address = remoteAddress.getAddress();
            if (address != null) {
                return address.getHostAddress();
            }
        }
        return "unknown";
    }

    private Mono<Boolean> checkRateLimit(String key, int limit) {
        return redisTemplate.opsForValue().increment(key)
                .flatMap(count -> {
                    if (count == 1) {
                        // First request in window — set expiry to 60 seconds
                        return redisTemplate.expire(key, Duration.ofSeconds(60))
                                .thenReturn(true);
                    }
                    return Mono.just(count <= limit);
                })
                .onErrorResume(e -> {
                    // If Redis is unavailable, allow the request (fail-open)
                    log.warn("Rate limit check failed, allowing request: {}", e.getMessage());
                    return Mono.just(true);
                });
    }

    private Mono<Void> rateLimitExceededResponse(ServerWebExchange exchange, int limit) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set("Retry-After", "60");
        String body = String.format(
                "{\"timestamp\":\"%s\",\"status\":429,\"errorCode\":\"RATE_LIMIT_EXCEEDED\","
                        + "\"message\":\"Rate limit exceeded. Maximum %d requests per minute.\","
                        + "\"path\":\"%s\"}",
                java.time.Instant.now().toString(),
                limit,
                exchange.getRequest().getURI().getPath()
        );
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(bytes))
        );
    }
}
