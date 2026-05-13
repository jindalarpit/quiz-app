package com.quizplatform.websocket.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.quizplatform.websocket.registry.ConnectionRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ExtendWith(MockitoExtension.class)
class MessageBroadcastServiceTest {

    @Mock private ConnectionRegistry connectionRegistry;
    @Mock private MessageQueueService messageQueueService;
    @Mock private WebSocketSession session1;
    @Mock private WebSocketSession session2;
    @Mock private WebSocketSession hostSession;

    private MessageBroadcastService broadcastService;

    @BeforeEach
    void setUp() {
        broadcastService = new MessageBroadcastService(connectionRegistry, messageQueueService);
    }

    @Test
    @DisplayName("Should broadcast message to all sessions for a PIN")
    void broadcastToSession() throws Exception {
        Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
        sessions.add(session1);
        sessions.add(session2);

        when(connectionRegistry.getSessionsForPin("ABC123")).thenReturn(sessions);
        when(session1.isOpen()).thenReturn(true);
        when(session2.isOpen()).thenReturn(true);

        broadcastService.broadcastToSession("ABC123", "{\"type\":\"test\"}");

        verify(session1).sendMessage(any(TextMessage.class));
        verify(session2).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("Should skip closed sessions during broadcast")
    void skipClosedSessions() throws Exception {
        Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
        sessions.add(session1);
        sessions.add(session2);

        when(connectionRegistry.getSessionsForPin("ABC123")).thenReturn(sessions);
        when(session1.isOpen()).thenReturn(true);
        when(session2.isOpen()).thenReturn(false);

        broadcastService.broadcastToSession("ABC123", "{\"type\":\"test\"}");

        verify(session1).sendMessage(any(TextMessage.class));
        verify(session2, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("Should handle send failures gracefully during broadcast")
    void handleSendFailure() throws Exception {
        Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
        sessions.add(session1);
        sessions.add(session2);

        when(connectionRegistry.getSessionsForPin("ABC123")).thenReturn(sessions);
        when(session1.isOpen()).thenReturn(true);
        when(session2.isOpen()).thenReturn(true);
        doThrow(new IOException("Connection reset")).when(session1).sendMessage(any());

        // Should not throw, should continue sending to session2
        broadcastService.broadcastToSession("ABC123", "{\"type\":\"test\"}");

        verify(session2).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("Should send to specific participant when connected")
    void sendToConnectedParticipant() throws Exception {
        when(connectionRegistry.getSessionByParticipantId("ABC123", "p1"))
                .thenReturn(Optional.of(session1));
        when(session1.isOpen()).thenReturn(true);

        broadcastService.sendToParticipant("ABC123", "p1", "{\"type\":\"test\"}");

        verify(session1).sendMessage(any(TextMessage.class));
        verify(messageQueueService, never()).queueMessage(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Should queue message when participant is disconnected")
    void queueForDisconnectedParticipant() throws Exception {
        when(connectionRegistry.getSessionByParticipantId("ABC123", "p1"))
                .thenReturn(Optional.empty());

        broadcastService.sendToParticipant("ABC123", "p1", "{\"type\":\"test\"}");

        verify(messageQueueService).queueMessage("ABC123", "p1", "{\"type\":\"test\"}");
    }

    @Test
    @DisplayName("Should queue message when send to participant fails")
    void queueOnSendFailure() throws Exception {
        when(connectionRegistry.getSessionByParticipantId("ABC123", "p1"))
                .thenReturn(Optional.of(session1));
        when(session1.isOpen()).thenReturn(true);
        doThrow(new IOException("Connection reset")).when(session1).sendMessage(any());

        broadcastService.sendToParticipant("ABC123", "p1", "{\"type\":\"test\"}");

        verify(messageQueueService).queueMessage("ABC123", "p1", "{\"type\":\"test\"}");
    }

    @Test
    @DisplayName("Should send message to host")
    void sendToHost() throws Exception {
        when(connectionRegistry.getHostSession("ABC123")).thenReturn(Optional.of(hostSession));
        when(hostSession.isOpen()).thenReturn(true);

        broadcastService.sendToHost("ABC123", "{\"type\":\"test\"}");

        verify(hostSession).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("Should handle missing host gracefully")
    void sendToMissingHost() throws Exception {
        when(connectionRegistry.getHostSession("ABC123")).thenReturn(Optional.empty());

        // Should not throw
        broadcastService.sendToHost("ABC123", "{\"type\":\"test\"}");

        verify(hostSession, never()).sendMessage(any());
    }
}
