package com.quizplatform.quiz.dto;

import com.quizplatform.quiz.model.ScoringMode;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating a quiz's scoring mode.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateScoringModeRequest {

    @NotNull(message = "Scoring mode is required")
    private ScoringMode scoringMode;
}
