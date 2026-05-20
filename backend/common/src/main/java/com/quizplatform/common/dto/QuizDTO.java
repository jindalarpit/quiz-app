package com.quizplatform.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Data transfer object representing a quiz.
 * Aligned with the quizzes PostgreSQL table.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuizDTO {

    private UUID id;
    private UUID ownerId;
    private String title;
    private String description;
    private String coverImageUrl;
    private Boolean isPublished;
    private String scoringMode;
    private Map<String, Object> settings;
    private List<QuestionDTO> questions;
    private Integer questionCount;
    private Instant createdAt;
    private Instant updatedAt;
}
