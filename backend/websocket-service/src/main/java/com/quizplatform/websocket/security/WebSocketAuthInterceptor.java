package com.quizplatform.websocket.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.crypto.SecretKey;

@Component
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthInterceptor.class);
    private static final String ATTR_PIN = "pin";
    private static final String ATTR_PARTICIPANT_ID = "participantId";
    private static final String ATTR_IS_HOST = "isHost";

    private final StringRedisTemplate redisTemplate;

    @Value("${jwt.secret:defaultSecretKeyForDevelopmentOnlyDoNotUseInProduction123456}")
    private String jwtSecret;

    public WebSocketAuthInterceptor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {

        // Extract PIN from URL path: /ws/{pin}
        String path = request.getURI().getPath();
        String pin = extractPinFromPath(path);

        if (pin == null || pin.isEmpty()) {
            log.warn("WebSocket handshake rejected: no PIN in path");
            return false;
        }

        // Validate session exists in Redis
        String sessionKey = "session:" + pin;
        Boolean sessionExists = redisTemplate.hasKey(sessionKey);
        if (!Boolean.TRUE.equals(sessionExists)) {
            log.warn("WebSocket handshake rejected: session not found for PIN {}", pin);
            return false;
        }

        attributes.put(ATTR_PIN, pin);

        // Extract query parameters
        String query = request.getURI().getQuery();
        Map<String, String> queryParams = parseQueryParams(query);

        String token = queryParams.get("token");
        String participantId = queryParams.get("participantId");

        if (token != null && !token.isEmpty()) {
            // Host authentication via JWT
            return authenticateHost(token, pin, attributes);
        } else if (participantId != null && !participantId.isEmpty()) {
            // Participant authentication via PIN + participantId
            return authenticateParticipant(pin, participantId, attributes);
        } else {
            log.warn("WebSocket handshake rejected: no auth credentials for PIN {}", pin);
            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // No-op
    }

    private boolean authenticateHost(String token, String pin, Map<String, Object> attributes) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            Claims claims =
                    Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();

            String userId = claims.getSubject();

            // Verify this user is the host of the session
            String sessionHostId =
                    (String) redisTemplate.opsForHash().get("session:" + pin, "host_id");
            if (sessionHostId == null || !sessionHostId.equals(userId)) {
                log.warn("WebSocket handshake rejected: user {} is not host of PIN {}", userId, pin);
                return false;
            }

            attributes.put(ATTR_PARTICIPANT_ID, userId);
            attributes.put(ATTR_IS_HOST, true);
            log.debug("Host authenticated for PIN {}: userId={}", pin, userId);
            return true;

        } catch (Exception e) {
            log.warn("WebSocket handshake rejected: invalid JWT for PIN {}: {}", pin, e.getMessage());
            return false;
        }
    }

    private boolean authenticateParticipant(
            String pin, String participantId, Map<String, Object> attributes) {
        // Verify participant exists in Redis
        String participantKey = "participant:" + pin + ":" + participantId;
        Boolean participantExists = redisTemplate.hasKey(participantKey);

        if (!Boolean.TRUE.equals(participantExists)) {
            log.warn(
                    "WebSocket handshake rejected: participant {} not found for PIN {}",
                    participantId,
                    pin);
            return false;
        }

        // Check if participant has been kicked
        Object isKicked = redisTemplate.opsForHash().get(participantKey, "is_kicked");
        if ("true".equals(isKicked)) {
            log.warn(
                    "WebSocket handshake rejected: participant {} has been kicked from PIN {}",
                    participantId,
                    pin);
            return false;
        }

        // Duplicate connection prevention: reject if already connected
        Object isConnected = redisTemplate.opsForHash().get(participantKey, "is_connected");
        if ("true".equals(isConnected)) {
            log.warn(
                    "WebSocket handshake rejected: participant {} already connected for PIN {}",
                    participantId,
                    pin);
            return false;
        }

        attributes.put(ATTR_PARTICIPANT_ID, participantId);
        attributes.put(ATTR_IS_HOST, false);
        log.debug("Participant authenticated for PIN {}: participantId={}", pin, participantId);
        return true;
    }

    private String extractPinFromPath(String path) {
        // Path format: /ws/{pin}
        if (path == null) {
            return null;
        }
        String[] segments = path.split("/");
        // Expected: ["", "ws", "{pin}"]
        if (segments.length >= 3 && "ws".equals(segments[1])) {
            return segments[2];
        }
        return null;
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new java.util.HashMap<>();
        if (query == null || query.isEmpty()) {
            return params;
        }
        for (String param : query.split("&")) {
            String[] keyValue = param.split("=", 2);
            if (keyValue.length == 2) {
                params.put(keyValue[0], keyValue[1]);
            }
        }
        return params;
    }
}
