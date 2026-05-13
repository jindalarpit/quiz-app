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
    private final ParticipantTrackingService participantTrackingService;

    public ReconnectionService(
            MessageQueueService messageQueueService,
            ParticipantTrackingService participantTrackingService) {
        this.messageQueueService = messageQueueService;
        this.participantTrackingService = participantTrackingService;
    }

    /**
     * On reconnect: restore participant state and deliver pending messages.
     */
    public void deliverPendingMessages(
            String pin, String participantId, WebSocketSession session) {
        // Mark as connected (already done in handler, but ensure state is consistent)
        participantTrackingService.markConnected(pin, participantId);

        // Drain and deliver pending messages
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
}
