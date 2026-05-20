package com.quizplatform.session.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a participant's score for a single round.
 * Used by DynamicScoreEngine to track individual round results.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParticipantRoundScore {

    /** The participant's unique identifier */
    private String participantId;

    /** The participant's display nickname */
    private String nickname;

    /** Score earned in this round (0 for incorrect/unanswered) */
    private int roundScore;

    /** Cumulative score after this round */
    private int cumulativeScore;

    /** Current rank after this round (1-indexed) */
    private int rank;

    /** Rank change from previous round (positive = moved up, negative = moved down) */
    private int rankDelta;

    /** Current streak count */
    private int streakCount;

    /** Current streak multiplier (1, 2, or 3) */
    private int streakMultiplier;

    /** Time taken to answer in milliseconds (null if unanswered) */
    private Long timeTakenMs;

    /** Whether the answer was correct */
    private boolean correct;

    /** Whether the participant was connected during this round */
    private boolean connected;
}
