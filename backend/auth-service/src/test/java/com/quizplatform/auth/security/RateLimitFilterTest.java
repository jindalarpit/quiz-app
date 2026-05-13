package com.quizplatform.auth.security;

import com.quizplatform.common.exception.RateLimitExceededException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class RateLimitFilterTest {

    private RateLimitFilter rateLimitFilter;
    private FilterChain filterChain;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        rateLimitFilter = new RateLimitFilter();
        filterChain = mock(FilterChain.class);
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("Should allow requests within rate limit (5 per minute)")
    void shouldAllowRequestsWithinLimit() throws ServletException, IOException {
        MockHttpServletRequest request = createRequest("/api/auth/login", "192.168.1.1");

        for (int i = 0; i < 5; i++) {
            rateLimitFilter.doFilterInternal(request, response, filterChain);
        }

        verify(filterChain, times(5)).doFilter(request, response);
    }

    @Test
    @DisplayName("Should throw RateLimitExceededException after 5 requests per minute")
    void shouldRejectRequestsExceedingLimit() throws ServletException, IOException {
        MockHttpServletRequest request = createRequest("/api/auth/login", "192.168.1.2");

        // Exhaust the 5 allowed requests
        for (int i = 0; i < 5; i++) {
            rateLimitFilter.doFilterInternal(request, response, filterChain);
        }

        // 6th request should be rejected
        assertThatThrownBy(() -> rateLimitFilter.doFilterInternal(request, response, filterChain))
                .isInstanceOf(RateLimitExceededException.class)
                .hasFieldOrPropertyWithValue("retryAfterSeconds", 60L);
    }

    @Test
    @DisplayName("Should track rate limits per IP address independently")
    void shouldTrackPerIpIndependently() throws ServletException, IOException {
        MockHttpServletRequest request1 = createRequest("/api/auth/login", "10.0.0.1");
        MockHttpServletRequest request2 = createRequest("/api/auth/login", "10.0.0.2");

        // Exhaust limit for IP 1
        for (int i = 0; i < 5; i++) {
            rateLimitFilter.doFilterInternal(request1, response, filterChain);
        }

        // IP 2 should still be allowed
        rateLimitFilter.doFilterInternal(request2, response, filterChain);
        verify(filterChain, times(6)).doFilter(any(), eq(response));
    }

    @Test
    @DisplayName("Should not filter non-auth endpoints")
    void shouldNotFilterNonAuthEndpoints() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/quizzes");

        boolean shouldNotFilter = rateLimitFilter.shouldNotFilter(request);

        assert shouldNotFilter;
    }

    @Test
    @DisplayName("Should filter all /api/auth/ endpoints")
    void shouldFilterAuthEndpoints() {
        String[] authPaths = {"/api/auth/login", "/api/auth/register", "/api/auth/refresh"};

        for (String path : authPaths) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI(path);

            boolean shouldNotFilter = rateLimitFilter.shouldNotFilter(request);

            assert !shouldNotFilter : "Should filter path: " + path;
        }
    }

    @Test
    @DisplayName("Should use X-Forwarded-For header for client IP when present")
    void shouldUseXForwardedForHeader() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/auth/login");
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.50, 70.41.3.18, 150.172.238.178");

        // Exhaust limit for the forwarded IP (203.0.113.50)
        for (int i = 0; i < 5; i++) {
            rateLimitFilter.doFilterInternal(request, response, filterChain);
        }

        // 6th request from same forwarded IP should be rejected
        assertThatThrownBy(() -> rateLimitFilter.doFilterInternal(request, response, filterChain))
                .isInstanceOf(RateLimitExceededException.class);

        // A request from a different forwarded IP should still work
        MockHttpServletRequest differentIpRequest = new MockHttpServletRequest();
        differentIpRequest.setRequestURI("/api/auth/login");
        differentIpRequest.setRemoteAddr("127.0.0.1");
        differentIpRequest.addHeader("X-Forwarded-For", "198.51.100.1");

        rateLimitFilter.doFilterInternal(differentIpRequest, response, filterChain);
        verify(filterChain, times(6)).doFilter(any(), eq(response));
    }

    @Test
    @DisplayName("Should apply rate limit to register endpoint")
    void shouldApplyRateLimitToRegisterEndpoint() throws ServletException, IOException {
        MockHttpServletRequest request = createRequest("/api/auth/register", "172.16.0.1");

        for (int i = 0; i < 5; i++) {
            rateLimitFilter.doFilterInternal(request, response, filterChain);
        }

        assertThatThrownBy(() -> rateLimitFilter.doFilterInternal(request, response, filterChain))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    @DisplayName("Should apply rate limit to refresh endpoint")
    void shouldApplyRateLimitToRefreshEndpoint() throws ServletException, IOException {
        MockHttpServletRequest request = createRequest("/api/auth/refresh", "172.16.0.2");

        for (int i = 0; i < 5; i++) {
            rateLimitFilter.doFilterInternal(request, response, filterChain);
        }

        assertThatThrownBy(() -> rateLimitFilter.doFilterInternal(request, response, filterChain))
                .isInstanceOf(RateLimitExceededException.class);
    }

    private MockHttpServletRequest createRequest(String uri, String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(uri);
        request.setRemoteAddr(remoteAddr);
        return request;
    }
}
