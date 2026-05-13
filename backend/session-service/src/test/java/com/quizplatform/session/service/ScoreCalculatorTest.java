package com.quizplatform.session.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreCalculatorTest {

    private ScoreCalculator scoreCalculator;

    @BeforeEach
    void setUp() {
        scoreCalculator = new ScoreCalculator();
    }

    @Test
    @DisplayName("Instant answer (0ms) should award full base points")
    void instantAnswer_awardsFullPoints() {
        int score = scoreCalculator.calculateScore(1000, 0, 20000);
        assertThat(score).isEqualTo(1000);
    }

    @Test
    @DisplayName("Answer at exactly the time limit should award 50% of base points")
    void answerAtTimeLimit_awardsHalfPoints() {
        int score = scoreCalculator.calculateScore(1000, 20000, 20000);
        assertThat(score).isEqualTo(500);
    }

    @Test
    @DisplayName("Answer at half the time limit should award 75% of base points")
    void answerAtHalfTime_awards75Percent() {
        int score = scoreCalculator.calculateScore(1000, 10000, 20000);
        assertThat(score).isEqualTo(750);
    }

    @Test
    @DisplayName("Double points question should scale correctly")
    void doublePointsQuestion_scalesCorrectly() {
        int score = scoreCalculator.calculateScore(2000, 0, 20000);
        assertThat(score).isEqualTo(2000);

        int halfTimeScore = scoreCalculator.calculateScore(2000, 10000, 20000);
        assertThat(halfTimeScore).isEqualTo(1500);
    }

    @Test
    @DisplayName("Negative time taken should be clamped to 0")
    void negativeTimeTaken_clampedToZero() {
        int score = scoreCalculator.calculateScore(1000, -100, 20000);
        assertThat(score).isEqualTo(1000);
    }

    @Test
    @DisplayName("Time taken exceeding limit should be clamped to limit")
    void timeTakenExceedingLimit_clampedToLimit() {
        int score = scoreCalculator.calculateScore(1000, 25000, 20000);
        assertThat(score).isEqualTo(500);
    }

    @Test
    @DisplayName("Zero base points should return 0")
    void zeroBasePoints_returnsZero() {
        int score = scoreCalculator.calculateScore(0, 5000, 20000);
        assertThat(score).isEqualTo(0);
    }

    @Test
    @DisplayName("Negative base points should return 0")
    void negativeBasePoints_returnsZero() {
        int score = scoreCalculator.calculateScore(-100, 5000, 20000);
        assertThat(score).isEqualTo(0);
    }

    @Test
    @DisplayName("Zero time limit should return 0")
    void zeroTimeLimit_returnsZero() {
        int score = scoreCalculator.calculateScore(1000, 5000, 0);
        assertThat(score).isEqualTo(0);
    }

    @Test
    @DisplayName("Score should be rounded to nearest integer")
    void scoreRounding() {
        // 1000 * (1 - (3333 / 20000) * 0.5) = 1000 * (1 - 0.08333) = 1000 * 0.91667 = 916.67 → 917
        int score = scoreCalculator.calculateScore(1000, 3333, 20000);
        assertThat(score).isEqualTo(917);
    }

    @Test
    @DisplayName("Score is deterministic - same inputs always produce same output")
    void scoreDeterminism() {
        int score1 = scoreCalculator.calculateScore(1000, 5000, 20000);
        int score2 = scoreCalculator.calculateScore(1000, 5000, 20000);
        int score3 = scoreCalculator.calculateScore(1000, 5000, 20000);
        assertThat(score1).isEqualTo(score2).isEqualTo(score3);
    }

    @Test
    @DisplayName("Score monotonicity - faster answers get higher scores")
    void scoreMonotonicity() {
        int fastScore = scoreCalculator.calculateScore(1000, 2000, 20000);
        int mediumScore = scoreCalculator.calculateScore(1000, 10000, 20000);
        int slowScore = scoreCalculator.calculateScore(1000, 18000, 20000);

        assertThat(fastScore).isGreaterThan(mediumScore);
        assertThat(mediumScore).isGreaterThan(slowScore);
    }

    @Test
    @DisplayName("Score should always be between 50% and 100% of base points for valid inputs")
    void scoreBounds() {
        for (long timeTaken = 0; timeTaken <= 20000; timeTaken += 1000) {
            int score = scoreCalculator.calculateScore(1000, timeTaken, 20000);
            assertThat(score).isBetween(500, 1000);
        }
    }
}
