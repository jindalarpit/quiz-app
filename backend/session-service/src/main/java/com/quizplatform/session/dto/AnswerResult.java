package com.quizplatform.session.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnswerResult {

    private boolean accepted;
    private int scoreAwarded;
    private int totalScore;
    private long rank;
    private int streak;
    private int multiplier;
}
