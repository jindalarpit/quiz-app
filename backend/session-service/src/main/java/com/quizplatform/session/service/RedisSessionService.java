package com.quizplatform.session.service;

import com.quizplatform.session.dto.LeaderboardEntry;
import com.quizplatform.session.dto.ParticipantLeaderboardView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisSessionService {

    private static final long SESSION_TTL_SECONDS = 14400; // 4 hours
    private static final int MAX_PIN_ATTEMPTS = 10;
    private static final String PIN_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int PIN_LENGTH = 6;

    private final StringRedisTemplate redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generate a unique 6-character alphanumeric PIN that doesn't collide with existing sessions.
     */
    public String generateUniquePin() {
        for (int attempt = 0; attempt < MAX_PIN_ATTEMPTS; attempt++) {
            String pin = generateRandomPin();
            String key = sessionKey(pin);
            if (Boolean.FALSE.equals(redisTemplate.hasKey(key))) {
                return pin;
            }
            log.debug("PIN collision detected for {}, retrying (attempt {})", pin, attempt + 1);
        }
        throw new RuntimeException("Failed to generate unique PIN after " + MAX_PIN_ATTEMPTS + " attempts");
    }

    /**
     * Create a new session in Redis with all initial fields.
     */
    public void createSession(String pin, UUID quizId, UUID hostId, Map<String, Object> settings) {
        createSession(pin, quizId, hostId, settings, null);
    }

    /**
     * Create a new session in Redis with all initial fields including scoring mode.
     * The scoring mode is loaded from the quiz configuration and remains fixed for the entire session duration.
     *
     * @param pin       session PIN
     * @param quizId    quiz UUID
     * @param hostId    host UUID
     * @param settings  session settings
     * @param scoringMode the quiz's configured scoring mode (stored as scoring_mode field)
     */
    public void createSession(String pin, UUID quizId, UUID hostId, Map<String, Object> settings, String scoringMode) {
        String key = sessionKey(pin);

        Map<String, String> fields = new HashMap<>();
        fields.put("state", "LOBBY");
        fields.put("quiz_id", quizId.toString());
        fields.put("host_id", hostId.toString());
        fields.put("current_question_index", "0");
        fields.put("question_start_time", "0");
        fields.put("question_duration_ms", "20000");
        fields.put("participant_count", "0");
        fields.put("previous_state", "");
        fields.put("allow_late_join", getSettingOrDefault(settings, "allowLateJoin", "true"));
        fields.put("music_enabled", getSettingOrDefault(settings, "musicEnabled", "true"));
        fields.put("created_at", String.valueOf(Instant.now().toEpochMilli()));
        // Store scoring mode - fixed for entire session duration (Requirement 7.7)
        fields.put("scoring_mode", scoringMode != null ? scoringMode : "SPEED_MATTERS");

        redisTemplate.opsForHash().putAll(key, fields);
        redisTemplate.expire(key, Duration.ofSeconds(SESSION_TTL_SECONDS));

        // Initialize nicknames set
        String nicknamesKey = nicknamesKey(pin);
        redisTemplate.expire(nicknamesKey, Duration.ofSeconds(SESSION_TTL_SECONDS));
    }

    /**
     * Get the scoring mode for a session from the Redis session hash.
     * The scoring mode is set once during session creation and remains fixed.
     *
     * @param pin session PIN
     * @return the scoring mode string, defaults to "SPEED_MATTERS" if not set
     */
    public String getScoringMode(String pin) {
        Object value = redisTemplate.opsForHash().get(sessionKey(pin), "scoring_mode");
        return value != null ? value.toString() : "SPEED_MATTERS";
    }

    /**
     * Get the current session state from Redis.
     */
    public String getSessionState(String pin) {
        Object state = redisTemplate.opsForHash().get(sessionKey(pin), "state");
        return state != null ? state.toString() : null;
    }

    /**
     * Get the host ID for a session.
     */
    public String getHostId(String pin) {
        Object hostId = redisTemplate.opsForHash().get(sessionKey(pin), "host_id");
        return hostId != null ? hostId.toString() : null;
    }

    /**
     * Get the previous state (used for pause/resume).
     */
    public String getPreviousState(String pin) {
        Object previousState = redisTemplate.opsForHash().get(sessionKey(pin), "previous_state");
        return previousState != null ? previousState.toString() : null;
    }

    /**
     * Update the session state in Redis.
     */
    public void updateSessionState(String pin, String newState) {
        redisTemplate.opsForHash().put(sessionKey(pin), "state", newState);
    }

    /**
     * Store the previous state before pausing.
     */
    public void storePreviousState(String pin, String previousState) {
        redisTemplate.opsForHash().put(sessionKey(pin), "previous_state", previousState);
    }

    /**
     * Add a participant to the session in Redis.
     */
    public void addParticipant(String pin, UUID participantId, String nickname) {
        String participantKey = participantKey(pin, participantId);

        Map<String, String> fields = new HashMap<>();
        fields.put("nickname", nickname);
        fields.put("score", "0");
        fields.put("streak", "0");
        fields.put("multiplier", "1");
        fields.put("last_answer_time", String.valueOf(Long.MAX_VALUE)); // For late-joining tiebreaker
        fields.put("is_connected", "false");
        fields.put("disconnect_time", "0");
        fields.put("is_flagged", "false");
        fields.put("is_kicked", "false");
        fields.put("fast_answer_count", "0");
        // New fields for ranking tiebreakers (Requirement 4.6, 4.7)
        fields.put("total_response_time_ms", "0");
        fields.put("answered_rounds", "0");

        redisTemplate.opsForHash().putAll(participantKey, fields);
        redisTemplate.expire(participantKey, Duration.ofSeconds(SESSION_TTL_SECONDS));

        // Add participant to leaderboard sorted set with initial composite score
        // Composite score = cumulativeScore × 1_000_000 + (MAX_TIME - avgResponseTimeMs)
        // For new participants: score=0, avgResponseTime=MAX_TIME (worst), so composite = 0
        String leaderboardKeyStr = leaderboardKey(pin);
        redisTemplate.opsForZSet().add(leaderboardKeyStr, participantId.toString(), 0);
        redisTemplate.expire(leaderboardKeyStr, Duration.ofSeconds(SESSION_TTL_SECONDS));

        // Add nickname to the nicknames set (lowercase for case-insensitive uniqueness)
        redisTemplate.opsForSet().add(nicknamesKey(pin), nickname.toLowerCase());
        redisTemplate.expire(nicknamesKey(pin), Duration.ofSeconds(SESSION_TTL_SECONDS));

        // Increment participant count
        redisTemplate.opsForHash().increment(sessionKey(pin), "participant_count", 1);
    }

    /**
     * Get the current participant count for a session.
     */
    public int getParticipantCount(String pin) {
        Object count = redisTemplate.opsForHash().get(sessionKey(pin), "participant_count");
        return count != null ? Integer.parseInt(count.toString()) : 0;
    }

    /**
     * Check if a nickname is already taken in the session (case-insensitive).
     */
    public boolean isNicknameTaken(String pin, String nickname) {
        return Boolean.TRUE.equals(
                redisTemplate.opsForSet().isMember(nicknamesKey(pin), nickname.toLowerCase())
        );
    }

    /**
     * Check if a session exists in Redis.
     */
    public boolean sessionExists(String pin) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(sessionKey(pin)));
    }

    /**
     * Get the current question index.
     */
    public int getCurrentQuestionIndex(String pin) {
        Object index = redisTemplate.opsForHash().get(sessionKey(pin), "current_question_index");
        return index != null ? Integer.parseInt(index.toString()) : 0;
    }

    /**
     * Increment the current question index.
     */
    public void incrementQuestionIndex(String pin) {
        redisTemplate.opsForHash().increment(sessionKey(pin), "current_question_index", 1);
    }

    /**
     * Get the allow_late_join setting.
     */
    public boolean isLateJoinAllowed(String pin) {
        Object value = redisTemplate.opsForHash().get(sessionKey(pin), "allow_late_join");
        return value != null && "true".equals(value.toString());
    }

    /**
     * Get all session fields from Redis.
     */
    public Map<Object, Object> getSessionFields(String pin) {
        return redisTemplate.opsForHash().entries(sessionKey(pin));
    }

    // ==================== Answer & Leaderboard Methods ====================

    /**
     * Get the question start time from the session hash.
     */
    public long getQuestionStartTime(String pin) {
        Object value = redisTemplate.opsForHash().get(sessionKey(pin), "question_start_time");
        return value != null ? Long.parseLong(value.toString()) : 0;
    }

    /**
     * Get the question duration in milliseconds from the session hash.
     */
    public int getQuestionDurationMs(String pin) {
        Object value = redisTemplate.opsForHash().get(sessionKey(pin), "question_duration_ms");
        return value != null ? Integer.parseInt(value.toString()) : 20000;
    }

    /**
     * Set the question start time and duration when a question is opened.
     */
    public void setQuestionTiming(String pin, long startTimeMs, int durationMs) {
        redisTemplate.opsForHash().put(sessionKey(pin), "question_start_time", String.valueOf(startTimeMs));
        redisTemplate.opsForHash().put(sessionKey(pin), "question_duration_ms", String.valueOf(durationMs));
    }

    /**
     * Attempt to store an answer atomically using HSETNX (prevents duplicates).
     *
     * @return true if the answer was stored (first submission), false if already submitted
     */
    public boolean storeAnswerIfAbsent(String pin, int questionIndex, String participantId, String answerValue) {
        String key = answersKey(pin, questionIndex);
        Boolean result = redisTemplate.opsForHash().putIfAbsent(key, participantId, answerValue);
        if (Boolean.TRUE.equals(result)) {
            redisTemplate.expire(key, Duration.ofSeconds(SESSION_TTL_SECONDS));
            return true;
        }
        return false;
    }

    /**
     * Get all answers for a specific question.
     */
    public Map<Object, Object> getAnswersForQuestion(String pin, int questionIndex) {
        return redisTemplate.opsForHash().entries(answersKey(pin, questionIndex));
    }

    /**
     * Store the correct answer for a question index.
     */
    public void storeCorrectAnswer(String pin, int questionIndex, String correctAnswer) {
        String key = correctAnswersKey(pin);
        redisTemplate.opsForHash().put(key, String.valueOf(questionIndex), correctAnswer);
        redisTemplate.expire(key, Duration.ofSeconds(SESSION_TTL_SECONDS));
    }

    /**
     * Get the correct answer for a question index.
     */
    public String getCorrectAnswer(String pin, int questionIndex) {
        Object value = redisTemplate.opsForHash().get(correctAnswersKey(pin), String.valueOf(questionIndex));
        return value != null ? value.toString() : null;
    }

    /**
     * Update the leaderboard score for a participant using ZINCRBY.
     */
    public void incrementLeaderboardScore(String pin, String participantId, long scoreToAdd) {
        String key = leaderboardKey(pin);
        redisTemplate.opsForZSet().incrementScore(key, participantId, scoreToAdd);
        redisTemplate.expire(key, Duration.ofSeconds(SESSION_TTL_SECONDS));
    }

    /**
     * Get the top N participants from the leaderboard.
     * Extracts the actual cumulative score from the composite score before returning to clients.
     */
    public List<LeaderboardEntry> getTopN(String pin, int n) {
        String key = leaderboardKey(pin);
        Set<ZSetOperations.TypedTuple<String>> results =
                redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, n - 1);

        if (results == null || results.isEmpty()) {
            return Collections.emptyList();
        }

        List<LeaderboardEntry> entries = new ArrayList<>();
        long rank = 1;
        for (ZSetOperations.TypedTuple<String> tuple : results) {
            String participantId = tuple.getValue();
            double rawScore = tuple.getScore() != null ? tuple.getScore() : 0;
            // Extract cumulative score from composite score (composite = cumulative × SCORE_MULTIPLIER + tiebreaker)
            long cumulativeScore = (long) (rawScore / RankingService.SCORE_MULTIPLIER);
            String nickname = getParticipantNickname(pin, participantId);
            int streak = getStreak(pin, participantId);
            int multiplier = getMultiplier(pin, participantId);

            entries.add(LeaderboardEntry.builder()
                    .participantId(participantId)
                    .nickname(nickname != null ? nickname : participantId)
                    .score(cumulativeScore)
                    .rank(rank)
                    .rankChange(0) // rank change tracking can be added later
                    .streak(streak)
                    .multiplier(multiplier)
                    .build());
            rank++;
        }
        return entries;
    }

    /**
     * Get the rank of a participant (0-indexed from ZREVRANK, we return 1-indexed).
     */
    public long getParticipantRank(String pin, String participantId) {
        Long rank = redisTemplate.opsForZSet().reverseRank(leaderboardKey(pin), participantId);
        return rank != null ? rank + 1 : -1;
    }

    /**
     * Get the total cumulative score of a participant from the leaderboard.
     * Extracts the actual cumulative score from the composite score.
     */
    public double getParticipantScore(String pin, String participantId) {
        Double score = redisTemplate.opsForZSet().score(leaderboardKey(pin), participantId);
        double rawScore = score != null ? score : 0;
        return (long) (rawScore / RankingService.SCORE_MULTIPLIER);
    }

    /**
     * Get the nickname for a participant.
     */
    public String getParticipantNickname(String pin, String participantId) {
        Object nickname = redisTemplate.opsForHash().get(
                "participant:" + pin + ":" + participantId, "nickname");
        return nickname != null ? nickname.toString() : null;
    }

    // ==================== Streak Methods ====================

    /**
     * Get the current streak for a participant.
     */
    public int getStreak(String pin, String participantId) {
        Object streak = redisTemplate.opsForHash().get(
                "participant:" + pin + ":" + participantId, "streak");
        return streak != null ? Integer.parseInt(streak.toString()) : 0;
    }

    /**
     * Get the current multiplier for a participant.
     */
    public int getMultiplier(String pin, String participantId) {
        Object multiplier = redisTemplate.opsForHash().get(
                "participant:" + pin + ":" + participantId, "multiplier");
        return multiplier != null ? Integer.parseInt(multiplier.toString()) : 1;
    }

    /**
     * Update streak and multiplier for a participant.
     */
    public void updateStreak(String pin, String participantId, int streak, int multiplier) {
        String key = "participant:" + pin + ":" + participantId;
        redisTemplate.opsForHash().put(key, "streak", String.valueOf(streak));
        redisTemplate.opsForHash().put(key, "multiplier", String.valueOf(multiplier));
    }

    /**
     * Reset streak to 0 and multiplier to 1 for a participant.
     */
    public void resetStreak(String pin, String participantId) {
        String key = "participant:" + pin + ":" + participantId;
        redisTemplate.opsForHash().put(key, "streak", "0");
        redisTemplate.opsForHash().put(key, "multiplier", "1");
    }

    /**
     * Store the last answer submission timestamp for a participant (for tie-breaking).
     */
    public void storeLastAnswerTime(String pin, String participantId, long timestampMs) {
        String key = "participant:" + pin + ":" + participantId;
        redisTemplate.opsForHash().put(key, "last_answer_time", String.valueOf(timestampMs));
    }

    /**
     * Get the last answer submission timestamp for a participant.
     */
    public long getLastAnswerTime(String pin, String participantId) {
        Object value = redisTemplate.opsForHash().get(
                "participant:" + pin + ":" + participantId, "last_answer_time");
        return value != null ? Long.parseLong(value.toString()) : 0;
    }

    // ==================== Participant Leaderboard View ====================

    /**
     * Get a participant-centric leaderboard view showing own rank, score, and neighbors.
     * Extracts the actual cumulative score from the composite score before returning to clients.
     */
    public ParticipantLeaderboardView getParticipantView(String pin, String participantId) {
        String key = leaderboardKey(pin);

        // Get own rank (0-indexed from ZREVRANK, convert to 1-indexed)
        Long rankZero = redisTemplate.opsForZSet().reverseRank(key, participantId);
        long ownRank = rankZero != null ? rankZero + 1 : -1;

        // Get own score - extract cumulative from composite
        Double score = redisTemplate.opsForZSet().score(key, participantId);
        double rawOwnScore = score != null ? score : 0;
        long ownScore = (long) (rawOwnScore / RankingService.SCORE_MULTIPLIER);

        // Get rank change
        long rankChange = getRankChange(pin, participantId);

        // Get neighbor above (rank - 1)
        LeaderboardEntry above = null;
        if (rankZero != null && rankZero > 0) {
            Set<ZSetOperations.TypedTuple<String>> aboveSet =
                    redisTemplate.opsForZSet().reverseRangeWithScores(key, rankZero - 1, rankZero - 1);
            if (aboveSet != null && !aboveSet.isEmpty()) {
                ZSetOperations.TypedTuple<String> tuple = aboveSet.iterator().next();
                String aboveId = tuple.getValue();
                double aboveRawScore = tuple.getScore() != null ? tuple.getScore() : 0;
                long aboveCumulativeScore = (long) (aboveRawScore / RankingService.SCORE_MULTIPLIER);
                String aboveNickname = getParticipantNickname(pin, aboveId);
                above = LeaderboardEntry.builder()
                        .participantId(aboveId)
                        .nickname(aboveNickname != null ? aboveNickname : aboveId)
                        .score(aboveCumulativeScore)
                        .rank(ownRank - 1)
                        .rankChange(getRankChange(pin, aboveId))
                        .build();
            }
        }

        // Get neighbor below (rank + 1)
        LeaderboardEntry below = null;
        if (rankZero != null) {
            Set<ZSetOperations.TypedTuple<String>> belowSet =
                    redisTemplate.opsForZSet().reverseRangeWithScores(key, rankZero + 1, rankZero + 1);
            if (belowSet != null && !belowSet.isEmpty()) {
                ZSetOperations.TypedTuple<String> tuple = belowSet.iterator().next();
                String belowId = tuple.getValue();
                double belowRawScore = tuple.getScore() != null ? tuple.getScore() : 0;
                long belowCumulativeScore = (long) (belowRawScore / RankingService.SCORE_MULTIPLIER);
                String belowNickname = getParticipantNickname(pin, belowId);
                below = LeaderboardEntry.builder()
                        .participantId(belowId)
                        .nickname(belowNickname != null ? belowNickname : belowId)
                        .score(belowCumulativeScore)
                        .rank(ownRank + 1)
                        .rankChange(getRankChange(pin, belowId))
                        .build();
            }
        }

        return ParticipantLeaderboardView.builder()
                .ownRank(ownRank)
                .ownScore(ownScore)
                .rankChange(rankChange)
                .above(above)
                .below(below)
                .build();
    }

    // ==================== Rank Movement Methods ====================

    /**
     * Store current ranks as previous ranks before a leaderboard update.
     * Called before scoring a new round.
     */
    public void snapshotCurrentRanks(String pin) {
        String leaderboardKeyStr = leaderboardKey(pin);
        String previousRanksKey = previousRanksKey(pin);

        Set<ZSetOperations.TypedTuple<String>> allEntries =
                redisTemplate.opsForZSet().reverseRangeWithScores(leaderboardKeyStr, 0, -1);

        if (allEntries == null || allEntries.isEmpty()) {
            return;
        }

        Map<String, String> rankMap = new HashMap<>();
        long rank = 1;
        for (ZSetOperations.TypedTuple<String> tuple : allEntries) {
            if (tuple.getValue() != null) {
                rankMap.put(tuple.getValue(), String.valueOf(rank));
            }
            rank++;
        }

        redisTemplate.opsForHash().putAll(previousRanksKey, rankMap);
        redisTemplate.expire(previousRanksKey, Duration.ofSeconds(SESSION_TTL_SECONDS));
    }

    /**
     * Get the rank change for a participant (previousRank - currentRank).
     * Positive = moved up, negative = moved down, 0 = unchanged.
     */
    public long getRankChange(String pin, String participantId) {
        String previousRanksKey = previousRanksKey(pin);
        Object previousRankObj = redisTemplate.opsForHash().get(previousRanksKey, participantId);

        if (previousRankObj == null) {
            return 0; // No previous rank data (first round)
        }

        long previousRank = Long.parseLong(previousRankObj.toString());
        long currentRank = getParticipantRank(pin, participantId);

        if (currentRank == -1) {
            return 0;
        }

        return previousRank - currentRank;
    }

    /**
     * Get the top N participants with rank change information populated.
     * Extracts the actual cumulative score from the composite score before returning to clients.
     */
    public List<LeaderboardEntry> getTopNWithRankChanges(String pin, int n) {
        String key = leaderboardKey(pin);
        Set<ZSetOperations.TypedTuple<String>> results =
                redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, n - 1);

        if (results == null || results.isEmpty()) {
            return Collections.emptyList();
        }

        List<LeaderboardEntry> entries = new ArrayList<>();
        long rank = 1;
        for (ZSetOperations.TypedTuple<String> tuple : results) {
            String participantId = tuple.getValue();
            double rawScore = tuple.getScore() != null ? tuple.getScore() : 0;
            // Extract cumulative score from composite score (composite = cumulative × SCORE_MULTIPLIER + tiebreaker)
            long cumulativeScore = (long) (rawScore / RankingService.SCORE_MULTIPLIER);
            String nickname = getParticipantNickname(pin, participantId);
            int streak = getStreak(pin, participantId);
            int multiplier = getMultiplier(pin, participantId);
            long rankDelta = getRankChange(pin, participantId);

            entries.add(LeaderboardEntry.builder()
                    .participantId(participantId)
                    .nickname(nickname != null ? nickname : participantId)
                    .score(cumulativeScore)
                    .rank(rank)
                    .rankChange(rankDelta)
                    .streak(streak)
                    .multiplier(multiplier)
                    .build());
            rank++;
        }
        return entries;
    }

    /**
     * Get the total number of participants in the leaderboard.
     */
    public long getLeaderboardSize(String pin) {
        Long size = redisTemplate.opsForZSet().zCard(leaderboardKey(pin));
        return size != null ? size : 0;
    }

    // ==================== Anti-Cheat Methods ====================

    /**
     * Increment the fast answer count for a participant.
     * @return the new fast_answer_count value
     */
    public long incrementFastAnswerCount(String pin, String participantId) {
        String key = "participant:" + pin + ":" + participantId;
        return redisTemplate.opsForHash().increment(key, "fast_answer_count", 1);
    }

    /**
     * Get the fast answer count for a participant.
     */
    public int getFastAnswerCount(String pin, String participantId) {
        Object value = redisTemplate.opsForHash().get(
                "participant:" + pin + ":" + participantId, "fast_answer_count");
        return value != null ? Integer.parseInt(value.toString()) : 0;
    }

    /**
     * Flag a participant as suspicious.
     */
    public void flagParticipant(String pin, String participantId) {
        String key = "participant:" + pin + ":" + participantId;
        redisTemplate.opsForHash().put(key, "is_flagged", "true");
        log.info("Participant flagged: pin={}, participantId={}", pin, participantId);
    }

    /**
     * Check if a participant is flagged.
     */
    public boolean isParticipantFlagged(String pin, String participantId) {
        Object value = redisTemplate.opsForHash().get(
                "participant:" + pin + ":" + participantId, "is_flagged");
        return "true".equals(value);
    }

    /**
     * Mark a participant as kicked.
     */
    public void kickParticipant(String pin, String participantId) {
        String key = "participant:" + pin + ":" + participantId;
        redisTemplate.opsForHash().put(key, "is_kicked", "true");
        redisTemplate.opsForHash().put(key, "is_connected", "false");
        log.info("Participant kicked: pin={}, participantId={}", pin, participantId);
    }

    /**
     * Check if a participant has been kicked.
     */
    public boolean isParticipantKicked(String pin, String participantId) {
        Object value = redisTemplate.opsForHash().get(
                "participant:" + pin + ":" + participantId, "is_kicked");
        return "true".equals(value);
    }

    /**
     * Remove a participant from the leaderboard.
     */
    public void removeFromLeaderboard(String pin, String participantId) {
        String key = leaderboardKey(pin);
        redisTemplate.opsForZSet().remove(key, participantId);
        log.info("Participant removed from leaderboard: pin={}, participantId={}", pin, participantId);
    }

    /**
     * Check if a participant is currently connected.
     */
    public boolean isParticipantConnected(String pin, String participantId) {
        Object value = redisTemplate.opsForHash().get(
                "participant:" + pin + ":" + participantId, "is_connected");
        return "true".equals(value);
    }

    // ==================== Response Time Tracking Methods (Requirement 4.6, 4.7) ====================

    /**
     * Get the total response time for a participant across all answered rounds.
     */
    public long getTotalResponseTimeMs(String pin, String participantId) {
        Object value = redisTemplate.opsForHash().get(
                "participant:" + pin + ":" + participantId, "total_response_time_ms");
        return value != null ? Long.parseLong(value.toString()) : 0;
    }

    /**
     * Get the number of rounds a participant has answered.
     */
    public int getAnsweredRounds(String pin, String participantId) {
        Object value = redisTemplate.opsForHash().get(
                "participant:" + pin + ":" + participantId, "answered_rounds");
        return value != null ? Integer.parseInt(value.toString()) : 0;
    }

    /**
     * Record a response time for a participant after answering a question.
     * Updates total_response_time_ms, answered_rounds, and last_answer_time.
     * 
     * @param pin session PIN
     * @param participantId participant UUID
     * @param responseTimeMs response time for this answer in milliseconds
     * @param answerTimestamp timestamp when the answer was submitted
     */
    public void recordResponseTime(String pin, String participantId, long responseTimeMs, long answerTimestamp) {
        String key = "participant:" + pin + ":" + participantId;
        
        // Increment total_response_time_ms
        redisTemplate.opsForHash().increment(key, "total_response_time_ms", responseTimeMs);
        
        // Increment answered_rounds
        redisTemplate.opsForHash().increment(key, "answered_rounds", 1);
        
        // Update last_answer_time for tiebreaker
        redisTemplate.opsForHash().put(key, "last_answer_time", String.valueOf(answerTimestamp));
    }

    /**
     * Calculate the average response time for a participant.
     * Returns Long.MAX_VALUE if participant has not answered any questions (for proper tiebreaker ordering).
     */
    public long calculateAverageResponseTimeMs(String pin, String participantId) {
        long totalResponseTime = getTotalResponseTimeMs(pin, participantId);
        int answeredRounds = getAnsweredRounds(pin, participantId);
        
        if (answeredRounds == 0) {
            return Long.MAX_VALUE; // Late-joining participants get worst average time
        }
        
        return totalResponseTime / answeredRounds;
    }

    // ==================== Audit Logging Methods ====================

    /**
     * Log an answer submission event to Redis Stream for audit purposes.
     */
    public void logAuditEvent(String pin, String participantId, int questionIndex,
                              String answer, long timestamp, boolean accepted, String reason) {
        try {
            Map<String, String> fields = new HashMap<>();
            fields.put("pin", pin);
            fields.put("participantId", participantId);
            fields.put("questionIndex", String.valueOf(questionIndex));
            fields.put("answer", answer != null ? answer : "");
            fields.put("timestamp", String.valueOf(timestamp));
            fields.put("accepted", String.valueOf(accepted));
            fields.put("reason", reason != null ? reason : "");
            fields.put("event_type", "ANSWER_SUBMISSION");

            redisTemplate.opsForStream().add("events:audit", fields);
            log.debug("Audit event logged: pin={}, participant={}, question={}, accepted={}",
                    pin, participantId, questionIndex, accepted);
        } catch (Exception e) {
            log.warn("Failed to log audit event: pin={}, participant={}", pin, participantId, e);
        }
    }

    private String generateRandomPin() {
        StringBuilder pin = new StringBuilder(PIN_LENGTH);
        for (int i = 0; i < PIN_LENGTH; i++) {
            pin.append(PIN_CHARS.charAt(secureRandom.nextInt(PIN_CHARS.length())));
        }
        return pin.toString();
    }

    private String getSettingOrDefault(Map<String, Object> settings, String key, String defaultValue) {
        if (settings == null || !settings.containsKey(key)) {
            return defaultValue;
        }
        return String.valueOf(settings.get(key));
    }

    private String sessionKey(String pin) {
        return "session:" + pin;
    }

    private String participantKey(String pin, UUID participantId) {
        return "participant:" + pin + ":" + participantId;
    }

    private String nicknamesKey(String pin) {
        return "nicknames:" + pin;
    }

    private String answersKey(String pin, int questionIndex) {
        return "answers:" + pin + ":" + questionIndex;
    }

    private String correctAnswersKey(String pin) {
        return "correct_answers:" + pin;
    }

    private String leaderboardKey(String pin) {
        return "leaderboard:" + pin;
    }

    private String previousRanksKey(String pin) {
        return "previous_ranks:" + pin;
    }
}
