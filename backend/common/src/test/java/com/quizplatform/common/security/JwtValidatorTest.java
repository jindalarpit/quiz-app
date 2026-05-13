package com.quizplatform.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JwtValidator - Shared JWT Validation Library")
class JwtValidatorTest {

    private static final String SECRET = "test-secret-key-must-be-at-least-32-characters-long";

    private String createToken(UUID userId, Instant expiry) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    private String createTokenWithClaim(UUID userId, Instant expiry, String claimKey, String claimValue) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(userId.toString())
                .claim(claimKey, claimValue)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    @Test
    @DisplayName("validateToken returns claims for a valid token")
    void validateToken_validToken_returnsClaims() {
        UUID userId = UUID.randomUUID();
        String token = createToken(userId, Instant.now().plus(1, ChronoUnit.HOURS));

        Claims claims = JwtValidator.validateToken(token, SECRET);

        assertNotNull(claims);
        assertEquals(userId.toString(), claims.getSubject());
    }

    @Test
    @DisplayName("validateToken throws JwtValidationException for expired token")
    void validateToken_expiredToken_throwsException() {
        UUID userId = UUID.randomUUID();
        String token = createToken(userId, Instant.now().minus(1, ChronoUnit.HOURS));

        JwtValidationException exception = assertThrows(
                JwtValidationException.class,
                () -> JwtValidator.validateToken(token, SECRET)
        );
        assertTrue(exception.getMessage().contains("expired"));
    }

    @Test
    @DisplayName("validateToken throws JwtValidationException for invalid signature")
    void validateToken_invalidSignature_throwsException() {
        UUID userId = UUID.randomUUID();
        String token = createToken(userId, Instant.now().plus(1, ChronoUnit.HOURS));
        String wrongSecret = "wrong-secret-key-must-be-at-least-32-characters-long";

        assertThrows(
                JwtValidationException.class,
                () -> JwtValidator.validateToken(token, wrongSecret)
        );
    }

    @Test
    @DisplayName("validateToken throws JwtValidationException for malformed token")
    void validateToken_malformedToken_throwsException() {
        assertThrows(
                JwtValidationException.class,
                () -> JwtValidator.validateToken("not.a.valid.token", SECRET)
        );
    }

    @Test
    @DisplayName("extractUserId returns UUID for valid token")
    void extractUserId_validToken_returnsUUID() {
        UUID userId = UUID.randomUUID();
        String token = createToken(userId, Instant.now().plus(1, ChronoUnit.HOURS));

        UUID extracted = JwtValidator.extractUserId(token, SECRET);

        assertEquals(userId, extracted);
    }

    @Test
    @DisplayName("extractUserId throws for token with non-UUID subject")
    void extractUserId_nonUuidSubject_throwsException() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .subject("not-a-uuid")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();

        JwtValidationException exception = assertThrows(
                JwtValidationException.class,
                () -> JwtValidator.extractUserId(token, SECRET)
        );
        assertTrue(exception.getMessage().contains("not a valid UUID"));
    }

    @Test
    @DisplayName("extractClaim returns claim value when present")
    void extractClaim_claimPresent_returnsValue() {
        UUID userId = UUID.randomUUID();
        String token = createTokenWithClaim(userId,
                Instant.now().plus(1, ChronoUnit.HOURS), "role", "HOST");

        String role = JwtValidator.extractClaim(token, SECRET, "role");

        assertEquals("HOST", role);
    }

    @Test
    @DisplayName("extractClaim returns null when claim is not present")
    void extractClaim_claimAbsent_returnsNull() {
        UUID userId = UUID.randomUUID();
        String token = createToken(userId, Instant.now().plus(1, ChronoUnit.HOURS));

        String role = JwtValidator.extractClaim(token, SECRET, "nonexistent");

        assertNull(role);
    }
}
