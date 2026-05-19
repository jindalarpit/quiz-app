package com.quizplatform.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaderboardEntryDTO {

    private int rank;
    private String nickname;
    private int score;
    private int correctAnswers;
    private int totalAnswers;
    private int maxStreak;
    private double avgResponseTimeSec;
}
