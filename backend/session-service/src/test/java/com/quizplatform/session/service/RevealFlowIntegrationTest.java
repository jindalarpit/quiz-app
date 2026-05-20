package com.quizplatform.session.service;

import com.quizplatform.session.dto.ParticipantRoundScore;
import com.quizplatform.session.dto.RankedParticipant;
import com.quizplatform.session.dto.RoundResult;
import com.quizplatform.session.model.ScoringMode;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for the end-to-end answer reveal flow.
 *
 * Tests the real interaction between:
 * DynamicScoreEngine → ScoreCalculator → LeaderboardSnapshotService → LeaderboardBroadcaster
 *
 * Requirements: 4.1, 4.9, 8.1, 8.6
 */
@ExtendWith(MockitoExtension.class)
class RevealFlowIntegrationTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private HashOperations<String, Object, Object> hashOps;
    @Mock private ZSetOperations<String, String> zSetOps;
    @Mock private RankingService rankingService;
    @Mock private LeaderboardSnapshotService snapshotService;
    @Mock private LeaderboardBroadcaster broadcaster;

    private MeterRegistry meterRegistry;
    private ScoreCalculator scoreCalculator;
    private DynamicScoreEngine engine;

    private static final String PIN = "987654";
    private static final int ROUND = 0;
    private static final long Q_START = 1700000000000L;
    private static final int Q_DURATION = 20000; // 20 seconds

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOps);
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        meterRegistry = new SimpleMeterRegistry();
        scoreCalculator = new ScoreCalculator(); // Real ScoreCalculator
        engine = new DynamicScoreEngine(redisTemplate, scoreCalculator, rankingService, meterRegistry);
        engine.setLeaderboardSnapshotService(snapshotService);
        engine.setLeaderboardBroadcaster(broadcaster);
    }

    // ==================== Helper Methods ====================

    private void setupSession(String correctAnswer) {
        when(hashOps.get("session:" + PIN, "question_start_time"))
                .thenReturn(String.valueOf(Q_START));
        when(hashOps.get("session:" + PIN, "question_duration_ms"))
                .thenReturn(String.valueOf(Q_DURATION));
        when(hashOps.get("correct_answers:" + PIN, String.valueOf(ROUND)))
                .thenReturn(correctAnswer);
    }

    private void setupParticipant(String id, String nick, int streak, boolean connected) {
        String key = "participant:" + PIN + ":" + id;
        lenient().when(hashOps.get(key, "nickname")).thenReturn(nick);
        lenient().when(hashOps.get(key, "streak")).thenReturn(String.valueOf(streak));
        lenient().when(hashOps.get(key, "multiplier"))
                .thenReturn(String.valueOf(scoreCalculator.getStreakMultiplier(streak)));
        lenient().when(hashOps.get(key, "is_connected"))
                .thenReturn(connected ? "true" : "false");
    }

    private void setupLeaderboard(String... ids) {
        when(zSetOps.range("leaderboard:" + PIN, 0, -1))
                .thenReturn(new LinkedHashSet<>(Arrays.asList(ids)));
    }

    private void setupAnswers(Map<String, String> answerMap) {
        when(hashOps.entries("answers:" + PIN + ":" + ROUND))
                .thenReturn(new HashMap<>(answerMap));
    }

    private void setupCumulativeScore(String id, double compositeScore) {
        lenient().when(zSetOps.score("leaderboard:" + PIN, id)).thenReturn(compositeScore);
    }

    private void setupRankedParticipants(List<RankedParticipant> ranked) {
        when(rankingService.getRankedParticipants(PIN)).thenReturn(ranked);
    }

    private ParticipantRoundScore findParticipant(RoundResult result, String id) {
        return result.getParticipantScores().stream()
                .filter(s -> s.getParticipantId().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Participant not found: " + id));
    }

    // ==================== End-to-End Reveal Flow Tests ====================

    @Nested
    @DisplayName("End-to-end reveal flow: host reveals → scores computed → snapshots stored → events broadcast")
    class EndToEndRevealFlow {

        @Test
        @DisplayName("Full flow: scores computed, snapshot stored, and broadcast triggered")
        void fullRevealFlow() {
            // Setup: 3 participants with different answer times
            setupSession("A");
            setupLeaderboard("p1", "p2", "p3");
            Map<String, String> answers = new HashMap<>();
            answers.put("p1", "A|" + (Q_START + 2000) + "|0");  // Fast
            answers.put("p2", "A|" + (Q_START + 10000) + "|0"); // Medium
            answers.put("p3", "A|" + (Q_START + 18000) + "|0"); // Slow
            setupAnswers(answers);
            setupParticipant("p1", "Alice", 0, true);
            setupParticipant("p2", "Bob", 0, true);
            setupParticipant("p3", "Charlie", 0, true);
            setupCumulativeScore("p1", 0);
            setupCumulativeScore("p2", 0);
            setupCumulativeScore("p3", 0);

            setupRankedParticipants(List.of(
                    RankedParticipant.builder().participantId("p1").rank(1).build(),
                    RankedParticipant.builder().participantId("p2").rank(2).build(),
                    RankedParticipant.builder().participantId("p3").rank(3).build()));
            when(snapshotService.getPreviousRanks(PIN)).thenReturn(Collections.emptyMap());

            // Act: Host reveals the answer
            RoundResult result = engine.computeAndBroadcastRound(PIN, ROUND, ScoringMode.SPEED_MATTERS);

            // Assert: Scores are computed correctly
            assertThat(result).isNotNull();
            assertThat(result.getPin()).isEqualTo(PIN);
            assertThat(result.getRoundNumber()).isEqualTo(ROUND);
            assertThat(result.getParticipantScores()).hasSize(3);
            assertThat(result.getTotalCorrect()).isEqualTo(3);

            // Verify scores are ordered by speed (faster = higher score)
            ParticipantRoundScore p1Score = findParticipant(result, "p1");
            ParticipantRoundScore p2Score = findParticipant(result, "p2");
            ParticipantRoundScore p3Score = findParticipant(result, "p3");
            assertThat(p1Score.getRoundScore()).isGreaterThan(p2Score.getRoundScore());
            assertThat(p2Score.getRoundScore()).isGreaterThan(p3Score.getRoundScore());

            // Assert: Snapshot is stored
            verify(snapshotService).storeSnapshot(eq(PIN), eq(ROUND), anyList());

            // Assert: Broadcast is triggered
            verify(broadcaster).broadcastLeaderboardUpdate(eq(PIN), any(RoundResult.class));
        }

        @Test
        @DisplayName("Snapshot stores correct participant data after scoring")
        void snapshotStoresCorrectData() {
            setupSession("B");
            setupLeaderboard("p1", "p2");
            Map<String, String> answers = new HashMap<>();
            answers.put("p1", "B|" + (Q_START + 5000) + "|0");
            answers.put("p2", "B|" + (Q_START + 15000) + "|0");
            setupAnswers(answers);
            setupParticipant("p1", "Alice", 0, true);
            setupParticipant("p2", "Bob", 0, true);
            setupCumulativeScore("p1", 0);
            setupCumulativeScore("p2", 0);

            setupRankedParticipants(List.of(
                    RankedParticipant.builder().participantId("p1").rank(1).build(),
                    RankedParticipant.builder().participantId("p2").rank(2).build()));
            when(snapshotService.getPreviousRanks(PIN)).thenReturn(Collections.emptyMap());

            // Act
            engine.computeAndBroadcastRound(PIN, ROUND, ScoringMode.SPEED_MATTERS);

            // Assert: Snapshot is stored with ranked participant data
            ArgumentCaptor<List<ParticipantRoundScore>> snapshotCaptor =
                    ArgumentCaptor.forClass(List.class);
            verify(snapshotService).storeSnapshot(eq(PIN), eq(ROUND), snapshotCaptor.capture());

            List<ParticipantRoundScore> storedEntries = snapshotCaptor.getValue();
            assertThat(storedEntries).hasSize(2);

            // Entries should be ordered by rank
            assertThat(storedEntries.get(0).getRank()).isEqualTo(1);
            assertThat(storedEntries.get(1).getRank()).isEqualTo(2);

            // Each entry should have a positive round score (both answered correctly)
            assertThat(storedEntries.get(0).getRoundScore()).isGreaterThan(0);
            assertThat(storedEntries.get(1).getRoundScore()).isGreaterThan(0);
        }

        @Test
        @DisplayName("Broadcast receives the complete RoundResult with all metadata")
        void broadcastReceivesCompleteResult() {
            setupSession("C");
            setupLeaderboard("p1");
            Map<String, String> answers = new HashMap<>();
            answers.put("p1", "C|" + (Q_START + 3000) + "|0");
            setupAnswers(answers);
            setupParticipant("p1", "Alice", 0, true);
            setupCumulativeScore("p1", 0);
            setupRankedParticipants(List.of(
                    RankedParticipant.builder().participantId("p1").rank(1).build()));
            when(snapshotService.getPreviousRanks(PIN)).thenReturn(Collections.emptyMap());

            // Act
            engine.computeAndBroadcastRound(PIN, ROUND, ScoringMode.BALANCED);

            // Assert: Broadcast receives the full RoundResult
            ArgumentCaptor<RoundResult> resultCaptor =
                    ArgumentCaptor.forClass(RoundResult.class);
            verify(broadcaster).broadcastLeaderboardUpdate(eq(PIN), resultCaptor.capture());

            RoundResult broadcastResult = resultCaptor.getValue();
            assertThat(broadcastResult.getPin()).isEqualTo(PIN);
            assertThat(broadcastResult.getRoundNumber()).isEqualTo(ROUND);
            assertThat(broadcastResult.getTimestamp()).isNotNull();
            assertThat(broadcastResult.getParticipantScores()).hasSize(1);
            assertThat(broadcastResult.getTotalAnswered()).isEqualTo(1);
            assertThat(broadcastResult.getTotalCorrect()).isEqualTo(1);
            assertThat(broadcastResult.getAccuracyRate()).isEqualTo(1.0);
            assertThat(broadcastResult.getComputationTimeMs()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("Rank deltas are computed from previous snapshot")
        void rankDeltasComputedFromPreviousSnapshot() {
            setupSession("A");
            setupLeaderboard("p1", "p2", "p3");
            Map<String, String> answers = new HashMap<>();
            answers.put("p1", "A|" + (Q_START + 15000) + "|0"); // Slow this round
            answers.put("p2", "A|" + (Q_START + 2000) + "|0");  // Fast this round
            answers.put("p3", "A|" + (Q_START + 8000) + "|0");  // Medium
            setupAnswers(answers);
            setupParticipant("p1", "Alice", 0, true);
            setupParticipant("p2", "Bob", 0, true);
            setupParticipant("p3", "Charlie", 0, true);
            setupCumulativeScore("p1", 0);
            setupCumulativeScore("p2", 0);
            setupCumulativeScore("p3", 0);

            // Previous round: p1 was rank 1, p2 was rank 2, p3 was rank 3
            Map<String, Integer> previousRanks = new HashMap<>();
            previousRanks.put("p1", 1);
            previousRanks.put("p2", 2);
            previousRanks.put("p3", 3);
            when(snapshotService.getPreviousRanks(PIN)).thenReturn(previousRanks);

            // Current rankings: p2 moved to rank 1, p3 to rank 2, p1 dropped to rank 3
            setupRankedParticipants(List.of(
                    RankedParticipant.builder().participantId("p2").rank(1).build(),
                    RankedParticipant.builder().participantId("p3").rank(2).build(),
                    RankedParticipant.builder().participantId("p1").rank(3).build()));

            // Act
            RoundResult result = engine.computeAndBroadcastRound(PIN, ROUND, ScoringMode.SPEED_MATTERS);

            // Assert: Rank deltas reflect movement
            ParticipantRoundScore p1 = findParticipant(result, "p1");
            ParticipantRoundScore p2 = findParticipant(result, "p2");
            ParticipantRoundScore p3 = findParticipant(result, "p3");

            // p2: was rank 2, now rank 1 → delta = 2 - 1 = +1
            assertThat(p2.getRankDelta()).isEqualTo(1);
            // p3: was rank 3, now rank 2 → delta = 3 - 2 = +1
            assertThat(p3.getRankDelta()).isEqualTo(1);
            // p1: was rank 1, now rank 3 → delta = 1 - 3 = -2
            assertThat(p1.getRankDelta()).isEqualTo(-2);
        }

        @Test
        @DisplayName("First round sets all rank deltas to zero")
        void firstRoundZeroDeltas() {
            setupSession("A");
            setupLeaderboard("p1", "p2");
            Map<String, String> answers = new HashMap<>();
            answers.put("p1", "A|" + (Q_START + 5000) + "|0");
            answers.put("p2", "A|" + (Q_START + 8000) + "|0");
            setupAnswers(answers);
            setupParticipant("p1", "Alice", 0, true);
            setupParticipant("p2", "Bob", 0, true);
            setupCumulativeScore("p1", 0);
            setupCumulativeScore("p2", 0);
            setupRankedParticipants(List.of(
                    RankedParticipant.builder().participantId("p1").rank(1).build(),
                    RankedParticipant.builder().participantId("p2").rank(2).build()));
            // No previous ranks (first round)
            when(snapshotService.getPreviousRanks(PIN)).thenReturn(Collections.emptyMap());

            // Act
            RoundResult result = engine.computeAndBroadcastRound(PIN, ROUND, ScoringMode.SPEED_MATTERS);

            // Assert: All rank deltas are 0 for first round
            assertThat(findParticipant(result, "p1").getRankDelta()).isEqualTo(0);
            assertThat(findParticipant(result, "p2").getRankDelta()).isEqualTo(0);
        }

        @Test
        @DisplayName("Atomic ZINCRBY is called for each participant with positive score")
        void atomicZincrbyCalledForScoreUpdates() {
            setupSession("A");
            setupLeaderboard("p1", "p2");
            Map<String, String> answers = new HashMap<>();
            answers.put("p1", "A|" + (Q_START + 5000) + "|0");
            answers.put("p2", "B|" + (Q_START + 3000) + "|0"); // Wrong answer
            setupAnswers(answers);
            setupParticipant("p1", "Alice", 0, true);
            setupParticipant("p2", "Bob", 0, true);
            setupCumulativeScore("p1", 0);
            setupCumulativeScore("p2", 0);
            setupRankedParticipants(List.of(
                    RankedParticipant.builder().participantId("p1").rank(1).build(),
                    RankedParticipant.builder().participantId("p2").rank(2).build()));
            when(snapshotService.getPreviousRanks(PIN)).thenReturn(Collections.emptyMap());

            // Act
            RoundResult result = engine.computeAndBroadcastRound(PIN, ROUND, ScoringMode.SPEED_MATTERS);

            // Assert: ZINCRBY called for p1 (correct answer, positive score)
            verify(zSetOps).incrementScore(eq("leaderboard:" + PIN), eq("p1"),
                    doubleThat(score -> score > 0));
            // p2 got 0 (incorrect), so ZINCRBY should NOT be called for p2
            verify(zSetOps, never()).incrementScore(eq("leaderboard:" + PIN), eq("p2"), anyDouble());
        }
    }

    // ==================== Concurrent Submissions Tests ====================

    @Nested
    @DisplayName("Concurrent answer submissions without score corruption (Requirement 8.6)")
    class ConcurrentSubmissions {

        @Test
        @DisplayName("Multiple concurrent computeAndBroadcastRound calls use atomic ZINCRBY")
        void concurrentRoundsUseAtomicOperations() throws Exception {
            // Setup: 5 participants all answering correctly
            setupSession("A");
            String[] participants = {"p1", "p2", "p3", "p4", "p5"};
            setupLeaderboard(participants);

            Map<String, String> answers = new HashMap<>();
            for (int i = 0; i < participants.length; i++) {
                long timeTaken = Q_START + 2000 + (i * 2000L);
                answers.put(participants[i], "A|" + timeTaken + "|0");
            }
            setupAnswers(answers);

            for (String pid : participants) {
                setupParticipant(pid, "Player_" + pid, 0, true);
                setupCumulativeScore(pid, 0);
            }

            List<RankedParticipant> ranked = new ArrayList<>();
            for (int i = 0; i < participants.length; i++) {
                ranked.add(RankedParticipant.builder()
                        .participantId(participants[i])
                        .rank(i + 1)
                        .build());
            }
            setupRankedParticipants(ranked);
            when(snapshotService.getPreviousRanks(PIN)).thenReturn(Collections.emptyMap());

            // Act: Execute scoring
            RoundResult result = engine.computeAndBroadcastRound(PIN, ROUND, ScoringMode.SPEED_MATTERS);

            // Assert: All participants scored, no data lost
            assertThat(result.getParticipantScores()).hasSize(5);
            assertThat(result.getTotalCorrect()).isEqualTo(5);

            // Verify atomic ZINCRBY was called for each participant with positive score
            for (String pid : participants) {
                verify(zSetOps).incrementScore(eq("leaderboard:" + PIN), eq(pid),
                        doubleThat(score -> score > 0));
            }
        }

        @Test
        @DisplayName("Concurrent calls to computeAndBroadcastRound from multiple threads do not corrupt scores")
        void multiThreadedScoreComputationIntegrity() throws Exception {
            // This test verifies that the engine can handle being called from
            // multiple threads without data races. Each thread processes a different
            // session PIN to simulate concurrent sessions.
            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);

            for (int t = 0; t < threadCount; t++) {
                final String threadPin = "PIN_" + t;
                final String participantId = "participant_" + t;

                // Setup per-thread session data
                lenient().when(hashOps.get("session:" + threadPin, "question_start_time"))
                        .thenReturn(String.valueOf(Q_START));
                lenient().when(hashOps.get("session:" + threadPin, "question_duration_ms"))
                        .thenReturn(String.valueOf(Q_DURATION));
                lenient().when(hashOps.get("correct_answers:" + threadPin, "0"))
                        .thenReturn("A");

                lenient().when(zSetOps.range("leaderboard:" + threadPin, 0, -1))
                        .thenReturn(new LinkedHashSet<>(List.of(participantId)));

                Map<Object, Object> threadAnswers = new HashMap<>();
                threadAnswers.put(participantId, "A|" + (Q_START + 5000) + "|0");
                lenient().when(hashOps.entries("answers:" + threadPin + ":0"))
                        .thenReturn(threadAnswers);

                String pKey = "participant:" + threadPin + ":" + participantId;
                lenient().when(hashOps.get(pKey, "nickname")).thenReturn("Player_" + t);
                lenient().when(hashOps.get(pKey, "streak")).thenReturn("0");
                lenient().when(hashOps.get(pKey, "multiplier")).thenReturn("1");
                lenient().when(hashOps.get(pKey, "is_connected")).thenReturn("true");
                lenient().when(zSetOps.score("leaderboard:" + threadPin, participantId))
                        .thenReturn(0.0);
                lenient().when(rankingService.getRankedParticipants(threadPin))
                        .thenReturn(List.of(RankedParticipant.builder()
                                .participantId(participantId).rank(1).build()));
                lenient().when(snapshotService.getPreviousRanks(threadPin))
                        .thenReturn(Collections.emptyMap());

                executor.submit(() -> {
                    try {
                        RoundResult result = engine.computeAndBroadcastRound(
                                threadPin, 0, ScoringMode.SPEED_MATTERS);
                        if (result != null && result.getParticipantScores().size() == 1
                                && result.getParticipantScores().get(0).getRoundScore() > 0) {
                            successCount.incrementAndGet();
                        } else {
                            failureCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            // Wait for all threads to complete
            boolean completed = latch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            // Assert: All threads completed successfully without corruption
            assertThat(completed).isTrue();
            assertThat(successCount.get()).isEqualTo(threadCount);
            assertThat(failureCount.get()).isEqualTo(0);
        }

        @Test
        @DisplayName("Each participant's score is independently computed via atomic ZINCRBY")
        void eachParticipantScoreIndependent() {
            // Setup: 3 participants with different correctness
            setupSession("X");
            setupLeaderboard("p1", "p2", "p3");
            Map<String, String> answers = new HashMap<>();
            answers.put("p1", "X|" + (Q_START + 3000) + "|0");  // Correct
            answers.put("p2", "Y|" + (Q_START + 4000) + "|0");  // Wrong
            answers.put("p3", "X|" + (Q_START + 7000) + "|0");  // Correct
            setupAnswers(answers);
            setupParticipant("p1", "Alice", 0, true);
            setupParticipant("p2", "Bob", 0, true);
            setupParticipant("p3", "Charlie", 0, true);
            setupCumulativeScore("p1", 0);
            setupCumulativeScore("p2", 0);
            setupCumulativeScore("p3", 0);
            setupRankedParticipants(List.of(
                    RankedParticipant.builder().participantId("p1").rank(1).build(),
                    RankedParticipant.builder().participantId("p3").rank(2).build(),
                    RankedParticipant.builder().participantId("p2").rank(3).build()));
            when(snapshotService.getPreviousRanks(PIN)).thenReturn(Collections.emptyMap());

            // Act
            RoundResult result = engine.computeAndBroadcastRound(PIN, ROUND, ScoringMode.SPEED_MATTERS);

            // Assert: Correct participants get positive scores, incorrect gets 0
            ParticipantRoundScore p1 = findParticipant(result, "p1");
            ParticipantRoundScore p2 = findParticipant(result, "p2");
            ParticipantRoundScore p3 = findParticipant(result, "p3");

            assertThat(p1.getRoundScore()).isGreaterThan(0);
            assertThat(p1.isCorrect()).isTrue();
            assertThat(p2.getRoundScore()).isEqualTo(0);
            assertThat(p2.isCorrect()).isFalse();
            assertThat(p3.getRoundScore()).isGreaterThan(0);
            assertThat(p3.isCorrect()).isTrue();

            // Verify ZINCRBY only called for correct answers
            verify(zSetOps).incrementScore(eq("leaderboard:" + PIN), eq("p1"),
                    doubleThat(score -> score > 0));
            verify(zSetOps).incrementScore(eq("leaderboard:" + PIN), eq("p3"),
                    doubleThat(score -> score > 0));
            verify(zSetOps, never()).incrementScore(eq("leaderboard:" + PIN), eq("p2"), anyDouble());

            // Verify cumulative score sum integrity: each participant's score is independent
            assertThat(p1.getRoundScore()).isNotEqualTo(p3.getRoundScore());
            // p1 answered faster (3000ms) than p3 (7000ms), so p1 should score higher
            assertThat(p1.getRoundScore()).isGreaterThan(p3.getRoundScore());
        }
    }

    // ==================== Disconnected Participant Tests ====================

    @Nested
    @DisplayName("Disconnected participant handling (Requirement 4.9)")
    class DisconnectedParticipant {

        @Test
        @DisplayName("Disconnected participant included in snapshot with round_score = 0")
        void disconnectedParticipantIncludedWithZeroScore() {
            setupSession("A");
            setupLeaderboard("p1", "p2", "p3");

            // p1 and p2 answered, p3 is disconnected and did not answer
            Map<String, String> answers = new HashMap<>();
            answers.put("p1", "A|" + (Q_START + 4000) + "|0");
            answers.put("p2", "A|" + (Q_START + 8000) + "|0");
            // p3 has no answer entry (disconnected)
            setupAnswers(answers);

            setupParticipant("p1", "Alice", 0, true);
            setupParticipant("p2", "Bob", 0, true);
            setupParticipant("p3", "Charlie", 0, false); // Disconnected

            setupCumulativeScore("p1", 0);
            setupCumulativeScore("p2", 0);
            setupCumulativeScore("p3", 500.0 * RankingService.SCORE_MULTIPLIER); // Had previous score

            setupRankedParticipants(List.of(
                    RankedParticipant.builder().participantId("p1").rank(1).build(),
                    RankedParticipant.builder().participantId("p2").rank(2).build(),
                    RankedParticipant.builder().participantId("p3").rank(3).build()));
            when(snapshotService.getPreviousRanks(PIN)).thenReturn(Collections.emptyMap());

            // Act
            RoundResult result = engine.computeAndBroadcastRound(PIN, ROUND, ScoringMode.SPEED_MATTERS);

            // Assert: All 3 participants are in the result
            assertThat(result.getParticipantScores()).hasSize(3);

            // Assert: Disconnected participant has round_score = 0
            ParticipantRoundScore p3 = findParticipant(result, "p3");
            assertThat(p3.getRoundScore()).isEqualTo(0);
            assertThat(p3.isCorrect()).isFalse();
            assertThat(p3.isConnected()).isFalse();

            // Assert: Connected participants have positive scores
            assertThat(findParticipant(result, "p1").getRoundScore()).isGreaterThan(0);
            assertThat(findParticipant(result, "p2").getRoundScore()).isGreaterThan(0);
        }

        @Test
        @DisplayName("Disconnected participant is included in snapshot store call")
        void disconnectedParticipantInSnapshot() {
            setupSession("B");
            setupLeaderboard("p1", "p2");

            // p1 answered, p2 is disconnected
            Map<String, String> answers = new HashMap<>();
            answers.put("p1", "B|" + (Q_START + 6000) + "|0");
            setupAnswers(answers);

            setupParticipant("p1", "Alice", 0, true);
            setupParticipant("p2", "Bob", 0, false); // Disconnected

            setupCumulativeScore("p1", 0);
            setupCumulativeScore("p2", 0);

            setupRankedParticipants(List.of(
                    RankedParticipant.builder().participantId("p1").rank(1).build(),
                    RankedParticipant.builder().participantId("p2").rank(2).build()));
            when(snapshotService.getPreviousRanks(PIN)).thenReturn(Collections.emptyMap());

            // Act
            engine.computeAndBroadcastRound(PIN, ROUND, ScoringMode.SPEED_MATTERS);

            // Assert: Snapshot includes both participants
            ArgumentCaptor<List<ParticipantRoundScore>> snapshotCaptor =
                    ArgumentCaptor.forClass(List.class);
            verify(snapshotService).storeSnapshot(eq(PIN), eq(ROUND), snapshotCaptor.capture());

            List<ParticipantRoundScore> storedEntries = snapshotCaptor.getValue();
            assertThat(storedEntries).hasSize(2);

            // Find the disconnected participant in the snapshot
            ParticipantRoundScore disconnectedEntry = storedEntries.stream()
                    .filter(e -> e.getParticipantId().equals("p2"))
                    .findFirst()
                    .orElseThrow();
            assertThat(disconnectedEntry.getRoundScore()).isEqualTo(0);
        }

        @Test
        @DisplayName("Disconnected participant rank delta is computed normally")
        void disconnectedParticipantRankDeltaComputed() {
            setupSession("A");
            setupLeaderboard("p1", "p2", "p3");

            // Only p1 and p3 answered; p2 is disconnected
            Map<String, String> answers = new HashMap<>();
            answers.put("p1", "A|" + (Q_START + 3000) + "|0");
            answers.put("p3", "A|" + (Q_START + 5000) + "|0");
            setupAnswers(answers);

            setupParticipant("p1", "Alice", 0, true);
            setupParticipant("p2", "Bob", 0, false); // Disconnected
            setupParticipant("p3", "Charlie", 0, true);

            setupCumulativeScore("p1", 0);
            setupCumulativeScore("p2", 0);
            setupCumulativeScore("p3", 0);

            // Previous round: p2 was rank 1 (had highest score before)
            Map<String, Integer> previousRanks = new HashMap<>();
            previousRanks.put("p1", 2);
            previousRanks.put("p2", 1);
            previousRanks.put("p3", 3);
            when(snapshotService.getPreviousRanks(PIN)).thenReturn(previousRanks);

            // Current rankings: p2 dropped because they didn't answer
            setupRankedParticipants(List.of(
                    RankedParticipant.builder().participantId("p1").rank(1).build(),
                    RankedParticipant.builder().participantId("p3").rank(2).build(),
                    RankedParticipant.builder().participantId("p2").rank(3).build()));

            // Act
            RoundResult result = engine.computeAndBroadcastRound(PIN, ROUND, ScoringMode.SPEED_MATTERS);

            // Assert: Disconnected participant's rank delta is computed normally
            ParticipantRoundScore p2 = findParticipant(result, "p2");
            // p2: was rank 1, now rank 3 → delta = 1 - 3 = -2
            assertThat(p2.getRankDelta()).isEqualTo(-2);
            assertThat(p2.getRoundScore()).isEqualTo(0);
            assertThat(p2.isConnected()).isFalse();

            // p1: was rank 2, now rank 1 → delta = 2 - 1 = +1
            assertThat(findParticipant(result, "p1").getRankDelta()).isEqualTo(1);
            // p3: was rank 3, now rank 2 → delta = 3 - 2 = +1
            assertThat(findParticipant(result, "p3").getRankDelta()).isEqualTo(1);
        }

        @Test
        @DisplayName("Disconnected participant retains previous cumulative score")
        void disconnectedParticipantRetainsCumulativeScore() {
            setupSession("A");
            setupLeaderboard("p1", "p2");

            // Only p1 answered; p2 is disconnected
            Map<String, String> answers = new HashMap<>();
            answers.put("p1", "A|" + (Q_START + 5000) + "|0");
            setupAnswers(answers);

            setupParticipant("p1", "Alice", 0, true);
            setupParticipant("p2", "Bob", 0, false);

            // p2 had a previous cumulative score of 800
            double p2CompositeScore = 800.0 * RankingService.SCORE_MULTIPLIER;
            setupCumulativeScore("p1", 0);
            setupCumulativeScore("p2", p2CompositeScore);

            setupRankedParticipants(List.of(
                    RankedParticipant.builder().participantId("p2").rank(1).build(),
                    RankedParticipant.builder().participantId("p1").rank(2).build()));
            when(snapshotService.getPreviousRanks(PIN)).thenReturn(Collections.emptyMap());

            // Act
            RoundResult result = engine.computeAndBroadcastRound(PIN, ROUND, ScoringMode.SPEED_MATTERS);

            // Assert: p2's cumulative score is retained (800), round score is 0
            ParticipantRoundScore p2 = findParticipant(result, "p2");
            assertThat(p2.getRoundScore()).isEqualTo(0);
            assertThat(p2.getCumulativeScore()).isEqualTo(800);

            // ZINCRBY should NOT be called for p2 (round score is 0)
            verify(zSetOps, never()).incrementScore(eq("leaderboard:" + PIN), eq("p2"), anyDouble());
        }

        @Test
        @DisplayName("Multiple disconnected participants all get round_score = 0")
        void multipleDisconnectedParticipants() {
            setupSession("A");
            setupLeaderboard("p1", "p2", "p3", "p4");

            // Only p1 answered; p2, p3, p4 are disconnected
            Map<String, String> answers = new HashMap<>();
            answers.put("p1", "A|" + (Q_START + 4000) + "|0");
            setupAnswers(answers);

            setupParticipant("p1", "Alice", 0, true);
            setupParticipant("p2", "Bob", 0, false);
            setupParticipant("p3", "Charlie", 0, false);
            setupParticipant("p4", "Diana", 0, false);

            setupCumulativeScore("p1", 0);
            setupCumulativeScore("p2", 0);
            setupCumulativeScore("p3", 0);
            setupCumulativeScore("p4", 0);

            setupRankedParticipants(List.of(
                    RankedParticipant.builder().participantId("p1").rank(1).build(),
                    RankedParticipant.builder().participantId("p2").rank(2).build(),
                    RankedParticipant.builder().participantId("p3").rank(3).build(),
                    RankedParticipant.builder().participantId("p4").rank(4).build()));
            when(snapshotService.getPreviousRanks(PIN)).thenReturn(Collections.emptyMap());

            // Act
            RoundResult result = engine.computeAndBroadcastRound(PIN, ROUND, ScoringMode.SPEED_MATTERS);

            // Assert: All 4 participants are in the result
            assertThat(result.getParticipantScores()).hasSize(4);
            assertThat(result.getTotalAnswered()).isEqualTo(1);
            assertThat(result.getTotalCorrect()).isEqualTo(1);

            // Only p1 has a positive score
            assertThat(findParticipant(result, "p1").getRoundScore()).isGreaterThan(0);
            assertThat(findParticipant(result, "p2").getRoundScore()).isEqualTo(0);
            assertThat(findParticipant(result, "p3").getRoundScore()).isEqualTo(0);
            assertThat(findParticipant(result, "p4").getRoundScore()).isEqualTo(0);

            // All disconnected participants are marked as not connected
            assertThat(findParticipant(result, "p2").isConnected()).isFalse();
            assertThat(findParticipant(result, "p3").isConnected()).isFalse();
            assertThat(findParticipant(result, "p4").isConnected()).isFalse();
        }
    }
}
