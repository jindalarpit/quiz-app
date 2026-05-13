package com.quizplatform.websocket.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.util.Optional;
import java.util.Set;

class ConnectionRegistryTest {

    private ConnectionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ConnectionRegistry();
    }

    @Test
    @DisplayName("Should register and retrieve sessions for a PIN")
    void registerAndRetrieve() {
        WebSocketSession session = mockSession("session-1");
        ConnectionInfo info = ConnectionInfo.builder()
                .pin("ABC123")
                .participantId("participant-1")
                .isHost(false)
                .connectedAt(System.currentTimeMillis())
                .build();

        registry.register("ABC123", session, info);

        Set<WebSocketSession> sessions = registry.getSessionsForPin("ABC123");
        assertThat(sessions).hasSize(1).contains(session);
        assertThat(registry.getConnectionCount("ABC123")).isEqualTo(1);
    }

    @Test
    @DisplayName("Should register multiple sessions for same PIN")
    void registerMultipleSessions() {
        WebSocketSession session1 = mockSession("session-1");
        WebSocketSession session2 = mockSession("session-2");

        ConnectionInfo info1 = ConnectionInfo.builder()
                .pin("ABC123")
                .participantId("participant-1")
                .isHost(false)
                .connectedAt(System.currentTimeMillis())
                .build();
        ConnectionInfo info2 = ConnectionInfo.builder()
                .pin("ABC123")
                .participantId("participant-2")
                .isHost(false)
                .connectedAt(System.currentTimeMillis())
                .build();

        registry.register("ABC123", session1, info1);
        registry.register("ABC123", session2, info2);

        assertThat(registry.getConnectionCount("ABC123")).isEqualTo(2);
    }

    @Test
    @DisplayName("Should unregister session and clean up empty PIN")
    void unregisterSession() {
        WebSocketSession session = mockSession("session-1");
        ConnectionInfo info = ConnectionInfo.builder()
                .pin("ABC123")
                .participantId("participant-1")
                .isHost(false)
                .connectedAt(System.currentTimeMillis())
                .build();

        registry.register("ABC123", session, info);
        registry.unregister("ABC123", session);

        assertThat(registry.getConnectionCount("ABC123")).isEqualTo(0);
        assertThat(registry.hasConnectionsForPin("ABC123")).isFalse();
    }

    @Test
    @DisplayName("Should find session by participant ID")
    void findByParticipantId() {
        WebSocketSession session = mockSession("session-1");
        ConnectionInfo info = ConnectionInfo.builder()
                .pin("ABC123")
                .participantId("participant-1")
                .isHost(false)
                .connectedAt(System.currentTimeMillis())
                .build();

        registry.register("ABC123", session, info);

        Optional<WebSocketSession> found =
                registry.getSessionByParticipantId("ABC123", "participant-1");
        assertThat(found).isPresent().contains(session);
    }

    @Test
    @DisplayName("Should return empty when participant not found")
    void participantNotFound() {
        Optional<WebSocketSession> found =
                registry.getSessionByParticipantId("ABC123", "nonexistent");
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Should find host session")
    void findHostSession() {
        WebSocketSession hostSession = mockSession("host-session");
        WebSocketSession participantSession = mockSession("participant-session");

        ConnectionInfo hostInfo = ConnectionInfo.builder()
                .pin("ABC123")
                .participantId("host-user-id")
                .isHost(true)
                .connectedAt(System.currentTimeMillis())
                .build();
        ConnectionInfo participantInfo = ConnectionInfo.builder()
                .pin("ABC123")
                .participantId("participant-1")
                .isHost(false)
                .connectedAt(System.currentTimeMillis())
                .build();

        registry.register("ABC123", hostSession, hostInfo);
        registry.register("ABC123", participantSession, participantInfo);

        Optional<WebSocketSession> found = registry.getHostSession("ABC123");
        assertThat(found).isPresent().contains(hostSession);
    }

    @Test
    @DisplayName("Should return connection info by session ID")
    void getConnectionInfo() {
        WebSocketSession session = mockSession("session-1");
        ConnectionInfo info = ConnectionInfo.builder()
                .pin("ABC123")
                .participantId("participant-1")
                .isHost(false)
                .connectedAt(System.currentTimeMillis())
                .build();

        registry.register("ABC123", session, info);

        Optional<ConnectionInfo> found = registry.getConnectionInfo("session-1");
        assertThat(found).isPresent();
        assertThat(found.get().getPin()).isEqualTo("ABC123");
        assertThat(found.get().getParticipantId()).isEqualTo("participant-1");
        assertThat(found.get().isHost()).isFalse();
    }

    @Test
    @DisplayName("Should return empty sessions for unknown PIN")
    void unknownPin() {
        Set<WebSocketSession> sessions = registry.getSessionsForPin("UNKNOWN");
        assertThat(sessions).isEmpty();
    }

    @Test
    @DisplayName("Should track active PINs")
    void activePins() {
        WebSocketSession session1 = mockSession("session-1");
        WebSocketSession session2 = mockSession("session-2");

        ConnectionInfo info1 = ConnectionInfo.builder()
                .pin("ABC123")
                .participantId("p1")
                .isHost(false)
                .connectedAt(System.currentTimeMillis())
                .build();
        ConnectionInfo info2 = ConnectionInfo.builder()
                .pin("DEF456")
                .participantId("p2")
                .isHost(false)
                .connectedAt(System.currentTimeMillis())
                .build();

        registry.register("ABC123", session1, info1);
        registry.register("DEF456", session2, info2);

        assertThat(registry.getActivePins()).containsExactlyInAnyOrder("ABC123", "DEF456");
    }

    private WebSocketSession mockSession(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        return session;
    }
}
