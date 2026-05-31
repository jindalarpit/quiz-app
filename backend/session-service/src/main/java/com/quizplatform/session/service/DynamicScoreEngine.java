package com.quizplatform.session.service;

import com.quizplatform.session.dto.ParticipantRoundScore;
import com.quizplatform.session.dto.RoundResult;
import com.quizplatform.session.model.ScoringMode;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Orchestrates round scoring for dynamic scoring with time-weighted points.
 * 
 * This service:
 * - Retrieves all participant answers for a round from Redis
 * - Computes scores using ScoreCalculator with configurable time factor
 * - Updates cumulative scores atomically using Redis ZINCRBY
 * - Handles disconnected participants (round_score = 0)
 * - Returns RoundResult containing all participant scores and rank deltas
 * 
 * Integrates with LeaderboardSnapshotService and LeaderboardBroadcaster (wired in later tasks).
 * 
 * Requirements: 1.1, 4.1, 8.1, 8.6
 */
@Slf4j
@Service
public class DynamicScoreEngine {

    private static final long SESSION_TTL_SECONDS = 14400; // 4 hours
    private static final int DEFAULT_BASE_POINTS = 1000;
    /** Timeout threshold in milliseconds. If computation exceeds this, broadcast partial results. */
    static final long COMPUTATION_TIMEOUT_MS = 500;

    private final StringRedisTemplate redisTemplate;
    private final ScoreCalculator scoreCalculator;
    private final RankingService rankingService;
    private final MeterRegistry meterRegistry;
    private final Timer scoreComputationTimer;

    // Optional dependencies - will be wired in later tasks
    private LeaderboardSnapshotService leaderboardSnapshotService;
    private LeaderboardBroadcaster leaderboardBroadcaster;

    public DynamicScoreEngine(
            StringRedisTemplate redisTemplate,
            ScoreCalculator scoreCalculator,
            RankingService rankingService,
            MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.scoreCalculator = scoreCalculator;
        this.rankingService = rankingService;
        this.meterRegistry = meterRegistry;
        this.scoreComputationTimer = Timer.builder("dynamic.score.computation.duration")
                .description("Time taken to compute round scores")
                .tag("service", "session-service")
                .register(meterRegistry);
    }

    /**
     * Optional setter for LeaderboardSnapshotService (wired in later tasks).
     */
    @Autowired(required = false)
    public void setLeaderboardSnapshotService(LeaderboardSnapshotService leaderboardSnapshotService) {
        this.leaderboardSnapshotService = leaderboardSnapshotService;
    }

    /**
     * Optional setter for LeaderboardBroadcaster (wired in later tasks).
     */
    @Autowired(required = false)
    public void setLeaderboardBroadcaster(LeaderboardBroadcaster leaderboardBroadcaster) {
        this.leaderboardBroadcaster = leaderboardBroadcaster;
    }

    /**
     * Compute scores for all participants in a round and update the leaderboard.
     * Called when the host reveals the answer.
     *
     * Includes timing instrumentation via Micrometer. If computation exceeds 500ms,
     * a warning metric is logged and scores computed so far are broadcast without
     * blocking the session flow (Requirement 8.5).
     *
     * @param pin           session PIN
     * @param roundNumber   current round (0-indexed)
     * @param scoringMode   the quiz's configured scoring mode
     * @return RoundResult containing all participant scores and rank deltas
     */
    public RoundResult computeAndBroadcastRound(String pin, int roundNumber, ScoringMode scoringMode) {
        long startTime = System.currentTimeMillis();
        log.info("Computing scores for round {} in session {}", roundNumber, pin);

        // Get question timing information
        long questionStartTime = getQuestionStartTime(pin);
        int questionDurationMs = getQuestionDurationMs(pin);

        // Get the correct answer for this round
        String correctAnswer = getCorrectAnswer(pin, roundNumber);

        // Get all participant answers for this round
        Map<Object, Object> answers = getAnswersForQuestion(pin, roundNumber);

        // Get all participants from the leaderboard
        Set<String> allParticipants = getAllParticipantIds(pin);

        // Compute scores for each participant with timeout awareness
        List<ParticipantRoundScore> participantScores = new ArrayList<>();
        int totalAnswered = 0;
        int totalCorrect = 0;
        boolean timedOut = false;

        for (String participantId : allParticipants) {
            // Check if we've exceeded the timeout threshold
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed > COMPUTATION_TIMEOUT_MS) {
                timedOut = true;
                meterRegistry.counter("dynamic.score.computation.timeout",
                        "session", pin, "round", String.valueOf(roundNumber)).increment();
                log.warn("Score computation exceeded {}ms for session {} at participant {}/{}: {}ms elapsed. " +
                         "Broadcasting partial results for {} participants computed so far.",
                         COMPUTATION_TIMEOUT_MS, pin, participantScores.size(), allParticipants.size(),
                         elapsed, participantScores.size());
                break;
            }

            ParticipantRoundScore score = computeParticipantScore(
                    pin, participantId, roundNumber, answers, correctAnswer,
                    questionStartTime, questionDurationMs, scoringMode);
            participantScores.add(score);

            if (score.getTimeTakenMs() != null) {
                totalAnswered++;
            }
            if (score.isCorrect()) {
                totalCorrect++;
            }
        }

        // Update cumulative scores atomically using Redis ZINCRBY
        updateCumulativeScores(pin, participantScores);

        // Get updated rankings with rank deltas
        List<ParticipantRoundScore> rankedScores = computeRankingsAndDeltas(pin, participantScores);

        // Calculate accuracy rate
        double accuracyRate = totalAnswered > 0 ? (double) totalCorrect / totalAnswered : 0.0;

        long computationTimeMs = System.currentTimeMillis() - startTime;

        // Record the computation duration in Micrometer timer
        scoreComputationTimer.record(computationTimeMs, TimeUnit.MILLISECONDS);

        // Build the round result
        RoundResult result = RoundResult.builder()
                .pin(pin)
                .roundNumber(roundNumber)
                .timestamp(Instant.now())
                .participantScores(rankedScores)
                .correctAnswer(correctAnswer)
                .totalAnswered(totalAnswered)
                .totalCorrect(totalCorrect)
                .accuracyRate(accuracyRate)
                .computationTimeMs(computationTimeMs)
                .build();

        // Log performance metrics
        if (computationTimeMs > COMPUTATION_TIMEOUT_MS) {
            log.warn("Score computation exceeded {}ms for session {}: {}ms (timedOut={})",
                     COMPUTATION_TIMEOUT_MS, pin, computationTimeMs, timedOut);
            meterRegistry.counter("dynamic.score.computation.slow",
                    "session", pin).increment();
        } else {
            log.info("Score computation completed for session {} in {}ms", pin, computationTimeMs);
        }

        // Store snapshot and broadcast (if services are wired)
        // These operations should not block the session flow
        if (leaderboardSnapshotService != null) {
            try {
                leaderboardSnapshotService.storeSnapshot(pin, roundNumber, rankedScores);
            } catch (Exception e) {
                log.error("Failed to store leaderboard snapshot for session {}: {}", pin, e.getMessage());
            }
        }

        if (leaderboardBroadcaster != null) {
            try {
                leaderboardBroadcaster.broadcastLeaderboardUpdate(pin, result);
            } catch (Exception e) {
                log.error("Failed to broadcast leaderboard update for session {}: {}", pin, e.getMessage());
            }

            try {
                leaderboardBroadcaster.publishScoreEvent(pin, result);
            } catch (Exception e) {
                log.error("Failed to publish score event for session {}: {}", pin, e.getMessage());
            }
        }

        return result;
    }

    /**
     * Compute the score for a single participant in a round.
     */
    private ParticipantRoundScore computeParticipantScore(
            String pin,
            String participantId,
            int roundNumber,
            Map<Object, Object> answers,
            String correctAnswer,
            long questionStartTime,
            int questionDurationMs,
            ScoringMode scoringMode) {

        String nickname = getParticipantNickname(pin, participantId);
        int currentStreak = getStreak(pin, participantId);
        int currentMultiplier = getMultiplier(pin, participantId);
        boolean isConnected = isParticipantConnected(pin, participantId);

        // Check if participant submitted an answer
        Object answerValue = answers.get(participantId);

        if (answerValue == null) {
            // Disconnected or didn't answer - round_score = 0
            // Reset streak for unanswered questions
            resetStreak(pin, participantId);

            return ParticipantRoundScore.builder()
                    .participantId(participantId)
                    .nickname(nickname != null ? nickname : participantId)
                    .roundScore(0)
                    .cumulativeScore(getCumulativeScore(pin, participantId))
                    .rank(0) // Will be set after ranking
                    .rankDelta(0) // Will be computed after ranking
                    .streakCount(0)
                    .streakMultiplier(1)
                    .timeTakenMs(null)
                    .correct(false)
                    .connected(isConnected)
                    .build();
        }

        // Parse the answer value: {answer}|{timestamp}|{score}
        String[] parts = answerValue.toString().split("\\|");
        String submittedAnswer = parts.length >= 1 ? parts[0] : "";
        long submissionTimestamp = parts.length >= 2 ? Long.parseLong(parts[1]) : 0;

        // Check if answer is correct
        boolean isCorrect = correctAnswer != null && submittedAnswer.equalsIgnoreCase(correctAnswer);

        // Calculate time taken
        Long timeTakenMs = questionStartTime > 0 ? submissionTimestamp - questionStartTime : null;

        int roundScore = 0;
        int newStreak = currentStreak;
        int newMultiplier = currentMultiplier;

        if (isCorrect) {
            // Increment streak
            newStreak = currentStreak + 1;
            newMultiplier = scoreCalculator.getStreakMultiplier(newStreak);

            // Calculate score with time factor
            if (timeTakenMs != null && questionDurationMs > 0) {
                roundScore = scoreCalculator.calculateScoreWithTimeFactor(
                        DEFAULT_BASE_POINTS,
                        timeTakenMs,
                        questionDurationMs,
                        scoringMode.getTimeFactor(),
                        newStreak);
            } else {
                // Edge case: no timing info, award full base points with streak
                roundScore = scoreCalculator.calculateScoreForZeroTimeLimit(DEFAULT_BASE_POINTS, newStreak);
            }

            // Update streak in Redis
            updateStreak(pin, participantId, newStreak, newMultiplier);

            // Record response time for ranking tiebreakers
            if (timeTakenMs != null) {
                rankingService.recordResponseTime(pin, participantId, timeTakenMs, submissionTimestamp);
            }
        } else {
            // Incorrect answer - reset streak
            newStreak = 0;
            newMultiplier = 1;
            resetStreak(pin, participantId);
        }

        return ParticipantRoundScore.builder()
                .participantId(participantId)
                .nickname(nickname != null ? nickname : participantId)
                .roundScore(roundScore)
                .cumulativeScore(getCumulativeScore(pin, participantId)) // Will be updated after ZINCRBY
                .rank(0) // Will be set after ranking
                .rankDelta(0) // Will be computed after ranking
                .streakCount(newStreak)
                .streakMultiplier(newMultiplier)
                .timeTakenMs(timeTakenMs)
                .correct(isCorrect)
                .connected(isConnected)
                .build();
    }

    /**
     * Update cumulative scores atomically using Redis ZINCRBY.
     * This ensures concurrent score accumulation integrity (Requirement 8.6).
     */
    private void updateCumulativeScores(String pin, List<ParticipantRoundScore> participantScores) {
        String leaderboardKey = leaderboardKey(pin);

        for (ParticipantRoundScore score : participantScores) {
            // Read the cumulative score (already incremented by AnswerService at submission time)
            int cumulativeScore = getCumulativeScore(pin, score.getParticipantId());
            score.setCumulativeScore(cumulativeScore);

            // Update leaderboard with composite score for proper ranking (includes tiebreaker)
            rankingService.updateLeaderboardWithCompositeScore(
                    pin, score.getParticipantId(), cumulativeScore);
        }

        // Ensure TTL is maintained
        redisTemplate.expire(leaderboardKey, Duration.ofSeconds(SESSION_TTL_SECONDS));
    }

    /**
     * Compute rankings and rank deltas for all participants.
     */
    private List<ParticipantRoundScore> computeRankingsAndDeltas(
            String pin, List<ParticipantRoundScore> participantScores) {

        // Get previous ranks from snapshot service (if available)
        Map<String, Integer> previousRanks = new HashMap<>();
        if (leaderboardSnapshotService != null) {
            try {
                previousRanks = leaderboardSnapshotService.getPreviousRanks(pin);
            } catch (Exception e) {
                log.warn("Failed to get previous ranks for session {}: {}", pin, e.getMessage());
            }
        }

        // Get current rankings from RankingService
        var rankedParticipants = rankingService.getRankedParticipants(pin);

        // Build a map of participantId to rank
        Map<String, Integer> currentRanks = new HashMap<>();
        for (var ranked : rankedParticipants) {
            currentRanks.put(ranked.getParticipantId(), ranked.getRank());
        }

        // Update participant scores with ranks and deltas
        for (ParticipantRoundScore score : participantScores) {
            Integer currentRank = currentRanks.get(score.getParticipantId());
            if (currentRank != null) {
                score.setRank(currentRank);

                // Compute rank delta
                Integer previousRank = previousRanks.get(score.getParticipantId());
                if (previousRank != null) {
                    // rank_delta = previous_rank - current_rank
                    // Positive = moved up, negative = moved down
                    score.setRankDelta(previousRank - currentRank);
                } else {
                    // Late-joining participant or first round - rank_delta = 0
                    score.setRankDelta(0);
                }
            }
        }

        // Sort by rank ascending
        participantScores.sort(Comparator.comparingInt(ParticipantRoundScore::getRank));

        return participantScores;
    }

    // ==================== Redis Helper Methods ====================

    private long getQuestionStartTime(String pin) {
        Object value = redisTemplate.opsForHash().get(sessionKey(pin), "question_start_time");
        return value != null ? Long.parseLong(value.toString()) : 0;
    }

    private int getQuestionDurationMs(String pin) {
        Object value = redisTemplate.opsForHash().get(sessionKey(pin), "question_duration_ms");
        return value != null ? Integer.parseInt(value.toString()) : 20000;
    }

    private String getCorrectAnswer(String pin, int roundNumber) {
        Object value = redisTemplate.opsForHash().get(correctAnswersKey(pin), String.valueOf(roundNumber));
        return value != null ? value.toString() : null;
    }

    private Map<Object, Object> getAnswersForQuestion(String pin, int roundNumber) {
        return redisTemplate.opsForHash().entries(answersKey(pin, roundNumber));
    }

    private Set<String> getAllParticipantIds(String pin) {
        Set<String> members = redisTemplate.opsForZSet().range(leaderboardKey(pin), 0, -1);
        return members != null ? members : Collections.emptySet();
    }

    private String getParticipantNickname(String pin, String participantId) {
        Object nickname = redisTemplate.opsForHash().get(participantKey(pin, participantId), "nickname");
        return nickname != null ? nickname.toString() : null;
    }

    private int getStreak(String pin, String participantId) {
        Object streak = redisTemplate.opsForHash().get(participantKey(pin, participantId), "streak");
        return streak != null ? Integer.parseInt(streak.toString()) : 0;
    }

    private int getMultiplier(String pin, String participantId) {
        Object multiplier = redisTemplate.opsForHash().get(participantKey(pin, participantId), "multiplier");
        return multiplier != null ? Integer.parseInt(multiplier.toString()) : 1;
    }

    private void updateStreak(String pin, String participantId, int streak, int multiplier) {
        String key = participantKey(pin, participantId);
        redisTemplate.opsForHash().put(key, "streak", String.valueOf(streak));
        redisTemplate.opsForHash().put(key, "multiplier", String.valueOf(multiplier));
    }

    private void resetStreak(String pin, String participantId) {
        String key = participantKey(pin, participantId);
        redisTemplate.opsForHash().put(key, "streak", "0");
        redisTemplate.opsForHash().put(key, "multiplier", "1");
    }

    private boolean isParticipantConnected(String pin, String participantId) {
        Object value = redisTemplate.opsForHash().get(participantKey(pin, participantId), "is_connected");
        return "true".equals(value);
    }

    private int getCumulativeScore(String pin, String participantId) {
        Double score = redisTemplate.opsForZSet().score(leaderboardKey(pin), participantId);
        if (score == null) {
            return 0;
        }
        // Extract cumulative score from composite score
        return (int) (score / RankingService.SCORE_MULTIPLIER);
    }

    // ==================== Redis Key Helpers ====================

    private String sessionKey(String pin) {
        return "session:" + pin;
    }

    private String participantKey(String pin, String participantId) {
        return "participant:" + pin + ":" + participantId;
    }

    private String answersKey(String pin, int roundNumber) {
        return "answers:" + pin + ":" + roundNumber;
    }

    private String correctAnswersKey(String pin) {
        return "correct_answers:" + pin;
    }

    private String leaderboardKey(String pin) {
        return "leaderboard:" + pin;
    }
}
