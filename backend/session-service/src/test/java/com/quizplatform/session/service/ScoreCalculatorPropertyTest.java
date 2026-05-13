package com.quizplatform.session.service;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for ScoreCalculator.
 *
 * **Validates: Requirements 5.1**
 */
class ScoreCalculatorPropertyTest {

    private final ScoreCalculator scoreCalculator = new ScoreCalculator();

    /**
     * P8: Score Calculation Determinism
     *
     * Given the same inputs (basePoints, timeTaken, timeLimit), the score calculation
     * always produces the same result.
     *
     * **Validates: Requirements 5.1**
     */
    @Property(tries = 1000)
    void scoreCalculationIsDeterministic(
            @ForAll @IntRange(min = 100, max = 5000) int basePoints,
            @ForAll @LongRange(min = 0, max = 60000) long timeTakenMs,
            @ForAll @IntRange(min = 1000, max = 60000) int timeLimitMs) {

        int score1 = scoreCalculator.calculateScore(basePoints, timeTakenMs, timeLimitMs);
        int score2 = scoreCalculator.calculateScore(basePoints, timeTakenMs, timeLimitMs);
        int score3 = scoreCalculator.calculateScore(basePoints, timeTakenMs, timeLimitMs);

        assertThat(score1).isEqualTo(score2).isEqualTo(score3);
    }

    /**
     * P1: Score Monotonicity
     *
     * For the same base points and time limit, a faster answer (lower timeTaken)
     * always results in a score greater than or equal to a slower answer.
     *
     * **Validates: Requirements 5.1**
     */
    @Property(tries = 1000)
    void fasterAnswersScoreHigherOrEqual(
            @ForAll @IntRange(min = 100, max = 5000) int basePoints,
            @ForAll @IntRange(min = 1000, max = 60000) int timeLimitMs,
            @ForAll @LongRange(min = 0, max = 60000) long timeTaken1,
            @ForAll @LongRange(min = 0, max = 60000) long timeTaken2) {

        int score1 = scoreCalculator.calculateScore(basePoints, timeTaken1, timeLimitMs);
        int score2 = scoreCalculator.calculateScore(basePoints, timeTaken2, timeLimitMs);

        if (timeTaken1 <= timeTaken2) {
            assertThat(score1).isGreaterThanOrEqualTo(score2);
        } else {
            assertThat(score2).isGreaterThanOrEqualTo(score1);
        }
    }

    /**
     * Score is always bounded between 50% and 100% of base points for valid inputs.
     *
     * **Validates: Requirements 5.1**
     */
    @Property(tries = 1000)
    void scoreIsBoundedBetweenHalfAndFullBasePoints(
            @ForAll @IntRange(min = 100, max = 5000) int basePoints,
            @ForAll @LongRange(min = 0, max = 60000) long timeTakenMs,
            @ForAll @IntRange(min = 1000, max = 60000) int timeLimitMs) {

        int score = scoreCalculator.calculateScore(basePoints, timeTakenMs, timeLimitMs);

        int minExpected = (int) Math.round(basePoints * 0.5);
        int maxExpected = basePoints;

        assertThat(score).isBetween(minExpected, maxExpected);
    }

    /**
     * Score is always non-negative for any valid inputs.
     *
     * **Validates: Requirements 5.1**
     */
    @Property(tries = 1000)
    void scoreIsNeverNegative(
            @ForAll @IntRange(min = 0, max = 5000) int basePoints,
            @ForAll @LongRange(min = -1000, max = 120000) long timeTakenMs,
            @ForAll @IntRange(min = 0, max = 60000) int timeLimitMs) {

        int score = scoreCalculator.calculateScore(basePoints, timeTakenMs, timeLimitMs);

        assertThat(score).isGreaterThanOrEqualTo(0);
    }

    /**
     * Score at time 0 always equals the full base points.
     *
     * **Validates: Requirements 5.1**
     */
    @Property(tries = 100)
    void instantAnswerAlwaysGetsFullPoints(
            @ForAll @IntRange(min = 100, max = 5000) int basePoints,
            @ForAll @IntRange(min = 1000, max = 60000) int timeLimitMs) {

        int score = scoreCalculator.calculateScore(basePoints, 0, timeLimitMs);

        assertThat(score).isEqualTo(basePoints);
    }

    /**
     * Score at exactly the time limit always equals 50% of base points.
     *
     * **Validates: Requirements 5.1**
     */
    @Property(tries = 100)
    void answerAtTimeLimitGetsHalfPoints(
            @ForAll @IntRange(min = 100, max = 5000) int basePoints,
            @ForAll @IntRange(min = 1000, max = 60000) int timeLimitMs) {

        int score = scoreCalculator.calculateScore(basePoints, timeLimitMs, timeLimitMs);

        int expected = (int) Math.round(basePoints * 0.5);
        assertThat(score).isEqualTo(expected);
    }
}
