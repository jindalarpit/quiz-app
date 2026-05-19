package com.quizplatform.websocket.pubsub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizplatform.websocket.service.MessageBroadcastService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RedisMessageListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisMessageListener.class);
    private static final String CHANNEL_PREFIX = "session:";
    private static final String CHANNEL_SUFFIX = ":broadcast";
    private static final String SESSION_ENDED_EVENT_TYPE = "session.ended";

    private final RedisMessageListenerContainer listenerContainer;
    private final MessageBroadcastService messageBroadcastService;
    private final ObjectMapper objectMapper;
    private final Set<String> subscribedPins = ConcurrentHashMap.newKeySet();

    public RedisMessageListener(
            RedisMessageListenerContainer listenerContainer,
            MessageBroadcastService messageBroadcastService,
            ObjectMapper objectMapper) {
        this.listenerContainer = listenerContainer;
        this.messageBroadcastService = messageBroadcastService;
        this.objectMapper = objectMapper;
    }

    public void subscribeIfNeeded(String pin) {
        if (subscribedPins.add(pin)) {
            String channel = CHANNEL_PREFIX + pin + CHANNEL_SUFFIX;
            listenerContainer.addMessageListener(this, new ChannelTopic(channel));
            log.info("Subscribed to Redis channel: {}", channel);
        }
    }

    public void unsubscribeIfEmpty(String pin) {
        if (subscribedPins.remove(pin)) {
            String channel = CHANNEL_PREFIX + pin + CHANNEL_SUFFIX;
            listenerContainer.removeMessageListener(this, new ChannelTopic(channel));
            log.info("Unsubscribed from Redis channel: {}", channel);
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        String body = new String(message.getBody(), StandardCharsets.UTF_8);

        // Extract PIN from channel name: session:{pin}:broadcast
        String pin = extractPinFromChannel(channel);
        if (pin == null) {
            log.warn("Could not extract PIN from channel: {}", channel);
            return;
        }

        log.debug("Received Redis message on channel {}: {}", channel, body);

        // For session.ended events, use broadcastLeaderboardUpdate which has retry logic
        // to ensure delivery within 2 seconds (200ms + 500ms + 1300ms backoff)
        if (isSessionEndedEvent(body)) {
            log.info("Broadcasting session.ended leaderboard event to PIN {} with retry logic", pin);
            messageBroadcastService.broadcastLeaderboardUpdate(pin, body);
        } else {
            // Fan out to all local WebSocket connections for this PIN
            messageBroadcastService.broadcastToSession(pin, body);
        }
    }

    /**
     * Checks if the message is a session.ended event by parsing the JSON type field.
     * Falls back to simple string matching if JSON parsing fails.
     */
    private boolean isSessionEndedEvent(String body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            if (node.has("type")) {
                return SESSION_ENDED_EVENT_TYPE.equals(node.get("type").asText());
            }
        } catch (Exception e) {
            log.debug("Could not parse message as JSON for event type detection: {}", e.getMessage());
        }
        // Fallback: simple string check
        return body.contains("\"type\":\"session.ended\"");
    }

    private String extractPinFromChannel(String channel) {
        if (channel.startsWith(CHANNEL_PREFIX) && channel.endsWith(CHANNEL_SUFFIX)) {
            return channel.substring(
                    CHANNEL_PREFIX.length(),
                    channel.length() - CHANNEL_SUFFIX.length());
        }
        return null;
    }

    public boolean isSubscribed(String pin) {
        return subscribedPins.contains(pin);
    }
}
