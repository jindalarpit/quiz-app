package com.quizplatform.websocket.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.quizplatform.websocket.heartbeat.HeartbeatManager;
import com.quizplatform.websocket.pubsub.RedisMessageListener;
import com.quizplatform.websocket.registry.ConnectionInfo;
import com.quizplatform.websocket.registry.ConnectionRegistry;
import com.quizplatform.websocket.service.ParticipantTrackingService;
import com.quizplatform.websocket.service.ReconnectionService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;

@Component
public class QuizWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(QuizWebSocketHandler.class);
    private static final String ATTR_PIN = "pin";
    private static final String ATTR_PARTICIPANT_ID = "participantId";
    private static final String ATTR_IS_HOST = "isHost";

    private final ConnectionRegistry connectionRegistry;
    private final HeartbeatManager heartbeatManager;
    private final RedisMessageListener redisMessageListener;
    private final AnswerSubmitHandler answerSubmitHandler;
    private final ParticipantTrackingService participantTrackingService;
    private final ReconnectionService reconnectionService;
    private final ObjectMapper objectMapper;

    public QuizWebSocketHandler(
            ConnectionRegistry connectionRegistry,
            HeartbeatManager heartbeatManager,
            RedisMessageListener redisMessageListener,
            AnswerSubmitHandler answerSubmitHandler,
            ParticipantTrackingService participantTrackingService,
            ReconnectionService reconnectionService,
            ObjectMapper objectMapper) {
        this.connectionRegistry = connectionRegistry;
        this.heartbeatManager = heartbeatManager;
        this.redisMessageListener = redisMessageListener;
        this.answerSubmitHandler = answerSubmitHandler;
        this.participantTrackingService = participantTrackingService;
        this.reconnectionService = reconnectionService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Map<String, Object> attributes = session.getAttributes();
        String pin = (String) attributes.get(ATTR_PIN);
        String participantId = (String) attributes.get(ATTR_PARTICIPANT_ID);
        boolean isHost = Boolean.TRUE.equals(attributes.get(ATTR_IS_HOST));

        if (pin == null) {
            log.warn("Connection rejected: no PIN in session attributes");
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        ConnectionInfo info =
                ConnectionInfo.builder()
                        .pin(pin)
                        .participantId(participantId)
                        .isHost(isHost)
                        .connectedAt(System.currentTimeMillis())
                        .build();

        connectionRegistry.register(pin, session, info);
        heartbeatManager.registerSession(session);

        // Subscribe to Redis Pub/Sub for this PIN if first connection
        redisMessageListener.subscribeIfNeeded(pin);

        // Track connection in Redis
        if (participantId != null && !isHost) {
            participantTrackingService.markConnected(pin, participantId);
            // Deliver any pending messages from disconnection period
            reconnectionService.deliverPendingMessages(pin, participantId, session);
        }

        log.info(
                "WebSocket connected: session={}, pin={}, participant={}, host={}",
                session.getId(),
                pin,
                participantId,
                isHost);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message)
            throws Exception {
        String payload = message.getPayload();

        try {
            JsonNode jsonMessage = objectMapper.readTree(payload);
            String type = jsonMessage.has("type") ? jsonMessage.get("type").asText() : null;
            JsonNode messagePayload =
                    jsonMessage.has("payload") ? jsonMessage.get("payload") : null;

            if (type == null) {
                sendError(session, "Missing message type");
                return;
            }

            Map<String, Object> attributes = session.getAttributes();
            String pin = (String) attributes.get(ATTR_PIN);
            String participantId = (String) attributes.get(ATTR_PARTICIPANT_ID);

            switch (type) {
                case "answer.submit":
                    answerSubmitHandler.handleAnswerSubmit(
                            session, pin, participantId, messagePayload);
                    break;

                case "clock.sync_request":
                    handleClockSyncRequest(session, messagePayload);
                    break;

                case "heartbeat_ack":
                    heartbeatManager.recordAck(session);
                    break;

                default:
                    log.debug("Unknown message type: {}", type);
                    sendError(session, "Unknown message type: " + type);
                    break;
            }
        } catch (Exception e) {
            log.error(
                    "Error processing message from session {}: {}",
                    session.getId(),
                    e.getMessage());
            sendError(session, "Invalid message format");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status)
            throws Exception {
        Map<String, Object> attributes = session.getAttributes();
        String pin = (String) attributes.get(ATTR_PIN);
        String participantId = (String) attributes.get(ATTR_PARTICIPANT_ID);
        boolean isHost = Boolean.TRUE.equals(attributes.get(ATTR_IS_HOST));

        if (pin != null) {
            connectionRegistry.unregister(pin, session);
            heartbeatManager.unregisterSession(session);

            // Track disconnection in Redis
            if (participantId != null && !isHost) {
                participantTrackingService.markDisconnected(pin, participantId);
            }

            // Unsubscribe from Redis Pub/Sub if no more connections for this PIN
            if (!connectionRegistry.hasConnectionsForPin(pin)) {
                redisMessageListener.unsubscribeIfEmpty(pin);
            }
        }

        log.info(
                "WebSocket disconnected: session={}, pin={}, participant={}, status={}",
                session.getId(),
                pin,
                participantId,
                status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception)
            throws Exception {
        log.error(
                "WebSocket transport error for session {}: {}",
                session.getId(),
                exception.getMessage());

        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    private void handleClockSyncRequest(WebSocketSession session, JsonNode payload) {
        try {
            long clientTimestamp =
                    payload != null && payload.has("clientTimestamp")
                            ? payload.get("clientTimestamp").asLong()
                            : 0;

            ObjectNode response = objectMapper.createObjectNode();
            response.put("type", "clock.sync_response");
            ObjectNode responsePayload = objectMapper.createObjectNode();
            responsePayload.put("serverTimestamp", System.currentTimeMillis());
            responsePayload.put("clientTimestamp", clientTimestamp);
            response.set("payload", responsePayload);

            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        } catch (IOException e) {
            log.error(
                    "Failed to send clock sync response to session {}: {}",
                    session.getId(),
                    e.getMessage());
        }
    }

    private void sendError(WebSocketSession session, String errorMessage) {
        try {
            ObjectNode error = objectMapper.createObjectNode();
            error.put("type", "error");
            ObjectNode errorPayload = objectMapper.createObjectNode();
            errorPayload.put("message", errorMessage);
            error.set("payload", errorPayload);

            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(error)));
        } catch (IOException e) {
            log.error(
                    "Failed to send error message to session {}: {}",
                    session.getId(),
                    e.getMessage());
        }
    }
}
