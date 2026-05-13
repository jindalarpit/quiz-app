package com.quizplatform.quiz.dto;

import com.quizplatform.quiz.model.QuestionType;
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
public class UpdateQuestionRequest {

    private QuestionType type;

    @Size(max = 500, message = "Question text must not exceed 500 characters")
    private String text;

    private List<Map<String, Object>> options;

    private String correctAnswer;

    private Integer timeLimitSeconds;

    private Integer points;

    @Size(max = 500, message = "Media URL must not exceed 500 characters")
    private String mediaUrl;
}
