package com.quizplatform.quiz.dto;

import com.quizplatform.quiz.model.QuestionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionResponse {

    private UUID id;
    private QuestionType type;
    private String text;
    private List<Map<String, Object>> options;
    private String correctAnswer;
    private Integer timeLimitSeconds;
    private Integer points;
    private Integer position;
    private String mediaUrl;
}
