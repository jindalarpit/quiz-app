package com.quizplatform.session.service;

import com.quizplatform.session.model.Session;
import com.quizplatform.session.model.SessionStatus;
import com.quizplatform.session.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Scheduled job that checks for sessions approaching Redis TTL expiry.
 * 
 * Redis keys have a 4-hour TTL that handles auto-expiry. This job runs every 5 minutes
 * to find sessions that have been in a non-ENDED state for over 4 hours and persists
 * them to PostgreSQL before they expire from Redis.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionCleanupJob {

    private static final long SESSION_MAX_DURATION_MS = 4 * 60 * 60 * 1000L; // 4 hours

    private final StringRedisTemplate redisTemplate;
    private final SessionRepository sessionRepository;
    private final RedisSessionService redisSessionService;

    /**
     * Runs every 5 minutes to check for orphaned/expired sessions.
     * Sessions that have been active for more than 4 hours are persisted to PostgreSQL
     * and their state is set to ENDED.
     */
    @Scheduled(fixedRate = 300000) // every 5 minutes
    public void cleanupExpiredSessions() {
        log.info("Session cleanup job started");
        int cleanedCount = 0;

        try {
            ScanOptions scanOptions = ScanOptions.scanOptions()
                    .match("session:*")
                    .count(100)
                    .build();

            try (Cursor<String> cursor = redisTemplate.scan(scanOptions)) {
                while (cursor.hasNext()) {
                    String key = cursor.next();
                    String pin = extractPinFromKey(key);
                    if (pin == null) {
                        continue;
                    }

                    if (shouldCleanup(pin)) {
                        persistAndCleanup(pin);
                        cleanedCount++;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error during session cleanup job", e);
        }

        if (cleanedCount > 0) {
            log.info("Session cleanup job completed: {} sessions cleaned up", cleanedCount);
        } else {
            log.debug("Session cleanup job completed: no sessions needed cleanup");
        }
    }

    private boolean shouldCleanup(String pin) {
        Map<Object, Object> fields = redisSessionService.getSessionFields(pin);
        if (fields == null || fields.isEmpty()) {
            return false;
        }

        String state = fields.getOrDefault("state", "").toString();
        if (SessionStatus.ENDED.name().equals(state)) {
            return false; // Already ended, no cleanup needed
        }

        Object createdAtObj = fields.get("created_at");
        if (createdAtObj == null) {
            return false;
        }

        long createdAt = Long.parseLong(createdAtObj.toString());
        long elapsed = Instant.now().toEpochMilli() - createdAt;

        return elapsed >= SESSION_MAX_DURATION_MS;
    }

    private void persistAndCleanup(String pin) {
        try {
            Map<Object, Object> fields = redisSessionService.getSessionFields(pin);
            if (fields == null || fields.isEmpty()) {
                return;
            }

            Session session = Session.builder()
                    .quizId(UUID.fromString(fields.get("quiz_id").toString()))
                    .hostId(UUID.fromString(fields.get("host_id").toString()))
                    .pin(pin)
                    .status(SessionStatus.ENDED)
                    .startedAt(Instant.ofEpochMilli(Long.parseLong(fields.get("created_at").toString())))
                    .endedAt(Instant.now())
                    .participantCount(Integer.parseInt(fields.getOrDefault("participant_count", "0").toString()))
                    .build();

            sessionRepository.save(session);

            // Mark session as ended in Redis so it won't be picked up again
            redisSessionService.updateSessionState(pin, SessionStatus.ENDED.name());

            log.info("Expired session persisted and cleaned up: pin={}, age={}h",
                    pin, SESSION_MAX_DURATION_MS / (60 * 60 * 1000));
        } catch (Exception e) {
            log.error("Failed to persist expired session: pin={}", pin, e);
        }
    }

    private String extractPinFromKey(String key) {
        // Key format: "session:{pin}"
        if (key == null || !key.startsWith("session:")) {
            return null;
        }
        String pin = key.substring("session:".length());
        // Validate it looks like a PIN (6 chars, no colons indicating sub-keys)
        if (pin.length() == 6 && !pin.contains(":")) {
            return pin;
        }
        return null;
    }
}
