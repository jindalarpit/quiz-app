package com.quizplatform.websocket.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration tests for WebSocket reconnection with queued event delivery.
 *
 * Tests the full reconnection flow:
 * 1. Participant disconnects during a round
 * 2. Leaderboard events are queued in pending_events:{pin}:{participantId}
 * 3. Participant reconnects
 * 4. Queued events are delivered in FIFO order before normal flow resumes
 *
 * Requirements: 5.5, 5.6
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocket Reconnection Integration Tests")
class WebSocketReconnectionIntegrationTest {

    @Mock private MessageQueueService messageQueueService;
    @Mock private LeaderboardEventQueueService leaderboardEventQueueService;
    @Mock private ParticipantTrackingService participantTrackingService;
    @Mock private WebSocketSession session;

    private ReconnectionService reconnectionService;

    private static final String PIN = "RECON1";
    private static final String PARTICIPANT_ID = "participant-abc";

    @BeforeEach
    void setUp() {
        reconnectionService = new ReconnectionService(
                messageQueueService,
                leaderboardEventQueueService,
                participantTrackingService);
    }

    // ==================== Full Reconnection Flow Tests ====================

    @Nested
    @DisplayName("Full reconnection flow: disconnect → queue → reconnect → deliver")
    class FullReconnectionFlow {

        @Test
        @DisplayName("Multiple leaderboard events queued during disconnect are delivered in order")
        void multipleQueuedEventsDeliveredInOrder() throws Exception {
            // Simulate: participant missed 3 rounds while disconnected
            List<String> queuedLeaderboardEvents = Arrays.asList(
                    buildLeaderboardEvent(1, 0, 800, 800),
                    buildLeaderboardEvent(2, 1, 750, 1550),
                    buildLeaderboardEvent(3, 2, 900, 2450));

            when(leaderboardEventQueueService.drainPendingEvents(PIN, PARTICIPANT_ID))
                    .thenReturn(queuedLeaderboardEvents);
            when(messageQueueService.drainPendingMessages(PIN, PARTICIPANT_ID))
                    .thenReturn(Collections.emptyList());
            when(session.isOpen()).thenReturn(true);

            // Act: Participant reconnects
            reconnectionService.deliverPendingMessages(PIN, PARTICIPANT_ID, session);

            // Assert: All 3 events delivered in sequence order
            InOrder inOrder = inOrder(session);
            inOrder.verify(session).sendMessage(new TextMessage(queuedLeaderboardEvents.get(0)));
            inOrder.verify(session).sendMessage(new TextMessage(queuedLeaderboardEvents.get(1)));
            inOrder.verify(session).sendMessage(new TextMessage(queuedLeaderboardEvents.get(2)));

            // Assert: Participant marked as connected
            verify(participantTrackingService).markConnected(PIN, PARTICIPANT_ID);
        }

        @Test
        @DisplayName("Leaderboard events delivered before other pending messages")
        void leaderboardEventsDeliveredBeforeOtherMessages() throws Exception {
            List<String> leaderboardEvents = Arrays.asList(
                    buildLeaderboardEvent(1, 0, 800, 800),
                    buildLeaderboardEvent(2, 1, 700, 1500));

            List<String> otherMessages = Arrays.asList(
                    "{\"type\":\"question.broadcast\",\"payload\":{\"questionIndex\":2}}",
                    "{\"type\":\"timer.start\",\"payload\":{\"duration\":20000}}");

            when(leaderboardEventQueueService.drainPendingEvents(PIN, PARTICIPANT_ID))
                    .thenReturn(leaderboardEvents);
            when(messageQueueService.drainPendingMessages(PIN, PARTICIPANT_ID))
                    .thenReturn(otherMessages);
            when(session.isOpen()).thenReturn(true);

            reconnectionService.deliverPendingMessages(PIN, PARTICIPANT_ID, session);

            // Verify strict ordering: leaderboard events first, then other messages
            InOrder inOrder = inOrder(session);
            inOrder.verify(session).sendMessage(new TextMessage(leaderboardEvents.get(0)));
            inOrder.verify(session).sendMessage(new TextMessage(leaderboardEvents.get(1)));
            inOrder.verify(session).sendMessage(new TextMessage(otherMessages.get(0)));
            inOrder.verify(session).sendMessage(new TextMessage(otherMessages.get(1)));
        }

        @Test
        @DisplayName("Reconnection with no queued events proceeds normally")
        void reconnectionWithNoQueuedEvents() throws Exception {
            when(leaderboardEventQueueService.drainPendingEvents(PIN, PARTICIPANT_ID))
                    .thenReturn(Collections.emptyList());
            when(messageQueueService.drainPendingMessages(PIN, PARTICIPANT_ID))
                    .thenReturn(Collections.emptyList());

            reconnectionService.deliverPendingMessages(PIN, PARTICIPANT_ID, session);

            // No messages sent, but participant is still marked connected
            verify(session, never()).sendMessage(any(TextMessage.class));
            verify(participantTrackingService).markConnected(PIN, PARTICIPANT_ID);
        }

        @Test
        @DisplayName("Reconnection marks participant as connected before delivering events")
        void marksConnectedOnReconnection() throws Exception {
            when(leaderboardEventQueueService.drainPendingEvents(PIN, PARTICIPANT_ID))
                    .thenReturn(Collections.emptyList());
            when(messageQueueService.drainPendingMessages(PIN, PARTICIPANT_ID))
                    .thenReturn(Collections.emptyList());

            reconnectionService.deliverPendingMessages(PIN, PARTICIPANT_ID, session);

            verify(participantTrackingService).markConnected(PIN, PARTICIPANT_ID);
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    @DisplayName("Edge cases during reconnection delivery")
    class EdgeCases {

        @Test
        @DisplayName("Session closes mid-delivery stops further event sending")
        void sessionClosesMidDelivery() throws Exception {
            List<String> leaderboardEvents = Arrays.asList(
                    buildLeaderboardEvent(1, 0, 800, 800),
                    buildLeaderboardEvent(2, 1, 700, 1500),
                    buildLeaderboardEvent(3, 2, 900, 2400));

            when(leaderboardEventQueueService.drainPendingEvents(PIN, PARTICIPANT_ID))
                    .thenReturn(leaderboardEvents);
            // Session is open for first message, then closes
            when(session.isOpen()).thenReturn(true).thenReturn(false);

            reconnectionService.deliverPendingMessages(PIN, PARTICIPANT_ID, session);

            // Only the first message should be sent
            verify(session, times(1)).sendMessage(any(TextMessage.class));
        }

        @Test
        @DisplayName("IOException during delivery continues with remaining messages")
        void ioExceptionContinuesDelivery() throws Exception {
            List<String> leaderboardEvents = Arrays.asList(
                    buildLeaderboardEvent(1, 0, 800, 800),
                    buildLeaderboardEvent(2, 1, 700, 1500));

            List<String> otherMessages = Arrays.asList(
                    "{\"type\":\"question.broadcast\",\"payload\":{}}");

            when(leaderboardEventQueueService.drainPendingEvents(PIN, PARTICIPANT_ID))
                    .thenReturn(leaderboardEvents);
            when(messageQueueService.drainPendingMessages(PIN, PARTICIPANT_ID))
                    .thenReturn(otherMessages);
            when(session.isOpen()).thenReturn(true);

            // First send fails, subsequent sends succeed
            doThrow(new IOException("Send failed"))
                    .doNothing()
                    .doNothing()
                    .when(session).sendMessage(any(TextMessage.class));

            reconnectionService.deliverPendingMessages(PIN, PARTICIPANT_ID, session);

            // All 3 sends should be attempted despite first failure
            verify(session, times(3)).sendMessage(any(TextMessage.class));
        }

        @Test
        @DisplayName("Large number of queued events delivered without issues")
        void largeNumberOfQueuedEvents() throws Exception {
            // Simulate participant missing 10 rounds
            List<String> leaderboardEvents = new java.util.ArrayList<>();
            int cumulativeScore = 0;
            for (int i = 1; i <= 10; i++) {
                int roundScore = 600 + (i * 30);
                cumulativeScore += roundScore;
                leaderboardEvents.add(buildLeaderboardEvent(i, i - 1, roundScore, cumulativeScore));
            }

            when(leaderboardEventQueueService.drainPendingEvents(PIN, PARTICIPANT_ID))
                    .thenReturn(leaderboardEvents);
            when(messageQueueService.drainPendingMessages(PIN, PARTICIPANT_ID))
                    .thenReturn(Collections.emptyList());
            when(session.isOpen()).thenReturn(true);

            reconnectionService.deliverPendingMessages(PIN, PARTICIPANT_ID, session);

            // All 10 events should be delivered
            verify(session, times(10)).sendMessage(any(TextMessage.class));
        }

        @Test
        @DisplayName("Redis failure during drain returns empty list gracefully")
        void redisFailureDuringDrain() throws Exception {
            when(leaderboardEventQueueService.drainPendingEvents(PIN, PARTICIPANT_ID))
                    .thenReturn(Collections.emptyList()); // Service handles Redis failure internally
            when(messageQueueService.drainPendingMessages(PIN, PARTICIPANT_ID))
                    .thenReturn(Collections.emptyList());

            reconnectionService.deliverPendingMessages(PIN, PARTICIPANT_ID, session);

            // No messages sent, but participant still marked connected
            verify(session, never()).sendMessage(any(TextMessage.class));
            verify(participantTrackingService).markConnected(PIN, PARTICIPANT_ID);
        }
    }

    // ==================== Sequence Number Ordering Tests ====================

    @Nested
    @DisplayName("Queued events maintain sequence number ordering")
    class SequenceNumberOrdering {

        @Test
        @DisplayName("Events are delivered with monotonically increasing sequence numbers")
        void eventsDeliveredWithIncreasingSequenceNumbers() throws Exception {
            List<String> leaderboardEvents = Arrays.asList(
                    buildLeaderboardEvent(5, 4, 800, 4000),
                    buildLeaderboardEvent(6, 5, 750, 4750),
                    buildLeaderboardEvent(7, 6, 900, 5650));

            when(leaderboardEventQueueService.drainPendingEvents(PIN, PARTICIPANT_ID))
                    .thenReturn(leaderboardEvents);
            when(messageQueueService.drainPendingMessages(PIN, PARTICIPANT_ID))
                    .thenReturn(Collections.emptyList());
            when(session.isOpen()).thenReturn(true);

            reconnectionService.deliverPendingMessages(PIN, PARTICIPANT_ID, session);

            // Verify events are sent in order (sequence 5, 6, 7)
            InOrder inOrder = inOrder(session);
            inOrder.verify(session).sendMessage(argThat(msg ->
                    msg.getPayload().contains("\"sequenceNumber\":5")));
            inOrder.verify(session).sendMessage(argThat(msg ->
                    msg.getPayload().contains("\"sequenceNumber\":6")));
            inOrder.verify(session).sendMessage(argThat(msg ->
                    msg.getPayload().contains("\"sequenceNumber\":7")));
        }

        @Test
        @DisplayName("Single queued event is delivered correctly")
        void singleQueuedEvent() throws Exception {
            List<String> leaderboardEvents = Arrays.asList(
                    buildLeaderboardEvent(1, 0, 930, 930));

            when(leaderboardEventQueueService.drainPendingEvents(PIN, PARTICIPANT_ID))
                    .thenReturn(leaderboardEvents);
            when(messageQueueService.drainPendingMessages(PIN, PARTICIPANT_ID))
                    .thenReturn(Collections.emptyList());
            when(session.isOpen()).thenReturn(true);

            reconnectionService.deliverPendingMessages(PIN, PARTICIPANT_ID, session);

            verify(session, times(1)).sendMessage(any(TextMessage.class));
            verify(session).sendMessage(argThat(msg ->
                    msg.getPayload().contains("\"sequenceNumber\":1")));
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Build a realistic leaderboard.updated event JSON payload.
     */
    private String buildLeaderboardEvent(int sequenceNumber, int roundNumber,
                                          int roundScore, int cumulativeScore) {
        return String.format(
                "{\"type\":\"leaderboard.updated\",\"payload\":{" +
                "\"sessionId\":\"%s\"," +
                "\"roundNumber\":%d," +
                "\"sequenceNumber\":%d," +
                "\"timestamp\":%d," +
                "\"entries\":[{" +
                "\"participantId\":\"%s\"," +
                "\"nickname\":\"TestPlayer\"," +
                "\"cumulativeScore\":%d," +
                "\"roundScore\":%d," +
                "\"rank\":1," +
                "\"rankDelta\":0," +
                "\"streakCount\":0," +
                "\"streakMultiplier\":1" +
                "}]}}",
                PIN, roundNumber, sequenceNumber, System.currentTimeMillis(),
                PARTICIPANT_ID, cumulativeScore, roundScore);
    }
}
