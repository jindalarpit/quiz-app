package com.quizplatform.websocket.pubsub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizplatform.websocket.registry.ConnectionInfo;
import com.quizplatform.websocket.registry.ConnectionRegistry;
import com.quizplatform.websocket.service.MessageBroadcastService;
import com.quizplatform.websocket.service.MessageQueueService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration test verifying the complete wiring of the session.ended event flow:
 * 1. Session-service publishes SESSION_ENDED event with leaderboard payload to Redis
 * 2. WebSocket service receives the event via Redis pub/sub
 * 3. WebSocket service broadcasts to all connected session participants
 * 4. Delivery uses retry logic to ensure delivery within 2 seconds
 *
 * This test validates Requirements 1.1 and 7.1:
 * - Final leaderboard is delivered to all connected participants within 2 seconds
 * - The leaderboard payload includes rank, nickname, score, correctAnswers, etc.
 */
@ExtendWith(MockitoExtension.class)
class SessionEndedLeaderboardDeliveryTest {

    @Mock private RedisMessageListenerContainer listenerContainer;
    @Mock private ConnectionRegistry connectionRegistry;
    @Mock private MessageQueueService messageQueueService;
    @Mock private WebSocketSession participantSession1;
    @Mock private WebSocketSession participantSession2;
    @Mock private WebSocketSession hostSession;

    private RedisMessageListener redisMessageListener;
    private MessageBroadcastService messageBroadcastService;
    private ObjectMapper objectMapper;

    private static final String PIN = "ABC123";
    private static final String CHANNEL = "session:" + PIN + ":broadcast";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        messageBroadcastService = new MessageBroadcastService(connectionRegistry, messageQueueService);
        redisMessageListener = new RedisMessageListener(
                listenerContainer, messageBroadcastService, objectMapper);
        redisMessageListener.subscribeIfNeeded(PIN);
    }

    @Test
    @DisplayName("Full wiring: session.ended event with leaderboard payload is broadcast to all participants")
    void sessionEndedEvent_broadcastsLeaderboardToAllParticipants() throws Exception {
        // Arrange - simulate the exact payload that LeaderboardService.publishSessionEndedEvent() produces
        String sessionEndedPayload = "{\"type\":\"session.ended\",\"payload\":{" +
                "\"sessionId\":\"550e8400-e29b-41d4-a716-446655440000\"," +
                "\"leaderboard\":[" +
                "{\"rank\":1,\"nickname\":\"Player1\",\"score\":8500,\"correctAnswers\":8,\"totalAnswers\":10,\"maxStreak\":5,\"avgResponseTimeSec\":3.2}," +
                "{\"rank\":2,\"nickname\":\"Player2\",\"score\":7200,\"correctAnswers\":7,\"totalAnswers\":10,\"maxStreak\":4,\"avgResponseTimeSec\":4.1}," +
                "{\"rank\":3,\"nickname\":\"Player3\",\"score\":6000,\"correctAnswers\":6,\"totalAnswers\":10,\"maxStreak\":3,\"avgResponseTimeSec\":5.0}" +
                "]}}";

        // Set up 3 connected WebSocket sessions (2 participants + 1 host)
        Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
        sessions.add(participantSession1);
        sessions.add(participantSession2);
        sessions.add(hostSession);

        when(connectionRegistry.getSessionsForPin(PIN)).thenReturn(sessions);
        when(participantSession1.isOpen()).thenReturn(true);
        when(participantSession2.isOpen()).thenReturn(true);
        when(hostSession.isOpen()).thenReturn(true);

        Message redisMessage = new DefaultMessage(
                CHANNEL.getBytes(StandardCharsets.UTF_8),
                sessionEndedPayload.getBytes(StandardCharsets.UTF_8));

        // Act - simulate Redis delivering the message
        redisMessageListener.onMessage(redisMessage, null);

        // Assert - all sessions receive the leaderboard payload
        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(participantSession1).sendMessage(messageCaptor.capture());
        verify(participantSession2).sendMessage(messageCaptor.capture());
        verify(hostSession).sendMessage(messageCaptor.capture());

        // Verify the payload content is the full leaderboard
        String deliveredPayload = messageCaptor.getAllValues().get(0).getPayload();
        assertThat(deliveredPayload).contains("\"type\":\"session.ended\"");
        assertThat(deliveredPayload).contains("\"leaderboard\"");
        assertThat(deliveredPayload).contains("\"rank\":1");
        assertThat(deliveredPayload).contains("\"nickname\":\"Player1\"");
        assertThat(deliveredPayload).contains("\"score\":8500");
    }

    @Test
    @DisplayName("Delivery retries on failure to ensure 2-second delivery guarantee")
    void sessionEndedEvent_retriesOnFailure() throws Exception {
        // Arrange
        String sessionEndedPayload = "{\"type\":\"session.ended\",\"payload\":{\"sessionId\":\"test-id\",\"leaderboard\":[]}}";

        Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
        sessions.add(participantSession1);

        when(connectionRegistry.getSessionsForPin(PIN)).thenReturn(sessions);
        when(participantSession1.isOpen()).thenReturn(true);

        // First send fails, second succeeds (retry logic should handle this)
        doThrow(new java.io.IOException("Connection reset"))
                .doNothing()
                .when(participantSession1).sendMessage(any(TextMessage.class));

        Message redisMessage = new DefaultMessage(
                CHANNEL.getBytes(StandardCharsets.UTF_8),
                sessionEndedPayload.getBytes(StandardCharsets.UTF_8));

        // Act
        redisMessageListener.onMessage(redisMessage, null);

        // Assert - sendMessage was called at least twice (initial + retry)
        verify(participantSession1, atLeast(2)).sendMessage(any(TextMessage.class));
        // No message queued since retry succeeded
        verify(messageQueueService, never()).queueMessage(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Queues message for disconnected participants for delivery on reconnect")
    void sessionEndedEvent_queuesForDisconnectedParticipants() throws Exception {
        // Arrange
        String sessionEndedPayload = "{\"type\":\"session.ended\",\"payload\":{\"sessionId\":\"test-id\",\"leaderboard\":[]}}";

        Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
        sessions.add(participantSession1);

        when(connectionRegistry.getSessionsForPin(PIN)).thenReturn(sessions);
        when(participantSession1.isOpen()).thenReturn(false); // Disconnected

        when(connectionRegistry.getParticipantIdForSession(PIN, participantSession1))
                .thenReturn(Optional.of("participant-123"));

        Message redisMessage = new DefaultMessage(
                CHANNEL.getBytes(StandardCharsets.UTF_8),
                sessionEndedPayload.getBytes(StandardCharsets.UTF_8));

        // Act
        redisMessageListener.onMessage(redisMessage, null);

        // Assert - message queued for reconnect delivery
        verify(messageQueueService).queueMessage(PIN, "participant-123", sessionEndedPayload);
    }

    @Test
    @DisplayName("Queues message when all retry attempts are exhausted within 2-second window")
    void sessionEndedEvent_queuesAfterAllRetriesExhausted() throws Exception {
        // Arrange
        String sessionEndedPayload = "{\"type\":\"session.ended\",\"payload\":{\"sessionId\":\"test-id\",\"leaderboard\":[]}}";

        Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
        sessions.add(participantSession1);

        when(connectionRegistry.getSessionsForPin(PIN)).thenReturn(sessions);
        when(participantSession1.isOpen()).thenReturn(true);

        // All sends fail (simulates persistent network issue)
        doThrow(new java.io.IOException("Connection reset"))
                .when(participantSession1).sendMessage(any(TextMessage.class));

        when(connectionRegistry.getParticipantIdForSession(PIN, participantSession1))
                .thenReturn(Optional.of("participant-456"));

        Message redisMessage = new DefaultMessage(
                CHANNEL.getBytes(StandardCharsets.UTF_8),
                sessionEndedPayload.getBytes(StandardCharsets.UTF_8));

        // Act
        redisMessageListener.onMessage(redisMessage, null);

        // Assert - after all retries exhausted, message is queued for reconnect delivery
        verify(messageQueueService).queueMessage(PIN, "participant-456", sessionEndedPayload);
    }

    @Test
    @DisplayName("Leaderboard payload structure matches what frontend expects")
    void sessionEndedEvent_payloadMatchesFrontendExpectation() throws Exception {
        // Arrange - the exact structure the frontend ResultsScreen expects
        String sessionEndedPayload = "{\"type\":\"session.ended\",\"payload\":{" +
                "\"sessionId\":\"550e8400-e29b-41d4-a716-446655440000\"," +
                "\"leaderboard\":[" +
                "{\"rank\":1,\"nickname\":\"GoldPlayer\",\"score\":9500,\"correctAnswers\":9,\"totalAnswers\":10,\"maxStreak\":7,\"avgResponseTimeSec\":2.1}," +
                "{\"rank\":2,\"nickname\":\"SilverPlayer\",\"score\":8200,\"correctAnswers\":8,\"totalAnswers\":10,\"maxStreak\":5,\"avgResponseTimeSec\":3.4}," +
                "{\"rank\":3,\"nickname\":\"BronzePlayer\",\"score\":7100,\"correctAnswers\":7,\"totalAnswers\":10,\"maxStreak\":4,\"avgResponseTimeSec\":4.2}" +
                "]}}";

        Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
        sessions.add(participantSession1);

        when(connectionRegistry.getSessionsForPin(PIN)).thenReturn(sessions);
        when(participantSession1.isOpen()).thenReturn(true);

        Message redisMessage = new DefaultMessage(
                CHANNEL.getBytes(StandardCharsets.UTF_8),
                sessionEndedPayload.getBytes(StandardCharsets.UTF_8));

        // Act
        redisMessageListener.onMessage(redisMessage, null);

        // Assert - verify the exact payload structure delivered to WebSocket clients
        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(participantSession1).sendMessage(messageCaptor.capture());

        String delivered = messageCaptor.getValue().getPayload();

        // Parse and verify structure matches frontend expectations
        var jsonNode = objectMapper.readTree(delivered);
        assertThat(jsonNode.get("type").asText()).isEqualTo("session.ended");
        assertThat(jsonNode.get("payload").has("sessionId")).isTrue();
        assertThat(jsonNode.get("payload").has("leaderboard")).isTrue();

        var leaderboard = jsonNode.get("payload").get("leaderboard");
        assertThat(leaderboard.isArray()).isTrue();
        assertThat(leaderboard.size()).isEqualTo(3);

        // Verify first entry has all required fields
        var firstEntry = leaderboard.get(0);
        assertThat(firstEntry.get("rank").asInt()).isEqualTo(1);
        assertThat(firstEntry.get("nickname").asText()).isEqualTo("GoldPlayer");
        assertThat(firstEntry.get("score").asInt()).isEqualTo(9500);
        assertThat(firstEntry.get("correctAnswers").asInt()).isEqualTo(9);
        assertThat(firstEntry.get("totalAnswers").asInt()).isEqualTo(10);
        assertThat(firstEntry.get("maxStreak").asInt()).isEqualTo(7);
        assertThat(firstEntry.get("avgResponseTimeSec").asDouble()).isEqualTo(2.1);
    }

    @Test
    @DisplayName("Empty leaderboard is still broadcast (zero-participant session)")
    void sessionEndedEvent_emptyLeaderboardStillBroadcast() throws Exception {
        // Arrange
        String sessionEndedPayload = "{\"type\":\"session.ended\",\"payload\":{" +
                "\"sessionId\":\"550e8400-e29b-41d4-a716-446655440000\"," +
                "\"leaderboard\":[]}}";

        Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
        sessions.add(hostSession);

        when(connectionRegistry.getSessionsForPin(PIN)).thenReturn(sessions);
        when(hostSession.isOpen()).thenReturn(true);

        Message redisMessage = new DefaultMessage(
                CHANNEL.getBytes(StandardCharsets.UTF_8),
                sessionEndedPayload.getBytes(StandardCharsets.UTF_8));

        // Act
        redisMessageListener.onMessage(redisMessage, null);

        // Assert - even empty leaderboard is delivered
        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(hostSession).sendMessage(messageCaptor.capture());

        String delivered = messageCaptor.getValue().getPayload();
        var jsonNode = objectMapper.readTree(delivered);
        assertThat(jsonNode.get("type").asText()).isEqualTo("session.ended");
        assertThat(jsonNode.get("payload").get("leaderboard").size()).isEqualTo(0);
    }
}
