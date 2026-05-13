package com.quizplatform.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * CORS configuration for the API Gateway.
 * Configurable allowed origins with sensible defaults for development.
 */
@Configuration
public class CorsConfig {

    @Value("${gateway.cors.allowed-origins:*}")
    private String allowedOrigins;

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration corsConfig = new CorsConfiguration();

        // Parse comma-separated origins
        List<String> origins = Arrays.asList(allowedOrigins.split(","));
        origins.forEach(origin -> corsConfig.addAllowedOriginPattern(origin.trim()));

        // Allowed methods
        corsConfig.addAllowedMethod("GET");
        corsConfig.addAllowedMethod("POST");
        corsConfig.addAllowedMethod("PUT");
        corsConfig.addAllowedMethod("DELETE");
        corsConfig.addAllowedMethod("OPTIONS");

        // Allowed headers
        corsConfig.addAllowedHeader("Authorization");
        corsConfig.addAllowedHeader("Content-Type");
        corsConfig.addAllowedHeader("X-User-Id");
        corsConfig.addAllowedHeader("X-Correlation-Id");

        // Exposed headers
        corsConfig.addExposedHeader("X-Correlation-Id");
        corsConfig.addExposedHeader("Retry-After");

        corsConfig.setAllowCredentials(false);
        corsConfig.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);

        return new CorsWebFilter(source);
    }
}
