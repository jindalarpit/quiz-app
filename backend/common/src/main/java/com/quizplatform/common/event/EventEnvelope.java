package com.quizplatform.common.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Standard event envelope for Redis Streams inter-service communication.
 * All events published to Redis Streams conform to this schema.
 *
 * <p>Fields:
 * <ul>
 *   <li>event_id - Unique identifier for the event (UUID)</li>
 *   <li>event_type - Type of event (from {@link EventType})</li>
 *   <li>timestamp - When the event occurred (epoch milliseconds)</li>
 *   <li>source_service - The service that produced the event</li>
 *   <li>correlation_id - Request correlation ID for distributed tracing</li>
 *   <li>payload - Event-specific data</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EventEnvelope {

    private UUID eventId;
    private EventType eventType;
    private Instant timestamp;
    private String sourceService;
    private String correlationId;
    private Map<String, Object> payload;

    /**
     * Creates a new EventEnvelope with auto-generated eventId and current timestamp.
     *
     * @param eventType     the type of event
     * @param sourceService the service producing the event
     * @param correlationId the correlation ID for tracing
     * @param payload       the event-specific data
     * @return a fully populated EventEnvelope
     */
    public static EventEnvelope create(EventType eventType, String sourceService,
                                       String correlationId, Map<String, Object> payload) {
        return EventEnvelope.builder()
                .eventId(UUID.randomUUID())
                .eventType(eventType)
                .timestamp(Instant.now())
                .sourceService(sourceService)
                .correlationId(correlationId)
                .payload(payload)
                .build();
    }
}
