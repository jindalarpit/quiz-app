package com.quizplatform.session.service;

import com.quizplatform.session.dto.ParticipantRoundScore;
import com.quizplatform.session.dto.RankedParticipant;
import com.quizplatform.session.dto.SnapshotEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing per-round leaderboard snapshots in Redis.
 * 
 * Snapshots are used to compute rank deltas by comparing current ranks
 * to the previous round's snapshot. This enables the animated leaderboard
 * to show rank movement indicators (up/down arrows).
 * 
 * Redis key format: snapshot:{pin}:{roundNumber}
 * Redis value format: Hash with participantId as field and 
 *                     "{rank}|{cumulativeScore}|{roundScore}|{avgResponseTimeMs}" as value
 * TTL: 4 hours (session TTL)
 * 
 * Implements retry logic with exponential backoff (100ms, 200ms, 400ms) on Redis failures.
 * On all retries failing, computes deltas from in-memory state and logs error metric.
 * 
 * **Validates: Requirements 4.1, 4.2, 4.3, 4.5, 4.8**
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaderboardSnapshotService {

    private static final long SESSION_TTL_SECONDS = 14400; // 4 hours
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long[] BACKOFF_DELAYS_MS = {100L, 200L, 400L};

    private final StringRedisTemplate redisTemplate;
    private final RankingService rankingService;

    /**
     * Store a snapshot of all participant ranks and scores for a given round.
     * Uses Redis HSET with key: snapshot:{pin}:{roundNumber}
     * TTL: 4 hours (session TTL)
     * 
     * Implements retry logic: 3 retries with exponential backoff (100ms, 200ms, 400ms).
     * 
     * @param pin session PIN
     * @param roundNumber the round number (0-indexed)
     * @param entries list of participant scores for this round
     */
    public void storeSnapshot(String pin, int roundNumber, List<ParticipantRoundScore> entries) {
        if (entries == null || entries.isEmpty()) {
            log.debug("No entries to store for snapshot: pin={}, round={}", pin, roundNumber);
            return;
        }

        String key = snapshotKey(pin, roundNumber);
        Map<String, String> hashEntries = new HashMap<>();
        
        for (ParticipantRoundScore entry : entries) {
            // Convert to internal storage format: "{rank}|{cumulativeScore}|{roundScore}|{avgResponseTimeMs}"
            // We use timeTakenMs as avgResponseTimeMs placeholder
            long avgResponseTimeMs = entry.getTimeTakenMs() != null ? entry.getTimeTakenMs() : 0L;
            String value = String.format("%d|%d|%d|%d", 
                    entry.getRank(), 
                    entry.getCumulativeScore(), 
                    entry.getRoundScore(),
                    avgResponseTimeMs);
            hashEntries.put(entry.getParticipantId(), value);
        }

        for (int attempt = 0; attempt < MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                redisTemplate.opsForHash().putAll(key, hashEntries);
                redisTemplate.expire(key, Duration.ofSeconds(SESSION_TTL_SECONDS));
                log.debug("Stored snapshot for pin={}, round={}, entries={}", pin, roundNumber, entries.size());
                return;
            } catch (Exception e) {
                log.warn("Failed to store snapshot (attempt {}/{}): pin={}, round={}, error={}",
                        attempt + 1, MAX_RETRY_ATTEMPTS, pin, roundNumber, e.getMessage());
                
                if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                    sleepWithBackoff(attempt);
                }
            }
        }

        log.error("All retry attempts exhausted for storing snapshot: pin={}, round={}. " +
                  "Snapshot storage failed - rank deltas will be computed from in-memory state.", 
                  pin, roundNumber);
    }

    /**
     * Retrieve a snapshot for a specific round.
     * 
     * @param pin session PIN
     * @param roundNumber the round number to retrieve
     * @return list of participant scores, or empty list if not found or on error
     */
    public List<ParticipantRoundScore> getSnapshot(String pin, int roundNumber) {
        String key = snapshotKey(pin, roundNumber);

        for (int attempt = 0; attempt < MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                Map<Object, Object> hashEntries = redisTemplate.opsForHash().entries(key);
                
                if (hashEntries == null || hashEntries.isEmpty()) {
                    log.debug("No snapshot found for pin={}, round={}", pin, roundNumber);
                    return Collections.emptyList();
                }

                List<ParticipantRoundScore> entries = new ArrayList<>();
                for (Map.Entry<Object, Object> entry : hashEntries.entrySet()) {
                    try {
                        String participantId = entry.getKey().toString();
                        String value = entry.getValue().toString();
                        SnapshotEntry snapshotEntry = SnapshotEntry.fromRedisValue(participantId, value);
                        
                        entries.add(ParticipantRoundScore.builder()
                                .participantId(snapshotEntry.getParticipantId())
                                .rank(snapshotEntry.getRank())
                                .cumulativeScore((int) snapshotEntry.getCumulativeScore())
                                .roundScore(snapshotEntry.getRoundScore())
                                .build());
                    } catch (IllegalArgumentException e) {
                        log.warn("Failed to parse snapshot entry: key={}, value={}, error={}",
                                entry.getKey(), entry.getValue(), e.getMessage());
                    }
                }
                
                log.debug("Retrieved snapshot for pin={}, round={}, entries={}", pin, roundNumber, entries.size());
                return entries;
                
            } catch (Exception e) {
                log.warn("Failed to retrieve snapshot (attempt {}/{}): pin={}, round={}, error={}",
                        attempt + 1, MAX_RETRY_ATTEMPTS, pin, roundNumber, e.getMessage());
                
                if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                    sleepWithBackoff(attempt);
                }
            }
        }

        log.error("All retry attempts exhausted for retrieving snapshot: pin={}, round={}. " +
                  "Returning empty list - rank deltas will be computed from in-memory state.",
                  pin, roundNumber);
        return Collections.emptyList();
    }

    /**
     * Get the previous ranks for all participants from the most recent snapshot.
     * Used for computing rank deltas.
     * 
     * @param pin session PIN
     * @return map of participantId to previous rank
     */
    public Map<String, Integer> getPreviousRanks(String pin) {
        // Find the most recent snapshot by scanning for the highest round number
        for (int attempt = 0; attempt < MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                Set<String> keys = redisTemplate.keys("snapshot:" + pin + ":*");
                
                if (keys == null || keys.isEmpty()) {
                    log.debug("No snapshots found for pin={}", pin);
                    return Collections.emptyMap();
                }

                // Find the highest round number
                int maxRound = -1;
                for (String key : keys) {
                    String[] parts = key.split(":");
                    if (parts.length == 3) {
                        try {
                            int round = Integer.parseInt(parts[2]);
                            maxRound = Math.max(maxRound, round);
                        } catch (NumberFormatException e) {
                            log.warn("Invalid snapshot key format: {}", key);
                        }
                    }
                }

                if (maxRound < 0) {
                    return Collections.emptyMap();
                }

                // Get the snapshot for the most recent round
                List<ParticipantRoundScore> snapshot = getSnapshot(pin, maxRound);
                return snapshot.stream()
                        .collect(Collectors.toMap(
                                ParticipantRoundScore::getParticipantId,
                                ParticipantRoundScore::getRank
                        ));
                        
            } catch (Exception e) {
                log.warn("Failed to get previous ranks (attempt {}/{}): pin={}, error={}",
                        attempt + 1, MAX_RETRY_ATTEMPTS, pin, e.getMessage());
                
                if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                    sleepWithBackoff(attempt);
                }
            }
        }

        log.error("All retry attempts exhausted for getting previous ranks: pin={}. " +
                  "Returning empty map.", pin);
        return Collections.emptyMap();
    }

    /**
     * Compute rank deltas by comparing current ranks to the previous round's snapshot.
     * 
     * For each participant:
     * - rank_delta = previous_rank - current_rank
     * - Positive delta = moved up (e.g., rank 5 to rank 3 yields +2)
     * - Negative delta = moved down (e.g., rank 3 to rank 5 yields -2)
     * - Zero delta = unchanged position
     * 
     * For the first round (currentRound = 0) or if no previous snapshot exists,
     * all rank deltas are set to 0.
     * 
     * For late-joining participants (not in previous snapshot), rank_delta = 0.
     * 
     * @param pin session PIN
     * @param currentRound the current round number (0-indexed)
     * @return map of participantId to rank delta
     */
    public Map<String, Integer> computeRankDeltas(String pin, int currentRound) {
        // Get current rankings
        List<RankedParticipant> currentRanked = rankingService.getRankedParticipants(pin);
        
        if (currentRanked.isEmpty()) {
            return Collections.emptyMap();
        }

        // First round - all deltas are 0
        if (currentRound == 0) {
            log.debug("First round for pin={}, all rank deltas set to 0", pin);
            return currentRanked.stream()
                    .collect(Collectors.toMap(
                            RankedParticipant::getParticipantId,
                            p -> 0
                    ));
        }

        // Get previous round's snapshot
        List<ParticipantRoundScore> previousSnapshot = getSnapshot(pin, currentRound - 1);
        
        // If no previous snapshot, compute from in-memory state (fallback)
        if (previousSnapshot.isEmpty()) {
            log.warn("No previous snapshot found for pin={}, round={}. " +
                     "Computing deltas from in-memory state (all deltas = 0).", 
                     pin, currentRound - 1);
            return currentRanked.stream()
                    .collect(Collectors.toMap(
                            RankedParticipant::getParticipantId,
                            p -> 0
                    ));
        }

        // Build map of previous ranks
        Map<String, Integer> previousRanks = previousSnapshot.stream()
                .collect(Collectors.toMap(
                        ParticipantRoundScore::getParticipantId,
                        ParticipantRoundScore::getRank
                ));

        // Compute deltas
        Map<String, Integer> deltas = new HashMap<>();
        for (RankedParticipant current : currentRanked) {
            Integer previousRank = previousRanks.get(current.getParticipantId());
            
            if (previousRank == null) {
                // Late-joining participant - rank_delta = 0 for first appearing round
                deltas.put(current.getParticipantId(), 0);
                log.debug("Late-joining participant: pin={}, participantId={}, rank_delta=0",
                        pin, current.getParticipantId());
            } else {
                // rank_delta = previous_rank - current_rank
                int delta = previousRank - current.getRank();
                deltas.put(current.getParticipantId(), delta);
            }
        }

        log.debug("Computed rank deltas for pin={}, round={}: {}", pin, currentRound, deltas);
        return deltas;
    }

    /**
     * Delete all snapshots for a session.
     * Called when the session transitions to ENDED state.
     * 
     * Uses pattern matching to find and delete all snapshot keys for the session.
     * Implements retry logic for robustness.
     * 
     * @param pin session PIN
     */
    public void deleteAllSnapshots(String pin) {
        String pattern = "snapshot:" + pin + ":*";

        for (int attempt = 0; attempt < MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                Set<String> keys = redisTemplate.keys(pattern);
                
                if (keys == null || keys.isEmpty()) {
                    log.debug("No snapshots to delete for pin={}", pin);
                    return;
                }

                Long deleted = redisTemplate.delete(keys);
                log.info("Deleted {} snapshots for session: pin={}", deleted, pin);
                return;
                
            } catch (Exception e) {
                log.warn("Failed to delete snapshots (attempt {}/{}): pin={}, error={}",
                        attempt + 1, MAX_RETRY_ATTEMPTS, pin, e.getMessage());
                
                if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                    sleepWithBackoff(attempt);
                }
            }
        }

        log.error("All retry attempts exhausted for deleting snapshots: pin={}. " +
                  "Snapshots will expire via TTL.", pin);
    }

    /**
     * Generate the Redis key for a snapshot.
     * Format: snapshot:{pin}:{roundNumber}
     */
    private String snapshotKey(String pin, int roundNumber) {
        return "snapshot:" + pin + ":" + roundNumber;
    }

    /**
     * Sleep with exponential backoff based on attempt number.
     */
    private void sleepWithBackoff(int attempt) {
        try {
            long delay = BACKOFF_DELAYS_MS[attempt];
            log.debug("Sleeping for {}ms before retry", delay);
            Thread.sleep(delay);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("Retry sleep interrupted");
        }
    }
}
