package com.quizplatform.quiz.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class QuizResponse {

    private UUID id;
    private String title;
    private String description;
    private String coverImageUrl;
    private Boolean isPublished;
    private Map<String, Object> settings;
    private int questionCount;
    private Instant createdAt;
    private Instant updatedAt;
}
