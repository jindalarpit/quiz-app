package com.quizplatform.quiz.model;

/**
 * Defines the scoring sensitivity mode for a quiz.
 * Controls how much speed matters relative to correctness.
 * 
 * The time factor determines the minimum score percentage for a correct answer:
 * - SPEED_MATTERS (0.7): Min 30% of base points at time limit
 * - BALANCED (0.5): Min 50% of base points at time limit
 * - KNOWLEDGE_FIRST (0.3): Min 70% of base points at time limit
 * 
 * Formula: base_points × (1 - (time_taken / time_limit) × time_factor)
 */
public enum ScoringMode {
    SPEED_MATTERS(0.7),    // Min 30% of base points
    BALANCED(0.5),         // Min 50% of base points
    KNOWLEDGE_FIRST(0.3);  // Min 70% of base points

    private final double timeFactor;

    ScoringMode(double timeFactor) {
        this.timeFactor = timeFactor;
    }

    /**
     * Returns the time factor used in the scoring formula.
     * Higher values mean speed matters more.
     * 
     * @return the time factor (0.3, 0.5, or 0.7)
     */
    public double getTimeFactor() {
        return timeFactor;
    }
}
