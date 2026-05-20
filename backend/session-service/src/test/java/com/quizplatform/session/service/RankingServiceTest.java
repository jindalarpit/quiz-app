package com.quizplatform.session.service;

import com.quizplatform.session.dto.RankedParticipant;
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

import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RankingService.
 * 
 * Tests the ranking algorithm with tiebreakers:
 * 1. Cumulative score (descending)
 * 2. Average response time (ascending)
 * 3. Most recent answer timestamp (ascending)
 * 
 * **Validates: Requirements 4.6, 4.7**
 */
@ExtendWith(MockitoExtension.class)
class RankingServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    private RankingService rankingService;

    private static final String PIN = "ABC123";

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        rankingService = new RankingService(redisTemplate);
    }

    @Nested
    @DisplayName("Composite Score Calculation")
    class CompositeScoreCalculation {

        @Test
        @DisplayName("Should calculate composite score correctly")
        void shouldCalculateCompositeScoreCorrectly() {
            // Given
            long cumulativeScore = 1000;
            long avgResponseTimeMs = 500;

            // When
            double compositeScore = rankingService.calculateCompositeScore(cumulativeScore, avgResponseTimeMs);

            // Then
            // Expected: 1000 * 1_000_000 + (999_999_999 - 500) = 1_000_000_000 + 999_999_499 = 1_999_999_499
            double expected = 1000.0 * RankingService.SCORE_MULTIPLIER + (RankingService.MAX_TIME - 500);
            assertThat(compositeScore).isEqualTo(expected);
        }

        @Test
        @DisplayName("Should handle zero cumulative score")
        void shouldHandleZeroCumulativeScore() {
            // Given
            long cumulativeScore = 0;
            long avgResponseTimeMs = 1000;

            // When
            double compositeScore = rankingService.calculateCompositeScore(cumulativeScore, avgResponseTimeMs);

            // Then
            double expected = RankingService.MAX_TIME - 1000;
            assertThat(compositeScore).isEqualTo(expected);
        }

        @Test
        @DisplayName("Should clamp negative avgResponseTimeMs to zero")
        void shouldClampNegativeAvgResponseTime() {
            // Given
            long cumulativeScore = 100;
            long avgResponseTimeMs = -500;

            // When
            double compositeScore = rankingService.calculateCompositeScore(cumulativeScore, avgResponseTimeMs);

            // Then
            // Clamped to 0, so: 100 * 1_000_000 + (MAX_TIME - 0)
            double expected = 100.0 * RankingService.SCORE_MULTIPLIER + RankingService.MAX_TIME;
            assertThat(compositeScore).isEqualTo(expected);
        }

        @Test
        @DisplayName("Should clamp avgResponseTimeMs exceeding MAX_TIME")
        void shouldClampExcessiveAvgResponseTime() {
            // Given
            long cumulativeScore = 100;
            long avgResponseTimeMs = RankingService.MAX_TIME + 1000;

            // When
            double compositeScore = rankingService.calculateCompositeScore(cumulativeScore, avgResponseTimeMs);

            // Then
            // Clamped to MAX_TIME, so: 100 * 1_000_000 + (MAX_TIME - MAX_TIME) = 100_000_000
            double expected = 100.0 * RankingService.SCORE_MULTIPLIER;
            assertThat(compositeScore).isEqualTo(expected);
        }

        @Test
        @DisplayName("Higher cumulative score should always produce higher composite score")
        void higherCumulativeScoreShouldProduceHigherCompositeScore() {
            // Given
            long score1 = 1000;
            long score2 = 999;
            long avgTime = 500;

            // When
            double composite1 = rankingService.calculateCompositeScore(score1, avgTime);
            double composite2 = rankingService.calculateCompositeScore(score2, avgTime);

            // Then
            assertThat(composite1).isGreaterThan(composite2);
        }

        @Test
        @DisplayName("Lower avgResponseTime should produce higher composite score for same cumulative score")
        void lowerAvgResponseTimeShouldProduceHigherCompositeScore() {
            // Given
            long cumulativeScore = 1000;
            long fastTime = 100;
            long slowTime = 500;

            // When
            double compositeFast = rankingService.calculateCompositeScore(cumulativeScore, fastTime);
            double compositeSlow = rankingService.calculateCompositeScore(cumulativeScore, slowTime);

            // Then
            assertThat(compositeFast).isGreaterThan(compositeSlow);
        }
    }

    @Nested
    @DisplayName("Response Time Recording")
    class ResponseTimeRecording {

        @Test
        @DisplayName("Should record response time correctly")
        void shouldRecordResponseTimeCorrectly() {
            // Given
            String participantId = "participant-1";
            long responseTimeMs = 1500;
            long answerTimestamp = System.currentTimeMillis();

            // When
            rankingService.recordResponseTime(PIN, participantId, responseTimeMs, answerTimestamp);

            // Then
            String participantKey = "participant:" + PIN + ":" + participantId;
            verify(hashOperations).increment(participantKey, "total_response_time_ms", responseTimeMs);
            verify(hashOperations).increment(participantKey, "answered_rounds", 1);
            verify(hashOperations).put(participantKey, "last_answer_time", String.valueOf(answerTimestamp));
            verify(redisTemplate).expire(eq(participantKey), eq(Duration.ofSeconds(14400)));
        }
    }

    @Nested
    @DisplayName("Average Response Time Calculation")
    class AverageResponseTimeCalculation {

        @Test
        @DisplayName("Should calculate average response time correctly")
        void shouldCalculateAverageResponseTimeCorrectly() {
            // Given
            String participantId = "participant-1";
            String participantKey = "participant:" + PIN + ":" + participantId;
            when(hashOperations.get(participantKey, "total_response_time_ms")).thenReturn("3000");
            when(hashOperations.get(participantKey, "answered_rounds")).thenReturn("3");

            // When
            long avgTime = rankingService.calculateAverageResponseTimeMs(PIN, participantId);

            // Then
            assertThat(avgTime).isEqualTo(1000); // 3000 / 3 = 1000
        }

        @Test
        @DisplayName("Should return MAX_TIME for participant with no answered rounds")
        void shouldReturnMaxTimeForNoAnsweredRounds() {
            // Given
            String participantId = "participant-1";
            String participantKey = "participant:" + PIN + ":" + participantId;
            when(hashOperations.get(participantKey, "total_response_time_ms")).thenReturn("0");
            when(hashOperations.get(participantKey, "answered_rounds")).thenReturn("0");

            // When
            long avgTime = rankingService.calculateAverageResponseTimeMs(PIN, participantId);

            // Then
            assertThat(avgTime).isEqualTo(RankingService.MAX_TIME);
        }

        @Test
        @DisplayName("Should return MAX_TIME for null answered rounds")
        void shouldReturnMaxTimeForNullAnsweredRounds() {
            // Given
            String participantId = "participant-1";
            String participantKey = "participant:" + PIN + ":" + participantId;
            when(hashOperations.get(participantKey, "total_response_time_ms")).thenReturn(null);
            when(hashOperations.get(participantKey, "answered_rounds")).thenReturn(null);

            // When
            long avgTime = rankingService.calculateAverageResponseTimeMs(PIN, participantId);

            // Then
            assertThat(avgTime).isEqualTo(RankingService.MAX_TIME);
        }
    }

    @Nested
    @DisplayName("Ranking Comparator")
    class RankingComparatorTests {

        @Test
        @DisplayName("Should rank by cumulative score descending")
        void shouldRankByCumulativeScoreDescending() {
            // Given
            RankedParticipant p1 = RankedParticipant.builder()
                    .participantId("p1")
                    .cumulativeScore(1000)
                    .avgResponseTimeMs(500)
                    .lastAnswerTime(1000)
                    .build();
            RankedParticipant p2 = RankedParticipant.builder()
                    .participantId("p2")
                    .cumulativeScore(2000)
                    .avgResponseTimeMs(500)
                    .lastAnswerTime(1000)
                    .build();

            List<RankedParticipant> participants = new ArrayList<>(Arrays.asList(p1, p2));

            // When
            participants.sort(rankingService.getRankingComparator());

            // Then
            assertThat(participants.get(0).getParticipantId()).isEqualTo("p2"); // Higher score first
            assertThat(participants.get(1).getParticipantId()).isEqualTo("p1");
        }

        @Test
        @DisplayName("Should use avgResponseTime as first tiebreaker")
        void shouldUseAvgResponseTimeAsFirstTiebreaker() {
            // Given - same cumulative score, different avg response times
            RankedParticipant p1 = RankedParticipant.builder()
                    .participantId("p1")
                    .cumulativeScore(1000)
                    .avgResponseTimeMs(1000) // Slower
                    .lastAnswerTime(1000)
                    .build();
            RankedParticipant p2 = RankedParticipant.builder()
                    .participantId("p2")
                    .cumulativeScore(1000)
                    .avgResponseTimeMs(500) // Faster
                    .lastAnswerTime(1000)
                    .build();

            List<RankedParticipant> participants = new ArrayList<>(Arrays.asList(p1, p2));

            // When
            participants.sort(rankingService.getRankingComparator());

            // Then
            assertThat(participants.get(0).getParticipantId()).isEqualTo("p2"); // Faster first
            assertThat(participants.get(1).getParticipantId()).isEqualTo("p1");
        }

        @Test
        @DisplayName("Should use lastAnswerTime as second tiebreaker")
        void shouldUseLastAnswerTimeAsSecondTiebreaker() {
            // Given - same cumulative score and avg response time, different last answer times
            RankedParticipant p1 = RankedParticipant.builder()
                    .participantId("p1")
                    .cumulativeScore(1000)
                    .avgResponseTimeMs(500)
                    .lastAnswerTime(2000) // Later
                    .build();
            RankedParticipant p2 = RankedParticipant.builder()
                    .participantId("p2")
                    .cumulativeScore(1000)
                    .avgResponseTimeMs(500)
                    .lastAnswerTime(1000) // Earlier
                    .build();

            List<RankedParticipant> participants = new ArrayList<>(Arrays.asList(p1, p2));

            // When
            participants.sort(rankingService.getRankingComparator());

            // Then
            assertThat(participants.get(0).getParticipantId()).isEqualTo("p2"); // Earlier first
            assertThat(participants.get(1).getParticipantId()).isEqualTo("p1");
        }

        @Test
        @DisplayName("Should produce deterministic ordering for identical participants")
        void shouldProduceDeterministicOrdering() {
            // Given - completely identical values
            RankedParticipant p1 = RankedParticipant.builder()
                    .participantId("p1")
                    .cumulativeScore(1000)
                    .avgResponseTimeMs(500)
                    .lastAnswerTime(1000)
                    .build();
            RankedParticipant p2 = RankedParticipant.builder()
                    .participantId("p2")
                    .cumulativeScore(1000)
                    .avgResponseTimeMs(500)
                    .lastAnswerTime(1000)
                    .build();

            List<RankedParticipant> participants1 = new ArrayList<>(Arrays.asList(p1, p2));
            List<RankedParticipant> participants2 = new ArrayList<>(Arrays.asList(p2, p1));

            // When
            participants1.sort(rankingService.getRankingComparator());
            participants2.sort(rankingService.getRankingComparator());

            // Then - both should produce the same order
            assertThat(participants1.get(0).getParticipantId())
                    .isEqualTo(participants2.get(0).getParticipantId());
            assertThat(participants1.get(1).getParticipantId())
                    .isEqualTo(participants2.get(1).getParticipantId());
        }
    }

    @Nested
    @DisplayName("Rank Delta Computation")
    class RankDeltaComputation {

        @Test
        @DisplayName("Should compute positive rank delta for upward movement")
        void shouldComputePositiveRankDeltaForUpwardMovement() {
            // Given
            Map<String, Integer> previousRanks = new HashMap<>();
            previousRanks.put("p1", 3); // Was rank 3

            // Mock current ranking where p1 is now rank 1
            Set<ZSetOperations.TypedTuple<String>> results = new LinkedHashSet<>();
            results.add(createTuple("p1", 1000.0 * RankingService.SCORE_MULTIPLIER + RankingService.MAX_TIME - 500));
            when(zSetOperations.reverseRangeWithScores(anyString(), eq(0L), eq(-1L))).thenReturn(results);
            
            mockParticipantData("p1", "0", "0", "1000", "Player1");

            // When
            Map<String, Integer> deltas = rankingService.computeRankDeltas(PIN, previousRanks);

            // Then
            assertThat(deltas.get("p1")).isEqualTo(2); // 3 - 1 = +2 (moved up)
        }

        @Test
        @DisplayName("Should compute negative rank delta for downward movement")
        void shouldComputeNegativeRankDeltaForDownwardMovement() {
            // Given
            Map<String, Integer> previousRanks = new HashMap<>();
            previousRanks.put("p1", 1); // Was rank 1

            // Mock current ranking where p1 is now rank 3
            Set<ZSetOperations.TypedTuple<String>> results = new LinkedHashSet<>();
            results.add(createTuple("p2", 2000.0 * RankingService.SCORE_MULTIPLIER));
            results.add(createTuple("p3", 1500.0 * RankingService.SCORE_MULTIPLIER));
            results.add(createTuple("p1", 1000.0 * RankingService.SCORE_MULTIPLIER));
            when(zSetOperations.reverseRangeWithScores(anyString(), eq(0L), eq(-1L))).thenReturn(results);
            
            mockParticipantData("p1", "0", "0", "1000", "Player1");
            mockParticipantData("p2", "0", "0", "1000", "Player2");
            mockParticipantData("p3", "0", "0", "1000", "Player3");

            // When
            Map<String, Integer> deltas = rankingService.computeRankDeltas(PIN, previousRanks);

            // Then
            assertThat(deltas.get("p1")).isEqualTo(-2); // 1 - 3 = -2 (moved down)
        }

        @Test
        @DisplayName("Should compute zero rank delta for unchanged position")
        void shouldComputeZeroRankDeltaForUnchangedPosition() {
            // Given
            Map<String, Integer> previousRanks = new HashMap<>();
            previousRanks.put("p1", 2); // Was rank 2

            // Mock current ranking where p1 is still rank 2
            Set<ZSetOperations.TypedTuple<String>> results = new LinkedHashSet<>();
            results.add(createTuple("p2", 2000.0 * RankingService.SCORE_MULTIPLIER));
            results.add(createTuple("p1", 1000.0 * RankingService.SCORE_MULTIPLIER));
            when(zSetOperations.reverseRangeWithScores(anyString(), eq(0L), eq(-1L))).thenReturn(results);
            
            mockParticipantData("p1", "0", "0", "1000", "Player1");
            mockParticipantData("p2", "0", "0", "1000", "Player2");

            // When
            Map<String, Integer> deltas = rankingService.computeRankDeltas(PIN, previousRanks);

            // Then
            assertThat(deltas.get("p1")).isEqualTo(0); // 2 - 2 = 0 (unchanged)
        }

        @Test
        @DisplayName("Should set rank delta to zero for late-joining participant")
        void shouldSetRankDeltaToZeroForLateJoiningParticipant() {
            // Given - empty previous ranks (first round for this participant)
            Map<String, Integer> previousRanks = new HashMap<>();

            // Mock current ranking
            Set<ZSetOperations.TypedTuple<String>> results = new LinkedHashSet<>();
            results.add(createTuple("p1", 1000.0 * RankingService.SCORE_MULTIPLIER));
            when(zSetOperations.reverseRangeWithScores(anyString(), eq(0L), eq(-1L))).thenReturn(results);
            
            mockParticipantData("p1", "0", "0", "1000", "Player1");

            // When
            Map<String, Integer> deltas = rankingService.computeRankDeltas(PIN, previousRanks);

            // Then
            assertThat(deltas.get("p1")).isEqualTo(0); // Late-joining = 0 delta
        }
    }

    @Nested
    @DisplayName("Late-Joining Participant Handling")
    class LateJoiningParticipantHandling {

        @Test
        @DisplayName("Should initialize late-joining participant with worst tiebreaker values")
        void shouldInitializeLateJoiningParticipantWithWorstTiebreakerValues() {
            // Given
            String participantId = "late-joiner";
            String participantKey = "participant:" + PIN + ":" + participantId;
            
            when(hashOperations.putIfAbsent(anyString(), anyString(), anyString())).thenReturn(true);

            // When
            rankingService.initializeLateJoiningParticipant(PIN, participantId);

            // Then
            verify(hashOperations).putIfAbsent(participantKey, "total_response_time_ms", "0");
            verify(hashOperations).putIfAbsent(participantKey, "answered_rounds", "0");
            verify(hashOperations).putIfAbsent(participantKey, "last_answer_time", String.valueOf(Long.MAX_VALUE));
        }
    }

    // Helper methods

    private ZSetOperations.TypedTuple<String> createTuple(String value, double score) {
        return new ZSetOperations.TypedTuple<String>() {
            @Override
            public String getValue() {
                return value;
            }

            @Override
            public Double getScore() {
                return score;
            }

            @Override
            public int compareTo(ZSetOperations.TypedTuple<String> o) {
                return Double.compare(score, o.getScore() != null ? o.getScore() : 0);
            }
        };
    }

    private void mockParticipantData(String participantId, String totalResponseTime, 
                                      String answeredRounds, String lastAnswerTime, String nickname) {
        String key = "participant:" + PIN + ":" + participantId;
        when(hashOperations.get(key, "total_response_time_ms")).thenReturn(totalResponseTime);
        when(hashOperations.get(key, "answered_rounds")).thenReturn(answeredRounds);
        when(hashOperations.get(key, "last_answer_time")).thenReturn(lastAnswerTime);
        when(hashOperations.get(key, "nickname")).thenReturn(nickname);
    }
}
