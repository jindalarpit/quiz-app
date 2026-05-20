package com.quizplatform.session.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Represents the complete result of scoring a round.
 * Contains all participant scores, rank deltas, and metadata.
 * Used by DynamicScoreEngine to return round scoring results.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoundResult {

    /** The session PIN */
    private String pin;

    /** The round number (0-indexed) */
    private int roundNumber;

    /** Timestamp when the round was scored */
    private Instant timestamp;

    /** List of all participant scores for this round, ordered by rank ascending */
    private List<ParticipantRoundScore> participantScores;

    /** The correct answer for this round */
    private String correctAnswer;

    /** Total number of participants who answered */
    private int totalAnswered;

    /** Total number of correct answers */
    private int totalCorrect;

    /** Accuracy rate (totalCorrect / totalAnswered) */
    private double accuracyRate;

    /** Time taken to compute scores in milliseconds (for performance monitoring) */
    private long computationTimeMs;
}
