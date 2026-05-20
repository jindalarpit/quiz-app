package com.quizplatform.session.service;

import com.quizplatform.session.dto.ParticipantRoundScore;
import com.quizplatform.session.dto.RankedParticipant;
import com.quizplatform.session.dto.RoundResult;
import com.quizplatform.session.model.ScoringMode;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
 * Unit tests for DynamicScoreEngine.
 * Requirements: 1.1, 4.9, 8.6
 */
@ExtendWith(MockitoExtension.class)
class DynamicScoreEngineTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private HashOperations<String, Object, Object> hashOps;
    @Mock private ZSetOperations<String, String> zSetOps;
    @Mock private RankingService rankingService;
    @Mock private LeaderboardSnapshotService snapshotService;
    @Mock private LeaderboardBroadcaster broadcaster;

    private MeterRegistry meterRegistry;
    private ScoreCalculator scoreCalculator;
    private DynamicScoreEngine engine;

    private static final String PIN = "123456";
    private static final int ROUND = 0;
    private static final long Q_START = 1000000L;
    private static final int Q_DURATION = 20000;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOps);
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        meterRegistry = new SimpleMeterRegistry();
        scoreCalculator = new ScoreCalculator();
        engine = new DynamicScoreEngine(redisTemplate, scoreCalculator, rankingService, meterRegistry);
        engine.setLeaderboardSnapshotService(snapshotService);
        engine.setLeaderboardBroadcaster(broadcaster);
    }

    private void session(String correctAnswer) {
        when(hashOps.get("session:" + PIN, "question_start_time")).thenReturn(String.valueOf(Q_START));
        when(hashOps.get("session:" + PIN, "question_duration_ms")).thenReturn(String.valueOf(Q_DURATION));
        when(hashOps.get("correct_answers:" + PIN, String.valueOf(ROUND))).thenReturn(correctAnswer);
    }

    private void participant(String id, String nick, int streak, boolean connected) {
        String key = "participant:" + PIN + ":" + id;
        lenient().when(hashOps.get(key, "nickname")).thenReturn(nick);
        lenient().when(hashOps.get(key, "streak")).thenReturn(String.valueOf(streak));
        lenient().when(hashOps.get(key, "multiplier")).thenReturn(String.valueOf(scoreCalculator.getStreakMultiplier(streak)));
        lenient().when(hashOps.get(key, "is_connected")).thenReturn(connected ? "true" : "false");
    }

    private void leaderboard(String... ids) {
        when(zSetOps.range("leaderboard:" + PIN, 0, -1)).thenReturn(new LinkedHashSet<>(Arrays.asList(ids)));
    }

    private void answers(Map<String, String> map) {
        when(hashOps.entries("answers:" + PIN + ":" + ROUND)).thenReturn(new HashMap<>(map));
    }

    private void score(String id, double composite) {
        lenient().when(zSetOps.score("leaderboard:" + PIN, id)).thenReturn(composite);
    }

    private void ranked(List<RankedParticipant> list) {
        when(rankingService.getRankedParticipants(PIN)).thenReturn(list);
    }

    private ParticipantRoundScore find(RoundResult result, String id) {
        return result.getParticipantScores().stream()
                .filter(s -> s.getParticipantId().equals(id)).findFirst()
                .orElseThrow(() -> new AssertionError("Not found: " + id));
    }

    @Nested
    @DisplayName("Score computation for multiple participants in a round")
    class MultiParticipantScoring {

        @Test
        @DisplayName("Computes time-weighted scores for participants at different speeds")
        void differentSpeeds() {
            session("A");
            leaderboard("p1", "p2", "p3");
            Map<String, String> ans = new HashMap<>();
            ans.put("p1", "A|" + (Q_START + 2000) + "|0");
            ans.put("p2", "A|" + (Q_START + 10000) + "|0");
            ans.put("p3", "A|" + (Q_START + 18000) + "|0");
            answers(ans);
            participant("p1", "Player1", 0, true);
            participant("p2", "Player2", 0, true);
            participant("p3", "Player3", 0, true);
            score("p1", 0); score("p2", 0); score("p3", 0);
            ranked(List.of(
                RankedParticipant.builder().participantId("p1").rank(1).build(),
                RankedParticipant.builder().participantId("p2").rank(2).build(),
                RankedParticipant.builder().participantId("p3").rank(3).build()));
            when(snapshotService.getPreviousRanks(PIN)).thenReturn(Collections.emptyMap());

            RoundResult result = engine.computeAndBroadcastRound(PIN, ROUND, ScoringMode.SPEED_MATTERS);

            assertThat(result.getParticipantScores()).hasSize(3);
            assertThat(result.getTotalAnswered()).isEqualTo(3);
            assertThat(result.getTotalCorrect()).isEqualTo(3);
            assertThat(find(result, "p1").getRoundScore()).isEqualTo(930);
            assertThat(find(result, "p2").getRoundScore()).isEqualTo(650);
            assertThat(find(result, "p3").getRoundScore()).isEqualTo(370);
        }

        @Test
        @DisplayName("Awards 0 for incorrect answers regardless of speed")
        void incorrectAnswers() {
            session("B");
            leaderboard("p1", "p2");
            Map<String, String> ans = new HashMap<>();
            ans.put("p1", "B|" + (Q_START + 5000) + "|0");
            ans.put("p2", "A|" + (Q_START + 3000) + "|0");
            answers(ans);
            participant("p1", "Player1", 0, true);
            participant("p2", "Player2", 0, true);
            score("p1", 0); score("p2", 0);
            ranked(List.of(
                RankedParticipant.builder().participantId("p1").rank(1).build(),
                RankedParticipant.builder().participantId("p2").rank(2).build()));
            when(snapshotService.getPreviousRanks(PIN)).thenReturn(Collections.emptyMap());

            RoundResult result = engine.computeAndBroadcastRound(PIN, ROUND, ScoringMode.SPEED_MATTERS);

            assertThat(find(result, "p1").getRoundScore()).isGreaterThan(0);
            assertThat(find(result, "p1").isCorrect()).isTrue();
            assertThat(find(result, "p2").getRoundScore()).isEqualTo(0);
            assertThat(find(result, "p2").isCorrect()).isFalse();
        }

        @Test
        @DisplayName("Applies streak multipliers correctly")
        void streakMultipliers() {
            session("C");
            leaderboard("p1", "p2");
            Map<String, String> ans = new HashMap<>();
            ans.put("p1", "C|" + (Q_START + 5000) + "|0");
            ans.put("p2", "C|" + (Q_START + 5000) + "|0");
            answers(ans);
            participant("p1", "Player1", 2, true);
            participant("p2", "Player2", 4, true);
            score("p1", 0); score("p2", 0);
            ranked(List.of(
                RankedParticipant.builder().participantId("p2").rank(1).build(),
                RankedParticipant.builder().participantId("p1").rank(2).build()));
            when(snapshotService.getPreviousRanks(PIN)).thenReturn(Collections.emptyMap());

            RoundResult result = engine.computeAndBroadcastRound(PIN, ROUND, ScoringMode.SPEED_MATTERS);

            assertThat(find(result, "p1").getRoundScore()).isEqualTo(1650);
            assertThat(find(result, "p1").getStreakCount()).isEqualTo(3);
            assertThat(find(result, "p2").getRoundScore()).isEqualTo(2475);
            assertThat(find(result, "p2").getStreakCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("Uses configured scoring mode time factor")
        void scoringModes() {
            session("A");
            leaderboard("p1");
            Map<String, String> ans = new HashMap<>();
            ans.put("p1", "A|" + (Q_START + Q_DURATION) + "|0");
            answers(ans);
            participant("p1", "Player1", 0, true);
            score("p1", 0);
            ranked(List.of(RankedParticipant.builder().participantId("p1").rank(1).build()));
            when(snapshotService.getPreviousRanks(PIN)).thenReturn(Collections.emptyMap());

            assertThat(find(engine.computeAndBroadcastRound(PIN, ROUND, ScoringMode.SPEED_MATTERS), "p1").getRoundScore()).isEqualTo(300);
            assertThat(find(engine.computeAndBroadcastRound(PIN, ROUND, ScoringMode.BALANCED), "p1").getRoundScore()).isEqualTo(500);
            assertThat(find(engine.computeAndBroadcastRound(PIN, ROUND, ScoringMode.KNOWLEDGE_FIRST), "p1").getRoundScore()).isEqualTo(700);
        }

        @Test
        @DisplayName("Computes accuracy rate correctly")
        void accuracyRate() {
            session("B");
            leaderboard("p1", "p2", "p3", "p4");
            Map<String, String> ans = new HashMap<>();
            ans.put("p1", "B|" + (Q_START + 5000) + "|0");
            ans.put("p2", "B|" + (Q_START + 8000) + "|0");
            ans.put("p3", "A|" + (Q_START + 3000) + "|0");
            ans.put("p4", "C|" + (Q_START + 12000) + "|0");
            answers(ans);
            participant("p1", "P1", 0, true); participant("p2", "P2", 0, true);
            participant("p3", "P3", 0, true); participant("p4", "P4", 0, true);
            score("p1", 0); score("p2", 0); score("p3", 0); score("p4", 0);
            ranked(List.of(
                RankedParticipant.builder().participantId("p1").rank(1).build(),
                RankedParticipant.builder().participantId("p2").rank(2).build(),
                RankedParticipant.builder().participantId("p3").rank(3).build(),
                RankedParticipant.builder().participantId("p4").rank(4).build()));
            when(snapshotService.getPreviousRanks(PIN)).thenReturn(Collections.emptyMap());

            RoundResult result = engine.computeAndBroadcastRound(PIN, ROUND, ScoringMode.SPEED_MATTERS);
            assertThat(result.getTotalAnswered()).isEqualTo(4);
            assertThat(result.getTotalCorrect()).isEqualTo(2);
            assertThat(result.getAccuracyRate()).isEqualTo(0.5);
        }
    }

    @Nested
    @DisplayName("Performance timeout and degradation (Requirement 8.1, 8.5)")
    class PerformanceTimeoutTests {

        @Test
        @DisplayName("Records computation time in Micrometer timer metric")
        void recordsTimerMetric() {
            session("A");
            leaderboard("p1");
            Map<String, String> ans = new HashMap<>();
            ans.put("p1", "A|" + (Q_START + 5000) + "|0");
            answers(ans);
            participant("p1", "Player1", 0, true);
            score("p1", 0);
            ranked(List.of(RankedParticipant.builder().participantId("p1").rank(1).build()));
            when(snapshotService.getPreviousRanks(PIN)).thenReturn(Collections.emptyMap());

            engine.computeAndBroadcastRound(PIN, ROUND, ScoringMode.SPEED_MATTERS);

            Timer timer = meterRegistry.find("dynamic.score.computation.duration").timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1);
            assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("Computation time is recorded in RoundResult")
        void computationTimeRecorded() {
            session("A");
            leaderboard("p1");
            Map<String, String> ans = new HashMap<>();
            ans.put("p1", "A|" + (Q_START + 5000) + "|0");
            answers(ans);
            participant("p1", "Player1", 0, true);
            score("p1", 0);
            ranked(List.of(RankedParticipant.builder().participantId("p1").rank(1).build()));
            when(snapshotService.getPreviousRanks(PIN)).thenReturn(Collections.emptyMap());

            RoundResult result = engine.computeAndBroadcastRound(PIN, ROUND, ScoringMode.SPEED_MATTERS);

            assertThat(result.getComputationTimeMs()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("Normal computation does not trigger timeout counter")
        void normalComputationNoTimeout() {
            session("A");
            leaderboard("p1", "p2");
            Map<String, String> ans = new HashMap<>();
            ans.put("p1", "A|" + (Q_START + 5000) + "|0");
            ans.put("p2", "A|" + (Q_START + 8000) + "|0");
            answers(ans);
            participant("p1", "Player1", 0, true);
            participant("p2", "Player2", 0, true);
            score("p1", 0); score("p2", 0);
            ranked(List.of(
                RankedParticipant.builder().participantId("p1").rank(1).build(),
                RankedParticipant.builder().participantId("p2").rank(2).build()));
            when(snapshotService.getPreviousRanks(PIN)).thenReturn(Collections.emptyMap());

            RoundResult result = engine.computeAndBroadcastRound(PIN, ROUND, ScoringMode.SPEED_MATTERS);

            // All participants should be scored
            assertThat(result.getParticipantScores()).hasSize(2);
            // No timeout counter should be incremented for fast computation
            var counter = meterRegistry.find("dynamic.score.computation.timeout").counter();
            assertThat(counter).isNull();
        }

        @Test
        @DisplayName("Session flow is not blocked - result is always returned")
        void sessionFlowNotBlocked() {
            session("A");
            leaderboard("p1");
            Map<String, String> ans = new HashMap<>();
            ans.put("p1", "A|" + (Q_START + 5000) + "|0");
            answers(ans);
            participant("p1", "Player1", 0, true);
            score("p1", 0);
            ranked(List.of(RankedParticipant.builder().participantId("p1").rank(1).build()));
            when(snapshotService.getPreviousRanks(PIN)).thenReturn(Collections.emptyMap());

            // The method should always return a result, never throw or block indefinitely
            RoundResult result = engine.computeAndBroadcastRound(PIN, ROUND, ScoringMode.SPEED_MATTERS);

            assertThat(result).isNotNull();
            assertThat(result.getPin()).isEqualTo(PIN);
            assertThat(result.getRoundNumber()).isEqualTo(ROUND);
            assertThat(result.getTimestamp()).isNotNull();
        }

        @Test
        @DisplayName("Broadcast still happens even when snapshot service fails")
        void broadcastNotBlockedBySnapshotFailure() {
            session("A");
            leaderboard("p1");
            Map<String, String> ans = new HashMap<>();
            ans.put("p1", "A|" + (Q_START + 5000) + "|0");
            answers(ans);
            participant("p1", "Player1", 0, true);
            score("p1", 0);
            ranked(List.of(RankedParticipant.builder().participantId("p1").rank(1).build()));
            when(snapshotService.getPreviousRanks(PIN)).thenReturn(Collections.emptyMap());
            doThrow(new RuntimeException("Redis unavailable"))
                    .when(snapshotService).storeSnapshot(anyString(), anyInt(), anyList());

            RoundResult result = engine.computeAndBroadcastRound(PIN, ROUND, ScoringMode.SPEED_MATTERS);

            // Result should still be returned
            assertThat(result).isNotNull();
            assertThat(result.getParticipantScores()).hasSize(1);
            // Broadcast should still be attempted
            verify(broadcaster).broadcastLeaderboardUpdate(eq(PIN), any(RoundResult.class));
        }

        @Test
        @DisplayName("Result is returned even when broadcaster fails")
        void resultReturnedWhenBroadcastFails() {
            session("A");
            leaderboard("p1");
            Map<String, String> ans = new HashMap<>();
            ans.put("p1", "A|" + (Q_START + 5000) + "|0");
            answers(ans);
            participant("p1", "Player1", 0, true);
            score("p1", 0);
            ranked(List.of(RankedParticipant.builder().participantId("p1").rank(1).build()));
            when(snapshotService.getPreviousRanks(PIN)).thenReturn(Collections.emptyMap());
            doThrow(new RuntimeException("WebSocket failure"))
                    .when(broadcaster).broadcastLeaderboardUpdate(anyString(), any(RoundResult.class));

            RoundResult result = engine.computeAndBroadcastRound(PIN, ROUND, ScoringMode.SPEED_MATTERS);

            // Result should still be returned - session flow not blocked
            assertThat(result).isNotNull();
            assertThat(result.getParticipantScores()).hasSize(1);
            assertThat(find(result, "p1").getRoundScore()).isGreaterThan(0);
        }
    }
}
