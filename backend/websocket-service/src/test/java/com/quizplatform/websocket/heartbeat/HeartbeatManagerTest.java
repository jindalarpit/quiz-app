package com.quizplatform.websocket.heartbeat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizplatform.websocket.registry.ConnectionInfo;
import com.quizplatform.websocket.registry.ConnectionRegistry;
import com.quizplatform.websocket.service.ParticipantTrackingService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class HeartbeatManagerTest {

    @Mock private ConnectionRegistry connectionRegistry;
    @Mock private ParticipantTrackingService participantTrackingService;
    @Mock private WebSocketSession session;

    private HeartbeatManager heartbeatManager;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        heartbeatManager =
                new HeartbeatManager(objectMapper, connectionRegistry, participantTrackingService);
    }

    @Test
    @DisplayName("Should send heartbeat to registered sessions")
    void sendHeartbeat() throws Exception {
        when(session.isOpen()).thenReturn(true);
        when(session.getId()).thenReturn("session-1");

        heartbeatManager.registerSession(session);
        heartbeatManager.sendHeartbeats();

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());

        String payload = captor.getValue().getPayload();
        var json = objectMapper.readTree(payload);
        assert json.get("type").asText().equals("heartbeat");
        assert json.get("payload").has("timestamp");
    }

    @Test
    @DisplayName("Should reset missed count on ack")
    void recordAck() throws Exception {
        when(session.isOpen()).thenReturn(true);
        when(session.getId()).thenReturn("session-1");

        heartbeatManager.registerSession(session);

        // Send 2 heartbeats without ack
        heartbeatManager.sendHeartbeats();
        heartbeatManager.sendHeartbeats();

        // Record ack - resets counter
        heartbeatManager.recordAck(session);

        // Send 3 more heartbeats - should not close since counter was reset
        heartbeatManager.sendHeartbeats();
        heartbeatManager.sendHeartbeats();
        heartbeatManager.sendHeartbeats();

        // Session should still be open (3 missed after reset, not > 3)
        verify(session, never()).close(any());
    }

    @Test
    @DisplayName("Should close session after 3 missed heartbeats")
    void closeAfterMissedHeartbeats() throws Exception {
        when(session.isOpen()).thenReturn(true);
        when(session.getId()).thenReturn("session-1");

        ConnectionInfo info = ConnectionInfo.builder()
                .pin("ABC123")
                .participantId("participant-1")
                .isHost(false)
                .build();
        when(connectionRegistry.getConnectionInfo("session-1")).thenReturn(Optional.of(info));

        heartbeatManager.registerSession(session);

        // Send 4 heartbeats without ack (exceeds MAX_MISSED_HEARTBEATS = 3)
        heartbeatManager.sendHeartbeats(); // missed = 1
        heartbeatManager.sendHeartbeats(); // missed = 2
        heartbeatManager.sendHeartbeats(); // missed = 3
        heartbeatManager.sendHeartbeats(); // missed = 4 > 3, should close

        verify(session).close(any());
        verify(participantTrackingService).markDisconnected("ABC123", "participant-1");
    }

    @Test
    @DisplayName("Should not send heartbeat to closed sessions")
    void skipClosedSessions() throws Exception {
        when(session.isOpen()).thenReturn(false);
        when(session.getId()).thenReturn("session-1");

        heartbeatManager.registerSession(session);
        heartbeatManager.sendHeartbeats();

        verify(session, never()).sendMessage(any());
    }

    @Test
    @DisplayName("Should unregister session")
    void unregisterSession() throws Exception {
        when(session.isOpen()).thenReturn(true);
        when(session.getId()).thenReturn("session-1");

        heartbeatManager.registerSession(session);
        heartbeatManager.unregisterSession(session);
        heartbeatManager.sendHeartbeats();

        verify(session, never()).sendMessage(any());
    }
}
