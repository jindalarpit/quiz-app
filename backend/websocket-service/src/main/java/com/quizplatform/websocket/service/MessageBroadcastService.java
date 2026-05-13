package com.quizplatform.websocket.service;

import com.quizplatform.websocket.registry.ConnectionInfo;
import com.quizplatform.websocket.registry.ConnectionRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

@Service
public class MessageBroadcastService {

    private static final Logger log = LoggerFactory.getLogger(MessageBroadcastService.class);

    private static final int MAX_RETRIES = 3;
    private static final long[] RETRY_DELAYS_MS = {200, 500, 1300}; // Exponential backoff within 2s

    private final ConnectionRegistry connectionRegistry;
    private final MessageQueueService messageQueueService;

    public MessageBroadcastService(
            ConnectionRegistry connectionRegistry, MessageQueueService messageQueueService) {
        this.connectionRegistry = connectionRegistry;
        this.messageQueueService = messageQueueService;
    }

    /**
     * Broadcast a message to all connections for a given PIN.
     */
    public void broadcastToSession(String pin, String message) {
        Set<WebSocketSession> sessions = connectionRegistry.getSessionsForPin(pin);
        TextMessage textMessage = new TextMessage(message);

        int sent = 0;
        int failed = 0;

        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    synchronized (session) {
                        session.sendMessage(textMessage);
                    }
                    sent++;
                } catch (IOException e) {
                    log.debug(
                            "Failed to send message to session {}: {}",
                            session.getId(),
                            e.getMessage());
                    failed++;
                }
            }
        }

        log.debug(
                "Broadcast to PIN {}: sent={}, failed={}, total={}",
                pin,
                sent,
                failed,
                sessions.size());
    }

    /**
     * Broadcast a leaderboard update to all participants with retry logic.
     * If delivery fails for a participant, retries up to 3 times with exponential backoff
     * (200ms, 500ms, 1300ms). If still unsuccessful, queues the message for reconnect delivery.
     */
    public void broadcastLeaderboardUpdate(String pin, String message) {
        Set<WebSocketSession> sessions = connectionRegistry.getSessionsForPin(pin);
        TextMessage textMessage = new TextMessage(message);

        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                boolean delivered = sendWithRetry(session, textMessage);
                if (!delivered) {
                    // Queue for delivery on reconnect
                    Optional<String> participantId = connectionRegistry.getParticipantIdForSession(pin, session);
                    participantId.ifPresent(id -> {
                        log.info("Leaderboard delivery failed after retries for participant {}, queuing", id);
                        messageQueueService.queueMessage(pin, id, message);
                    });
                }
            } else {
                // Session not open, queue for reconnect
                Optional<String> participantId = connectionRegistry.getParticipantIdForSession(pin, session);
                participantId.ifPresent(id -> messageQueueService.queueMessage(pin, id, message));
            }
        }
    }

    /**
     * Send a message to a specific participant. If the participant is disconnected, queue the
     * message.
     */
    public void sendToParticipant(String pin, String participantId, String message) {
        Optional<WebSocketSession> sessionOpt =
                connectionRegistry.getSessionByParticipantId(pin, participantId);

        if (sessionOpt.isPresent() && sessionOpt.get().isOpen()) {
            try {
                synchronized (sessionOpt.get()) {
                    sessionOpt.get().sendMessage(new TextMessage(message));
                }
            } catch (IOException e) {
                log.debug(
                        "Failed to send to participant {}, queuing message: {}",
                        participantId,
                        e.getMessage());
                messageQueueService.queueMessage(pin, participantId, message);
            }
        } else {
            // Participant is disconnected, queue the message
            messageQueueService.queueMessage(pin, participantId, message);
        }
    }

    /**
     * Send a leaderboard message to a specific participant with retry logic.
     * Retries up to 3 times with exponential backoff (200ms, 500ms, 1300ms).
     */
    public void sendLeaderboardToParticipant(String pin, String participantId, String message) {
        Optional<WebSocketSession> sessionOpt =
                connectionRegistry.getSessionByParticipantId(pin, participantId);

        if (sessionOpt.isPresent() && sessionOpt.get().isOpen()) {
            boolean delivered = sendWithRetry(sessionOpt.get(), new TextMessage(message));
            if (!delivered) {
                log.info("Leaderboard delivery failed after retries for participant {}, queuing", participantId);
                messageQueueService.queueMessage(pin, participantId, message);
            }
        } else {
            // Participant is disconnected, queue the message for reconnect delivery
            messageQueueService.queueMessage(pin, participantId, message);
        }
    }

    /**
     * Send a message to the host connection only.
     */
    public void sendToHost(String pin, String message) {
        Optional<WebSocketSession> hostSession = connectionRegistry.getHostSession(pin);

        if (hostSession.isPresent() && hostSession.get().isOpen()) {
            try {
                synchronized (hostSession.get()) {
                    hostSession.get().sendMessage(new TextMessage(message));
                }
            } catch (IOException e) {
                log.error(
                        "Failed to send message to host for PIN {}: {}",
                        pin,
                        e.getMessage());
            }
        } else {
            log.warn("No host connection found for PIN {}", pin);
        }
    }

    /**
     * Attempt to send a message with retry logic.
     * Uses exponential backoff: 200ms, 500ms, 1300ms (total ~2s).
     *
     * @return true if message was delivered successfully, false if all retries exhausted
     */
    private boolean sendWithRetry(WebSocketSession session, TextMessage message) {
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            if (!session.isOpen()) {
                return false;
            }

            try {
                synchronized (session) {
                    session.sendMessage(message);
                }
                return true;
            } catch (IOException e) {
                if (attempt < MAX_RETRIES) {
                    log.debug("Send attempt {} failed for session {}, retrying in {}ms",
                            attempt + 1, session.getId(), RETRY_DELAYS_MS[attempt]);
                    try {
                        Thread.sleep(RETRY_DELAYS_MS[attempt]);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                } else {
                    log.debug("All {} retry attempts exhausted for session {}",
                            MAX_RETRIES, session.getId());
                }
            }
        }
        return false;
    }
}
