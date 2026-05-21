package com.quizplatform.session.service;

import com.quizplatform.session.dto.LeaderboardEntry;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import org.mockito.MockMakers;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doAnswer;

/**
 * Bug Condition Exploration Tests - Quiz Session Flow Bugs
 *
 * These tests are EXPECTED TO FAIL on unfixed code - failure confirms the bugs exist.
 * They encode the expected (correct) behavior and will validate the fix when they pass.
 *
 * **Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5**
 */
class BugConditionExplorationTest {

    // Helper to create StringRedisTemplate mock compatible with Java 25+
    private static StringRedisTemplate createMockRedisTemplate() {
        return mock(StringRedisTemplate.class,
                Mockito.withSettings().mockMaker(MockMakers.SUBCLASS).defaultAnswer(Mockito.RETURNS_DEEP_STUBS));
    }

    // ==================== Bug 1: Composite Score Leak ====================

    /**
     * Bug 1 - Composite Score Leak: getTopN() returns actual cumulative scores, not composite scores.
     *
     * For any cumulative score in [1, 100000], the score returned by getTopN() should equal
     * the cumulative score (compositeScore / SCORE_MULTIPLIER), NOT the raw composite score.
     * The raw composite score encodes tiebreaker data and is always >= SCORE_MULTIPLIER for score > 0.
     *
     * EXPECTED TO FAIL on unfixed code: getTopN() returns raw composite scores (values > 1,000,000).
     *
     * **Validates: Requirements 1.1**
     */
    @Property(tries = 50)
    void bug1_getTopN_returnsActualCumulativeScore_notCompositeScore(
            @ForAll @LongRange(min = 1, max = 100000) long cumulativeScore,
            @ForAll @LongRange(min = 0, max = 999999999) long avgResponseTimeMs) {

        // Setup: Create a composite score as stored in Redis
        long clampedAvgTime = Math.max(0, Math.min(avgResponseTimeMs, RankingService.MAX_TIME));
        double compositeScore = (double) (cumulativeScore * RankingService.SCORE_MULTIPLIER + (RankingService.MAX_TIME - clampedAvgTime));

        // Create mock Redis template that returns the composite score
        StringRedisTemplate mockRedisTemplate = createMockRedisTemplate();
        String participantId = UUID.randomUUID().toString();
        String pin = "TEST01";

        // Mock the ZSET reverseRangeWithScores to return our composite score
        ZSetOperations.TypedTuple<String> tuple = mock(ZSetOperations.TypedTuple.class);
        when(tuple.getValue()).thenReturn(participantId);
        when(tuple.getScore()).thenReturn(compositeScore);

        Set<ZSetOperations.TypedTuple<String>> tupleSet = new LinkedHashSet<>();
        tupleSet.add(tuple);

        when(mockRedisTemplate.opsForZSet().reverseRangeWithScores(eq("leaderboard:" + pin), eq(0L), eq(4L)))
                .thenReturn(tupleSet);

        // Mock participant data
        when(mockRedisTemplate.opsForHash().get(eq("participant:" + pin + ":" + participantId), eq("nickname")))
                .thenReturn("TestPlayer");
        when(mockRedisTemplate.opsForHash().get(eq("participant:" + pin + ":" + participantId), eq("streak")))
                .thenReturn("0");
        when(mockRedisTemplate.opsForHash().get(eq("participant:" + pin + ":" + participantId), eq("multiplier")))
                .thenReturn("1");

        RedisSessionService service = new RedisSessionService(mockRedisTemplate);

        // Act
        List<LeaderboardEntry> entries = service.getTopN(pin, 5);

        // Assert: The returned score should be the cumulative score, NOT the composite score
        assertThat(entries).hasSize(1);
        LeaderboardEntry entry = entries.get(0);

        // The score should be the actual cumulative score (small number, not billions)
        assertThat((long) entry.getScore())
                .as("Score should be cumulative score (%d), not composite score (%.0f). " +
                    "Bug: getTopN() leaks raw composite score to clients.", cumulativeScore, compositeScore)
                .isEqualTo(cumulativeScore);
    }

    /**
     * Bug 1 - Composite Score Leak: getTopNWithRankChanges() returns actual cumulative scores.
     *
     * Same as above but for getTopNWithRankChanges() method.
     *
     * EXPECTED TO FAIL on unfixed code: returns raw composite scores.
     *
     * **Validates: Requirements 1.1**
     */
    @Property(tries = 50)
    void bug1_getTopNWithRankChanges_returnsActualCumulativeScore(
            @ForAll @LongRange(min = 1, max = 100000) long cumulativeScore,
            @ForAll @LongRange(min = 0, max = 999999999) long avgResponseTimeMs) {

        // Setup: Create a composite score as stored in Redis
        long clampedAvgTime = Math.max(0, Math.min(avgResponseTimeMs, RankingService.MAX_TIME));
        double compositeScore = (double) (cumulativeScore * RankingService.SCORE_MULTIPLIER + (RankingService.MAX_TIME - clampedAvgTime));

        // Create mock Redis template
        StringRedisTemplate mockRedisTemplate = createMockRedisTemplate();
        String participantId = UUID.randomUUID().toString();
        String pin = "TEST02";

        // Mock the ZSET reverseRangeWithScores
        ZSetOperations.TypedTuple<String> tuple = mock(ZSetOperations.TypedTuple.class);
        when(tuple.getValue()).thenReturn(participantId);
        when(tuple.getScore()).thenReturn(compositeScore);

        Set<ZSetOperations.TypedTuple<String>> tupleSet = new LinkedHashSet<>();
        tupleSet.add(tuple);

        when(mockRedisTemplate.opsForZSet().reverseRangeWithScores(eq("leaderboard:" + pin), eq(0L), eq(4L)))
                .thenReturn(tupleSet);

        // Mock participant data
        when(mockRedisTemplate.opsForHash().get(eq("participant:" + pin + ":" + participantId), eq("nickname")))
                .thenReturn("TestPlayer");
        when(mockRedisTemplate.opsForHash().get(eq("participant:" + pin + ":" + participantId), eq("streak")))
                .thenReturn("0");
        when(mockRedisTemplate.opsForHash().get(eq("participant:" + pin + ":" + participantId), eq("multiplier")))
                .thenReturn("1");

        // Mock previous ranks (for rank change calculation)
        when(mockRedisTemplate.opsForHash().get(eq("previous_ranks:" + pin), eq(participantId)))
                .thenReturn(null);

        // Mock reverseRank for getRankChange
        when(mockRedisTemplate.opsForZSet().reverseRank(eq("leaderboard:" + pin), eq(participantId)))
                .thenReturn(0L);

        RedisSessionService service = new RedisSessionService(mockRedisTemplate);

        // Act
        List<LeaderboardEntry> entries = service.getTopNWithRankChanges(pin, 5);

        // Assert: The returned score should be the cumulative score
        assertThat(entries).hasSize(1);
        LeaderboardEntry entry = entries.get(0);

        assertThat((long) entry.getScore())
                .as("Score should be cumulative score (%d), not composite score (%.0f). " +
                    "Bug: getTopNWithRankChanges() leaks raw composite score to clients.", cumulativeScore, compositeScore)
                .isEqualTo(cumulativeScore);
    }

    /**
     * Bug 1 - Composite Score Leak: Scores returned must be in valid range.
     *
     * For any participant with cumulative score in [0, 100000], the returned score
     * should never exceed 100000 (max possible cumulative score for a long quiz).
     *
     * EXPECTED TO FAIL on unfixed code: returns values > 1,000,000.
     *
     * **Validates: Requirements 1.1**
     */
    @Property(tries = 50)
    void bug1_returnedScores_areInValidCumulativeRange(
            @ForAll @LongRange(min = 1, max = 100000) long cumulativeScore,
            @ForAll @LongRange(min = 0, max = 999999999) long avgResponseTimeMs) {

        long clampedAvgTime = Math.max(0, Math.min(avgResponseTimeMs, RankingService.MAX_TIME));
        double compositeScore = (double) (cumulativeScore * RankingService.SCORE_MULTIPLIER + (RankingService.MAX_TIME - clampedAvgTime));

        StringRedisTemplate mockRedisTemplate = createMockRedisTemplate();
        String participantId = UUID.randomUUID().toString();
        String pin = "TEST03";

        ZSetOperations.TypedTuple<String> tuple = mock(ZSetOperations.TypedTuple.class);
        when(tuple.getValue()).thenReturn(participantId);
        when(tuple.getScore()).thenReturn(compositeScore);

        Set<ZSetOperations.TypedTuple<String>> tupleSet = new LinkedHashSet<>();
        tupleSet.add(tuple);

        when(mockRedisTemplate.opsForZSet().reverseRangeWithScores(eq("leaderboard:" + pin), eq(0L), eq(4L)))
                .thenReturn(tupleSet);

        when(mockRedisTemplate.opsForHash().get(eq("participant:" + pin + ":" + participantId), eq("nickname")))
                .thenReturn("TestPlayer");
        when(mockRedisTemplate.opsForHash().get(eq("participant:" + pin + ":" + participantId), eq("streak")))
                .thenReturn("0");
        when(mockRedisTemplate.opsForHash().get(eq("participant:" + pin + ":" + participantId), eq("multiplier")))
                .thenReturn("1");

        RedisSessionService service = new RedisSessionService(mockRedisTemplate);

        List<LeaderboardEntry> entries = service.getTopN(pin, 5);

        assertThat(entries).hasSize(1);
        double returnedScore = entries.get(0).getScore();

        // Score should be in valid cumulative range, not in composite range (millions/billions)
        assertThat(returnedScore)
                .as("Returned score (%.0f) should be <= 100000 (max cumulative). " +
                    "Bug: composite score leaked to client.", returnedScore)
                .isLessThanOrEqualTo(100000.0);
    }

    // ==================== Bug 2: Skip Does Not Auto-Advance to REVEAL ====================

    /**
     * Bug 2 - Skip Auto-Advance: skipQuestion() should auto-advance to REVEAL state.
     *
     * After calling skipQuestion(), the session state should reach REVEAL (not just QUESTION_CLOSED).
     * The system should automatically trigger the reveal flow (score computation, answer reveal,
     * leaderboard broadcast) without requiring a separate manual action.
     *
     * EXPECTED TO FAIL on unfixed code: state stays at QUESTION_CLOSED, no REVEAL transition occurs.
     *
     * **Validates: Requirements 1.2, 1.5**
     */
    @Property(tries = 20)
    void bug2_skipQuestion_autoAdvancesToReveal(
            @ForAll("validPins") String pin) {

        // Setup mocks
        StringRedisTemplate mockRedisTemplate = createMockRedisTemplate();
        RedisSessionService redisSessionService = mock(RedisSessionService.class);
        SessionStateMachine stateMachine = mock(SessionStateMachine.class);
        AnswerService answerService = mock(AnswerService.class);

        UUID hostId = UUID.randomUUID();

        // Session is in QUESTION_OPEN state
        when(redisSessionService.sessionExists(pin)).thenReturn(true);
        when(redisSessionService.getHostId(pin)).thenReturn(hostId.toString());
        when(redisSessionService.getSessionState(pin)).thenReturn("QUESTION_OPEN");

        // When answerService.revealAnswer is called, it should update state to REVEAL
        doAnswer(invocation -> {
            redisSessionService.updateSessionState(pin, "REVEAL");
            return null;
        }).when(answerService).revealAnswer(eq(pin), eq(hostId));

        // Create SessionService with mocked dependencies
        SessionService sessionService = new SessionService(
                redisSessionService,
                stateMachine,
                mock(ProfanityFilter.class),
                mock(com.quizplatform.session.repository.SessionRepository.class),
                mock(com.quizplatform.session.repository.SessionParticipantRepository.class),
                mockRedisTemplate,
                mock(com.quizplatform.session.client.QuizServiceClient.class),
                mock(LeaderboardService.class),
                mock(LeaderboardSnapshotService.class),
                mock(LeaderboardBroadcasterImpl.class),
                answerService
        );

        // Act
        sessionService.skipQuestion(pin, hostId);

        // Assert: After skip, the system should have auto-advanced to REVEAL.
        // On unfixed code, only updateSessionState(pin, "QUESTION_CLOSED") is called.
        // The fix should also call updateSessionState(pin, "REVEAL") or equivalent.
        // We verify that REVEAL state was set at some point during the skip operation.
        verify(redisSessionService, atLeastOnce()).updateSessionState(eq(pin), eq("REVEAL"));
    }

    /**
     * Bug 5 - Unnecessary Reveal Step: After skip, the final state should be REVEAL.
     *
     * This verifies that skipQuestion() does not leave the session in QUESTION_CLOSED
     * as its terminal state. The session should progress to REVEAL automatically.
     *
     * EXPECTED TO FAIL on unfixed code: skipQuestion only sets QUESTION_CLOSED and stops.
     *
     * **Validates: Requirements 1.5**
     */
    @Property(tries = 20)
    void bug5_skipQuestion_finalStateIsReveal(
            @ForAll("validPins") String pin) {

        // Setup
        StringRedisTemplate mockRedisTemplate = createMockRedisTemplate();
        RedisSessionService redisSessionService = mock(RedisSessionService.class);
        SessionStateMachine stateMachine = mock(SessionStateMachine.class);
        AnswerService answerService = mock(AnswerService.class);

        UUID hostId = UUID.randomUUID();

        when(redisSessionService.sessionExists(pin)).thenReturn(true);
        when(redisSessionService.getHostId(pin)).thenReturn(hostId.toString());
        when(redisSessionService.getSessionState(pin)).thenReturn("QUESTION_OPEN");

        // Track state updates to verify the final state
        List<String> stateUpdates = new ArrayList<>();
        doAnswer(invocation -> {
            stateUpdates.add(invocation.getArgument(1));
            return null;
        }).when(redisSessionService).updateSessionState(eq(pin), anyString());

        // When answerService.revealAnswer is called, it should update state to REVEAL
        doAnswer(invocation -> {
            redisSessionService.updateSessionState(pin, "REVEAL");
            return null;
        }).when(answerService).revealAnswer(eq(pin), eq(hostId));

        SessionService sessionService = new SessionService(
                redisSessionService,
                stateMachine,
                mock(ProfanityFilter.class),
                mock(com.quizplatform.session.repository.SessionRepository.class),
                mock(com.quizplatform.session.repository.SessionParticipantRepository.class),
                mockRedisTemplate,
                mock(com.quizplatform.session.client.QuizServiceClient.class),
                mock(LeaderboardService.class),
                mock(LeaderboardSnapshotService.class),
                mock(LeaderboardBroadcasterImpl.class),
                answerService
        );

        // Act
        sessionService.skipQuestion(pin, hostId);

        // Assert: The last state update should be REVEAL, not QUESTION_CLOSED.
        // On unfixed code: stateUpdates = ["QUESTION_CLOSED"] (only one update, stops there)
        // Expected after fix: stateUpdates ends with "REVEAL"
        assertThat(stateUpdates)
                .as("skipQuestion should auto-advance to REVEAL. " +
                    "Bug: state stops at QUESTION_CLOSED without progressing to REVEAL. " +
                    "Actual state updates: %s", stateUpdates)
                .isNotEmpty()
                .last()
                .isEqualTo("REVEAL");
    }

    // ==================== Providers ====================

    @Provide
    Arbitrary<String> validPins() {
        return Arbitraries.strings()
                .withCharRange('A', 'Z')
                .ofLength(6);
    }
}
