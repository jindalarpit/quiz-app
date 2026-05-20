package com.quizplatform.websocket.service;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

/**
 * Unit tests for ReconnectionService.
 * Validates that on reconnection, pending leaderboard events are delivered
 * first (from pending_events:{pin}:{participantId}), followed by other
 * pending messages, ensuring correct event ordering.
 *
 * Requirements: 5.5
 */
@ExtendWith(MockitoExtension.class)
class ReconnectionServiceTest {

    @Mock private MessageQueueService messageQueueService;
    @Mock private LeaderboardEventQueueService leaderboardEventQueueService;
    @Mock private ParticipantTrackingService participantTrackingService;
    @Mock private WebSocketSession session;

    private ReconnectionService reconnectionService;

    @BeforeEach
    void setUp() {
        reconnectionService = new ReconnectionService(
                messageQueueService,
                leaderboardEventQueueService,
                participantTrackingService);
    }

    @Test
    @DisplayName("Should deliver pending leaderboard events before other pending messages")
    void deliverLeaderboardEventsFirst() throws Exception {
        String pin = "ABC123";
        String participantId = "participant-1";

        List<String> leaderboardEvents = Arrays.asList(
                "{\"type\":\"leaderboard.updated\",\"payload\":{\"sequenceNumber\":1}}",
                "{\"type\":\"leaderboard.updated\",\"payload\":{\"sequenceNumber\":2}}");
        List<String> otherMessages = Arrays.asList(
                "{\"type\":\"question.broadcast\",\"payload\":{}}");

        when(leaderboardEventQueueService.drainPendingEvents(pin, participantId))
                .thenReturn(leaderboardEvents);
        when(messageQueueService.drainPendingMessages(pin, participantId))
                .thenReturn(otherMessages);
        when(session.isOpen()).thenReturn(true);

        reconnectionService.deliverPendingMessages(pin, participantId, session);

        // Verify ordering: leaderboard events delivered before other messages
        InOrder inOrder = inOrder(session);
        inOrder.verify(session).sendMessage(new TextMessage(leaderboardEvents.get(0)));
        inOrder.verify(session).sendMessage(new TextMessage(leaderboardEvents.get(1)));
        inOrder.verify(session).sendMessage(new TextMessage(otherMessages.get(0)));
    }

    @Test
    @DisplayName("Should deliver leaderboard events even when no other pending messages exist")
    void deliverOnlyLeaderboardEvents() throws Exception {
        String pin = "ABC123";
        String participantId = "participant-1";

        List<String> leaderboardEvents = Arrays.asList(
                "{\"type\":\"leaderboard.updated\",\"payload\":{\"sequenceNumber\":5}}");

        when(leaderboardEventQueueService.drainPendingEvents(pin, participantId))
                .thenReturn(leaderboardEvents);
        when(messageQueueService.drainPendingMessages(pin, participantId))
                .thenReturn(Collections.emptyList());
        when(session.isOpen()).thenReturn(true);

        reconnectionService.deliverPendingMessages(pin, participantId, session);

        verify(session).sendMessage(new TextMessage(leaderboardEvents.get(0)));
    }

    @Test
    @DisplayName("Should handle no pending events gracefully")
    void noPendingEventsOrMessages() throws Exception {
        String pin = "ABC123";
        String participantId = "participant-1";

        when(leaderboardEventQueueService.drainPendingEvents(pin, participantId))
                .thenReturn(Collections.emptyList());
        when(messageQueueService.drainPendingMessages(pin, participantId))
                .thenReturn(Collections.emptyList());

        reconnectionService.deliverPendingMessages(pin, participantId, session);

        verify(session, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("Should mark participant as connected on reconnection")
    void markParticipantConnected() throws Exception {
        String pin = "ABC123";
        String participantId = "participant-1";

        when(leaderboardEventQueueService.drainPendingEvents(pin, participantId))
                .thenReturn(Collections.emptyList());
        when(messageQueueService.drainPendingMessages(pin, participantId))
                .thenReturn(Collections.emptyList());

        reconnectionService.deliverPendingMessages(pin, participantId, session);

        verify(participantTrackingService).markConnected(pin, participantId);
    }

    @Test
    @DisplayName("Should stop delivering leaderboard events if session closes mid-delivery")
    void stopOnSessionClose() throws Exception {
        String pin = "ABC123";
        String participantId = "participant-1";

        List<String> leaderboardEvents = Arrays.asList(
                "{\"type\":\"leaderboard.updated\",\"payload\":{\"sequenceNumber\":1}}",
                "{\"type\":\"leaderboard.updated\",\"payload\":{\"sequenceNumber\":2}}",
                "{\"type\":\"leaderboard.updated\",\"payload\":{\"sequenceNumber\":3}}");

        when(leaderboardEventQueueService.drainPendingEvents(pin, participantId))
                .thenReturn(leaderboardEvents);
        // Session is open for first message, then closes
        when(session.isOpen()).thenReturn(true).thenReturn(false);

        reconnectionService.deliverPendingMessages(pin, participantId, session);

        // Only the first message should be sent
        verify(session, times(1)).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("Should continue delivering other messages even if leaderboard event delivery fails")
    void continueOnLeaderboardEventFailure() throws Exception {
        String pin = "ABC123";
        String participantId = "participant-1";

        List<String> leaderboardEvents = Arrays.asList(
                "{\"type\":\"leaderboard.updated\",\"payload\":{\"sequenceNumber\":1}}");
        List<String> otherMessages = Arrays.asList(
                "{\"type\":\"question.broadcast\",\"payload\":{}}");

        when(leaderboardEventQueueService.drainPendingEvents(pin, participantId))
                .thenReturn(leaderboardEvents);
        when(messageQueueService.drainPendingMessages(pin, participantId))
                .thenReturn(otherMessages);
        when(session.isOpen()).thenReturn(true);

        // First sendMessage (leaderboard event) throws, second (other message) succeeds
        doThrow(new IOException("Send failed"))
                .doNothing()
                .when(session).sendMessage(any(TextMessage.class));

        reconnectionService.deliverPendingMessages(pin, participantId, session);

        // Both sends should be attempted
        verify(session, times(2)).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("Should deliver multiple leaderboard events in order")
    void multipleLeaderboardEventsInOrder() throws Exception {
        String pin = "ABC123";
        String participantId = "participant-1";

        List<String> leaderboardEvents = Arrays.asList(
                "{\"type\":\"leaderboard.updated\",\"payload\":{\"sequenceNumber\":1}}",
                "{\"type\":\"leaderboard.updated\",\"payload\":{\"sequenceNumber\":2}}",
                "{\"type\":\"leaderboard.updated\",\"payload\":{\"sequenceNumber\":3}}",
                "{\"type\":\"leaderboard.updated\",\"payload\":{\"sequenceNumber\":4}}");

        when(leaderboardEventQueueService.drainPendingEvents(pin, participantId))
                .thenReturn(leaderboardEvents);
        when(messageQueueService.drainPendingMessages(pin, participantId))
                .thenReturn(Collections.emptyList());
        when(session.isOpen()).thenReturn(true);

        reconnectionService.deliverPendingMessages(pin, participantId, session);

        InOrder inOrder = inOrder(session);
        inOrder.verify(session).sendMessage(new TextMessage(leaderboardEvents.get(0)));
        inOrder.verify(session).sendMessage(new TextMessage(leaderboardEvents.get(1)));
        inOrder.verify(session).sendMessage(new TextMessage(leaderboardEvents.get(2)));
        inOrder.verify(session).sendMessage(new TextMessage(leaderboardEvents.get(3)));
    }
}
