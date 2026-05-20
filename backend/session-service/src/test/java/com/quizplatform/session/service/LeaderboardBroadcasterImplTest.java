package com.quizplatform.session.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizplatform.session.dto.ParticipantRoundScore;
import com.quizplatform.session.dto.RoundResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LeaderboardBroadcasterImpl.
 *
 * Tests host view construction, participant view construction,
 * sequence number monotonicity, retry logic, and pending event queuing.
 *
 * Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.7
 */
@ExtendWith(MockitoExtension.class)
class LeaderboardBroadcasterImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ListOperations<String, String> listOperations;

    private ObjectMapper objectMapper;
    private LeaderboardBroadcasterImpl broadcaster;

    private static final String PIN = "ABC123";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        lenient().when(redisTemplate.opsForList()).thenReturn(listOperations);
        broadcaster = new LeaderboardBroadcasterImpl(redisTemplate, objectMapper);
    }

    @Nested
    @DisplayName("Host View Construction")
    class HostViewConstruction {

        @Test
        @DisplayName("Should return top 5 entries when more than 5 participants")
        void shouldReturnTop5EntriesWhenMoreThan5Participants() {
            List<ParticipantRoundScore> allScores = createParticipants(8);

            List<ParticipantRoundScore> hostView = broadcaster.constructHostView(allScores);

            assertThat(hostView).hasSize(5);
            assertThat(hostView.get(0).getRank()).isEqualTo(1);
            assertThat(hostView.get(4).getRank()).isEqualTo(5);
        }

        @Test
        @DisplayName("Should return all entries when fewer than 5 participants")
        void shouldReturnAllEntriesWhenFewerThan5Participants() {
            List<ParticipantRoundScore> allScores = createParticipants(3);

            List<ParticipantRoundScore> hostView = broadcaster.constructHostView(allScores);

            assertThat(hostView).hasSize(3);
        }

        @Test
        @DisplayName("Should return entries ordered by rank ascending")
        void shouldReturnEntriesOrderedByRankAscending() {
            List<ParticipantRoundScore> allScores = createParticipants(5);
            Collections.shuffle(allScores); // Shuffle to test ordering

            List<ParticipantRoundScore> hostView = broadcaster.constructHostView(allScores);

            for (int i = 0; i < hostView.size() - 1; i++) {
                assertThat(hostView.get(i).getRank())
                        .isLessThan(hostView.get(i + 1).getRank());
            }
        }

        @Test
        @DisplayName("Should include all fields including rank_delta")
        void shouldIncludeAllFieldsIncludingRankDelta() {
            ParticipantRoundScore score = ParticipantRoundScore.builder()
                    .participantId("p1")
                    .nickname("Player1")
                    .cumulativeScore(4500)
                    .roundScore(920)
                    .rank(1)
                    .rankDelta(2)
                    .streakCount(4)
                    .streakMultiplier(2)
                    .timeTakenMs(2340L)
                    .correct(true)
                    .connected(true)
                    .build();
            List<ParticipantRoundScore> allScores = Collections.singletonList(score);

            List<ParticipantRoundScore> hostView = broadcaster.constructHostView(allScores);

            assertThat(hostView).hasSize(1);
            ParticipantRoundScore entry = hostView.get(0);
            assertThat(entry.getParticipantId()).isEqualTo("p1");
            assertThat(entry.getNickname()).isEqualTo("Player1");
            assertThat(entry.getCumulativeScore()).isEqualTo(4500);
            assertThat(entry.getRoundScore()).isEqualTo(920);
            assertThat(entry.getRank()).isEqualTo(1);
            assertThat(entry.getRankDelta()).isEqualTo(2);
            assertThat(entry.getStreakCount()).isEqualTo(4);
            assertThat(entry.getStreakMultiplier()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should return exactly 5 entries when exactly 5 participants")
        void shouldReturnExactly5EntriesWhenExactly5Participants() {
            List<ParticipantRoundScore> allScores = createParticipants(5);

            List<ParticipantRoundScore> hostView = broadcaster.constructHostView(allScores);

            assertThat(hostView).hasSize(5);
        }
    }

    @Nested
    @DisplayName("Participant View Construction")
    class ParticipantViewConstruction {

        @Test
        @DisplayName("Should return top 5 only when participant is in top 5")
        void shouldReturnTop5OnlyWhenParticipantIsInTop5() {
            List<ParticipantRoundScore> allScores = createParticipants(10);
            ParticipantRoundScore participant = allScores.get(2); // Rank 3

            List<ParticipantRoundScore> view =
                    broadcaster.constructParticipantView(allScores, participant);

            assertThat(view).hasSize(5);
            assertThat(view.stream().mapToInt(ParticipantRoundScore::getRank).max().orElse(0))
                    .isEqualTo(5);
        }

        @Test
        @DisplayName("Should include own entry with 1 above and 1 below when ranked below 5th")
        void shouldIncludeOwnEntryWithContextWhenRankedBelow5th() {
            List<ParticipantRoundScore> allScores = createParticipants(10);
            ParticipantRoundScore participant = allScores.get(6); // Rank 7

            List<ParticipantRoundScore> view =
                    broadcaster.constructParticipantView(allScores, participant);

            // Should have: top 5 + rank 6 (above) + rank 7 (self) + rank 8 (below)
            assertThat(view).hasSize(8);
            Set<Integer> ranks = new HashSet<>();
            for (ParticipantRoundScore s : view) {
                ranks.add(s.getRank());
            }
            assertThat(ranks).contains(1, 2, 3, 4, 5, 6, 7, 8);
        }

        @Test
        @DisplayName("Should omit above when participant is ranked 6th (already in top 5)")
        void shouldOmitAboveWhenParticipantIsRanked6th() {
            List<ParticipantRoundScore> allScores = createParticipants(10);
            ParticipantRoundScore participant = allScores.get(5); // Rank 6

            List<ParticipantRoundScore> view =
                    broadcaster.constructParticipantView(allScores, participant);

            // Top 5 + rank 6 (self) + rank 7 (below)
            // Rank 5 is already in top 5, so "above" is already included
            assertThat(view).hasSize(7);
            Set<Integer> ranks = new HashSet<>();
            for (ParticipantRoundScore s : view) {
                ranks.add(s.getRank());
            }
            assertThat(ranks).contains(1, 2, 3, 4, 5, 6, 7);
        }

        @Test
        @DisplayName("Should omit below when participant is ranked last")
        void shouldOmitBelowWhenParticipantIsRankedLast() {
            List<ParticipantRoundScore> allScores = createParticipants(10);
            ParticipantRoundScore participant = allScores.get(9); // Rank 10 (last)

            List<ParticipantRoundScore> view =
                    broadcaster.constructParticipantView(allScores, participant);

            // Top 5 + rank 9 (above) + rank 10 (self), no below
            assertThat(view).hasSize(7);
            Set<Integer> ranks = new HashSet<>();
            for (ParticipantRoundScore s : view) {
                ranks.add(s.getRank());
            }
            assertThat(ranks).contains(1, 2, 3, 4, 5, 9, 10);
        }

        @Test
        @DisplayName("Should return all entries when fewer than 5 participants")
        void shouldReturnAllEntriesWhenFewerThan5Participants() {
            List<ParticipantRoundScore> allScores = createParticipants(3);
            ParticipantRoundScore participant = allScores.get(2); // Rank 3

            List<ParticipantRoundScore> view =
                    broadcaster.constructParticipantView(allScores, participant);

            assertThat(view).hasSize(3);
        }

        @Test
        @DisplayName("Should never exceed 8 entries in participant view")
        void shouldNeverExceed8EntriesInParticipantView() {
            List<ParticipantRoundScore> allScores = createParticipants(100);
            // Test for a participant in the middle
            ParticipantRoundScore participant = allScores.get(49); // Rank 50

            List<ParticipantRoundScore> view =
                    broadcaster.constructParticipantView(allScores, participant);

            assertThat(view.size()).isLessThanOrEqualTo(8);
        }

        @Test
        @DisplayName("Should return view sorted by rank ascending")
        void shouldReturnViewSortedByRankAscending() {
            List<ParticipantRoundScore> allScores = createParticipants(10);
            ParticipantRoundScore participant = allScores.get(7); // Rank 8

            List<ParticipantRoundScore> view =
                    broadcaster.constructParticipantView(allScores, participant);

            for (int i = 0; i < view.size() - 1; i++) {
                assertThat(view.get(i).getRank())
                        .isLessThan(view.get(i + 1).getRank());
            }
        }
    }

    @Nested
    @DisplayName("Sequence Number")
    class SequenceNumber {

        @Test
        @DisplayName("Should produce monotonically increasing sequence numbers")
        void shouldProduceMonotonicallyIncreasingSequenceNumbers() {
            long seq1 = broadcaster.getNextSequenceNumber(PIN);
            long seq2 = broadcaster.getNextSequenceNumber(PIN);
            long seq3 = broadcaster.getNextSequenceNumber(PIN);

            assertThat(seq1).isLessThan(seq2);
            assertThat(seq2).isLessThan(seq3);
        }

        @Test
        @DisplayName("Should start at 1 for new session")
        void shouldStartAt1ForNewSession() {
            long seq = broadcaster.getNextSequenceNumber("NEW_PIN");

            assertThat(seq).isEqualTo(1);
        }

        @Test
        @DisplayName("Should maintain separate counters per session")
        void shouldMaintainSeparateCountersPerSession() {
            long seqA1 = broadcaster.getNextSequenceNumber("PIN_A");
            long seqA2 = broadcaster.getNextSequenceNumber("PIN_A");
            long seqB1 = broadcaster.getNextSequenceNumber("PIN_B");

            assertThat(seqA1).isEqualTo(1);
            assertThat(seqA2).isEqualTo(2);
            assertThat(seqB1).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Event Payload Construction")
    class EventPayloadConstruction {

        @Test
        @DisplayName("Should build valid JSON payload with all required fields")
        void shouldBuildValidJsonPayloadWithAllRequiredFields() throws Exception {
            RoundResult result = createRoundResult(3);
            List<ParticipantRoundScore> entries = createParticipants(2);

            String payload = broadcaster.buildEventPayload(PIN, result, entries, 7);

            assertThat(payload).isNotNull();
            Map<String, Object> parsed = objectMapper.readValue(payload, Map.class);
            assertThat(parsed.get("type")).isEqualTo("leaderboard.updated");

            Map<String, Object> payloadMap = (Map<String, Object>) parsed.get("payload");
            assertThat(payloadMap.get("sessionId")).isEqualTo(PIN);
            assertThat(payloadMap.get("roundNumber")).isEqualTo(3);
            assertThat(payloadMap.get("sequenceNumber")).isEqualTo(7);
            assertThat(payloadMap.get("timestamp")).isNotNull();

            List<Map<String, Object>> entryList =
                    (List<Map<String, Object>>) payloadMap.get("entries");
            assertThat(entryList).hasSize(2);
        }

        @Test
        @DisplayName("Should include all entry fields in payload")
        void shouldIncludeAllEntryFieldsInPayload() throws Exception {
            RoundResult result = createRoundResult(1);
            ParticipantRoundScore score = ParticipantRoundScore.builder()
                    .participantId("p1")
                    .nickname("TestPlayer")
                    .cumulativeScore(3000)
                    .roundScore(800)
                    .rank(2)
                    .rankDelta(-1)
                    .streakCount(3)
                    .streakMultiplier(2)
                    .build();

            String payload = broadcaster.buildEventPayload(
                    PIN, result, Collections.singletonList(score), 1);

            Map<String, Object> parsed = objectMapper.readValue(payload, Map.class);
            Map<String, Object> payloadMap = (Map<String, Object>) parsed.get("payload");
            List<Map<String, Object>> entries =
                    (List<Map<String, Object>>) payloadMap.get("entries");

            Map<String, Object> entry = entries.get(0);
            assertThat(entry.get("participantId")).isEqualTo("p1");
            assertThat(entry.get("nickname")).isEqualTo("TestPlayer");
            assertThat(entry.get("cumulativeScore")).isEqualTo(3000);
            assertThat(entry.get("roundScore")).isEqualTo(800);
            assertThat(entry.get("rank")).isEqualTo(2);
            assertThat(entry.get("rankDelta")).isEqualTo(-1);
            assertThat(entry.get("streakCount")).isEqualTo(3);
            assertThat(entry.get("streakMultiplier")).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Retry and Queuing Logic")
    class RetryAndQueuingLogic {

        @Test
        @DisplayName("Should deliver successfully on first attempt")
        void shouldDeliverSuccessfullyOnFirstAttempt() {
            broadcaster.deliverWithRetry(PIN, "p1", "{\"test\":true}");

            verify(redisTemplate).convertAndSend(
                    eq("session:" + PIN + ":p1"), eq("{\"test\":true}"));
        }

        @Test
        @DisplayName("Should retry on failure and succeed on second attempt")
        void shouldRetryOnFailureAndSucceedOnSecondAttempt() {
            doThrow(new RedisConnectionFailureException("Connection failed"))
                    .doNothing()
                    .when(redisTemplate).convertAndSend(anyString(), anyString());

            broadcaster.deliverWithRetry(PIN, "p1", "{\"test\":true}");

            verify(redisTemplate, times(2)).convertAndSend(anyString(), anyString());
            // Should NOT queue since delivery eventually succeeded
            verify(listOperations, never()).rightPush(anyString(), anyString());
        }

        @Test
        @DisplayName("Should queue event after all retries exhausted")
        void shouldQueueEventAfterAllRetriesExhausted() {
            doThrow(new RedisConnectionFailureException("Connection failed"))
                    .when(redisTemplate).convertAndSend(anyString(), anyString());

            broadcaster.deliverWithRetry(PIN, "p1", "{\"test\":true}");

            // 4 attempts total (1 initial + 3 retries)
            verify(redisTemplate, times(4)).convertAndSend(anyString(), anyString());
            // Should queue the event
            verify(listOperations).rightPush(
                    eq("pending_events:" + PIN + ":p1"), eq("{\"test\":true}"));
            verify(redisTemplate).expire(
                    eq("pending_events:" + PIN + ":p1"),
                    eq(Duration.ofSeconds(14400)));
        }

        @Test
        @DisplayName("Should not queue host events on failure")
        void shouldNotQueueHostEventsOnFailure() {
            doThrow(new RedisConnectionFailureException("Connection failed"))
                    .when(redisTemplate).convertAndSend(anyString(), anyString());

            broadcaster.deliverWithRetry(PIN, "host", "{\"test\":true}");

            // Should NOT queue host events
            verify(listOperations, never()).rightPush(anyString(), anyString());
        }

        @Test
        @DisplayName("Should set 4-hour TTL on pending events queue")
        void shouldSet4HourTtlOnPendingEventsQueue() {
            doThrow(new RedisConnectionFailureException("Connection failed"))
                    .when(redisTemplate).convertAndSend(anyString(), anyString());

            broadcaster.deliverWithRetry(PIN, "p1", "{\"test\":true}");

            verify(redisTemplate).expire(
                    eq("pending_events:" + PIN + ":p1"),
                    eq(Duration.ofHours(4)));
        }
    }

    @Nested
    @DisplayName("Discard Pending Events")
    class DiscardPendingEvents {

        @Test
        @DisplayName("Should delete all pending event queues for session")
        void shouldDeleteAllPendingEventQueuesForSession() {
            Set<String> keys = new HashSet<>(Arrays.asList(
                    "pending_events:" + PIN + ":p1",
                    "pending_events:" + PIN + ":p2"
            ));
            when(redisTemplate.keys("pending_events:" + PIN + ":*")).thenReturn(keys);
            when(redisTemplate.delete(keys)).thenReturn(2L);

            broadcaster.discardPendingEvents(PIN);

            verify(redisTemplate).keys("pending_events:" + PIN + ":*");
            verify(redisTemplate).delete(keys);
        }

        @Test
        @DisplayName("Should handle no pending events gracefully")
        void shouldHandleNoPendingEventsGracefully() {
            when(redisTemplate.keys("pending_events:" + PIN + ":*"))
                    .thenReturn(Collections.emptySet());

            broadcaster.discardPendingEvents(PIN);

            verify(redisTemplate, never()).delete(anyCollection());
        }

        @Test
        @DisplayName("Should clean up sequence counter on discard")
        void shouldCleanUpSequenceCounterOnDiscard() {
            // Generate some sequence numbers
            broadcaster.getNextSequenceNumber(PIN);
            broadcaster.getNextSequenceNumber(PIN);
            assertThat(broadcaster.getCurrentSequenceNumber(PIN)).isEqualTo(2);

            when(redisTemplate.keys(anyString())).thenReturn(Collections.emptySet());

            broadcaster.discardPendingEvents(PIN);

            // After discard, sequence counter should be reset
            assertThat(broadcaster.getCurrentSequenceNumber(PIN)).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Deliver Pending Events")
    class DeliverPendingEvents {

        @Test
        @DisplayName("Should deliver all queued events on reconnection")
        void shouldDeliverAllQueuedEventsOnReconnection() {
            String queueKey = "pending_events:" + PIN + ":p1";
            when(listOperations.leftPop(queueKey))
                    .thenReturn("{\"event\":1}")
                    .thenReturn("{\"event\":2}")
                    .thenReturn(null);

            List<String> events = broadcaster.deliverPendingEvents(PIN, "p1");

            assertThat(events).hasSize(2);
            assertThat(events.get(0)).isEqualTo("{\"event\":1}");
            assertThat(events.get(1)).isEqualTo("{\"event\":2}");
        }

        @Test
        @DisplayName("Should return empty list when no pending events")
        void shouldReturnEmptyListWhenNoPendingEvents() {
            String queueKey = "pending_events:" + PIN + ":p1";
            when(listOperations.leftPop(queueKey)).thenReturn(null);

            List<String> events = broadcaster.deliverPendingEvents(PIN, "p1");

            assertThat(events).isEmpty();
        }
    }

    @Nested
    @DisplayName("Broadcast Leaderboard Update")
    class BroadcastLeaderboardUpdate {

        @Test
        @DisplayName("Should broadcast to host and all participants")
        void shouldBroadcastToHostAndAllParticipants() {
            RoundResult result = createRoundResult(1);
            result.setParticipantScores(createParticipants(3));

            broadcaster.broadcastLeaderboardUpdate(PIN, result);

            // 1 host broadcast + 3 participant broadcasts = 4 total
            verify(redisTemplate, times(4)).convertAndSend(anyString(), anyString());
        }

        @Test
        @DisplayName("Should handle null result gracefully")
        void shouldHandleNullResultGracefully() {
            broadcaster.broadcastLeaderboardUpdate(PIN, null);

            verify(redisTemplate, never()).convertAndSend(anyString(), anyString());
        }

        @Test
        @DisplayName("Should handle empty participant list gracefully")
        void shouldHandleEmptyParticipantListGracefully() {
            RoundResult result = createRoundResult(1);
            result.setParticipantScores(Collections.emptyList());

            broadcaster.broadcastLeaderboardUpdate(PIN, result);

            verify(redisTemplate, never()).convertAndSend(anyString(), anyString());
        }

        @Test
        @DisplayName("Should include sequence number in broadcast")
        void shouldIncludeSequenceNumberInBroadcast() {
            RoundResult result = createRoundResult(1);
            result.setParticipantScores(createParticipants(1));

            broadcaster.broadcastLeaderboardUpdate(PIN, result);

            // Verify sequence number was incremented
            assertThat(broadcaster.getCurrentSequenceNumber(PIN)).isEqualTo(1);
        }
    }

    // ==================== Helper Methods ====================

    private List<ParticipantRoundScore> createParticipants(int count) {
        List<ParticipantRoundScore> scores = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            scores.add(ParticipantRoundScore.builder()
                    .participantId("p" + i)
                    .nickname("Player" + i)
                    .cumulativeScore(1000 * (count - i + 1))
                    .roundScore(500 - (i * 10))
                    .rank(i)
                    .rankDelta(i % 3 == 0 ? 1 : (i % 3 == 1 ? -1 : 0))
                    .streakCount(i % 5)
                    .streakMultiplier(i >= 3 ? 2 : 1)
                    .timeTakenMs((long) (i * 1000))
                    .correct(i % 2 == 0)
                    .connected(true)
                    .build());
        }
        return scores;
    }

    private RoundResult createRoundResult(int roundNumber) {
        return RoundResult.builder()
                .pin(PIN)
                .roundNumber(roundNumber)
                .timestamp(Instant.now())
                .participantScores(new ArrayList<>())
                .correctAnswer("A")
                .totalAnswered(5)
                .totalCorrect(3)
                .accuracyRate(0.6)
                .computationTimeMs(50)
                .build();
    }
}
