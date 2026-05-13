package com.quizplatform.session.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Leaderboard view tailored for a specific participant.
 * Shows own rank, own score, and immediate neighbors (above/below).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParticipantLeaderboardView {

    private long ownRank;
    private double ownScore;
    private long rankChange;
    private LeaderboardEntry above;
    private LeaderboardEntry below;
}
