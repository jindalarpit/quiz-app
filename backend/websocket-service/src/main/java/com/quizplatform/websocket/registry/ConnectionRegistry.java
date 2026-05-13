package com.quizplatform.websocket.registry;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
public class ConnectionRegistry {

    private static final Logger log = LoggerFactory.getLogger(ConnectionRegistry.class);

    // PIN → Set of WebSocket sessions
    private final ConcurrentHashMap<String, Set<WebSocketSession>> pinToSessions =
            new ConcurrentHashMap<>();

    // sessionId → ConnectionInfo (reverse map)
    private final ConcurrentHashMap<String, ConnectionInfo> sessionToInfo =
            new ConcurrentHashMap<>();

    public void register(String pin, WebSocketSession session, ConnectionInfo info) {
        pinToSessions
                .computeIfAbsent(pin, k -> ConcurrentHashMap.newKeySet())
                .add(session);
        sessionToInfo.put(session.getId(), info);
        log.debug(
                "Registered session {} for PIN {} (participant: {}, host: {})",
                session.getId(),
                pin,
                info.getParticipantId(),
                info.isHost());
    }

    public void unregister(String pin, WebSocketSession session) {
        Set<WebSocketSession> sessions = pinToSessions.get(pin);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                pinToSessions.remove(pin);
            }
        }
        sessionToInfo.remove(session.getId());
        log.debug("Unregistered session {} from PIN {}", session.getId(), pin);
    }

    public Set<WebSocketSession> getSessionsForPin(String pin) {
        return pinToSessions.getOrDefault(pin, Collections.emptySet());
    }

    public int getConnectionCount(String pin) {
        Set<WebSocketSession> sessions = pinToSessions.get(pin);
        return sessions != null ? sessions.size() : 0;
    }

    public Optional<WebSocketSession> getSessionByParticipantId(String pin, String participantId) {
        Set<WebSocketSession> sessions = pinToSessions.get(pin);
        if (sessions == null) {
            return Optional.empty();
        }
        return sessions.stream()
                .filter(
                        session -> {
                            ConnectionInfo info = sessionToInfo.get(session.getId());
                            return info != null
                                    && participantId.equals(info.getParticipantId());
                        })
                .findFirst();
    }

    public Optional<WebSocketSession> getHostSession(String pin) {
        Set<WebSocketSession> sessions = pinToSessions.get(pin);
        if (sessions == null) {
            return Optional.empty();
        }
        return sessions.stream()
                .filter(
                        session -> {
                            ConnectionInfo info = sessionToInfo.get(session.getId());
                            return info != null && info.isHost();
                        })
                .findFirst();
    }

    public Optional<ConnectionInfo> getConnectionInfo(String sessionId) {
        return Optional.ofNullable(sessionToInfo.get(sessionId));
    }

    public Optional<String> getParticipantIdForSession(String pin, WebSocketSession session) {
        ConnectionInfo info = sessionToInfo.get(session.getId());
        if (info != null && info.getParticipantId() != null) {
            return Optional.of(info.getParticipantId());
        }
        return Optional.empty();
    }

    public boolean hasConnectionsForPin(String pin) {
        Set<WebSocketSession> sessions = pinToSessions.get(pin);
        return sessions != null && !sessions.isEmpty();
    }

    public Set<String> getActivePins() {
        return Collections.unmodifiableSet(pinToSessions.keySet());
    }
}
