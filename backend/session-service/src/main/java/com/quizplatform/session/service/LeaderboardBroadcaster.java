package com.quizplatform.session.service;

import com.quizplatform.session.dto.RoundResult;

/**
 * Service for broadcasting leaderboard updates via WebSocket and Kafka.
 * Handles personalized view construction and event delivery with retry logic.
 * 
 * This interface will be implemented in Task 5.1.
 * 
 * Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7
 */
public interface LeaderboardBroadcaster {

    /**
     * Broadcast leaderboard.updated event to host (top 5) and each participant
     * (personalized view with context rows).
     * 
     * Includes retry logic for failed WebSocket deliveries:
     * - 3 retries with exponential backoff within 2 seconds
     * - Queue failed events for reconnection delivery
     * 
     * @param pin session PIN
     * @param result the round result containing all participant scores
     */
    void broadcastLeaderboardUpdate(String pin, RoundResult result);

    /**
     * Publish score.awarded event to Kafka for analytics.
     * 
     * Payload includes: session_id, round_number, timestamp, and entries array
     * with participant_id, round_score, cumulative_score, rank, rank_delta,
     * streak_count, streak_multiplier, time_taken_ms, is_correct.
     * 
     * @param pin session PIN
     * @param result the round result containing all participant scores
     */
    void publishScoreEvent(String pin, RoundResult result);
}
