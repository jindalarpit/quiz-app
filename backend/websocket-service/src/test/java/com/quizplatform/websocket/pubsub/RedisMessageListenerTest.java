package com.quizplatform.websocket.pubsub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizplatform.websocket.service.MessageBroadcastService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests that the RedisMessageListener correctly routes session.ended events
 * to broadcastLeaderboardUpdate (with retry logic for 2-second delivery guarantee)
 * and other events to the standard broadcastToSession.
 *
 * Validates Requirements 1.1 and 7.1: WebSocket delivery of final leaderboard
 * within 2 seconds of session completion.
 */
@ExtendWith(MockitoExtension.class)
class RedisMessageListenerTest {

    @Mock private RedisMessageListenerContainer listenerContainer;
    @Mock private MessageBroadcastService messageBroadcastService;

    private RedisMessageListener redisMessageListener;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        redisMessageListener = new RedisMessageListener(
                listenerContainer, messageBroadcastService, objectMapper);
    }

    @Test
    @DisplayName("session.ended event should use broadcastLeaderboardUpdate with retry logic")
    void sessionEndedEvent_usesBroadcastLeaderboardUpdate() {
        // Arrange
        String pin = "ABC123";
        String channel = "session:" + pin + ":broadcast";
        String sessionEndedEvent = "{\"type\":\"session.ended\",\"payload\":{\"sessionId\":\"test-id\",\"leaderboard\":[]}}";

        Message message = new DefaultMessage(
                channel.getBytes(StandardCharsets.UTF_8),
                sessionEndedEvent.getBytes(StandardCharsets.UTF_8));

        // Subscribe first
        redisMessageListener.subscribeIfNeeded(pin);

        // Act
        redisMessageListener.onMessage(message, null);

        // Assert - should use broadcastLeaderboardUpdate (has retry logic for 2s delivery)
        verify(messageBroadcastService).broadcastLeaderboardUpdate(pin, sessionEndedEvent);
        verify(messageBroadcastService, never()).broadcastToSession(anyString(), anyString());
    }

    @Test
    @DisplayName("session.ended event with leaderboard payload should use retry broadcast")
    void sessionEndedEventWithLeaderboard_usesBroadcastLeaderboardUpdate() {
        // Arrange
        String pin = "XYZ789";
        String channel = "session:" + pin + ":broadcast";
        String sessionEndedEvent = "{\"type\":\"session.ended\",\"payload\":{\"sessionId\":\"abc-123\"," +
                "\"leaderboard\":[{\"rank\":1,\"nickname\":\"Player1\",\"score\":8500}]}}";

        Message message = new DefaultMessage(
                channel.getBytes(StandardCharsets.UTF_8),
                sessionEndedEvent.getBytes(StandardCharsets.UTF_8));

        redisMessageListener.subscribeIfNeeded(pin);

        // Act
        redisMessageListener.onMessage(message, null);

        // Assert
        verify(messageBroadcastService).broadcastLeaderboardUpdate(pin, sessionEndedEvent);
        verify(messageBroadcastService, never()).broadcastToSession(anyString(), anyString());
    }

    @Test
    @DisplayName("Non session.ended events should use standard broadcastToSession")
    void nonSessionEndedEvent_usesBroadcastToSession() {
        // Arrange
        String pin = "ABC123";
        String channel = "session:" + pin + ":broadcast";
        String stateChangeEvent = "{\"type\":\"session.state_changed\",\"payload\":{\"state\":\"QUESTION_OPEN\"}}";

        Message message = new DefaultMessage(
                channel.getBytes(StandardCharsets.UTF_8),
                stateChangeEvent.getBytes(StandardCharsets.UTF_8));

        redisMessageListener.subscribeIfNeeded(pin);

        // Act
        redisMessageListener.onMessage(message, null);

        // Assert - should use standard broadcast (no retry needed)
        verify(messageBroadcastService).broadcastToSession(pin, stateChangeEvent);
        verify(messageBroadcastService, never()).broadcastLeaderboardUpdate(anyString(), anyString());
    }

    @Test
    @DisplayName("session.joined event should use standard broadcastToSession")
    void sessionJoinedEvent_usesBroadcastToSession() {
        // Arrange
        String pin = "ABC123";
        String channel = "session:" + pin + ":broadcast";
        String joinEvent = "{\"type\":\"session.joined\",\"payload\":{\"nickname\":\"Player1\",\"count\":5}}";

        Message message = new DefaultMessage(
                channel.getBytes(StandardCharsets.UTF_8),
                joinEvent.getBytes(StandardCharsets.UTF_8));

        redisMessageListener.subscribeIfNeeded(pin);

        // Act
        redisMessageListener.onMessage(message, null);

        // Assert
        verify(messageBroadcastService).broadcastToSession(pin, joinEvent);
        verify(messageBroadcastService, never()).broadcastLeaderboardUpdate(anyString(), anyString());
    }

    @Test
    @DisplayName("question.start event should use standard broadcastToSession")
    void questionStartEvent_usesBroadcastToSession() {
        // Arrange
        String pin = "ABC123";
        String channel = "session:" + pin + ":broadcast";
        String questionEvent = "{\"type\":\"question.start\",\"payload\":{\"questionId\":\"q1\"}}";

        Message message = new DefaultMessage(
                channel.getBytes(StandardCharsets.UTF_8),
                questionEvent.getBytes(StandardCharsets.UTF_8));

        redisMessageListener.subscribeIfNeeded(pin);

        // Act
        redisMessageListener.onMessage(message, null);

        // Assert
        verify(messageBroadcastService).broadcastToSession(pin, questionEvent);
        verify(messageBroadcastService, never()).broadcastLeaderboardUpdate(anyString(), anyString());
    }

    @Test
    @DisplayName("Malformed JSON should fall back to string matching for session.ended detection")
    void malformedJson_fallsBackToStringMatching() {
        // Arrange
        String pin = "ABC123";
        String channel = "session:" + pin + ":broadcast";
        // Malformed JSON but contains the session.ended type string
        String malformedEvent = "{\"type\":\"session.ended\", invalid json here";

        Message message = new DefaultMessage(
                channel.getBytes(StandardCharsets.UTF_8),
                malformedEvent.getBytes(StandardCharsets.UTF_8));

        redisMessageListener.subscribeIfNeeded(pin);

        // Act
        redisMessageListener.onMessage(message, null);

        // Assert - should still detect session.ended via string matching fallback
        verify(messageBroadcastService).broadcastLeaderboardUpdate(pin, malformedEvent);
    }

    @Test
    @DisplayName("Message from unknown channel should be ignored")
    void unknownChannel_isIgnored() {
        // Arrange
        String channel = "unknown:channel";
        String event = "{\"type\":\"session.ended\",\"payload\":{}}";

        Message message = new DefaultMessage(
                channel.getBytes(StandardCharsets.UTF_8),
                event.getBytes(StandardCharsets.UTF_8));

        // Act
        redisMessageListener.onMessage(message, null);

        // Assert - no broadcast should happen
        verify(messageBroadcastService, never()).broadcastToSession(anyString(), anyString());
        verify(messageBroadcastService, never()).broadcastLeaderboardUpdate(anyString(), anyString());
    }

    @Test
    @DisplayName("Subscribe and unsubscribe should manage PIN tracking correctly")
    void subscribeAndUnsubscribe() {
        String pin = "TEST01";

        // Initially not subscribed
        assertThat(redisMessageListener.isSubscribed(pin)).isFalse();

        // Subscribe
        redisMessageListener.subscribeIfNeeded(pin);
        assertThat(redisMessageListener.isSubscribed(pin)).isTrue();

        // Subscribe again (idempotent)
        redisMessageListener.subscribeIfNeeded(pin);
        assertThat(redisMessageListener.isSubscribed(pin)).isTrue();

        // Unsubscribe
        redisMessageListener.unsubscribeIfEmpty(pin);
        assertThat(redisMessageListener.isSubscribed(pin)).isFalse();
    }

    @Test
    @DisplayName("session.persistence_failed event should use standard broadcastToSession")
    void persistenceFailedEvent_usesBroadcastToSession() {
        // Arrange
        String pin = "ABC123";
        String channel = "session:" + pin + ":broadcast";
        String failureEvent = "{\"type\":\"session.persistence_failed\",\"payload\":{\"error\":\"Results could not be saved.\"}}";

        Message message = new DefaultMessage(
                channel.getBytes(StandardCharsets.UTF_8),
                failureEvent.getBytes(StandardCharsets.UTF_8));

        redisMessageListener.subscribeIfNeeded(pin);

        // Act
        redisMessageListener.onMessage(message, null);

        // Assert - persistence failure is not a leaderboard event, use standard broadcast
        verify(messageBroadcastService).broadcastToSession(pin, failureEvent);
        verify(messageBroadcastService, never()).broadcastLeaderboardUpdate(anyString(), anyString());
    }
}
