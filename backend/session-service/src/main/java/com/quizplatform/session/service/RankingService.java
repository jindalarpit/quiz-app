package com.quizplatform.session.service;

import com.quizplatform.session.dto.RankedParticipant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for computing participant rankings with tiebreakers.
 * 
 * Ranking order:
 * 1. Cumulative score (descending - higher is better)
 * 2. Average response time (ascending - faster is better), computed as total_response_time_ms / answered_rounds
 * 3. Most recent answer timestamp (ascending - earlier is better)
 * 
 * Uses Redis composite score encoding for efficient sorting:
 * compositeScore = cumulativeScore × 1_000_000 + (MAX_TIME - avgResponseTimeMs)
 * 
 * This allows a single ZREVRANGEBYSCORE to produce correct ordering for primary and first tiebreaker.
 * The second tiebreaker (most recent answer timestamp) is resolved in-memory for ties at same composite score.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RankingService {

    private static final long SESSION_TTL_SECONDS = 14400; // 4 hours
    
    /**
     * MAX_TIME constant for composite score calculation.
     * This ensures proper ordering when subtracting avgResponseTimeMs.
     * Using 999_999_999 to fit within the composite score encoding.
     */
    public static final long MAX_TIME = 999_999_999L;
    
    /**
     * Multiplier for cumulative score in composite score encoding.
     * Ensures cumulative score is the primary sort key.
     * Must be greater than MAX_TIME to prevent tiebreaker overflow into cumulative score space.
     */
    public static final long SCORE_MULTIPLIER = 1_000_000_000L;

    private final StringRedisTemplate redisTemplate;

    /**
     * Update participant's response time tracking after answering a question.
     * 
     * @param pin session PIN
     * @param participantId participant UUID
     * @param responseTimeMs response time for this answer in milliseconds
     * @param answerTimestamp timestamp when the answer was submitted
     */
    public void recordResponseTime(String pin, String participantId, long responseTimeMs, long answerTimestamp) {
        String participantKey = participantKey(pin, participantId);
        
        // Increment total_response_time_ms
        redisTemplate.opsForHash().increment(participantKey, "total_response_time_ms", responseTimeMs);
        
        // Increment answered_rounds
        redisTemplate.opsForHash().increment(participantKey, "answered_rounds", 1);
        
        // Update last_answer_time for tiebreaker
        redisTemplate.opsForHash().put(participantKey, "last_answer_time", String.valueOf(answerTimestamp));
        
        // Ensure TTL is maintained
        redisTemplate.expire(participantKey, Duration.ofSeconds(SESSION_TTL_SECONDS));
    }

    /**
     * Get the total response time for a participant.
     */
    public long getTotalResponseTimeMs(String pin, String participantId) {
        Object value = redisTemplate.opsForHash().get(participantKey(pin, participantId), "total_response_time_ms");
        return value != null ? Long.parseLong(value.toString()) : 0;
    }

    /**
     * Get the number of answered rounds for a participant.
     */
    public int getAnsweredRounds(String pin, String participantId) {
        Object value = redisTemplate.opsForHash().get(participantKey(pin, participantId), "answered_rounds");
        return value != null ? Integer.parseInt(value.toString()) : 0;
    }

    /**
     * Get the last answer timestamp for a participant.
     */
    public long getLastAnswerTime(String pin, String participantId) {
        Object value = redisTemplate.opsForHash().get(participantKey(pin, participantId), "last_answer_time");
        return value != null ? Long.parseLong(value.toString()) : 0;
    }

    /**
     * Calculate the average response time for a participant.
     * Returns MAX_TIME if participant has not answered any questions (for proper tiebreaker ordering).
     */
    public long calculateAverageResponseTimeMs(String pin, String participantId) {
        long totalResponseTime = getTotalResponseTimeMs(pin, participantId);
        int answeredRounds = getAnsweredRounds(pin, participantId);
        
        if (answeredRounds == 0) {
            return MAX_TIME; // Late-joining participants get worst average time
        }
        
        return totalResponseTime / answeredRounds;
    }

    /**
     * Calculate the composite score for Redis sorted set.
     * Formula: cumulativeScore × 1_000_000 + (MAX_TIME - avgResponseTimeMs)
     * 
     * This encoding ensures:
     * - Higher cumulative scores rank higher (primary sort)
     * - Among equal scores, lower avg response times rank higher (first tiebreaker)
     */
    public double calculateCompositeScore(long cumulativeScore, long avgResponseTimeMs) {
        // Clamp avgResponseTimeMs to valid range
        long clampedAvgTime = Math.max(0, Math.min(avgResponseTimeMs, MAX_TIME));
        
        return (double) (cumulativeScore * SCORE_MULTIPLIER + (MAX_TIME - clampedAvgTime));
    }

    /**
     * Update the leaderboard with composite score for a participant.
     * 
     * @param pin session PIN
     * @param participantId participant UUID
     * @param cumulativeScore the participant's cumulative score
     */
    public void updateLeaderboardWithCompositeScore(String pin, String participantId, long cumulativeScore) {
        long avgResponseTimeMs = calculateAverageResponseTimeMs(pin, participantId);
        double compositeScore = calculateCompositeScore(cumulativeScore, avgResponseTimeMs);
        
        String leaderboardKey = leaderboardKey(pin);
        redisTemplate.opsForZSet().add(leaderboardKey, participantId, compositeScore);
        redisTemplate.expire(leaderboardKey, Duration.ofSeconds(SESSION_TTL_SECONDS));
    }

    /**
     * Get all participants ranked with full tiebreaker resolution.
     * 
     * Uses Redis ZREVRANGE to get participants sorted by composite score,
     * then resolves ties at the same composite score using the second tiebreaker
     * (most recent answer timestamp) in-memory.
     * 
     * @param pin session PIN
     * @return list of ranked participants in order
     */
    public List<RankedParticipant> getRankedParticipants(String pin) {
        String leaderboardKey = leaderboardKey(pin);
        
        // Get all participants with their composite scores
        Set<ZSetOperations.TypedTuple<String>> results = 
                redisTemplate.opsForZSet().reverseRangeWithScores(leaderboardKey, 0, -1);
        
        if (results == null || results.isEmpty()) {
            return Collections.emptyList();
        }
        
        // Build list of participants with all ranking data
        List<RankedParticipant> participants = new ArrayList<>();
        for (ZSetOperations.TypedTuple<String> tuple : results) {
            String participantId = tuple.getValue();
            double compositeScore = tuple.getScore() != null ? tuple.getScore() : 0;
            
            // Extract cumulative score from composite score
            long cumulativeScore = (long) (compositeScore / SCORE_MULTIPLIER);
            
            // Get additional data for tiebreaking
            long avgResponseTimeMs = calculateAverageResponseTimeMs(pin, participantId);
            long lastAnswerTime = getLastAnswerTime(pin, participantId);
            String nickname = getParticipantNickname(pin, participantId);
            
            participants.add(RankedParticipant.builder()
                    .participantId(participantId)
                    .nickname(nickname != null ? nickname : participantId)
                    .cumulativeScore(cumulativeScore)
                    .compositeScore(compositeScore)
                    .avgResponseTimeMs(avgResponseTimeMs)
                    .lastAnswerTime(lastAnswerTime)
                    .build());
        }
        
        // Sort with full tiebreaker resolution
        // Redis already sorted by composite score (cumulative score + avg response time)
        // Now resolve ties at same composite score using last answer timestamp
        participants.sort(getRankingComparator());
        
        // Assign ranks (1-indexed)
        for (int i = 0; i < participants.size(); i++) {
            participants.get(i).setRank(i + 1);
        }
        
        return participants;
    }

    /**
     * Get the comparator for full ranking with all tiebreakers.
     * 
     * Order:
     * 1. Cumulative score (descending)
     * 2. Average response time (ascending)
     * 3. Most recent answer timestamp (ascending)
     */
    public Comparator<RankedParticipant> getRankingComparator() {
        return Comparator
                // Primary: cumulative score descending (higher is better)
                .comparingLong(RankedParticipant::getCumulativeScore).reversed()
                // First tiebreaker: avg response time ascending (faster is better)
                .thenComparingLong(RankedParticipant::getAvgResponseTimeMs)
                // Second tiebreaker: last answer timestamp ascending (earlier is better)
                .thenComparingLong(RankedParticipant::getLastAnswerTime);
    }

    /**
     * Get the rank of a specific participant with full tiebreaker resolution.
     * 
     * @param pin session PIN
     * @param participantId participant UUID
     * @return 1-indexed rank, or -1 if participant not found
     */
    public int getParticipantRank(String pin, String participantId) {
        List<RankedParticipant> ranked = getRankedParticipants(pin);
        
        for (RankedParticipant p : ranked) {
            if (p.getParticipantId().equals(participantId)) {
                return p.getRank();
            }
        }
        
        return -1;
    }

    /**
     * Handle late-joining participant ranking.
     * 
     * Late-joining participants (those who haven't answered any questions yet) are assigned:
     * - The lowest rank among participants with equal cumulative score (0)
     * - rank_delta = 0 for their first appearing round
     * 
     * This is achieved by:
     * - Setting avgResponseTimeMs to MAX_TIME (worst possible)
     * - Setting lastAnswerTime to Long.MAX_VALUE (worst possible)
     * 
     * @param pin session PIN
     * @param participantId participant UUID
     */
    public void initializeLateJoiningParticipant(String pin, String participantId) {
        String participantKey = participantKey(pin, participantId);
        
        // Initialize response time tracking fields
        redisTemplate.opsForHash().putIfAbsent(participantKey, "total_response_time_ms", "0");
        redisTemplate.opsForHash().putIfAbsent(participantKey, "answered_rounds", "0");
        redisTemplate.opsForHash().putIfAbsent(participantKey, "last_answer_time", String.valueOf(Long.MAX_VALUE));
        
        // Add to leaderboard with score 0 and worst tiebreaker values
        updateLeaderboardWithCompositeScore(pin, participantId, 0);
    }

    /**
     * Compute rank deltas for all participants by comparing current ranks to previous snapshot.
     * 
     * @param pin session PIN
     * @param previousRanks map of participantId to previous rank
     * @return map of participantId to rank delta (positive = moved up, negative = moved down)
     */
    public Map<String, Integer> computeRankDeltas(String pin, Map<String, Integer> previousRanks) {
        List<RankedParticipant> currentRanked = getRankedParticipants(pin);
        Map<String, Integer> deltas = new HashMap<>();
        
        for (RankedParticipant p : currentRanked) {
            Integer previousRank = previousRanks.get(p.getParticipantId());
            
            if (previousRank == null) {
                // Late-joining participant - rank_delta = 0 for first round
                deltas.put(p.getParticipantId(), 0);
            } else {
                // rank_delta = previous_rank - current_rank
                // Positive = moved up, negative = moved down
                deltas.put(p.getParticipantId(), previousRank - p.getRank());
            }
        }
        
        return deltas;
    }

    /**
     * Get the top N ranked participants.
     */
    public List<RankedParticipant> getTopN(String pin, int n) {
        List<RankedParticipant> all = getRankedParticipants(pin);
        return all.stream().limit(n).collect(Collectors.toList());
    }

    private String participantKey(String pin, String participantId) {
        return "participant:" + pin + ":" + participantId;
    }

    private String leaderboardKey(String pin) {
        return "leaderboard:" + pin;
    }

    private String getParticipantNickname(String pin, String participantId) {
        Object nickname = redisTemplate.opsForHash().get(participantKey(pin, participantId), "nickname");
        return nickname != null ? nickname.toString() : null;
    }
}
