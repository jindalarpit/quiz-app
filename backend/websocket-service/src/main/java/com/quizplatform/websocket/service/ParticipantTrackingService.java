package com.quizplatform.websocket.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class ParticipantTrackingService {

    private static final Logger log = LoggerFactory.getLogger(ParticipantTrackingService.class);
    private static final String PARTICIPANT_KEY_PREFIX = "participant:";

    private final StringRedisTemplate redisTemplate;

    public ParticipantTrackingService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Mark a participant as connected in Redis.
     */
    public void markConnected(String pin, String participantId) {
        String key = buildParticipantKey(pin, participantId);
        try {
            redisTemplate.opsForHash().put(key, "is_connected", "true");
            redisTemplate.opsForHash().delete(key, "disconnect_time");
            log.debug("Marked participant {} connected for PIN {}", participantId, pin);
        } catch (Exception e) {
            log.error(
                    "Failed to mark participant {} connected for PIN {}: {}",
                    participantId,
                    pin,
                    e.getMessage());
        }
    }

    /**
     * Mark a participant as disconnected in Redis with disconnect timestamp.
     */
    public void markDisconnected(String pin, String participantId) {
        String key = buildParticipantKey(pin, participantId);
        try {
            redisTemplate.opsForHash().put(key, "is_connected", "false");
            redisTemplate
                    .opsForHash()
                    .put(key, "disconnect_time", String.valueOf(System.currentTimeMillis()));
            log.debug("Marked participant {} disconnected for PIN {}", participantId, pin);
        } catch (Exception e) {
            log.error(
                    "Failed to mark participant {} disconnected for PIN {}: {}",
                    participantId,
                    pin,
                    e.getMessage());
        }
    }

    /**
     * Check if a participant is currently connected.
     */
    public boolean isConnected(String pin, String participantId) {
        String key = buildParticipantKey(pin, participantId);
        try {
            Object value = redisTemplate.opsForHash().get(key, "is_connected");
            return "true".equals(value);
        } catch (Exception e) {
            log.error(
                    "Failed to check connection status for participant {}: {}",
                    participantId,
                    e.getMessage());
            return false;
        }
    }

    private String buildParticipantKey(String pin, String participantId) {
        return PARTICIPANT_KEY_PREFIX + pin + ":" + participantId;
    }
}
