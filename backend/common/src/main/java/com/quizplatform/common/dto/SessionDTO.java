package com.quizplatform.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Data transfer object representing a quiz session.
 * Aligned with the sessions PostgreSQL table.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SessionDTO {

    private UUID id;
    private UUID quizId;
    private UUID hostId;
    private String pin;
    private SessionStatus status;
    private Instant startedAt;
    private Instant endedAt;
    private Integer participantCount;
    private Map<String, Object> settings;
    private Instant createdAt;

    /**
     * Session lifecycle states as defined in the state machine.
     */
    public enum SessionStatus {
        CREATED,
        LOBBY,
        ACTIVE,
        QUESTION_OPEN,
        QUESTION_CLOSED,
        REVEAL,
        PAUSED,
        ENDED
    }
}
