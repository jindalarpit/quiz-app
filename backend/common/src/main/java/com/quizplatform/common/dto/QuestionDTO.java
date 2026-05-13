package com.quizplatform.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Data transfer object representing a quiz question.
 * Aligned with the questions PostgreSQL table.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuestionDTO {

    private UUID id;
    private UUID quizId;
    private QuestionType type;
    private String text;
    private List<OptionDTO> options;
    private String correctAnswer;
    private Integer timeLimitSeconds;
    private Integer points;
    private Integer position;
    private String mediaUrl;
    private Instant createdAt;

    /**
     * Supported question types.
     */
    public enum QuestionType {
        MCQ,
        TRUE_FALSE,
        POLL
    }

    /**
     * Represents a single answer option within a question.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OptionDTO {
        private String id;
        private String text;
    }
}
