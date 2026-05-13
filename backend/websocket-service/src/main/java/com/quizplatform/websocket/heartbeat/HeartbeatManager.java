package com.quizplatform.websocket.heartbeat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.quizplatform.websocket.registry.ConnectionInfo;
import com.quizplatform.websocket.registry.ConnectionRegistry;
import com.quizplatform.websocket.service.ParticipantTrackingService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class HeartbeatManager {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatManager.class);
    private static final int MAX_MISSED_HEARTBEATS = 3;

    private final ConcurrentHashMap<String, AtomicInteger> missedHeartbeats =
            new ConcurrentHashMap<>();
    private final Set<WebSocketSession> activeSessions = ConcurrentHashMap.newKeySet();
    private final ObjectMapper objectMapper;
    private final ConnectionRegistry connectionRegistry;
    private final ParticipantTrackingService participantTrackingService;

    public HeartbeatManager(
            ObjectMapper objectMapper,
            ConnectionRegistry connectionRegistry,
            ParticipantTrackingService participantTrackingService) {
        this.objectMapper = objectMapper;
        this.connectionRegistry = connectionRegistry;
        this.participantTrackingService = participantTrackingService;
    }

    public void registerSession(WebSocketSession session) {
        activeSessions.add(session);
        missedHeartbeats.put(session.getId(), new AtomicInteger(0));
    }

    public void unregisterSession(WebSocketSession session) {
        activeSessions.remove(session);
        missedHeartbeats.remove(session.getId());
    }

    public void recordAck(WebSocketSession session) {
        AtomicInteger missed = missedHeartbeats.get(session.getId());
        if (missed != null) {
            missed.set(0);
        }
    }

    @Scheduled(fixedRate = 15000)
    public void sendHeartbeats() {
        String heartbeatMessage = buildHeartbeatMessage();
        if (heartbeatMessage == null) {
            return;
        }

        TextMessage textMessage = new TextMessage(heartbeatMessage);

        for (WebSocketSession session : activeSessions) {
            if (!session.isOpen()) {
                unregisterSession(session);
                continue;
            }

            AtomicInteger missed = missedHeartbeats.get(session.getId());
            if (missed == null) {
                continue;
            }

            int missedCount = missed.incrementAndGet();

            if (missedCount > MAX_MISSED_HEARTBEATS) {
                // 3 consecutive heartbeats missed (45s without ack)
                log.warn(
                        "Session {} missed {} heartbeats, closing connection",
                        session.getId(),
                        missedCount);
                closeStaleSession(session);
                continue;
            }

            try {
                session.sendMessage(textMessage);
            } catch (IOException e) {
                log.debug(
                        "Failed to send heartbeat to session {}: {}",
                        session.getId(),
                        e.getMessage());
            }
        }
    }

    private void closeStaleSession(WebSocketSession session) {
        try {
            // Mark participant as disconnected before closing
            Optional<ConnectionInfo> info =
                    connectionRegistry.getConnectionInfo(session.getId());
            if (info.isPresent() && info.get().getParticipantId() != null && !info.get().isHost()) {
                participantTrackingService.markDisconnected(
                        info.get().getPin(), info.get().getParticipantId());
            }

            if (session.isOpen()) {
                session.close(CloseStatus.GOING_AWAY);
            }
        } catch (IOException e) {
            log.error(
                    "Error closing stale session {}: {}", session.getId(), e.getMessage());
        } finally {
            unregisterSession(session);
        }
    }

    private String buildHeartbeatMessage() {
        try {
            ObjectNode heartbeat = objectMapper.createObjectNode();
            heartbeat.put("type", "heartbeat");
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("timestamp", System.currentTimeMillis());
            heartbeat.set("payload", payload);
            return objectMapper.writeValueAsString(heartbeat);
        } catch (Exception e) {
            log.error("Failed to build heartbeat message: {}", e.getMessage());
            return null;
        }
    }
}
