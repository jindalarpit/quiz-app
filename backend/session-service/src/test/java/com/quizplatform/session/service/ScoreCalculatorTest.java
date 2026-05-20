package com.quizplatform.session.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreCalculatorTest {

    private ScoreCalculator scoreCalculator;

    @BeforeEach
    void setUp() {
        scoreCalculator = new ScoreCalculator();
    }

    @Nested
    @DisplayName("calculateScore (legacy method with default time factor 0.5)")
    class CalculateScoreTests {

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
        @DisplayName("Zero time limit should award full base points")
        void zeroTimeLimit_awardsFullBasePoints() {
            int score = scoreCalculator.calculateScore(1000, 5000, 0);
            assertThat(score).isEqualTo(1000);
        }

        @Test
        @DisplayName("Negative time limit should award full base points")
        void negativeTimeLimit_awardsFullBasePoints() {
            int score = scoreCalculator.calculateScore(1000, 5000, -1000);
            assertThat(score).isEqualTo(1000);
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

    @Nested
    @DisplayName("calculateScoreWithTimeFactor")
    class CalculateScoreWithTimeFactorTests {

        @Test
        @DisplayName("Speed Matters mode (0.7) - instant answer awards full base points")
        void speedMattersMode_instantAnswer_awardsFullPoints() {
            int score = scoreCalculator.calculateScoreWithTimeFactor(1000, 0, 20000, 0.7, 0);
            assertThat(score).isEqualTo(1000);
        }

        @Test
        @DisplayName("Speed Matters mode (0.7) - answer at time limit awards 30% of base points")
        void speedMattersMode_answerAtTimeLimit_awards30Percent() {
            int score = scoreCalculator.calculateScoreWithTimeFactor(1000, 20000, 20000, 0.7, 0);
            assertThat(score).isEqualTo(300);
        }

        @Test
        @DisplayName("Balanced mode (0.5) - answer at time limit awards 50% of base points")
        void balancedMode_answerAtTimeLimit_awards50Percent() {
            int score = scoreCalculator.calculateScoreWithTimeFactor(1000, 20000, 20000, 0.5, 0);
            assertThat(score).isEqualTo(500);
        }

        @Test
        @DisplayName("Knowledge First mode (0.3) - answer at time limit awards 70% of base points")
        void knowledgeFirstMode_answerAtTimeLimit_awards70Percent() {
            int score = scoreCalculator.calculateScoreWithTimeFactor(1000, 20000, 20000, 0.3, 0);
            assertThat(score).isEqualTo(700);
        }

        @Test
        @DisplayName("Double points question with Speed Matters mode")
        void doublePointsQuestion_speedMattersMode() {
            // At time limit: 2000 * (1 - 1.0 * 0.7) = 2000 * 0.3 = 600
            int score = scoreCalculator.calculateScoreWithTimeFactor(2000, 20000, 20000, 0.7, 0);
            assertThat(score).isEqualTo(600);
        }

        @Test
        @DisplayName("Time clamping - negative time treated as 0")
        void timeClamping_negativeTime() {
            int score = scoreCalculator.calculateScoreWithTimeFactor(1000, -500, 20000, 0.7, 0);
            assertThat(score).isEqualTo(1000);
        }

        @Test
        @DisplayName("Time clamping - time exceeding limit treated as limit")
        void timeClamping_exceedingLimit() {
            int score = scoreCalculator.calculateScoreWithTimeFactor(1000, 30000, 20000, 0.7, 0);
            assertThat(score).isEqualTo(300);
        }

        @Test
        @DisplayName("Zero time limit awards full base points")
        void zeroTimeLimit_awardsFullBasePoints() {
            int score = scoreCalculator.calculateScoreWithTimeFactor(1000, 5000, 0, 0.7, 0);
            assertThat(score).isEqualTo(1000);
        }

        @Test
        @DisplayName("Negative time limit awards full base points")
        void negativeTimeLimit_awardsFullBasePoints() {
            int score = scoreCalculator.calculateScoreWithTimeFactor(1000, 5000, -1000, 0.7, 0);
            assertThat(score).isEqualTo(1000);
        }

        @Test
        @DisplayName("Zero base points returns 0")
        void zeroBasePoints_returnsZero() {
            int score = scoreCalculator.calculateScoreWithTimeFactor(0, 5000, 20000, 0.7, 0);
            assertThat(score).isEqualTo(0);
        }

        @Test
        @DisplayName("Negative base points returns 0")
        void negativeBasePoints_returnsZero() {
            int score = scoreCalculator.calculateScoreWithTimeFactor(-100, 5000, 20000, 0.7, 0);
            assertThat(score).isEqualTo(0);
        }

        @Test
        @DisplayName("Score rounding with time factor")
        void scoreRounding() {
            // 1000 * (1 - (3333 / 20000) * 0.7) = 1000 * (1 - 0.11666) = 1000 * 0.88334 = 883.34 → 883
            int score = scoreCalculator.calculateScoreWithTimeFactor(1000, 3333, 20000, 0.7, 0);
            assertThat(score).isEqualTo(883);
        }

        @Test
        @DisplayName("Score monotonicity - faster answers get higher scores")
        void scoreMonotonicity() {
            int fastScore = scoreCalculator.calculateScoreWithTimeFactor(1000, 2000, 20000, 0.7, 0);
            int mediumScore = scoreCalculator.calculateScoreWithTimeFactor(1000, 10000, 20000, 0.7, 0);
            int slowScore = scoreCalculator.calculateScoreWithTimeFactor(1000, 18000, 20000, 0.7, 0);

            assertThat(fastScore).isGreaterThan(mediumScore);
            assertThat(mediumScore).isGreaterThan(slowScore);
        }

        @Test
        @DisplayName("Score bounds for Speed Matters mode (30% to 100%)")
        void scoreBounds_speedMattersMode() {
            for (long timeTaken = 0; timeTaken <= 20000; timeTaken += 1000) {
                int score = scoreCalculator.calculateScoreWithTimeFactor(1000, timeTaken, 20000, 0.7, 0);
                assertThat(score).isBetween(300, 1000);
            }
        }

        @Test
        @DisplayName("Score bounds for Balanced mode (50% to 100%)")
        void scoreBounds_balancedMode() {
            for (long timeTaken = 0; timeTaken <= 20000; timeTaken += 1000) {
                int score = scoreCalculator.calculateScoreWithTimeFactor(1000, timeTaken, 20000, 0.5, 0);
                assertThat(score).isBetween(500, 1000);
            }
        }

        @Test
        @DisplayName("Score bounds for Knowledge First mode (70% to 100%)")
        void scoreBounds_knowledgeFirstMode() {
            for (long timeTaken = 0; timeTaken <= 20000; timeTaken += 1000) {
                int score = scoreCalculator.calculateScoreWithTimeFactor(1000, timeTaken, 20000, 0.3, 0);
                assertThat(score).isBetween(700, 1000);
            }
        }
    }

    @Nested
    @DisplayName("Streak multiplier with time factor")
    class StreakMultiplierWithTimeFactorTests {

        @Test
        @DisplayName("Streak of 3 applies 2x multiplier")
        void streak3_applies2xMultiplier() {
            // Base score at time limit with 0.7 factor: 1000 * 0.3 = 300
            // With 2x multiplier: 300 * 2 = 600
            int score = scoreCalculator.calculateScoreWithTimeFactor(1000, 20000, 20000, 0.7, 3);
            assertThat(score).isEqualTo(600);
        }

        @Test
        @DisplayName("Streak of 4 applies 2x multiplier")
        void streak4_applies2xMultiplier() {
            int score = scoreCalculator.calculateScoreWithTimeFactor(1000, 20000, 20000, 0.7, 4);
            assertThat(score).isEqualTo(600);
        }

        @Test
        @DisplayName("Streak of 5 applies 3x multiplier")
        void streak5_applies3xMultiplier() {
            // Base score at time limit with 0.7 factor: 1000 * 0.3 = 300
            // With 3x multiplier: 300 * 3 = 900
            int score = scoreCalculator.calculateScoreWithTimeFactor(1000, 20000, 20000, 0.7, 5);
            assertThat(score).isEqualTo(900);
        }

        @Test
        @DisplayName("Streak of 10 applies 3x multiplier")
        void streak10_applies3xMultiplier() {
            int score = scoreCalculator.calculateScoreWithTimeFactor(1000, 20000, 20000, 0.7, 10);
            assertThat(score).isEqualTo(900);
        }

        @Test
        @DisplayName("Multiplied score can exceed base points")
        void multipliedScore_canExceedBasePoints() {
            // Instant answer: 1000 points
            // With 3x multiplier: 1000 * 3 = 3000 (exceeds base_points of 1000)
            int score = scoreCalculator.calculateScoreWithTimeFactor(1000, 0, 20000, 0.7, 5);
            assertThat(score).isEqualTo(3000);
            assertThat(score).isGreaterThan(1000); // Exceeds base_points
        }

        @Test
        @DisplayName("Multiplied score is NOT clamped to base points")
        void multipliedScore_notClampedToBasePoints() {
            // Fast answer: 1000 * (1 - 0.1 * 0.7) = 1000 * 0.93 = 930
            // With 2x multiplier: 930 * 2 = 1860 (exceeds base_points of 1000)
            int score = scoreCalculator.calculateScoreWithTimeFactor(1000, 2000, 20000, 0.7, 3);
            assertThat(score).isGreaterThan(1000);
        }

        @Test
        @DisplayName("Streak of 0 applies 1x multiplier")
        void streak0_applies1xMultiplier() {
            int score = scoreCalculator.calculateScoreWithTimeFactor(1000, 0, 20000, 0.7, 0);
            assertThat(score).isEqualTo(1000);
        }

        @Test
        @DisplayName("Streak of 2 applies 1x multiplier")
        void streak2_applies1xMultiplier() {
            int score = scoreCalculator.calculateScoreWithTimeFactor(1000, 0, 20000, 0.7, 2);
            assertThat(score).isEqualTo(1000);
        }

        @Test
        @DisplayName("Negative streak applies 1x multiplier")
        void negativeStreak_applies1xMultiplier() {
            int score = scoreCalculator.calculateScoreWithTimeFactor(1000, 0, 20000, 0.7, -5);
            assertThat(score).isEqualTo(1000);
        }
    }

    @Nested
    @DisplayName("calculateScoreForZeroTimeLimit")
    class CalculateScoreForZeroTimeLimitTests {

        @Test
        @DisplayName("Awards full base points with no streak")
        void awardsFullBasePoints_noStreak() {
            int score = scoreCalculator.calculateScoreForZeroTimeLimit(1000, 0);
            assertThat(score).isEqualTo(1000);
        }

        @Test
        @DisplayName("Awards full base points with 2x streak multiplier")
        void awardsFullBasePoints_with2xStreak() {
            int score = scoreCalculator.calculateScoreForZeroTimeLimit(1000, 3);
            assertThat(score).isEqualTo(2000);
        }

        @Test
        @DisplayName("Awards full base points with 3x streak multiplier")
        void awardsFullBasePoints_with3xStreak() {
            int score = scoreCalculator.calculateScoreForZeroTimeLimit(1000, 5);
            assertThat(score).isEqualTo(3000);
        }

        @Test
        @DisplayName("Double points question with streak")
        void doublePointsQuestion_withStreak() {
            int score = scoreCalculator.calculateScoreForZeroTimeLimit(2000, 5);
            assertThat(score).isEqualTo(6000);
        }

        @Test
        @DisplayName("Zero base points returns 0")
        void zeroBasePoints_returnsZero() {
            int score = scoreCalculator.calculateScoreForZeroTimeLimit(0, 5);
            assertThat(score).isEqualTo(0);
        }

        @Test
        @DisplayName("Negative base points returns 0")
        void negativeBasePoints_returnsZero() {
            int score = scoreCalculator.calculateScoreForZeroTimeLimit(-100, 5);
            assertThat(score).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("getStreakMultiplier")
    class GetStreakMultiplierTests {

        @Test
        @DisplayName("Streak < 3 returns 1x multiplier")
        void streakLessThan3_returns1x() {
            assertThat(scoreCalculator.getStreakMultiplier(0)).isEqualTo(1);
            assertThat(scoreCalculator.getStreakMultiplier(1)).isEqualTo(1);
            assertThat(scoreCalculator.getStreakMultiplier(2)).isEqualTo(1);
        }

        @Test
        @DisplayName("Streak 3-4 returns 2x multiplier")
        void streak3to4_returns2x() {
            assertThat(scoreCalculator.getStreakMultiplier(3)).isEqualTo(2);
            assertThat(scoreCalculator.getStreakMultiplier(4)).isEqualTo(2);
        }

        @Test
        @DisplayName("Streak >= 5 returns 3x multiplier")
        void streak5OrMore_returns3x() {
            assertThat(scoreCalculator.getStreakMultiplier(5)).isEqualTo(3);
            assertThat(scoreCalculator.getStreakMultiplier(10)).isEqualTo(3);
            assertThat(scoreCalculator.getStreakMultiplier(100)).isEqualTo(3);
        }

        @Test
        @DisplayName("Negative streak returns 1x multiplier")
        void negativeStreak_returns1x() {
            assertThat(scoreCalculator.getStreakMultiplier(-1)).isEqualTo(1);
            assertThat(scoreCalculator.getStreakMultiplier(-100)).isEqualTo(1);
        }
    }
}
