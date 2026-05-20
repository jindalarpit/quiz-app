package com.quizplatform.session.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a participant with full ranking data including tiebreaker fields.
 * 
 * Used by RankingService for computing rankings with the three-level tiebreaker:
 * 1. Cumulative score (descending)
 * 2. Average response time (ascending)
 * 3. Most recent answer timestamp (ascending)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RankedParticipant {

    /**
     * Unique identifier for the participant.
     */
    private String participantId;

    /**
     * Display name of the participant.
     */
    private String nickname;

    /**
     * Total cumulative score across all rounds.
     */
    private long cumulativeScore;

    /**
     * Composite score used for Redis sorted set.
     * Formula: cumulativeScore × 1_000_000 + (MAX_TIME - avgResponseTimeMs)
     */
    private double compositeScore;

    /**
     * Average response time in milliseconds.
     * Computed as total_response_time_ms / answered_rounds.
     * Used as first tiebreaker (lower is better).
     */
    private long avgResponseTimeMs;

    /**
     * Timestamp of the most recent answer submission.
     * Used as second tiebreaker (earlier is better).
     */
    private long lastAnswerTime;

    /**
     * Current rank (1-indexed).
     * Assigned after full tiebreaker resolution.
     */
    private int rank;

    /**
     * Change in rank from previous round.
     * Positive = moved up, negative = moved down, zero = unchanged.
     */
    private int rankDelta;

    /**
     * Current streak count.
     */
    private int streakCount;

    /**
     * Current streak multiplier (1, 2, or 3).
     */
    private int streakMultiplier;

    /**
     * Score earned in the current round.
     */
    private int roundScore;
}
