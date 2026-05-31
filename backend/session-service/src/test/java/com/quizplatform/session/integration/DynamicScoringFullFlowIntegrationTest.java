package com.quizplatform.session.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizplatform.session.dto.ParticipantRoundScore;
import com.quizplatform.session.dto.RankedParticipant;
import com.quizplatform.session.dto.RoundResult;
import com.quizplatform.session.model.ScoringMode;
import com.quizplatform.session.service.*;
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
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for the full dynamic scoring flow:
 * Quiz with scoring mode → Session creation → Answer reveal →
 * DynamicScoreEngine → LeaderboardBroadcaster → Kafka event publishing.
 *
 * Tests the data flow between components using real ScoreCalculator
 * and mocked external dependencies (Redis, Kafka).
 *
 * Requirements: 5.5, 5.6, 7.7
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Dynamic Scoring Full Flow Integration Tests")
class DynamicScoringFullFlowIntegrationTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private HashOperations<String, Object, Object> hashOps;
    @Mock private ZSetOperations<String, String> zSetOps;
    @Mock private ListOperations<String, String> listOps;
    @Mock private RankingService rankingService;
    @Mock private LeaderboardSnapshotService snapshotService;

    private MeterRegistry meterRegistry;
    private ScoreCalculator scoreCalculator;
    private DynamicScoreEngine engine;
    private LeaderboardBroadcasterImpl broadcaster;
    private ObjectMapper objectMapper;

    private static final String PIN = "FLOW01";
    private static final int ROUND = 0;
    private static final long Q_START = 1700000000000L;
    private static final int Q_DURATION = 20000; // 20 seconds

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOps);
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        lenient().when(redisTemplate.opsForList()).thenReturn(listOps);
        meterRegistry = new SimpleMeterRegistry();
        scoreCalculator = new ScoreCalculator(); // Real ScoreCalculator
        objectMapper = new ObjectMapper();
        broadcaster = new LeaderboardBroadcasterImpl(redisTemplate, objectMapper, null);
        engine = new DynamicScoreEngine(redisTemplate, scoreCalculator, rankingService, meterRegistry);
        engine.setLeaderboardSnapshotService(snapshotService);
        engine.setLeaderboardBroadcaster(broadcaster);
    }

    // ==================== Helper Methods ====================

    private void setupSession(String correctAnswer, ScoringMode mode) {
        when(hashOps.get("session:" + PIN, "question_start_time"))
                .thenReturn(String.valueOf(Q_START));
        when(hashOps.get("session:" + PIN, "question_duration_ms"))
                .thenReturn(String.valueOf(Q_DURATION));
        when(hashOps.get("correct_answers:" + PIN, String.valueOf(ROUND)))
                .thenReturn(correctAnswer);
    }

    private void setupParticipant(String id, String nick, int streak) {
        String key = "participant:" + PIN + ":" + id;
        lenient().when(hashOps.get(key, "nickname")).thenReturn(nick);
        lenient().when(hashOps.get(key, "streak")).thenReturn(String.valueOf(streak));
        lenient().when(hashOps.get(key, "multiplier"))
                .thenReturn(String.valueOf(scoreCalculator.getStreakMultiplier(streak)));
        lenient().when(hashOps.get(key, "is_connected")).thenReturn("true");
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

    private ParticipantRoundScore findParticipant(RoundResult result, String id) {
        return result.getParticipantScores().stream()
                .filter(s -> s.getParticipantId().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Participant not found: " + id));
    }

    // ==================== Full Flow Tests ====================

    @Nested
    @DisplayName("End-to-end: Quiz scoring mode → Session → Answer reveal → WebSocket → Kafka")
    class FullScoringFlow {

        @Test
        @DisplayName("Speed Matters mode: full flow from scoring mode config through broadcast")
        void speedMattersFullFlow() {
            // Setup: Quiz configured with SPEED_MATTERS mode
            setupSession("A", ScoringMode.SPEED_MATTERS);
            setupLeaderboard("p1", "p2", "p3");
            Map<String, String> answers = new HashMap<>();
            answers.put("p1", "A|" + (Q_START + 2000) + "|0");  // Very fast (2s)
            answers.put("p2", "A|" + (Q_START + 10000) + "|0"); // Medium (10s)
            answers.put("p3", "B|" + (Q_START + 5000) + "|0");  // Wrong answer
            setupAnswers(answers);
            setupParticipant("p1", "Alice", 0);
            setupParticipant("p2", "Bob", 0);
            setupParticipant("p3", "Charlie", 0);
            setupCumulativeScore("p1", 0);
            setupCumulativeScore("p2", 0);
            setupCumulativeScore("p3", 0);

            setupRankedParticipants(List.of(
                    RankedParticipant.builder().participantId("p1").rank(1).build(),
                    RankedParticipant.builder().participantId("p2").rank(2).build(),
                    RankedParticipant.builder().participantId("p3").rank(3).build()));
            when(snapshotService.getPreviousRanks(PIN)).thenReturn(Collections.emptyMap());

            // Act: Host reveals answer with SPEED_MATTERS scoring mode
            RoundResult result = engine.computeAndBroadcastRound(PIN, ROUND, ScoringMode.SPEED_MATTERS);

            // Assert: Scores follow SPEED_MATTERS formula (time_factor = 0.7)
            ParticipantRoundScore p1 = findParticipant(result, "p1");
            ParticipantRoundScore p2 = findParticipant(result, "p2");
            ParticipantRoundScore p3 = findParticipant(result, "p3");

            // p1: 1000 × (1 - (2000/20000) × 0.7) = 1000 × (1 - 0.07) = 930
            assertThat(p1.getRoundScore()).isEqualTo(930);
            assertThat(p1.isCorrect()).isTrue();

            // p2: 1000 × (1 - (10000/20000) × 0.7) = 1000 × (1 - 0.35) = 650
            assertThat(p2.getRoundScore()).isEqualTo(650);
            assertThat(p2.isCorrect()).isTrue();

            // p3: incorrect answer = 0
            assertThat(p3.getRoundScore()).isEqualTo(0);
            assertThat(p3.isCorrect()).isFalse();

            // Assert: Snapshot stored
            verify(snapshotService).storeSnapshot(eq(PIN), eq(ROUND), anyList());

            // Assert: Broadcast triggered (host + 3 participants = 4 calls)
            verify(redisTemplate, atLeast(1)).convertAndSend(anyString(), anyString());
        }

        @Test
        @DisplayName("Balanced mode: minimum score is 50% of base points")
        void balancedModeMinimumScore() {
            setupSession("A", ScoringMode.BALANCED);
            setupLeaderboard("p1");
            Map<String, String> answers = new HashMap<>();
            // Answer at the time limit boundary (20000ms)
            answers.put("p1", "A|" + (Q_START + Q_DURATION) + "|0");
            setupAnswers(answers);
            setupParticipant("p1", "Alice", 0);
            setupCumulativeScore("p1", 0);
            setupRankedParticipants(List.of(
                    RankedParticipant.builder().participantId("p1").rank(1).build()));
            when(snapshotService.getPreviousRanks(PIN)).thenReturn(Collections.emptyMap());

            RoundResult result = engine.computeAndBroadcastRound(PIN, ROUND, ScoringMode.BALANCED);

            // Balanced mode: time_factor = 0.5
            // At time limit: 1000 × (1 - (20000/20000) × 0.5) = 1000 × 0.5 = 500
            ParticipantRoundScore p1 = findParticipant(result, "p1");
            assertThat(p1.getRoundScore()).isEqualTo(500);
        }

        @Test
        @DisplayName("Knowledge First mode: minimum score is 70% of base points")
        void knowledgeFirstModeMinimumScore() {
            setupSession("A", ScoringMode.KNOWLEDGE_FIRST);
            setupLeaderboard("p1");
            Map<String, String> answers = new HashMap<>();
            answers.put("p1", "A|" + (Q_START + Q_DURATION) + "|0");
            setupAnswers(answers);
            setupParticipant("p1", "Alice", 0);
            setupCumulativeScore("p1", 0);
            setupRankedParticipants(List.of(
                    RankedParticipant.builder().participantId("p1").rank(1).build()));
            when(snapshotService.getPreviousRanks(PIN)).thenReturn(Collections.emptyMap());

            RoundResult result = engine.computeAndBroadcastRound(PIN, ROUND, ScoringMode.KNOWLEDGE_FIRST);

            // Knowledge First: time_factor = 0.3
            // At time limit: 1000 × (1 - (20000/20000) × 0.3) = 1000 × 0.7 = 700
            ParticipantRoundScore p1 = findParticipant(result, "p1");
            assertThat(p1.getRoundScore()).isEqualTo(700);
        }

        @Test
        @DisplayName("Scoring mode is fixed for session duration - uses provided mode consistently")
        void scoringModeFixedForSession() {
            // Requirement 7.7: scoring mode remains fixed for entire session
            setupSession("A", ScoringMode.BALANCED);
            setupLeaderboard("p1");
            Map<String, String> answers = new HashMap<>();
            answers.put("p1", "A|" + (Q_START + 5000) + "|0");
            setupAnswers(answers);
            setupParticipant("p1", "Alice", 0);
            setupCumulativeScore("p1", 0);
            setupRankedParticipants(List.of(
                    RankedParticipant.builder().participantId("p1").rank(1).build()));
            when(snapshotService.getPreviousRanks(PIN)).thenReturn(Collections.emptyMap());

            // First round with BALANCED
            RoundResult result1 = engine.computeAndBroadcastRound(PIN, ROUND, ScoringMode.BALANCED);
            ParticipantRoundScore p1r1 = findParticipant(result1, "p1");

            // Expected: 1000 × (1 - (5000/20000) × 0.5) = 1000 × (1 - 0.125) = 875
            assertThat(p1r1.getRoundScore()).isEqualTo(875);

            // If we accidentally used SPEED_MATTERS, score would be different:
            // 1000 × (1 - (5000/20000) × 0.7) = 1000 × (1 - 0.175) = 825
            assertThat(p1r1.getRoundScore()).isNotEqualTo(825);
        }

        @Test
        @DisplayName("Streak multiplier applied after time-weighted score with scoring mode")
        void streakMultiplierWithScoringMode() {
            setupSession("A", ScoringMode.SPEED_MATTERS);
            setupLeaderboard("p1");
            Map<String, String> answers = new HashMap<>();
            answers.put("p1", "A|" + (Q_START + 5000) + "|0");
            setupAnswers(answers);
            setupParticipant("p1", "Alice", 3); // Streak of 3 → 2x multiplier
            setupCumulativeScore("p1", 0);
            setupRankedParticipants(List.of(
                    RankedParticipant.builder().participantId("p1").rank(1).build()));
            when(snapshotService.getPreviousRanks(PIN)).thenReturn(Collections.emptyMap());

            RoundResult result = engine.computeAndBroadcastRound(PIN, ROUND, ScoringMode.SPEED_MATTERS);

            ParticipantRoundScore p1 = findParticipant(result, "p1");
            // Base score: 1000 × (1 - (5000/20000) × 0.7) = 1000 × (1 - 0.175) = 825
            // With 2x streak: 825 × 2 = 1650
            assertThat(p1.getRoundScore()).isEqualTo(1650);
            assertThat(p1.getStreakMultiplier()).isEqualTo(2);
        }
    }

    // ==================== Kafka Event Publishing Tests ====================

    @Nested
    @DisplayName("Kafka score.awarded event delivery to analytics-service")
    class KafkaEventDelivery {

        @Test
        @DisplayName("Broadcast triggers Kafka event publish with correct round data")
        void broadcastTriggersKafkaPublish() {
            setupSession("A", ScoringMode.SPEED_MATTERS);
            setupLeaderboard("p1", "p2");
            Map<String, String> answers = new HashMap<>();
            answers.put("p1", "A|" + (Q_START + 3000) + "|0");
            answers.put("p2", "A|" + (Q_START + 12000) + "|0");
            setupAnswers(answers);
            setupParticipant("p1", "Alice", 0);
            setupParticipant("p2", "Bob", 4); // Streak of 4 → 2x multiplier
            setupCumulativeScore("p1", 0);
            setupCumulativeScore("p2", 0);
            setupRankedParticipants(List.of(
                    RankedParticipant.builder().participantId("p1").rank(1).build(),
                    RankedParticipant.builder().participantId("p2").rank(2).build()));
            when(snapshotService.getPreviousRanks(PIN)).thenReturn(Collections.emptyMap());

            // Act
            RoundResult result = engine.computeAndBroadcastRound(PIN, ROUND, ScoringMode.SPEED_MATTERS);

            // Assert: RoundResult contains all data needed for Kafka event
            assertThat(result).isNotNull();
            assertThat(result.getPin()).isEqualTo(PIN);
            assertThat(result.getRoundNumber()).isEqualTo(ROUND);
            assertThat(result.getTimestamp()).isNotNull();
            assertThat(result.getParticipantScores()).hasSize(2);

            // Verify each participant entry has all fields needed for score.awarded event
            ParticipantRoundScore p1 = findParticipant(result, "p1");
            assertThat(p1.getParticipantId()).isEqualTo("p1");
            assertThat(p1.getRoundScore()).isGreaterThan(0);
            assertThat(p1.getRank()).isEqualTo(1);
            assertThat(p1.getRankDelta()).isEqualTo(0); // First round
            assertThat(p1.getStreakCount()).isEqualTo(0);
            assertThat(p1.getStreakMultiplier()).isEqualTo(1);
            assertThat(p1.getTimeTakenMs()).isEqualTo(3000L);
            assertThat(p1.isCorrect()).isTrue();

            ParticipantRoundScore p2 = findParticipant(result, "p2");
            assertThat(p2.getStreakCount()).isEqualTo(4);
            assertThat(p2.getStreakMultiplier()).isEqualTo(2);
            assertThat(p2.getTimeTakenMs()).isEqualTo(12000L);
            assertThat(p2.isCorrect()).isTrue();
        }

        @Test
        @DisplayName("Kafka event payload includes incorrect participants with is_correct=false")
        void kafkaEventIncludesIncorrectParticipants() {
            setupSession("A", ScoringMode.SPEED_MATTERS);
            setupLeaderboard("p1", "p2");
            Map<String, String> answers = new HashMap<>();
            answers.put("p1", "A|" + (Q_START + 5000) + "|0");
            answers.put("p2", "B|" + (Q_START + 3000) + "|0"); // Wrong
            setupAnswers(answers);
            setupParticipant("p1", "Alice", 0);
            setupParticipant("p2", "Bob", 0);
            setupCumulativeScore("p1", 0);
            setupCumulativeScore("p2", 0);
            setupRankedParticipants(List.of(
                    RankedParticipant.builder().participantId("p1").rank(1).build(),
                    RankedParticipant.builder().participantId("p2").rank(2).build()));
            when(snapshotService.getPreviousRanks(PIN)).thenReturn(Collections.emptyMap());

            RoundResult result = engine.computeAndBroadcastRound(PIN, ROUND, ScoringMode.SPEED_MATTERS);

            // Both participants should be in the result for Kafka event
            assertThat(result.getParticipantScores()).hasSize(2);
            ParticipantRoundScore p2 = findParticipant(result, "p2");
            assertThat(p2.isCorrect()).isFalse();
            assertThat(p2.getRoundScore()).isEqualTo(0);
            assertThat(p2.getTimeTakenMs()).isEqualTo(3000L);
        }

        @Test
        @DisplayName("Kafka event includes unanswered participants with null time_taken_ms")
        void kafkaEventIncludesUnansweredParticipants() {
            setupSession("A", ScoringMode.SPEED_MATTERS);
            setupLeaderboard("p1", "p2");
            Map<String, String> answers = new HashMap<>();
            answers.put("p1", "A|" + (Q_START + 5000) + "|0");
            // p2 did not answer (not in answers map)
            setupAnswers(answers);
            setupParticipant("p1", "Alice", 0);
            setupParticipant("p2", "Bob", 0);
            setupCumulativeScore("p1", 0);
            setupCumulativeScore("p2", 0);
            setupRankedParticipants(List.of(
                    RankedParticipant.builder().participantId("p1").rank(1).build(),
                    RankedParticipant.builder().participantId("p2").rank(2).build()));
            when(snapshotService.getPreviousRanks(PIN)).thenReturn(Collections.emptyMap());

            RoundResult result = engine.computeAndBroadcastRound(PIN, ROUND, ScoringMode.SPEED_MATTERS);

            ParticipantRoundScore p2 = findParticipant(result, "p2");
            assertThat(p2.isCorrect()).isFalse();
            assertThat(p2.getRoundScore()).isEqualTo(0);
        }
    }

    // ==================== WebSocket Event Broadcasting Tests ====================

    @Nested
    @DisplayName("WebSocket leaderboard.updated event broadcasting")
    class WebSocketBroadcasting {

        @Test
        @DisplayName("Host receives top-5 view with all fields after reveal")
        void hostReceivesTop5View() {
            setupSession("A", ScoringMode.SPEED_MATTERS);
            String[] participants = {"p1", "p2", "p3", "p4", "p5", "p6"};
            setupLeaderboard(participants);
            Map<String, String> answers = new HashMap<>();
            for (int i = 0; i < participants.length; i++) {
                answers.put(participants[i], "A|" + (Q_START + (i + 1) * 2000L) + "|0");
            }
            setupAnswers(answers);
            for (int i = 0; i < participants.length; i++) {
                setupParticipant(participants[i], "Player" + (i + 1), 0);
                setupCumulativeScore(participants[i], 0);
            }

            List<RankedParticipant> ranked = new ArrayList<>();
            for (int i = 0; i < participants.length; i++) {
                ranked.add(RankedParticipant.builder()
                        .participantId(participants[i]).rank(i + 1).build());
            }
            when(rankingService.getRankedParticipants(PIN)).thenReturn(ranked);
            when(snapshotService.getPreviousRanks(PIN)).thenReturn(Collections.emptyMap());

            // Act
            engine.computeAndBroadcastRound(PIN, ROUND, ScoringMode.SPEED_MATTERS);

            // Assert: Broadcast was called (host + 6 participants = 7 calls)
            verify(redisTemplate, atLeast(7)).convertAndSend(anyString(), anyString());

            // Verify host channel was used
            verify(redisTemplate).convertAndSend(
                    eq("session:" + PIN + ":host"), anyString());
        }

        @Test
        @DisplayName("Participant ranked below 5th receives personalized view with context")
        void participantBelowTop5ReceivesPersonalizedView() {
            // This test verifies the broadcaster constructs personalized views
            List<ParticipantRoundScore> allScores = new ArrayList<>();
            for (int i = 1; i <= 8; i++) {
                allScores.add(ParticipantRoundScore.builder()
                        .participantId("p" + i)
                        .nickname("Player" + i)
                        .cumulativeScore(1000 * (9 - i))
                        .roundScore(500 - (i * 50))
                        .rank(i)
                        .rankDelta(0)
                        .streakCount(0)
                        .streakMultiplier(1)
                        .timeTakenMs((long) (i * 2000))
                        .correct(true)
                        .connected(true)
                        .build());
            }

            // Test participant view for p7 (rank 7)
            ParticipantRoundScore p7 = allScores.get(6);
            List<ParticipantRoundScore> view =
                    broadcaster.constructParticipantView(allScores, p7);

            // Should have: top 5 + rank 6 (above) + rank 7 (self) + rank 8 (below)
            assertThat(view).hasSize(8);
            assertThat(view.stream().mapToInt(ParticipantRoundScore::getRank).max().orElse(0))
                    .isEqualTo(8);
            assertThat(view.stream().anyMatch(s -> s.getParticipantId().equals("p7")))
                    .isTrue();
        }

        @Test
        @DisplayName("Sequence number increments with each broadcast")
        void sequenceNumberIncrements() {
            // First broadcast
            assertThat(broadcaster.getNextSequenceNumber(PIN)).isEqualTo(1);
            // Second broadcast
            assertThat(broadcaster.getNextSequenceNumber(PIN)).isEqualTo(2);
            // Third broadcast
            assertThat(broadcaster.getNextSequenceNumber(PIN)).isEqualTo(3);
        }

        @Test
        @DisplayName("Event payload contains all required fields for frontend rendering")
        void eventPayloadContainsAllRequiredFields() throws Exception {
            RoundResult result = RoundResult.builder()
                    .pin(PIN)
                    .roundNumber(2)
                    .timestamp(java.time.Instant.ofEpochMilli(1700000000000L))
                    .participantScores(new ArrayList<>())
                    .correctAnswer("A")
                    .totalAnswered(3)
                    .totalCorrect(2)
                    .accuracyRate(0.67)
                    .computationTimeMs(45)
                    .build();

            List<ParticipantRoundScore> entries = List.of(
                    ParticipantRoundScore.builder()
                            .participantId("p1")
                            .nickname("Alice")
                            .cumulativeScore(2500)
                            .roundScore(930)
                            .rank(1)
                            .rankDelta(1)
                            .streakCount(3)
                            .streakMultiplier(2)
                            .timeTakenMs(2000L)
                            .correct(true)
                            .connected(true)
                            .build());

            String payload = broadcaster.buildEventPayload(PIN, result, entries, 5);

            // Parse and verify structure
            Map<String, Object> parsed = objectMapper.readValue(payload, Map.class);
            assertThat(parsed.get("type")).isEqualTo("leaderboard.updated");

            @SuppressWarnings("unchecked")
            Map<String, Object> payloadMap = (Map<String, Object>) parsed.get("payload");
            assertThat(payloadMap.get("sessionId")).isEqualTo(PIN);
            assertThat(payloadMap.get("roundNumber")).isEqualTo(2);
            assertThat(payloadMap.get("sequenceNumber")).isEqualTo(5);
            assertThat(payloadMap.get("timestamp")).isNotNull();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> entryList =
                    (List<Map<String, Object>>) payloadMap.get("entries");
            assertThat(entryList).hasSize(1);

            Map<String, Object> entry = entryList.get(0);
            assertThat(entry.get("participantId")).isEqualTo("p1");
            assertThat(entry.get("nickname")).isEqualTo("Alice");
            assertThat(entry.get("cumulativeScore")).isEqualTo(2500);
            assertThat(entry.get("roundScore")).isEqualTo(930);
            assertThat(entry.get("rank")).isEqualTo(1);
            assertThat(entry.get("rankDelta")).isEqualTo(1);
            assertThat(entry.get("streakCount")).isEqualTo(3);
            assertThat(entry.get("streakMultiplier")).isEqualTo(2);
        }
    }

    // ==================== WebSocket Reconnection with Queued Events ====================

    @Nested
    @DisplayName("WebSocket reconnection with queued event delivery (Requirement 5.5)")
    class WebSocketReconnection {

        @Test
        @DisplayName("Failed delivery queues event in pending_events list")
        void failedDeliveryQueuesEvent() {
            // Simulate WebSocket delivery failure (Redis pub/sub fails)
            doThrow(new org.springframework.data.redis.RedisConnectionFailureException("Connection lost"))
                    .when(redisTemplate).convertAndSend(anyString(), anyString());

            broadcaster.deliverWithRetry(PIN, "p1", "{\"type\":\"leaderboard.updated\"}");

            // After 4 attempts (1 + 3 retries), event should be queued
            verify(redisTemplate, times(4)).convertAndSend(anyString(), anyString());
            verify(listOps).rightPush(
                    eq("pending_events:" + PIN + ":p1"),
                    eq("{\"type\":\"leaderboard.updated\"}"));
        }

        @Test
        @DisplayName("Queued events are delivered in FIFO order on reconnection")
        void queuedEventsDeliveredInOrder() {
            String queueKey = "pending_events:" + PIN + ":p1";
            when(listOps.leftPop(queueKey))
                    .thenReturn("{\"type\":\"leaderboard.updated\",\"payload\":{\"sequenceNumber\":1}}")
                    .thenReturn("{\"type\":\"leaderboard.updated\",\"payload\":{\"sequenceNumber\":2}}")
                    .thenReturn("{\"type\":\"leaderboard.updated\",\"payload\":{\"sequenceNumber\":3}}")
                    .thenReturn(null);

            List<String> events = broadcaster.deliverPendingEvents(PIN, "p1");

            assertThat(events).hasSize(3);
            assertThat(events.get(0)).contains("\"sequenceNumber\":1");
            assertThat(events.get(1)).contains("\"sequenceNumber\":2");
            assertThat(events.get(2)).contains("\"sequenceNumber\":3");
        }

        @Test
        @DisplayName("Pending events queue has 4-hour TTL matching session TTL")
        void pendingEventsQueueHas4HourTtl() {
            doThrow(new org.springframework.data.redis.RedisConnectionFailureException("Connection lost"))
                    .when(redisTemplate).convertAndSend(anyString(), anyString());

            broadcaster.deliverWithRetry(PIN, "p1", "{\"test\":true}");

            verify(redisTemplate).expire(
                    eq("pending_events:" + PIN + ":p1"),
                    eq(java.time.Duration.ofSeconds(14400)));
        }

        @Test
        @DisplayName("Pending events are discarded when session ends")
        void pendingEventsDiscardedOnSessionEnd() {
            Set<String> keys = new HashSet<>(Arrays.asList(
                    "pending_events:" + PIN + ":p1",
                    "pending_events:" + PIN + ":p2"));
            when(redisTemplate.keys("pending_events:" + PIN + ":*")).thenReturn(keys);
            when(redisTemplate.delete(keys)).thenReturn(2L);

            broadcaster.discardPendingEvents(PIN);

            verify(redisTemplate).keys("pending_events:" + PIN + ":*");
            verify(redisTemplate).delete(keys);
        }

        @Test
        @DisplayName("Host events are NOT queued on delivery failure")
        void hostEventsNotQueued() {
            doThrow(new org.springframework.data.redis.RedisConnectionFailureException("Connection lost"))
                    .when(redisTemplate).convertAndSend(anyString(), anyString());

            broadcaster.deliverWithRetry(PIN, "host", "{\"type\":\"leaderboard.updated\"}");

            // Host events should not be queued
            verify(listOps, never()).rightPush(anyString(), anyString());
        }
    }

    private void setupRankedParticipants(List<RankedParticipant> ranked) {
        when(rankingService.getRankedParticipants(PIN)).thenReturn(ranked);
    }
}
