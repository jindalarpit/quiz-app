package com.quizplatform.session.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Kafka event payload for the score.awarded topic.
 * Published after each round to the analytics-service.
 *
 * Requirements: 5.6
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreAwardedEvent {

    /** The session UUID */
    private String sessionId;

    /** The round number (0-indexed) */
    private int roundNumber;

    /** Timestamp when the round was scored (epoch milliseconds) */
    private long timestamp;

    /** List of participant score entries for this round */
    private List<ScoreAwardedEntry> entries;

    /**
     * Individual participant entry within the score.awarded event.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoreAwardedEntry {

        /** The participant's unique identifier */
        private String participantId;

        /** Score earned in this round */
        private int roundScore;

        /** Cumulative score after this round */
        private int cumulativeScore;

        /** Current rank after this round (1-indexed) */
        private int rank;

        /** Rank change from previous round */
        private int rankDelta;

        /** Current streak count */
        private int streakCount;

        /** Current streak multiplier (1, 2, or 3) */
        private int streakMultiplier;

        /** Time taken to answer in milliseconds (null if unanswered) */
        private Long timeTakenMs;

        /** Whether the answer was correct */
        private boolean isCorrect;
    }
}
