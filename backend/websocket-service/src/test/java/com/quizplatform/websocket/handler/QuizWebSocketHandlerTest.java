package com.quizplatform.websocket.handler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizplatform.websocket.heartbeat.HeartbeatManager;
import com.quizplatform.websocket.pubsub.RedisMessageListener;
import com.quizplatform.websocket.registry.ConnectionRegistry;
import com.quizplatform.websocket.service.ParticipantTrackingService;
import com.quizplatform.websocket.service.ReconnectionService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class QuizWebSocketHandlerTest {

    @Mock private ConnectionRegistry connectionRegistry;
    @Mock private HeartbeatManager heartbeatManager;
    @Mock private RedisMessageListener redisMessageListener;
    @Mock private AnswerSubmitHandler answerSubmitHandler;
    @Mock private ParticipantTrackingService participantTrackingService;
    @Mock private ReconnectionService reconnectionService;
    @Mock private WebSocketSession session;

    private QuizWebSocketHandler handler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        handler =
                new QuizWebSocketHandler(
                        connectionRegistry,
                        heartbeatManager,
                        redisMessageListener,
                        answerSubmitHandler,
                        participantTrackingService,
                        reconnectionService,
                        objectMapper);
    }

    @Test
    @DisplayName("Should register connection on open with valid PIN")
    void connectionEstablished() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("pin", "ABC123");
        attributes.put("participantId", "participant-1");
        attributes.put("isHost", false);

        when(session.getAttributes()).thenReturn(attributes);
        when(session.getId()).thenReturn("session-1");

        handler.afterConnectionEstablished(session);

        verify(connectionRegistry).register(eq("ABC123"), eq(session), any());
        verify(heartbeatManager).registerSession(session);
        verify(redisMessageListener).subscribeIfNeeded("ABC123");
        verify(participantTrackingService).markConnected("ABC123", "participant-1");
        verify(reconnectionService).deliverPendingMessages("ABC123", "participant-1", session);
    }

    @Test
    @DisplayName("Should reject connection without PIN")
    void connectionRejectedWithoutPin() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        when(session.getAttributes()).thenReturn(attributes);

        handler.afterConnectionEstablished(session);

        verify(session).close(CloseStatus.POLICY_VIOLATION);
        verify(connectionRegistry, never()).register(anyString(), any(), any());
    }

    @Test
    @DisplayName("Should not track host connection in participant tracking")
    void hostConnectionNotTracked() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("pin", "ABC123");
        attributes.put("participantId", "host-user-id");
        attributes.put("isHost", true);

        when(session.getAttributes()).thenReturn(attributes);
        when(session.getId()).thenReturn("session-1");

        handler.afterConnectionEstablished(session);

        verify(connectionRegistry).register(eq("ABC123"), eq(session), any());
        verify(participantTrackingService, never()).markConnected(anyString(), anyString());
    }

    @Test
    @DisplayName("Should route answer.submit messages to AnswerSubmitHandler")
    void routeAnswerSubmit() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("pin", "ABC123");
        attributes.put("participantId", "participant-1");

        when(session.getAttributes()).thenReturn(attributes);

        String message =
                "{\"type\":\"answer.submit\",\"payload\":{\"questionId\":\"q1\",\"answer\":\"B\",\"clientTimestamp\":1700000000000}}";

        handler.handleTextMessage(session, new TextMessage(message));

        verify(answerSubmitHandler).handleAnswerSubmit(eq(session), eq("ABC123"), eq("participant-1"), any());
    }

    @Test
    @DisplayName("Should handle clock.sync_request and respond with server timestamp")
    void handleClockSyncRequest() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("pin", "ABC123");
        attributes.put("participantId", "participant-1");

        when(session.getAttributes()).thenReturn(attributes);

        String message =
                "{\"type\":\"clock.sync_request\",\"payload\":{\"clientTimestamp\":1700000000000}}";

        handler.handleTextMessage(session, new TextMessage(message));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());

        String response = captor.getValue().getPayload();
        var responseJson = objectMapper.readTree(response);
        assert responseJson.get("type").asText().equals("clock.sync_response");
        assert responseJson.get("payload").get("clientTimestamp").asLong() == 1700000000000L;
        assert responseJson.get("payload").has("serverTimestamp");
    }

    @Test
    @DisplayName("Should record heartbeat ack")
    void handleHeartbeatAck() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("pin", "ABC123");
        attributes.put("participantId", "participant-1");

        when(session.getAttributes()).thenReturn(attributes);

        String message =
                "{\"type\":\"heartbeat_ack\",\"payload\":{\"timestamp\":1700000000000}}";

        handler.handleTextMessage(session, new TextMessage(message));

        verify(heartbeatManager).recordAck(session);
    }

    @Test
    @DisplayName("Should send error for unknown message type")
    void unknownMessageType() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("pin", "ABC123");
        attributes.put("participantId", "participant-1");

        when(session.getAttributes()).thenReturn(attributes);

        String message = "{\"type\":\"unknown.type\",\"payload\":{}}";

        handler.handleTextMessage(session, new TextMessage(message));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());

        String response = captor.getValue().getPayload();
        var responseJson = objectMapper.readTree(response);
        assert responseJson.get("type").asText().equals("error");
    }

    @Test
    @DisplayName("Should send error for invalid JSON")
    void invalidJson() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("pin", "ABC123");
        attributes.put("participantId", "participant-1");

        when(session.getAttributes()).thenReturn(attributes);

        handler.handleTextMessage(session, new TextMessage("not valid json"));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());

        String response = captor.getValue().getPayload();
        var responseJson = objectMapper.readTree(response);
        assert responseJson.get("type").asText().equals("error");
    }

    @Test
    @DisplayName("Should unregister and track disconnection on close")
    void connectionClosed() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("pin", "ABC123");
        attributes.put("participantId", "participant-1");
        attributes.put("isHost", false);

        when(session.getAttributes()).thenReturn(attributes);
        when(session.getId()).thenReturn("session-1");
        when(connectionRegistry.hasConnectionsForPin("ABC123")).thenReturn(false);

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        verify(connectionRegistry).unregister("ABC123", session);
        verify(heartbeatManager).unregisterSession(session);
        verify(participantTrackingService).markDisconnected("ABC123", "participant-1");
        verify(redisMessageListener).unsubscribeIfEmpty("ABC123");
    }

    @Test
    @DisplayName("Should not unsubscribe from Redis if other connections exist for PIN")
    void connectionClosedWithOtherConnections() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("pin", "ABC123");
        attributes.put("participantId", "participant-1");
        attributes.put("isHost", false);

        when(session.getAttributes()).thenReturn(attributes);
        when(session.getId()).thenReturn("session-1");
        when(connectionRegistry.hasConnectionsForPin("ABC123")).thenReturn(true);

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        verify(redisMessageListener, never()).unsubscribeIfEmpty("ABC123");
    }

    @Test
    @DisplayName("Should close session on transport error")
    void transportError() throws Exception {
        when(session.isOpen()).thenReturn(true);

        handler.handleTransportError(session, new RuntimeException("Connection reset"));

        verify(session).close(CloseStatus.SERVER_ERROR);
    }
}
