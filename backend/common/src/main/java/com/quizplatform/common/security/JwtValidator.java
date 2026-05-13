package com.quizplatform.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Shared JWT validation utility for use across all microservices.
 * Allows any service to validate JWTs without depending on auth-service.
 */
public class JwtValidator {

    private JwtValidator() {
        // Utility class — no instantiation
    }

    /**
     * Validate a JWT token and return its claims.
     *
     * @param token  the JWT token string
     * @param secret the signing secret (must be at least 32 characters for HS256)
     * @return the parsed Claims from the token
     * @throws JwtValidationException if the token is invalid, expired, or malformed
     */
    public static Claims validateToken(String token, String secret) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new JwtValidationException("Token has expired", e);
        } catch (JwtException e) {
            throw new JwtValidationException("Invalid token: " + e.getMessage(), e);
        }
    }

    /**
     * Extract the user ID (subject) from a JWT token.
     *
     * @param token  the JWT token string
     * @param secret the signing secret
     * @return the user ID as a UUID
     * @throws JwtValidationException if the token is invalid or the subject is not a valid UUID
     */
    public static UUID extractUserId(String token, String secret) {
        Claims claims = validateToken(token, secret);
        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new JwtValidationException("Token does not contain a valid subject");
        }
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException e) {
            throw new JwtValidationException("Token subject is not a valid UUID: " + subject, e);
        }
    }

    /**
     * Extract a specific claim from a JWT token.
     *
     * @param token  the JWT token string
     * @param secret the signing secret
     * @param claim  the claim key to extract
     * @return the claim value as a String, or null if not present
     * @throws JwtValidationException if the token is invalid
     */
    public static String extractClaim(String token, String secret, String claim) {
        Claims claims = validateToken(token, secret);
        Object value = claims.get(claim);
        return value != null ? value.toString() : null;
    }
}
