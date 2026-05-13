package com.quizplatform.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Data transfer object representing a session participant.
 * Aligned with the session_participants PostgreSQL table.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SessionParticipantDTO {

    private UUID id;
    private UUID sessionId;
    private String nickname;
    private Integer finalScore;
    private Integer finalRank;
    private Integer maxStreak;
    private Integer answersCorrect;
    private Integer answersTotal;
    private Integer avgResponseTimeMs;
    private Boolean isFlagged;
    private Instant joinedAt;
}
