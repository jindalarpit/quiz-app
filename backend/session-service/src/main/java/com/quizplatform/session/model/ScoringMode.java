package com.quizplatform.session.model;

/**
 * Defines the scoring sensitivity mode for a quiz session.
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

    /**
     * Parse a scoring mode from a string value.
     * Returns SPEED_MATTERS as the default if the value is null or invalid.
     * 
     * @param value the string value to parse
     * @return the corresponding ScoringMode, or SPEED_MATTERS if invalid
     */
    public static ScoringMode fromString(String value) {
        if (value == null || value.isBlank()) {
            return SPEED_MATTERS;
        }
        try {
            return ScoringMode.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return SPEED_MATTERS;
        }
    }
}
