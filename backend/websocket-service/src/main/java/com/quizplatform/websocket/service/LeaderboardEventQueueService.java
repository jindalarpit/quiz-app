package com.quizplatform.websocket.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Service responsible for draining pending leaderboard events queued by the
 * session-service's LeaderboardBroadcasterImpl when WebSocket delivery fails.
 *
 * Events are stored in Redis list with key: pending_events:{pin}:{participantId}
 * and are delivered in FIFO order (LPOP) on participant reconnection.
 *
 * Requirements: 5.5
 */
@Service
public class LeaderboardEventQueueService {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardEventQueueService.class);
    private static final String PENDING_EVENTS_PREFIX = "pending_events:";

    private final StringRedisTemplate redisTemplate;

    public LeaderboardEventQueueService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Drain all pending leaderboard events for a participant in FIFO order (LPOP).
     * Returns the events in the order they were queued so they can be delivered
     * before resuming normal event flow.
     *
     * @param pin session PIN
     * @param participantId participant UUID
     * @return list of queued event payloads in order, empty list if none
     */
    public List<String> drainPendingEvents(String pin, String participantId) {
        String key = buildKey(pin, participantId);
        List<String> events = new ArrayList<>();

        try {
            String event;
            while ((event = redisTemplate.opsForList().leftPop(key)) != null) {
                events.add(event);
            }

            if (!events.isEmpty()) {
                log.debug(
                        "Drained {} pending leaderboard events for participant {} (PIN {})",
                        events.size(),
                        participantId,
                        pin);
            }

            return events;
        } catch (Exception e) {
            log.error(
                    "Failed to drain pending leaderboard events for participant {} (PIN {}): {}",
                    participantId,
                    pin,
                    e.getMessage());
            return Collections.emptyList();
        }
    }

    private String buildKey(String pin, String participantId) {
        return PENDING_EVENTS_PREFIX + pin + ":" + participantId;
    }
}
