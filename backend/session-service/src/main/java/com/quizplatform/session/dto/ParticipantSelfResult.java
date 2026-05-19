package com.quizplatform.session.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParticipantSelfResult {

    private int rank;
    private int score;
    private int correctAnswers;
    private int totalQuestions;
    private int maxStreak;
    private double avgResponseTimeSec;
    private int scoreDifference;
    private boolean aboveAverage;
    private List<QuestionResult> questionBreakdown;
}
