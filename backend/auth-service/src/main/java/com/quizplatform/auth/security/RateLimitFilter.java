package com.quizplatform.auth.security;

import com.quizplatform.common.exception.RateLimitExceededException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rate limiting filter for authentication endpoints.
 * Limits each IP address to 5 requests per minute on /api/auth/** endpoints.
 * Uses a simple sliding window counter (no external dependencies).
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_REQUESTS_PER_MINUTE = 5;
    private static final long WINDOW_MS = 60_000L;
    private static final long RETRY_AFTER_SECONDS = 60;

    private final ConcurrentMap<String, RateWindow> windows = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/auth/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String clientIp = resolveClientIp(request);
        RateWindow window = windows.computeIfAbsent(clientIp, k -> new RateWindow());

        if (window.tryConsume()) {
            filterChain.doFilter(request, response);
        } else {
            throw new RateLimitExceededException(RETRY_AFTER_SECONDS);
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Simple fixed-window rate limiter per IP.
     */
    private static class RateWindow {
        private volatile long windowStart = System.currentTimeMillis();
        private final AtomicInteger count = new AtomicInteger(0);

        boolean tryConsume() {
            long now = System.currentTimeMillis();
            if (now - windowStart > WINDOW_MS) {
                // Reset window
                synchronized (this) {
                    if (now - windowStart > WINDOW_MS) {
                        windowStart = now;
                        count.set(0);
                    }
                }
            }
            return count.incrementAndGet() <= MAX_REQUESTS_PER_MINUTE;
        }
    }
}
