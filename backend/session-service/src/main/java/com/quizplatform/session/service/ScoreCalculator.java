package com.quizplatform.session.service;

import org.springframework.stereotype.Component;

/**
 * Calculates scores for quiz answers based on speed, correctness, and streak.
 *
 * Formula: base_points × (1 - (time_taken / time_limit) × 0.5) × multiplier
 *
 * - time_taken: milliseconds from question start to answer submission
 * - time_limit: total question duration in milliseconds
 * - multiplier: streak-based multiplier (1x, 2x, or 3x)
 * - Returns 0 for incorrect answers
 * - Score is rounded to the nearest integer
 */
@Component
public class ScoreCalculator {

    /**
     * Calculate the score for a correct answer based on response time (no streak).
     *
     * @param basePoints   the base point value for the question (e.g. 1000 or 2000)
     * @param timeTakenMs  time taken to answer in milliseconds
     * @param timeLimitMs  total time limit for the question in milliseconds
     * @return calculated score rounded to nearest integer, or 0 if inputs are invalid
     */
    public int calculateScore(int basePoints, long timeTakenMs, int timeLimitMs) {
        return calculateScoreWithStreak(basePoints, timeTakenMs, timeLimitMs, 0);
    }

    /**
     * Calculate the score for a correct answer based on response time and streak.
     *
     * Formula: base_points × (1 - (time_taken / time_limit) × 0.5) × multiplier
     *
     * @param basePoints   the base point value for the question (e.g. 1000 or 2000)
     * @param timeTakenMs  time taken to answer in milliseconds
     * @param timeLimitMs  total time limit for the question in milliseconds
     * @param streak       current streak count (used to determine multiplier)
     * @return calculated score rounded to nearest integer, or 0 if inputs are invalid
     */
    public int calculateScoreWithStreak(int basePoints, long timeTakenMs, int timeLimitMs, int streak) {
        if (basePoints <= 0 || timeLimitMs <= 0) {
            return 0;
        }

        // Clamp timeTaken to valid range
        if (timeTakenMs < 0) {
            timeTakenMs = 0;
        }
        if (timeTakenMs > timeLimitMs) {
            timeTakenMs = timeLimitMs;
        }

        double timeRatio = (double) timeTakenMs / timeLimitMs;
        double rawScore = basePoints * (1.0 - timeRatio * 0.5);

        int multiplier = getStreakMultiplier(streak);
        return (int) Math.round(rawScore * multiplier);
    }

    /**
     * Get the streak multiplier based on the current streak count.
     *
     * - streak >= 5 → 3x multiplier
     * - streak >= 3 → 2x multiplier
     * - otherwise → 1x multiplier
     *
     * @param streak the current streak count
     * @return the multiplier (1, 2, or 3)
     */
    public int getStreakMultiplier(int streak) {
        if (streak >= 5) return 3;
        if (streak >= 3) return 2;
        return 1;
    }
}
