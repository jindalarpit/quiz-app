package com.quizplatform.quiz.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ScoringMode enum.
 * Validates: Requirements 7.1, 7.6
 */
@DisplayName("ScoringMode Enum Tests")
class ScoringModeTest {

    @Test
    @DisplayName("SPEED_MATTERS should have time factor 0.7")
    void speedMatters_shouldHaveTimeFactor0_7() {
        assertThat(ScoringMode.SPEED_MATTERS.getTimeFactor()).isEqualTo(0.7);
    }

    @Test
    @DisplayName("BALANCED should have time factor 0.5")
    void balanced_shouldHaveTimeFactor0_5() {
        assertThat(ScoringMode.BALANCED.getTimeFactor()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("KNOWLEDGE_FIRST should have time factor 0.3")
    void knowledgeFirst_shouldHaveTimeFactor0_3() {
        assertThat(ScoringMode.KNOWLEDGE_FIRST.getTimeFactor()).isEqualTo(0.3);
    }

    @ParameterizedTest(name = "{0} should have time factor {1}")
    @CsvSource({
            "SPEED_MATTERS, 0.7",
            "BALANCED, 0.5",
            "KNOWLEDGE_FIRST, 0.3"
    })
    @DisplayName("Each scoring mode should map to correct time factor")
    void eachScoringMode_shouldMapToCorrectTimeFactor(ScoringMode mode, double expectedTimeFactor) {
        assertThat(mode.getTimeFactor()).isEqualTo(expectedTimeFactor);
    }

    @Test
    @DisplayName("All scoring modes should have time factors between 0 and 1")
    void allScoringModes_shouldHaveTimeFactorsBetween0And1() {
        for (ScoringMode mode : ScoringMode.values()) {
            assertThat(mode.getTimeFactor())
                    .as("Time factor for %s should be between 0 and 1", mode)
                    .isGreaterThan(0.0)
                    .isLessThanOrEqualTo(1.0);
        }
    }

    @Test
    @DisplayName("SPEED_MATTERS should yield minimum 30% of base points at time limit")
    void speedMatters_shouldYieldMinimum30PercentAtTimeLimit() {
        // Minimum score = base_points × (1 - time_factor)
        // For SPEED_MATTERS: 1 - 0.7 = 0.3 = 30%
        double minimumScorePercentage = 1 - ScoringMode.SPEED_MATTERS.getTimeFactor();
        assertThat(minimumScorePercentage).isEqualTo(0.3);
    }

    @Test
    @DisplayName("BALANCED should yield minimum 50% of base points at time limit")
    void balanced_shouldYieldMinimum50PercentAtTimeLimit() {
        // Minimum score = base_points × (1 - time_factor)
        // For BALANCED: 1 - 0.5 = 0.5 = 50%
        double minimumScorePercentage = 1 - ScoringMode.BALANCED.getTimeFactor();
        assertThat(minimumScorePercentage).isEqualTo(0.5);
    }

    @Test
    @DisplayName("KNOWLEDGE_FIRST should yield minimum 70% of base points at time limit")
    void knowledgeFirst_shouldYieldMinimum70PercentAtTimeLimit() {
        // Minimum score = base_points × (1 - time_factor)
        // For KNOWLEDGE_FIRST: 1 - 0.3 = 0.7 = 70%
        double minimumScorePercentage = 1 - ScoringMode.KNOWLEDGE_FIRST.getTimeFactor();
        assertThat(minimumScorePercentage).isEqualTo(0.7);
    }

    @Test
    @DisplayName("ScoringMode should have exactly 3 values")
    void scoringMode_shouldHaveExactly3Values() {
        assertThat(ScoringMode.values()).hasSize(3);
    }

    @Test
    @DisplayName("ScoringMode values should be in expected order")
    void scoringMode_valuesShouldBeInExpectedOrder() {
        ScoringMode[] values = ScoringMode.values();
        assertThat(values[0]).isEqualTo(ScoringMode.SPEED_MATTERS);
        assertThat(values[1]).isEqualTo(ScoringMode.BALANCED);
        assertThat(values[2]).isEqualTo(ScoringMode.KNOWLEDGE_FIRST);
    }

    @Test
    @DisplayName("Time factors should be ordered: SPEED_MATTERS > BALANCED > KNOWLEDGE_FIRST")
    void timeFactors_shouldBeOrderedBySpeedImportance() {
        assertThat(ScoringMode.SPEED_MATTERS.getTimeFactor())
                .isGreaterThan(ScoringMode.BALANCED.getTimeFactor());
        assertThat(ScoringMode.BALANCED.getTimeFactor())
                .isGreaterThan(ScoringMode.KNOWLEDGE_FIRST.getTimeFactor());
    }
}
