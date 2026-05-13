package com.quizplatform.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Data transfer object representing a single leaderboard entry.
 * Used for real-time leaderboard broadcasts.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LeaderboardEntryDTO {

    private UUID participantId;
    private String nickname;
    private Integer score;
    private Integer rank;
    private Integer previousRank;
    private Integer rankChange;
    private Integer streak;
    private Integer multiplier;
}
