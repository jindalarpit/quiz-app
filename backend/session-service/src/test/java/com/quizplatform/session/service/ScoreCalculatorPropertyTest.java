package com.quizplatform.session.service;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for ScoreCalculator (Properties 1-5).
 * 
 * Feature: dynamic-scoring-leaderboard
 *
 * **Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 7.2, 7.3, 7.4, 7.5, 7.8**
 */
class ScoreCalculatorPropertyTest {

    private final ScoreCalculator scoreCalculator = new ScoreCalculator();

    // ==================== Property 1: Score Formula Correctness ====================

    /**
     * Feature: dynamic-scoring-leaderboard, Property 1: Score Formula Correctness
     *
     * For any valid base_points, time_taken in [0, time_limit], time_limit > 0, and
     * time_factor in {0.3, 0.5, 0.7}, the computed score equals
     * round(base_points × (1 - (time_taken / time_limit) × time_factor))
     *
     * **Validates: Requirements 1.1, 1.2, 1.3, 7.2, 7.3, 7.4, 7.5**
     */
    @Property(tries = 100)
    void property1_scoreFormulaCorrectness(
            @ForAll @IntRange(min = 100, max = 5000) int basePoints,
            @ForAll @LongRange(min = 0, max = 60000) long timeTakenMs,
            @ForAll @IntRange(min = 1000, max = 60000) int timeLimitMs,
            @ForAll("validTimeFactors") double timeFactor) {

        // Clamp time to valid range for expected calculation
        long clampedTime = Math.max(0, Math.min(timeTakenMs, timeLimitMs));
        
        double timeRatio = (double) clampedTime / timeLimitMs;
        int expectedScore = (int) Math.round(basePoints * (1.0 - timeRatio * timeFactor));

        int actualScore = scoreCalculator.calculateScoreWithTimeFactor(basePoints, timeTakenMs, timeLimitMs, timeFactor, 0);

        assertThat(actualScore).isEqualTo(expectedScore);
    }

    /**
     * Feature: dynamic-scoring-leaderboard, Property 1: Score Formula Correctness (Bounds)
     *
     * Score is bounded between base_points × (1 - time_factor) and base_points
     *
     * **Validates: Requirements 1.2, 1.3, 7.3, 7.4, 7.5**
     */
    @Property(tries = 100)
    void property1_scoreIsBoundedByTimeFactor(
            @ForAll @IntRange(min = 100, max = 5000) int basePoints,
            @ForAll @LongRange(min = 0, max = 60000) long timeTakenMs,
            @ForAll @IntRange(min = 1000, max = 60000) int timeLimitMs,
            @ForAll("validTimeFactors") double timeFactor) {

        int score = scoreCalculator.calculateScoreWithTimeFactor(basePoints, timeTakenMs, timeLimitMs, timeFactor, 0);

        int minExpected = (int) Math.round(basePoints * (1.0 - timeFactor));
        int maxExpected = basePoints;

        assertThat(score).isBetween(minExpected, maxExpected);
    }

    /**
     * Feature: dynamic-scoring-leaderboard, Property 1: Score Formula Correctness (Max at time=0)
     *
     * Score at time 0 always equals the full base points.
     *
     * **Validates: Requirements 1.3**
     */
    @Property(tries = 100)
    void property1_instantAnswerGetsFullPoints(
            @ForAll @IntRange(min = 100, max = 5000) int basePoints,
            @ForAll @IntRange(min = 1000, max = 60000) int timeLimitMs,
            @ForAll("validTimeFactors") double timeFactor) {

        int score = scoreCalculator.calculateScoreWithTimeFactor(basePoints, 0, timeLimitMs, timeFactor, 0);

        assertThat(score).isEqualTo(basePoints);
    }

    /**
     * Feature: dynamic-scoring-leaderboard, Property 1: Score Formula Correctness (Min at time_limit)
     *
     * Score at exactly the time limit equals base_points × (1 - time_factor).
     *
     * **Validates: Requirements 1.2, 7.3, 7.4, 7.5**
     */
    @Property(tries = 100)
    void property1_answerAtTimeLimitGetsMinimumPoints(
            @ForAll @IntRange(min = 100, max = 5000) int basePoints,
            @ForAll @IntRange(min = 1000, max = 60000) int timeLimitMs,
            @ForAll("validTimeFactors") double timeFactor) {

        int score = scoreCalculator.calculateScoreWithTimeFactor(basePoints, timeLimitMs, timeLimitMs, timeFactor, 0);

        int expected = (int) Math.round(basePoints * (1.0 - timeFactor));
        assertThat(score).isEqualTo(expected);
    }

    // ==================== Property 2: Score Monotonicity ====================

    /**
     * Feature: dynamic-scoring-leaderboard, Property 2: Score Monotonicity
     *
     * For any fixed base_points, time_limit, and time_factor, if time_taken_A < time_taken_B
     * (both within [0, time_limit]), then the score for time_taken_A is greater than or equal
     * to the score for time_taken_B. Faster answers always score at least as high as slower answers.
     *
     * **Validates: Requirements 1.1**
     */
    @Property(tries = 100)
    void property2_fasterAnswersScoreHigherOrEqual(
            @ForAll @IntRange(min = 100, max = 5000) int basePoints,
            @ForAll @IntRange(min = 1000, max = 60000) int timeLimitMs,
            @ForAll @LongRange(min = 0, max = 60000) long timeTaken1,
            @ForAll @LongRange(min = 0, max = 60000) long timeTaken2,
            @ForAll("validTimeFactors") double timeFactor) {

        int score1 = scoreCalculator.calculateScoreWithTimeFactor(basePoints, timeTaken1, timeLimitMs, timeFactor, 0);
        int score2 = scoreCalculator.calculateScoreWithTimeFactor(basePoints, timeTaken2, timeLimitMs, timeFactor, 0);

        if (timeTaken1 <= timeTaken2) {
            assertThat(score1)
                .as("Faster answer (time=%d) should score >= slower answer (time=%d)", timeTaken1, timeTaken2)
                .isGreaterThanOrEqualTo(score2);
        } else {
            assertThat(score2)
                .as("Faster answer (time=%d) should score >= slower answer (time=%d)", timeTaken2, timeTaken1)
                .isGreaterThanOrEqualTo(score1);
        }
    }

    /**
     * Feature: dynamic-scoring-leaderboard, Property 2: Score Monotonicity (with streak)
     *
     * Monotonicity holds even when streak multipliers are applied.
     *
     * **Validates: Requirements 1.1, 1.5**
     */
    @Property(tries = 100)
    void property2_monotonicityHoldsWithStreak(
            @ForAll @IntRange(min = 100, max = 5000) int basePoints,
            @ForAll @IntRange(min = 1000, max = 60000) int timeLimitMs,
            @ForAll @LongRange(min = 0, max = 60000) long timeTaken1,
            @ForAll @LongRange(min = 0, max = 60000) long timeTaken2,
            @ForAll("validTimeFactors") double timeFactor,
            @ForAll @IntRange(min = 0, max = 10) int streak) {

        int score1 = scoreCalculator.calculateScoreWithTimeFactor(basePoints, timeTaken1, timeLimitMs, timeFactor, streak);
        int score2 = scoreCalculator.calculateScoreWithTimeFactor(basePoints, timeTaken2, timeLimitMs, timeFactor, streak);

        if (timeTaken1 <= timeTaken2) {
            assertThat(score1).isGreaterThanOrEqualTo(score2);
        } else {
            assertThat(score2).isGreaterThanOrEqualTo(score1);
        }
    }

    // ==================== Property 3: Streak Multiplier Application ====================

    /**
     * Feature: dynamic-scoring-leaderboard, Property 3: Streak Multiplier Application
     *
     * For any time-weighted score and streak count >= 0, the final score equals
     * time_weighted_score × getStreakMultiplier(streak). The multiplier is:
     * - 1 for streak < 3
     * - 2 for streak 3-4
     * - 3 for streak >= 5
     *
     * The resulting multiplied score is NOT clamped to base_points.
     *
     * **Validates: Requirements 1.5, 7.8**
     */
    @Property(tries = 100)
    void property3_streakMultiplierCorrectlyApplied(
            @ForAll @IntRange(min = 100, max = 5000) int basePoints,
            @ForAll @LongRange(min = 0, max = 60000) long timeTakenMs,
            @ForAll @IntRange(min = 1000, max = 60000) int timeLimitMs,
            @ForAll("validTimeFactors") double timeFactor,
            @ForAll @IntRange(min = 0, max = 20) int streak) {

        // Calculate expected score without streak
        int scoreWithoutStreak = scoreCalculator.calculateScoreWithTimeFactor(basePoints, timeTakenMs, timeLimitMs, timeFactor, 0);
        
        // Calculate expected multiplier
        int expectedMultiplier = scoreCalculator.getStreakMultiplier(streak);
        
        // Calculate actual score with streak
        int actualScore = scoreCalculator.calculateScoreWithTimeFactor(basePoints, timeTakenMs, timeLimitMs, timeFactor, streak);

        // Verify multiplier is correctly applied
        assertThat(actualScore)
            .as("Score with streak %d should be %d × %d = %d", streak, scoreWithoutStreak, expectedMultiplier, scoreWithoutStreak * expectedMultiplier)
            .isEqualTo(scoreWithoutStreak * expectedMultiplier);
    }

    /**
     * Feature: dynamic-scoring-leaderboard, Property 3: Streak Multiplier Application (Not Clamped)
     *
     * Multiplied score can exceed base_points — it is NOT clamped.
     *
     * **Validates: Requirements 1.5**
     */
    @Property(tries = 100)
    void property3_multipliedScoreNotClamped(
            @ForAll @IntRange(min = 100, max = 5000) int basePoints,
            @ForAll @IntRange(min = 1000, max = 60000) int timeLimitMs,
            @ForAll("validTimeFactors") double timeFactor) {

        // Instant answer with 3x streak multiplier (streak >= 5)
        int score = scoreCalculator.calculateScoreWithTimeFactor(basePoints, 0, timeLimitMs, timeFactor, 5);

        // Score should be 3x base_points (exceeds base_points)
        assertThat(score)
            .as("Score with 3x multiplier should be 3 × %d = %d", basePoints, basePoints * 3)
            .isEqualTo(basePoints * 3);
        assertThat(score)
            .as("Multiplied score should exceed base_points")
            .isGreaterThan(basePoints);
    }

    /**
     * Feature: dynamic-scoring-leaderboard, Property 3: Streak Multiplier Application (Boundaries)
     *
     * Streak multiplier boundaries are correct:
     * - streak < 3 → 1x
     * - streak 3-4 → 2x
     * - streak >= 5 → 3x
     *
     * **Validates: Requirements 1.5**
     */
    @Property(tries = 100)
    void property3_streakMultiplierBoundaries(
            @ForAll @IntRange(min = 0, max = 20) int streak) {

        int multiplier = scoreCalculator.getStreakMultiplier(streak);

        if (streak < 3) {
            assertThat(multiplier)
                .as("Streak %d should have 1x multiplier", streak)
                .isEqualTo(1);
        } else if (streak < 5) {
            assertThat(multiplier)
                .as("Streak %d should have 2x multiplier", streak)
                .isEqualTo(2);
        } else {
            assertThat(multiplier)
                .as("Streak %d should have 3x multiplier", streak)
                .isEqualTo(3);
        }
    }

    // ==================== Property 4: Time Clamping Equivalence ====================

    /**
     * Feature: dynamic-scoring-leaderboard, Property 4: Time Clamping Equivalence
     *
     * For any time_taken value outside [0, time_limit], the computed score equals
     * the score computed with time_taken clamped to the nearest boundary.
     * Specifically: score(negative_time) = score(0)
     *
     * **Validates: Requirements 1.6**
     */
    @Property(tries = 100)
    void property4_negativeTimeClampedToZero(
            @ForAll @IntRange(min = 100, max = 5000) int basePoints,
            @ForAll @IntRange(min = 1000, max = 60000) int timeLimitMs,
            @ForAll @LongRange(min = -10000, max = -1) long negativeTime,
            @ForAll("validTimeFactors") double timeFactor,
            @ForAll @IntRange(min = 0, max = 10) int streak) {

        int scoreWithNegativeTime = scoreCalculator.calculateScoreWithTimeFactor(basePoints, negativeTime, timeLimitMs, timeFactor, streak);
        int scoreWithZeroTime = scoreCalculator.calculateScoreWithTimeFactor(basePoints, 0, timeLimitMs, timeFactor, streak);

        assertThat(scoreWithNegativeTime)
            .as("Score with negative time (%d) should equal score with time=0", negativeTime)
            .isEqualTo(scoreWithZeroTime);
    }

    /**
     * Feature: dynamic-scoring-leaderboard, Property 4: Time Clamping Equivalence
     *
     * For any time_taken value exceeding time_limit, the computed score equals
     * the score computed with time_taken = time_limit.
     * Specifically: score(time > limit) = score(time_limit)
     *
     * **Validates: Requirements 1.6**
     */
    @Property(tries = 100)
    void property4_timeExceedingLimitClampedToLimit(
            @ForAll @IntRange(min = 100, max = 5000) int basePoints,
            @ForAll @IntRange(min = 1000, max = 60000) int timeLimitMs,
            @ForAll @LongRange(min = 1, max = 60000) long excessTime,
            @ForAll("validTimeFactors") double timeFactor,
            @ForAll @IntRange(min = 0, max = 10) int streak) {

        long exceedingTime = timeLimitMs + excessTime;
        
        int scoreWithExceedingTime = scoreCalculator.calculateScoreWithTimeFactor(basePoints, exceedingTime, timeLimitMs, timeFactor, streak);
        int scoreAtTimeLimit = scoreCalculator.calculateScoreWithTimeFactor(basePoints, timeLimitMs, timeLimitMs, timeFactor, streak);

        assertThat(scoreWithExceedingTime)
            .as("Score with time exceeding limit (%d > %d) should equal score at time_limit", exceedingTime, timeLimitMs)
            .isEqualTo(scoreAtTimeLimit);
    }

    /**
     * Feature: dynamic-scoring-leaderboard, Property 4: Time Clamping Equivalence (Zero/Negative time_limit)
     *
     * Zero or negative time limit awards full base_points with streak multiplier.
     *
     * **Validates: Requirements 1.7**
     */
    @Property(tries = 100)
    void property4_zeroOrNegativeTimeLimitAwardsFullBasePoints(
            @ForAll @IntRange(min = 100, max = 5000) int basePoints,
            @ForAll @LongRange(min = 0, max = 60000) long timeTakenMs,
            @ForAll @IntRange(min = -1000, max = 0) int timeLimitMs,
            @ForAll("validTimeFactors") double timeFactor,
            @ForAll @IntRange(min = 0, max = 10) int streak) {

        int score = scoreCalculator.calculateScoreWithTimeFactor(basePoints, timeTakenMs, timeLimitMs, timeFactor, streak);
        int expectedMultiplier = scoreCalculator.getStreakMultiplier(streak);

        assertThat(score)
            .as("Score with zero/negative time_limit should be base_points × multiplier")
            .isEqualTo(basePoints * expectedMultiplier);
    }

    // ==================== Property 5: Incorrect Answer Zero Score ====================

    /**
     * Feature: dynamic-scoring-leaderboard, Property 5: Incorrect Answer Zero Score
     *
     * For any base_points, time_taken, time_limit, time_factor, and streak count,
     * if the answer is incorrect or unanswered, the awarded score SHALL be exactly 0.
     *
     * **Validates: Requirements 1.4**
     */
    @Property(tries = 100)
    void property5_incorrectAnswerAlwaysZero(
            @ForAll @IntRange(min = 100, max = 5000) int basePoints,
            @ForAll @LongRange(min = 0, max = 60000) long timeTakenMs,
            @ForAll @IntRange(min = 1000, max = 60000) int timeLimitMs,
            @ForAll("validTimeFactors") double timeFactor,
            @ForAll @IntRange(min = 0, max = 20) int streak) {

        // isCorrect = false should always return 0
        int score = scoreCalculator.calculateScoreForAnswer(basePoints, timeTakenMs, timeLimitMs, timeFactor, streak, false);

        assertThat(score)
            .as("Incorrect answer should always yield 0 regardless of basePoints=%d, time=%d, streak=%d", 
                basePoints, timeTakenMs, streak)
            .isEqualTo(0);
    }

    /**
     * Feature: dynamic-scoring-leaderboard, Property 5: Incorrect Answer Zero Score (Edge Cases)
     *
     * Incorrect answers yield 0 even with extreme parameter values.
     *
     * **Validates: Requirements 1.4**
     */
    @Property(tries = 100)
    void property5_incorrectAnswerZeroWithEdgeCases(
            @ForAll @IntRange(min = 0, max = 10000) int basePoints,
            @ForAll @LongRange(min = -10000, max = 120000) long timeTakenMs,
            @ForAll @IntRange(min = -1000, max = 60000) int timeLimitMs,
            @ForAll("validTimeFactors") double timeFactor,
            @ForAll @IntRange(min = 0, max = 100) int streak) {

        // isCorrect = false should always return 0, even with edge case inputs
        int score = scoreCalculator.calculateScoreForAnswer(basePoints, timeTakenMs, timeLimitMs, timeFactor, streak, false);

        assertThat(score)
            .as("Incorrect answer should always yield 0 even with edge case inputs")
            .isEqualTo(0);
    }

    /**
     * Feature: dynamic-scoring-leaderboard, Property 5: Incorrect Answer Zero Score (Contrast with Correct)
     *
     * Verifies that correct answers get positive scores while incorrect answers get 0.
     *
     * **Validates: Requirements 1.4**
     */
    @Property(tries = 100)
    void property5_correctVsIncorrectAnswerContrast(
            @ForAll @IntRange(min = 100, max = 5000) int basePoints,
            @ForAll @LongRange(min = 0, max = 60000) long timeTakenMs,
            @ForAll @IntRange(min = 1000, max = 60000) int timeLimitMs,
            @ForAll("validTimeFactors") double timeFactor,
            @ForAll @IntRange(min = 0, max = 10) int streak) {

        int correctScore = scoreCalculator.calculateScoreForAnswer(basePoints, timeTakenMs, timeLimitMs, timeFactor, streak, true);
        int incorrectScore = scoreCalculator.calculateScoreForAnswer(basePoints, timeTakenMs, timeLimitMs, timeFactor, streak, false);

        assertThat(correctScore)
            .as("Correct answer should yield positive score")
            .isGreaterThan(0);
        assertThat(incorrectScore)
            .as("Incorrect answer should yield 0")
            .isEqualTo(0);
    }

    // ==================== Additional Supporting Tests ====================

    /**
     * Score calculation is deterministic.
     *
     * **Validates: Requirements 1.1**
     */
    @Property(tries = 100)
    void scoreCalculationIsDeterministic(
            @ForAll @IntRange(min = 100, max = 5000) int basePoints,
            @ForAll @LongRange(min = 0, max = 60000) long timeTakenMs,
            @ForAll @IntRange(min = 1000, max = 60000) int timeLimitMs,
            @ForAll("validTimeFactors") double timeFactor,
            @ForAll @IntRange(min = 0, max = 10) int streak,
            @ForAll boolean isCorrect) {

        int score1 = scoreCalculator.calculateScoreForAnswer(basePoints, timeTakenMs, timeLimitMs, timeFactor, streak, isCorrect);
        int score2 = scoreCalculator.calculateScoreForAnswer(basePoints, timeTakenMs, timeLimitMs, timeFactor, streak, isCorrect);
        int score3 = scoreCalculator.calculateScoreForAnswer(basePoints, timeTakenMs, timeLimitMs, timeFactor, streak, isCorrect);

        assertThat(score1).isEqualTo(score2).isEqualTo(score3);
    }

    /**
     * Score is always non-negative for any inputs.
     *
     * **Validates: Requirements 1.1**
     */
    @Property(tries = 100)
    void scoreIsNeverNegative(
            @ForAll @IntRange(min = 0, max = 5000) int basePoints,
            @ForAll @LongRange(min = -1000, max = 120000) long timeTakenMs,
            @ForAll @IntRange(min = -1000, max = 60000) int timeLimitMs,
            @ForAll("validTimeFactors") double timeFactor,
            @ForAll @IntRange(min = 0, max = 10) int streak,
            @ForAll boolean isCorrect) {

        int score = scoreCalculator.calculateScoreForAnswer(basePoints, timeTakenMs, timeLimitMs, timeFactor, streak, isCorrect);

        assertThat(score).isGreaterThanOrEqualTo(0);
    }

    // ==================== Providers ====================

    @Provide
    Arbitrary<Double> validTimeFactors() {
        return Arbitraries.of(0.3, 0.5, 0.7);
    }
}
