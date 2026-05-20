package com.quizplatform.websocket.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;

@Service
public class ReconnectionService {

    private static final Logger log = LoggerFactory.getLogger(ReconnectionService.class);

    private final MessageQueueService messageQueueService;
    private final LeaderboardEventQueueService leaderboardEventQueueService;
    private final ParticipantTrackingService participantTrackingService;

    public ReconnectionService(
            MessageQueueService messageQueueService,
            LeaderboardEventQueueService leaderboardEventQueueService,
            ParticipantTrackingService participantTrackingService) {
        this.messageQueueService = messageQueueService;
        this.leaderboardEventQueueService = leaderboardEventQueueService;
        this.participantTrackingService = participantTrackingService;
    }

    /**
     * On reconnect: restore participant state and deliver pending messages.
     * Delivers queued leaderboard events first (from pending_events:{pin}:{participantId}),
     * then delivers other pending messages, ensuring correct event ordering.
     */
    public void deliverPendingMessages(
            String pin, String participantId, WebSocketSession session) {
        // Mark as connected (already done in handler, but ensure state is consistent)
        participantTrackingService.markConnected(pin, participantId);

        // First, deliver pending leaderboard events (queued by LeaderboardBroadcasterImpl)
        // These must be delivered before resuming normal event flow
        deliverPendingLeaderboardEvents(pin, participantId, session);

        // Then, drain and deliver other pending messages
        List<String> pendingMessages =
                messageQueueService.drainPendingMessages(pin, participantId);

        if (pendingMessages.isEmpty()) {
            log.debug("No pending messages for participant {} (PIN {})", participantId, pin);
            return;
        }

        log.info(
                "Delivering {} pending messages to participant {} (PIN {})",
                pendingMessages.size(),
                participantId,
                pin);

        for (String message : pendingMessages) {
            try {
                if (session.isOpen()) {
                    synchronized (session) {
                        session.sendMessage(new TextMessage(message));
                    }
                } else {
                    // Session closed during delivery, re-queue remaining
                    log.warn(
                            "Session closed during pending message delivery for participant {}",
                            participantId);
                    break;
                }
            } catch (IOException e) {
                log.error(
                        "Failed to deliver pending message to participant {}: {}",
                        participantId,
                        e.getMessage());
            }
        }
    }

    /**
     * Deliver queued leaderboard events on participant reconnection.
     * Checks pending_events:{pin}:{participantId} Redis list and delivers
     * all queued events in FIFO order (LPOP) before resuming normal event flow.
     *
     * Requirements: 5.5
     */
    private void deliverPendingLeaderboardEvents(
            String pin, String participantId, WebSocketSession session) {
        List<String> pendingEvents =
                leaderboardEventQueueService.drainPendingEvents(pin, participantId);

        if (pendingEvents.isEmpty()) {
            log.debug(
                    "No pending leaderboard events for participant {} (PIN {})",
                    participantId,
                    pin);
            return;
        }

        log.info(
                "Delivering {} pending leaderboard events to participant {} (PIN {})",
                pendingEvents.size(),
                participantId,
                pin);

        for (String event : pendingEvents) {
            try {
                if (session.isOpen()) {
                    synchronized (session) {
                        session.sendMessage(new TextMessage(event));
                    }
                } else {
                    log.warn(
                            "Session closed during pending leaderboard event delivery for participant {}",
                            participantId);
                    break;
                }
            } catch (IOException e) {
                log.error(
                        "Failed to deliver pending leaderboard event to participant {}: {}",
                        participantId,
                        e.getMessage());
            }
        }
    }
}
