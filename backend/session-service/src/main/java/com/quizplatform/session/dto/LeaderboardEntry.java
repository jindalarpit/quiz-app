package com.quizplatform.session.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaderboardEntry {

    private String participantId;
    private String nickname;
    private double score;
    private long rank;
    private long rankChange;
    private int streak;
    private int multiplier;
}
