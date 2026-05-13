package com.quizplatform.websocket.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

@Service
public class MessageQueueService {

    private static final Logger log = LoggerFactory.getLogger(MessageQueueService.class);
    private static final String PENDING_MESSAGES_PREFIX = "pending_messages:";
    private static final Duration MESSAGE_TTL = Duration.ofSeconds(120);

    private final StringRedisTemplate redisTemplate;

    public MessageQueueService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Queue a message for a disconnected participant. Messages expire after 120 seconds.
     */
    public void queueMessage(String pin, String participantId, String message) {
        String key = buildKey(pin, participantId);
        try {
            redisTemplate.opsForList().rightPush(key, message);
            redisTemplate.expire(key, MESSAGE_TTL);
            log.debug("Queued message for participant {} (PIN {})", participantId, pin);
        } catch (Exception e) {
            log.error(
                    "Failed to queue message for participant {}: {}",
                    participantId,
                    e.getMessage());
        }
    }

    /**
     * Retrieve and clear all pending messages for a participant.
     */
    public List<String> drainPendingMessages(String pin, String participantId) {
        String key = buildKey(pin, participantId);
        try {
            Long size = redisTemplate.opsForList().size(key);
            if (size == null || size == 0) {
                return Collections.emptyList();
            }

            List<String> messages = redisTemplate.opsForList().range(key, 0, -1);
            redisTemplate.delete(key);

            log.debug(
                    "Drained {} pending messages for participant {} (PIN {})",
                    messages != null ? messages.size() : 0,
                    participantId,
                    pin);

            return messages != null ? messages : Collections.emptyList();
        } catch (Exception e) {
            log.error(
                    "Failed to drain pending messages for participant {}: {}",
                    participantId,
                    e.getMessage());
            return Collections.emptyList();
        }
    }

    private String buildKey(String pin, String participantId) {
        return PENDING_MESSAGES_PREFIX + pin + ":" + participantId;
    }
}
