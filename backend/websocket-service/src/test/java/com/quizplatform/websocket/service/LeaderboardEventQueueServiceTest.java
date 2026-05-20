package com.quizplatform.websocket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

/**
 * Unit tests for LeaderboardEventQueueService.
 * Validates that pending leaderboard events are drained in FIFO order (LPOP)
 * from the pending_events:{pin}:{participantId} Redis list.
 *
 * Requirements: 5.5
 */
@ExtendWith(MockitoExtension.class)
class LeaderboardEventQueueServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ListOperations<String, String> listOperations;

    private LeaderboardEventQueueService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        service = new LeaderboardEventQueueService(redisTemplate);
    }

    @Test
    @DisplayName("Should drain all pending events in FIFO order using LPOP")
    void drainPendingEventsInOrder() {
        String pin = "ABC123";
        String participantId = "participant-1";
        String key = "pending_events:" + pin + ":" + participantId;

        when(listOperations.leftPop(key))
                .thenReturn("{\"type\":\"leaderboard.updated\",\"payload\":{\"sequenceNumber\":1}}")
                .thenReturn("{\"type\":\"leaderboard.updated\",\"payload\":{\"sequenceNumber\":2}}")
                .thenReturn("{\"type\":\"leaderboard.updated\",\"payload\":{\"sequenceNumber\":3}}")
                .thenReturn(null);

        List<String> events = service.drainPendingEvents(pin, participantId);

        assertThat(events).hasSize(3);
        assertThat(events.get(0)).contains("\"sequenceNumber\":1");
        assertThat(events.get(1)).contains("\"sequenceNumber\":2");
        assertThat(events.get(2)).contains("\"sequenceNumber\":3");
        verify(listOperations, times(4)).leftPop(key);
    }

    @Test
    @DisplayName("Should return empty list when no pending events exist")
    void noPendingEvents() {
        String pin = "ABC123";
        String participantId = "participant-1";
        String key = "pending_events:" + pin + ":" + participantId;

        when(listOperations.leftPop(key)).thenReturn(null);

        List<String> events = service.drainPendingEvents(pin, participantId);

        assertThat(events).isEmpty();
        verify(listOperations, times(1)).leftPop(key);
    }

    @Test
    @DisplayName("Should return empty list on Redis exception")
    void redisException() {
        String pin = "ABC123";
        String participantId = "participant-1";
        String key = "pending_events:" + pin + ":" + participantId;

        when(listOperations.leftPop(key))
                .thenThrow(new RuntimeException("Redis connection failed"));

        List<String> events = service.drainPendingEvents(pin, participantId);

        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("Should use correct Redis key format: pending_events:{pin}:{participantId}")
    void correctKeyFormat() {
        String pin = "XYZ789";
        String participantId = "user-abc-123";
        String expectedKey = "pending_events:XYZ789:user-abc-123";

        when(listOperations.leftPop(expectedKey)).thenReturn(null);

        service.drainPendingEvents(pin, participantId);

        verify(listOperations).leftPop(expectedKey);
    }
}
