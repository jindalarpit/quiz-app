package com.quizplatform.websocket.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

@Component
public class AnswerSubmitHandler {

    private static final Logger log = LoggerFactory.getLogger(AnswerSubmitHandler.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${services.session-service.url:http://localhost:8082}")
    private String sessionServiceUrl;

    public AnswerSubmitHandler(ObjectMapper objectMapper) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
    }

    public void handleAnswerSubmit(
            WebSocketSession session, String pin, String participantId, JsonNode payload) {
        String questionId = payload.has("questionId") ? payload.get("questionId").asText() : null;
        String answer = payload.has("answer") ? payload.get("answer").asText() : null;
        long clientTimestamp =
                payload.has("clientTimestamp") ? payload.get("clientTimestamp").asLong() : 0;

        if (questionId == null || answer == null) {
            sendAck(session, questionId, false, "Missing required fields");
            return;
        }

        try {
            boolean accepted = forwardAnswerToSessionService(pin, participantId, questionId, answer, clientTimestamp);
            sendAck(session, questionId, accepted, null);
        } catch (Exception e) {
            log.error(
                    "Failed to forward answer to session service for PIN {}: {}",
                    pin,
                    e.getMessage());
            sendAck(session, questionId, false, "Service unavailable");
        }
    }

    @CircuitBreaker(name = "sessionService", fallbackMethod = "forwardAnswerFallback")
    public boolean forwardAnswerToSessionService(
            String pin, String participantId, String questionId, String answer, long clientTimestamp) {
        String url = sessionServiceUrl + "/api/sessions/" + pin + "/answer";

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("participantId", participantId);
        requestBody.put("questionId", questionId);
        requestBody.put("answer", answer);
        requestBody.put("clientTimestamp", clientTimestamp);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            HttpEntity<String> entity =
                    new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);

            ResponseEntity<String> response =
                    restTemplate.postForEntity(url, entity, String.class);

            return response.getStatusCode() == HttpStatus.OK;
        } catch (IOException e) {
            throw new RestClientException("Failed to serialize request body", e);
        }
    }

    @SuppressWarnings("unused")
    private boolean forwardAnswerFallback(
            String pin, String participantId, String questionId, String answer,
            long clientTimestamp, Throwable throwable) {
        log.error("Circuit breaker fallback: failed to forward answer for PIN {}: {}",
                pin, throwable.getMessage());
        return false;
    }

    private void sendAck(
            WebSocketSession session, String questionId, boolean accepted, String error) {
        try {
            ObjectNode ack = objectMapper.createObjectNode();
            ack.put("type", "answer.ack");
            ObjectNode ackPayload = objectMapper.createObjectNode();
            ackPayload.put("questionId", questionId != null ? questionId : "");
            ackPayload.put("accepted", accepted);
            if (error != null) {
                ackPayload.put("error", error);
            }
            ack.set("payload", ackPayload);

            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(ack)));
        } catch (IOException e) {
            log.error("Failed to send answer ack to session {}: {}", session.getId(), e.getMessage());
        }
    }
}
