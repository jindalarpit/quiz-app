package com.quizplatform.quiz.dto;

import com.quizplatform.quiz.model.QuestionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateQuestionRequest {

    @NotNull(message = "Question type is required")
    private QuestionType type;

    @NotBlank(message = "Question text is required")
    @Size(max = 500, message = "Question text must not exceed 500 characters")
    private String text;

    @NotEmpty(message = "Options are required")
    private List<Map<String, Object>> options;

    private String correctAnswer;

    @Builder.Default
    private Integer timeLimitSeconds = 20;

    @Builder.Default
    private Integer points = 1000;

    @Size(max = 500, message = "Media URL must not exceed 500 characters")
    private String mediaUrl;
}
