package com.quizplatform.session.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single entry in a leaderboard snapshot.
 * 
 * Stored in Redis as a hash field value with format:
 * "{rank}|{cumulativeScore}|{roundScore}|{avgResponseTimeMs}"
 * 
 * Used by LeaderboardSnapshotService for storing and retrieving
 * per-round snapshots to compute rank deltas.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotEntry {

    /**
     * Unique identifier for the participant.
     */
    private String participantId;

    /**
     * Participant's rank in this snapshot (1-indexed).
     */
    private int rank;

    /**
     * Cumulative score at the time of snapshot.
     */
    private long cumulativeScore;

    /**
     * Score earned in the round this snapshot was taken.
     */
    private int roundScore;

    /**
     * Average response time in milliseconds at the time of snapshot.
     */
    private long avgResponseTimeMs;

    /**
     * Serialize this entry to the Redis hash value format.
     * Format: "{rank}|{cumulativeScore}|{roundScore}|{avgResponseTimeMs}"
     */
    public String toRedisValue() {
        return String.format("%d|%d|%d|%d", rank, cumulativeScore, roundScore, avgResponseTimeMs);
    }

    /**
     * Parse a Redis hash value into a SnapshotEntry.
     * 
     * @param participantId the participant ID (from hash field)
     * @param redisValue the value in format "{rank}|{cumulativeScore}|{roundScore}|{avgResponseTimeMs}"
     * @return parsed SnapshotEntry
     * @throws IllegalArgumentException if the value format is invalid
     */
    public static SnapshotEntry fromRedisValue(String participantId, String redisValue) {
        if (redisValue == null || redisValue.isEmpty()) {
            throw new IllegalArgumentException("Redis value cannot be null or empty");
        }
        
        String[] parts = redisValue.split("\\|");
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid snapshot value format: " + redisValue);
        }
        
        try {
            return SnapshotEntry.builder()
                    .participantId(participantId)
                    .rank(Integer.parseInt(parts[0]))
                    .cumulativeScore(Long.parseLong(parts[1]))
                    .roundScore(Integer.parseInt(parts[2]))
                    .avgResponseTimeMs(Long.parseLong(parts[3]))
                    .build();
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number format in snapshot value: " + redisValue, e);
        }
    }
}
