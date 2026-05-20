package com.quizplatform.session.service;

import org.springframework.stereotype.Component;

/**
 * Calculates scores for quiz answers based on speed, correctness, and streak.
 *
 * Enhanced formula: base_points × (1 - (time_taken / time_limit) × time_factor) × multiplier
 *
 * - time_taken: milliseconds from question start to answer submission
 * - time_limit: total question duration in milliseconds
 * - time_factor: configurable factor (0.3, 0.5, or 0.7) determining speed importance
 * - multiplier: streak-based multiplier (1x, 2x, or 3x)
 * - Returns 0 for incorrect answers
 * - Score is rounded to the nearest integer
 * - Multiplied scores are NOT clamped to base_points (may exceed base_points with streak)
 */
@Component
public class ScoreCalculator {

    /** Default time factor (legacy behavior) */
    private static final double DEFAULT_TIME_FACTOR = 0.5;

    /**
     * Calculate the score for a correct answer based on response time (no streak).
     * Uses the default time factor of 0.5.
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
     * Uses the default time factor of 0.5.
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
        return calculateScoreWithTimeFactor(basePoints, timeTakenMs, timeLimitMs, DEFAULT_TIME_FACTOR, streak);
    }

    /**
     * Calculate the score for a correct answer with a configurable time factor.
     *
     * Formula: base_points × (1 - (time_taken / time_limit) × time_factor)
     *
     * The time-weighted score is rounded to the nearest integer, then the streak
     * multiplier is applied. The resulting multiplied score is NOT clamped to
     * base_points — it may exceed base_points when a streak multiplier is active.
     *
     * Minimum score (at time_limit): base_points × (1 - time_factor)
     * Maximum score (at time_taken = 0): base_points
     *
     * Time clamping: time_taken is clamped to [0, time_limit] before calculation.
     * Edge case: if time_limit <= 0, full base_points are awarded (use calculateScoreForZeroTimeLimit).
     *
     * @param basePoints   the base point value for the question (e.g. 1000 or 2000)
     * @param timeTakenMs  time taken to answer in milliseconds
     * @param timeLimitMs  total time limit for the question in milliseconds
     * @param timeFactor   the time factor (0.3, 0.5, or 0.7) determining speed importance
     * @param streak       current streak count (used to determine multiplier)
     * @return calculated score rounded to nearest integer, or 0 if basePoints <= 0
     */
    public int calculateScoreWithTimeFactor(int basePoints, long timeTakenMs, int timeLimitMs, 
                                             double timeFactor, int streak) {
        if (basePoints <= 0) {
            return 0;
        }

        // Edge case: time_limit <= 0 awards full base_points
        if (timeLimitMs <= 0) {
            return calculateScoreForZeroTimeLimit(basePoints, streak);
        }

        // Clamp timeTaken to valid range [0, time_limit]
        long clampedTimeTaken = Math.max(0, Math.min(timeTakenMs, timeLimitMs));

        // Calculate time-weighted score: base_points × (1 - (time_taken / time_limit) × time_factor)
        double timeRatio = (double) clampedTimeTaken / timeLimitMs;
        double rawScore = basePoints * (1.0 - timeRatio * timeFactor);

        // Round to nearest integer
        int roundedScore = (int) Math.round(rawScore);

        // Apply streak multiplier (NOT clamped to base_points)
        int multiplier = getStreakMultiplier(streak);
        return roundedScore * multiplier;
    }

    /**
     * Calculate the score when time_limit is zero or negative.
     *
     * When time_limit <= 0, the full base_points are awarded without time-based scaling.
     * Streak multipliers still apply to the awarded base_points.
     *
     * @param basePoints the base point value for the question (e.g. 1000 or 2000)
     * @param streak     current streak count (used to determine multiplier)
     * @return base_points × streak_multiplier, or 0 if basePoints <= 0
     */
    public int calculateScoreForZeroTimeLimit(int basePoints, int streak) {
        if (basePoints <= 0) {
            return 0;
        }
        int multiplier = getStreakMultiplier(streak);
        return basePoints * multiplier;
    }

    /**
     * Calculate the score for an answer, considering correctness.
     *
     * This method encapsulates the full scoring logic including the correctness check.
     * Incorrect or unanswered questions always yield 0 points regardless of other parameters.
     *
     * @param basePoints   the base point value for the question (e.g. 1000 or 2000)
     * @param timeTakenMs  time taken to answer in milliseconds
     * @param timeLimitMs  total time limit for the question in milliseconds
     * @param timeFactor   the time factor (0.3, 0.5, or 0.7) determining speed importance
     * @param streak       current streak count (used to determine multiplier)
     * @param isCorrect    whether the answer is correct
     * @return calculated score, or 0 if the answer is incorrect/unanswered
     */
    public int calculateScoreForAnswer(int basePoints, long timeTakenMs, int timeLimitMs,
                                        double timeFactor, int streak, boolean isCorrect) {
        if (!isCorrect) {
            return 0;
        }
        return calculateScoreWithTimeFactor(basePoints, timeTakenMs, timeLimitMs, timeFactor, streak);
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
