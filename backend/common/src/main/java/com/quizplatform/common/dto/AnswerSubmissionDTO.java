package com.quizplatform.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Data transfer object representing an answer submission.
 * Aligned with the answer_submissions PostgreSQL table.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnswerSubmissionDTO {

    private UUID id;
    private UUID sessionId;
    private UUID participantId;
    private UUID questionId;
    private String submittedAnswer;
    private Boolean isCorrect;
    private Integer responseTimeMs;
    private Integer scoreAwarded;
    private Integer streakAtTime;
    private Instant submittedAt;
}
