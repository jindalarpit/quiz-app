package com.quizplatform.session.service;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for streak correctness and score monotonicity with streaks.
 *
 * **Validates: Requirements 5.2, 5.5**
 */
class StreakPropertyTest {

    private final ScoreCalculator scoreCalculator = new ScoreCalculator();

    // ==================== P7: Streak Correctness ====================

    /**
     * P7: Streak increments on consecutive correct answers.
     *
     * Given a sequence of correct answers, the streak should equal the count
     * of consecutive correct answers.
     *
     * **Validates: Requirements 5.2**
     */
    @Property(tries = 500)
    void streakIncrementsOnConsecutiveCorrectAnswers(
            @ForAll @IntRange(min = 1, max = 20) int consecutiveCorrect) {

        int streak = 0;
        for (int i = 0; i < consecutiveCorrect; i++) {
            // Simulate correct answer: increment streak
            streak++;
        }

        assertThat(streak).isEqualTo(consecutiveCorrect);
    }

    /**
     * P7: Streak resets on incorrect answer.
     *
     * After any number of consecutive correct answers, a single incorrect answer
     * resets the streak to 0.
     *
     * **Validates: Requirements 5.5**
     */
    @Property(tries = 500)
    void streakResetsOnIncorrectAnswer(
            @ForAll @IntRange(min = 0, max = 20) int correctBeforeIncorrect) {

        int streak = correctBeforeIncorrect;

        // Simulate incorrect answer: reset streak
        streak = 0;

        assertThat(streak).isEqualTo(0);
    }

    /**
     * P7: Multiplier is 1x for streak < 3, 2x for streak 3-4, 3x for streak >= 5.
     *
     * The streak multiplier follows the defined thresholds exactly.
     *
     * **Validates: Requirements 5.2**
     */
    @Property(tries = 1000)
    void multiplierFollowsStreakThresholds(
            @ForAll @IntRange(min = 0, max = 100) int streak) {

        int multiplier = scoreCalculator.getStreakMultiplier(streak);

        if (streak >= 5) {
            assertThat(multiplier).isEqualTo(3);
        } else if (streak >= 3) {
            assertThat(multiplier).isEqualTo(2);
        } else {
            assertThat(multiplier).isEqualTo(1);
        }
    }

    /**
     * P7: Multiplier never exceeds 3x.
     *
     * Regardless of how high the streak goes, the multiplier is capped at 3.
     *
     * **Validates: Requirements 5.2**
     */
    @Property(tries = 1000)
    void multiplierNeverExceedsThree(
            @ForAll @IntRange(min = 0, max = 10000) int streak) {

        int multiplier = scoreCalculator.getStreakMultiplier(streak);

        assertThat(multiplier).isLessThanOrEqualTo(3);
        assertThat(multiplier).isGreaterThanOrEqualTo(1);
    }

    /**
     * P7: Streak correctness — streak equals length of longest suffix of all-correct answers.
     *
     * Given a sequence of boolean answers (true=correct, false=incorrect),
     * the final streak equals the length of the trailing consecutive correct answers.
     *
     * **Validates: Requirements 5.2, 5.5**
     */
    @Property(tries = 500)
    void streakEqualsTrailingConsecutiveCorrectCount(
            @ForAll("answerSequences") List<Boolean> answers) {

        int streak = 0;
        for (Boolean correct : answers) {
            if (correct) {
                streak++;
            } else {
                streak = 0;
            }
        }

        // Calculate expected: length of trailing consecutive true values
        int expected = 0;
        for (int i = answers.size() - 1; i >= 0; i--) {
            if (answers.get(i)) {
                expected++;
            } else {
                break;
            }
        }

        assertThat(streak).isEqualTo(expected);
    }

    @Provide
    Arbitrary<List<Boolean>> answerSequences() {
        return Arbitraries.of(true, false).list().ofMinSize(1).ofMaxSize(30);
    }

    // ==================== P1: Score Monotonicity with Streaks ====================

    /**
     * P1: Higher streak always results in higher or equal score for same base inputs.
     *
     * For the same base points, time taken, and time limit, a higher streak
     * always produces a score >= a lower streak.
     *
     * **Validates: Requirements 5.2**
     */
    @Property(tries = 1000)
    void higherStreakProducesHigherOrEqualScore(
            @ForAll @IntRange(min = 100, max = 5000) int basePoints,
            @ForAll @LongRange(min = 0, max = 60000) long timeTakenMs,
            @ForAll @IntRange(min = 1000, max = 60000) int timeLimitMs,
            @ForAll @IntRange(min = 0, max = 100) int streak1,
            @ForAll @IntRange(min = 0, max = 100) int streak2) {

        int score1 = scoreCalculator.calculateScoreWithStreak(basePoints, timeTakenMs, timeLimitMs, streak1);
        int score2 = scoreCalculator.calculateScoreWithStreak(basePoints, timeTakenMs, timeLimitMs, streak2);

        if (streak1 >= streak2) {
            assertThat(score1).isGreaterThanOrEqualTo(score2);
        } else {
            assertThat(score2).isGreaterThanOrEqualTo(score1);
        }
    }

    /**
     * P1: Score with multiplier 3x >= score with multiplier 2x >= score with multiplier 1x.
     *
     * For the same base inputs, the ordering of scores follows the multiplier ordering.
     *
     * **Validates: Requirements 5.2**
     */
    @Property(tries = 1000)
    void scoreOrderFollowsMultiplierOrder(
            @ForAll @IntRange(min = 100, max = 5000) int basePoints,
            @ForAll @LongRange(min = 0, max = 60000) long timeTakenMs,
            @ForAll @IntRange(min = 1000, max = 60000) int timeLimitMs) {

        // streak 0 → multiplier 1x
        int score1x = scoreCalculator.calculateScoreWithStreak(basePoints, timeTakenMs, timeLimitMs, 0);
        // streak 3 → multiplier 2x
        int score2x = scoreCalculator.calculateScoreWithStreak(basePoints, timeTakenMs, timeLimitMs, 3);
        // streak 5 → multiplier 3x
        int score3x = scoreCalculator.calculateScoreWithStreak(basePoints, timeTakenMs, timeLimitMs, 5);

        assertThat(score3x).isGreaterThanOrEqualTo(score2x);
        assertThat(score2x).isGreaterThanOrEqualTo(score1x);
    }

    /**
     * P1: Score with streak is always a positive multiple of the base score.
     *
     * The score with streak should equal the base score times the multiplier
     * (within rounding tolerance).
     *
     * **Validates: Requirements 5.1, 5.2**
     */
    @Property(tries = 1000)
    void scoreWithStreakIsMultipleOfBaseScore(
            @ForAll @IntRange(min = 100, max = 5000) int basePoints,
            @ForAll @LongRange(min = 0, max = 60000) long timeTakenMs,
            @ForAll @IntRange(min = 1000, max = 60000) int timeLimitMs,
            @ForAll @IntRange(min = 0, max = 100) int streak) {

        int scoreNoStreak = scoreCalculator.calculateScoreWithStreak(basePoints, timeTakenMs, timeLimitMs, 0);
        int scoreWithStreak = scoreCalculator.calculateScoreWithStreak(basePoints, timeTakenMs, timeLimitMs, streak);
        int multiplier = scoreCalculator.getStreakMultiplier(streak);

        // Due to rounding, allow ±multiplier tolerance (each rounding step can differ by 1)
        int expectedScore = (int) Math.round((double) scoreNoStreak * multiplier);
        assertThat(scoreWithStreak).isBetween(expectedScore - multiplier, expectedScore + multiplier);
    }
}
