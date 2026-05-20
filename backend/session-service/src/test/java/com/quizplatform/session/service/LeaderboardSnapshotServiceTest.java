package com.quizplatform.session.service;

import com.quizplatform.session.dto.ParticipantRoundScore;
import com.quizplatform.session.dto.RankedParticipant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LeaderboardSnapshotServiceImpl.
 * 
 * Tests snapshot storage, retrieval, rank delta computation, and cleanup.
 * Also tests retry logic with exponential backoff on Redis failures.
 * 
 * **Validates: Requirements 4.1, 4.2, 4.3, 4.5, 4.8**
 */
@ExtendWith(MockitoExtension.class)
class LeaderboardSnapshotServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private RankingService rankingService;

    private LeaderboardSnapshotService snapshotService;

    private static final String PIN = "ABC123";

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        snapshotService = new LeaderboardSnapshotService(redisTemplate, rankingService);
    }

    @Nested
    @DisplayName("Store Snapshot")
    class StoreSnapshot {

        @Test
        @DisplayName("Should store snapshot successfully")
        void shouldStoreSnapshotSuccessfully() {
            // Given
            int roundNumber = 1;
            List<ParticipantRoundScore> entries = Arrays.asList(
                    ParticipantRoundScore.builder()
                            .participantId("p1")
                            .rank(1)
                            .cumulativeScore(1000)
                            .roundScore(500)
                            .build(),
                    ParticipantRoundScore.builder()
                            .participantId("p2")
                            .rank(2)
                            .cumulativeScore(800)
                            .roundScore(400)
                            .build()
            );

            // When
            snapshotService.storeSnapshot(PIN, roundNumber, entries);

            // Then
            String expectedKey = "snapshot:" + PIN + ":" + roundNumber;
            verify(hashOperations).putAll(eq(expectedKey), anyMap());
            verify(redisTemplate).expire(eq(expectedKey), eq(Duration.ofSeconds(14400)));
        }

        @Test
        @DisplayName("Should set TTL to exactly 4 hours (14400 seconds)")
        void shouldSetTtlToFourHours() {
            // Given
            int roundNumber = 3;
            List<ParticipantRoundScore> entries = Collections.singletonList(
                    ParticipantRoundScore.builder()
                            .participantId("p1")
                            .rank(1)
                            .cumulativeScore(500)
                            .roundScore(500)
                            .build()
            );

            // When
            snapshotService.storeSnapshot(PIN, roundNumber, entries);

            // Then - TTL must be exactly 4 hours = 14400 seconds
            String expectedKey = "snapshot:" + PIN + ":" + roundNumber;
            Duration expectedTtl = Duration.ofHours(4);
            assertThat(expectedTtl.getSeconds()).isEqualTo(14400);
            verify(redisTemplate).expire(eq(expectedKey), eq(expectedTtl));
        }

        @Test
        @DisplayName("Should store entries in correct hash format: rank|cumulativeScore|roundScore|avgResponseTimeMs")
        void shouldStoreEntriesInCorrectHashFormat() {
            // Given
            int roundNumber = 1;
            List<ParticipantRoundScore> entries = Collections.singletonList(
                    ParticipantRoundScore.builder()
                            .participantId("p1")
                            .rank(2)
                            .cumulativeScore(1500)
                            .roundScore(750)
                            .timeTakenMs(2500L)
                            .build()
            );

            // When
            snapshotService.storeSnapshot(PIN, roundNumber, entries);

            // Then - verify the hash map contains the correctly formatted value
            @SuppressWarnings("unchecked")
            org.mockito.ArgumentCaptor<Map<String, String>> mapCaptor = 
                    org.mockito.ArgumentCaptor.forClass(Map.class);
            verify(hashOperations).putAll(anyString(), mapCaptor.capture());
            
            Map<String, String> storedMap = mapCaptor.getValue();
            assertThat(storedMap).containsKey("p1");
            assertThat(storedMap.get("p1")).isEqualTo("2|1500|750|2500");
        }

        @Test
        @DisplayName("Should store avgResponseTimeMs as 0 when timeTakenMs is null")
        void shouldStoreZeroAvgResponseTimeWhenTimeTakenIsNull() {
            // Given
            int roundNumber = 1;
            List<ParticipantRoundScore> entries = Collections.singletonList(
                    ParticipantRoundScore.builder()
                            .participantId("p1")
                            .rank(1)
                            .cumulativeScore(1000)
                            .roundScore(500)
                            .timeTakenMs(null)
                            .build()
            );

            // When
            snapshotService.storeSnapshot(PIN, roundNumber, entries);

            // Then
            @SuppressWarnings("unchecked")
            org.mockito.ArgumentCaptor<Map<String, String>> mapCaptor = 
                    org.mockito.ArgumentCaptor.forClass(Map.class);
            verify(hashOperations).putAll(anyString(), mapCaptor.capture());
            
            Map<String, String> storedMap = mapCaptor.getValue();
            assertThat(storedMap.get("p1")).isEqualTo("1|1000|500|0");
        }

        @Test
        @DisplayName("Should handle empty entries list gracefully")
        void shouldHandleEmptyEntriesListGracefully() {
            // Given
            int roundNumber = 1;
            List<ParticipantRoundScore> entries = Collections.emptyList();

            // When
            snapshotService.storeSnapshot(PIN, roundNumber, entries);

            // Then
            verify(hashOperations, never()).putAll(anyString(), anyMap());
        }

        @Test
        @DisplayName("Should handle null entries list gracefully")
        void shouldHandleNullEntriesListGracefully() {
            // When
            snapshotService.storeSnapshot(PIN, 1, null);

            // Then
            verify(hashOperations, never()).putAll(anyString(), anyMap());
        }

        @Test
        @DisplayName("Should retry on Redis failure and succeed on second attempt")
        void shouldRetryOnRedisFailureAndSucceedOnSecondAttempt() {
            // Given
            int roundNumber = 1;
            List<ParticipantRoundScore> entries = Collections.singletonList(
                    ParticipantRoundScore.builder()
                            .participantId("p1")
                            .rank(1)
                            .cumulativeScore(1000)
                            .roundScore(500)
                            .build()
            );

            // First call throws exception, second succeeds
            doThrow(new RedisConnectionFailureException("Connection failed"))
                    .doNothing()
                    .when(hashOperations).putAll(anyString(), anyMap());

            // When
            snapshotService.storeSnapshot(PIN, roundNumber, entries);

            // Then
            verify(hashOperations, times(2)).putAll(anyString(), anyMap());
        }

        @Test
        @DisplayName("Should log error after all retries exhausted")
        void shouldLogErrorAfterAllRetriesExhausted() {
            // Given
            int roundNumber = 1;
            List<ParticipantRoundScore> entries = Collections.singletonList(
                    ParticipantRoundScore.builder()
                            .participantId("p1")
                            .rank(1)
                            .cumulativeScore(1000)
                            .roundScore(500)
                            .build()
            );

            // All calls throw exception
            doThrow(new RedisConnectionFailureException("Connection failed"))
                    .when(hashOperations).putAll(anyString(), anyMap());

            // When
            snapshotService.storeSnapshot(PIN, roundNumber, entries);

            // Then
            verify(hashOperations, times(3)).putAll(anyString(), anyMap()); // 3 retry attempts
        }
    }

    @Nested
    @DisplayName("Get Snapshot")
    class GetSnapshot {

        @Test
        @DisplayName("Should retrieve snapshot successfully")
        void shouldRetrieveSnapshotSuccessfully() {
            // Given
            int roundNumber = 1;
            String key = "snapshot:" + PIN + ":" + roundNumber;
            
            Map<Object, Object> hashEntries = new HashMap<>();
            hashEntries.put("p1", "1|1000|500|1500");
            hashEntries.put("p2", "2|800|400|2000");
            
            when(hashOperations.entries(key)).thenReturn(hashEntries);

            // When
            List<ParticipantRoundScore> result = snapshotService.getSnapshot(PIN, roundNumber);

            // Then
            assertThat(result).hasSize(2);
            
            ParticipantRoundScore p1Entry = result.stream()
                    .filter(e -> e.getParticipantId().equals("p1"))
                    .findFirst()
                    .orElseThrow();
            assertThat(p1Entry.getRank()).isEqualTo(1);
            assertThat(p1Entry.getCumulativeScore()).isEqualTo(1000);
            assertThat(p1Entry.getRoundScore()).isEqualTo(500);
        }

        @Test
        @DisplayName("Should store and retrieve snapshot as a round-trip preserving all data")
        void shouldStoreAndRetrieveSnapshotRoundTrip() {
            // Given - store a snapshot
            int roundNumber = 2;
            String key = "snapshot:" + PIN + ":" + roundNumber;
            List<ParticipantRoundScore> originalEntries = Arrays.asList(
                    ParticipantRoundScore.builder()
                            .participantId("p1")
                            .rank(1)
                            .cumulativeScore(2500)
                            .roundScore(900)
                            .timeTakenMs(1200L)
                            .build(),
                    ParticipantRoundScore.builder()
                            .participantId("p2")
                            .rank(2)
                            .cumulativeScore(2100)
                            .roundScore(700)
                            .timeTakenMs(3400L)
                            .build(),
                    ParticipantRoundScore.builder()
                            .participantId("p3")
                            .rank(3)
                            .cumulativeScore(1800)
                            .roundScore(600)
                            .timeTakenMs(4500L)
                            .build()
            );

            // Capture what gets stored
            @SuppressWarnings("unchecked")
            org.mockito.ArgumentCaptor<Map<String, String>> mapCaptor = 
                    org.mockito.ArgumentCaptor.forClass(Map.class);

            // Store the snapshot
            snapshotService.storeSnapshot(PIN, roundNumber, originalEntries);
            verify(hashOperations).putAll(eq(key), mapCaptor.capture());

            // Simulate Redis returning the stored data
            Map<String, String> storedData = mapCaptor.getValue();
            Map<Object, Object> redisResponse = new HashMap<>(storedData);
            when(hashOperations.entries(key)).thenReturn(redisResponse);

            // When - retrieve the snapshot
            List<ParticipantRoundScore> retrievedEntries = snapshotService.getSnapshot(PIN, roundNumber);

            // Then - verify round-trip data integrity
            assertThat(retrievedEntries).hasSize(3);
            
            for (ParticipantRoundScore original : originalEntries) {
                ParticipantRoundScore retrieved = retrievedEntries.stream()
                        .filter(e -> e.getParticipantId().equals(original.getParticipantId()))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError(
                                "Missing participant: " + original.getParticipantId()));
                
                assertThat(retrieved.getRank()).isEqualTo(original.getRank());
                assertThat(retrieved.getCumulativeScore()).isEqualTo(original.getCumulativeScore());
                assertThat(retrieved.getRoundScore()).isEqualTo(original.getRoundScore());
            }
        }

        @Test
        @DisplayName("Should return empty list when snapshot not found")
        void shouldReturnEmptyListWhenSnapshotNotFound() {
            // Given
            int roundNumber = 1;
            String key = "snapshot:" + PIN + ":" + roundNumber;
            
            when(hashOperations.entries(key)).thenReturn(Collections.emptyMap());

            // When
            List<ParticipantRoundScore> result = snapshotService.getSnapshot(PIN, roundNumber);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should retry on Redis failure and succeed on second attempt")
        void shouldRetryOnRedisFailureAndSucceedOnSecondAttempt() {
            // Given
            int roundNumber = 1;
            String key = "snapshot:" + PIN + ":" + roundNumber;
            
            Map<Object, Object> hashEntries = new HashMap<>();
            hashEntries.put("p1", "1|1000|500|1500");

            // First call throws exception, second succeeds
            when(hashOperations.entries(key))
                    .thenThrow(new RedisConnectionFailureException("Connection failed"))
                    .thenReturn(hashEntries);

            // When
            List<ParticipantRoundScore> result = snapshotService.getSnapshot(PIN, roundNumber);

            // Then
            assertThat(result).hasSize(1);
            verify(hashOperations, times(2)).entries(key);
        }

        @Test
        @DisplayName("Should return empty list after all retries exhausted")
        void shouldReturnEmptyListAfterAllRetriesExhausted() {
            // Given
            int roundNumber = 1;
            String key = "snapshot:" + PIN + ":" + roundNumber;

            // All calls throw exception
            when(hashOperations.entries(key))
                    .thenThrow(new RedisConnectionFailureException("Connection failed"));

            // When
            List<ParticipantRoundScore> result = snapshotService.getSnapshot(PIN, roundNumber);

            // Then
            assertThat(result).isEmpty();
            verify(hashOperations, times(3)).entries(key); // 3 retry attempts
        }

        @Test
        @DisplayName("Should skip invalid entries and continue parsing")
        void shouldSkipInvalidEntriesAndContinueParsing() {
            // Given
            int roundNumber = 1;
            String key = "snapshot:" + PIN + ":" + roundNumber;
            
            Map<Object, Object> hashEntries = new HashMap<>();
            hashEntries.put("p1", "1|1000|500|1500"); // Valid
            hashEntries.put("p2", "invalid_format");   // Invalid
            hashEntries.put("p3", "2|800|400|2000");   // Valid
            
            when(hashOperations.entries(key)).thenReturn(hashEntries);

            // When
            List<ParticipantRoundScore> result = snapshotService.getSnapshot(PIN, roundNumber);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.stream().map(ParticipantRoundScore::getParticipantId))
                    .containsExactlyInAnyOrder("p1", "p3");
        }
    }

    @Nested
    @DisplayName("Compute Rank Deltas")
    class ComputeRankDeltas {

        @Test
        @DisplayName("Should return all zeros for first round")
        void shouldReturnAllZerosForFirstRound() {
            // Given
            int currentRound = 0;
            List<RankedParticipant> currentRanked = Arrays.asList(
                    createRankedParticipant("p1", 1),
                    createRankedParticipant("p2", 2)
            );
            when(rankingService.getRankedParticipants(PIN)).thenReturn(currentRanked);

            // When
            Map<String, Integer> deltas = snapshotService.computeRankDeltas(PIN, currentRound);

            // Then
            assertThat(deltas).containsEntry("p1", 0);
            assertThat(deltas).containsEntry("p2", 0);
        }

        @Test
        @DisplayName("Should compute positive delta for upward movement")
        void shouldComputePositiveDeltaForUpwardMovement() {
            // Given
            int currentRound = 1;
            
            // Current rankings: p1 is rank 1, p2 is rank 2
            List<RankedParticipant> currentRanked = Arrays.asList(
                    createRankedParticipant("p1", 1),
                    createRankedParticipant("p2", 2)
            );
            when(rankingService.getRankedParticipants(PIN)).thenReturn(currentRanked);

            // Previous snapshot: p1 was rank 3, p2 was rank 1
            Map<Object, Object> previousSnapshot = new HashMap<>();
            previousSnapshot.put("p1", "3|500|500|1500");  // Was rank 3
            previousSnapshot.put("p2", "1|1000|500|1500"); // Was rank 1
            when(hashOperations.entries("snapshot:" + PIN + ":0")).thenReturn(previousSnapshot);

            // When
            Map<String, Integer> deltas = snapshotService.computeRankDeltas(PIN, currentRound);

            // Then
            assertThat(deltas.get("p1")).isEqualTo(2);  // 3 - 1 = +2 (moved up)
            assertThat(deltas.get("p2")).isEqualTo(-1); // 1 - 2 = -1 (moved down)
        }

        @Test
        @DisplayName("Should compute negative delta for downward movement")
        void shouldComputeNegativeDeltaForDownwardMovement() {
            // Given
            int currentRound = 1;
            
            // Current rankings: p1 is rank 3
            List<RankedParticipant> currentRanked = Collections.singletonList(
                    createRankedParticipant("p1", 3)
            );
            when(rankingService.getRankedParticipants(PIN)).thenReturn(currentRanked);

            // Previous snapshot: p1 was rank 1
            Map<Object, Object> previousSnapshot = new HashMap<>();
            previousSnapshot.put("p1", "1|1000|500|1500");
            when(hashOperations.entries("snapshot:" + PIN + ":0")).thenReturn(previousSnapshot);

            // When
            Map<String, Integer> deltas = snapshotService.computeRankDeltas(PIN, currentRound);

            // Then
            assertThat(deltas.get("p1")).isEqualTo(-2); // 1 - 3 = -2 (moved down)
        }

        @Test
        @DisplayName("Should compute zero delta for unchanged position")
        void shouldComputeZeroDeltaForUnchangedPosition() {
            // Given
            int currentRound = 1;
            
            // Current rankings: p1 is rank 2
            List<RankedParticipant> currentRanked = Collections.singletonList(
                    createRankedParticipant("p1", 2)
            );
            when(rankingService.getRankedParticipants(PIN)).thenReturn(currentRanked);

            // Previous snapshot: p1 was rank 2
            Map<Object, Object> previousSnapshot = new HashMap<>();
            previousSnapshot.put("p1", "2|800|400|1500");
            when(hashOperations.entries("snapshot:" + PIN + ":0")).thenReturn(previousSnapshot);

            // When
            Map<String, Integer> deltas = snapshotService.computeRankDeltas(PIN, currentRound);

            // Then
            assertThat(deltas.get("p1")).isEqualTo(0); // 2 - 2 = 0 (unchanged)
        }

        @Test
        @DisplayName("Should set delta to zero for late-joining participant")
        void shouldSetDeltaToZeroForLateJoiningParticipant() {
            // Given
            int currentRound = 2;
            
            // Current rankings include a new participant p3
            List<RankedParticipant> currentRanked = Arrays.asList(
                    createRankedParticipant("p1", 1),
                    createRankedParticipant("p2", 2),
                    createRankedParticipant("p3", 3) // Late-joining
            );
            when(rankingService.getRankedParticipants(PIN)).thenReturn(currentRanked);

            // Previous snapshot doesn't include p3
            Map<Object, Object> previousSnapshot = new HashMap<>();
            previousSnapshot.put("p1", "1|1000|500|1500");
            previousSnapshot.put("p2", "2|800|400|1500");
            when(hashOperations.entries("snapshot:" + PIN + ":1")).thenReturn(previousSnapshot);

            // When
            Map<String, Integer> deltas = snapshotService.computeRankDeltas(PIN, currentRound);

            // Then
            assertThat(deltas.get("p3")).isEqualTo(0); // Late-joining = 0 delta
        }

        @Test
        @DisplayName("Should return all zeros when no previous snapshot exists")
        void shouldReturnAllZerosWhenNoPreviousSnapshotExists() {
            // Given
            int currentRound = 1;
            
            List<RankedParticipant> currentRanked = Arrays.asList(
                    createRankedParticipant("p1", 1),
                    createRankedParticipant("p2", 2)
            );
            when(rankingService.getRankedParticipants(PIN)).thenReturn(currentRanked);

            // No previous snapshot
            when(hashOperations.entries("snapshot:" + PIN + ":0")).thenReturn(Collections.emptyMap());

            // When
            Map<String, Integer> deltas = snapshotService.computeRankDeltas(PIN, currentRound);

            // Then
            assertThat(deltas).containsEntry("p1", 0);
            assertThat(deltas).containsEntry("p2", 0);
        }

        @Test
        @DisplayName("Should return empty map when no current participants")
        void shouldReturnEmptyMapWhenNoCurrentParticipants() {
            // Given
            int currentRound = 1;
            when(rankingService.getRankedParticipants(PIN)).thenReturn(Collections.emptyList());

            // When
            Map<String, Integer> deltas = snapshotService.computeRankDeltas(PIN, currentRound);

            // Then
            assertThat(deltas).isEmpty();
        }

        @Test
        @DisplayName("Should fallback to all-zero deltas when Redis fails during getSnapshot")
        void shouldFallbackToZeroDeltasWhenRedisFailsDuringGetSnapshot() {
            // Given
            int currentRound = 2;
            
            List<RankedParticipant> currentRanked = Arrays.asList(
                    createRankedParticipant("p1", 1),
                    createRankedParticipant("p2", 2)
            );
            when(rankingService.getRankedParticipants(PIN)).thenReturn(currentRanked);

            // Redis fails on all attempts when trying to get previous snapshot
            String previousSnapshotKey = "snapshot:" + PIN + ":1";
            when(hashOperations.entries(previousSnapshotKey))
                    .thenThrow(new RedisConnectionFailureException("Connection failed"));

            // When
            Map<String, Integer> deltas = snapshotService.computeRankDeltas(PIN, currentRound);

            // Then - fallback: all deltas should be 0 (in-memory computation)
            assertThat(deltas).containsEntry("p1", 0);
            assertThat(deltas).containsEntry("p2", 0);
            // Verify retry attempts were made (3 retries)
            verify(hashOperations, times(3)).entries(previousSnapshotKey);
        }
    }

    @Nested
    @DisplayName("Delete All Snapshots")
    class DeleteAllSnapshots {

        @Test
        @DisplayName("Should delete all snapshots for session")
        void shouldDeleteAllSnapshotsForSession() {
            // Given
            Set<String> keys = new HashSet<>(Arrays.asList(
                    "snapshot:" + PIN + ":0",
                    "snapshot:" + PIN + ":1",
                    "snapshot:" + PIN + ":2"
            ));
            when(redisTemplate.keys("snapshot:" + PIN + ":*")).thenReturn(keys);
            when(redisTemplate.delete(keys)).thenReturn(3L);

            // When
            snapshotService.deleteAllSnapshots(PIN);

            // Then
            verify(redisTemplate).keys("snapshot:" + PIN + ":*");
            verify(redisTemplate).delete(keys);
        }

        @Test
        @DisplayName("Should handle no snapshots to delete")
        void shouldHandleNoSnapshotsToDelete() {
            // Given
            when(redisTemplate.keys("snapshot:" + PIN + ":*")).thenReturn(Collections.emptySet());

            // When
            snapshotService.deleteAllSnapshots(PIN);

            // Then
            verify(redisTemplate).keys("snapshot:" + PIN + ":*");
            verify(redisTemplate, never()).delete(anyCollection());
        }

        @Test
        @DisplayName("Should retry on Redis failure")
        void shouldRetryOnRedisFailure() {
            // Given
            Set<String> keys = Collections.singleton("snapshot:" + PIN + ":0");
            
            // First call throws exception, second succeeds
            when(redisTemplate.keys("snapshot:" + PIN + ":*"))
                    .thenThrow(new RedisConnectionFailureException("Connection failed"))
                    .thenReturn(keys);
            when(redisTemplate.delete(keys)).thenReturn(1L);

            // When
            snapshotService.deleteAllSnapshots(PIN);

            // Then
            verify(redisTemplate, times(2)).keys("snapshot:" + PIN + ":*");
            verify(redisTemplate).delete(keys);
        }

        @Test
        @DisplayName("Should handle all retries exhausted gracefully during cleanup")
        void shouldHandleAllRetriesExhaustedGracefullyDuringCleanup() {
            // Given - Redis fails on all 3 attempts
            when(redisTemplate.keys("snapshot:" + PIN + ":*"))
                    .thenThrow(new RedisConnectionFailureException("Connection failed"));

            // When - should not throw exception
            snapshotService.deleteAllSnapshots(PIN);

            // Then - all 3 retry attempts were made
            verify(redisTemplate, times(3)).keys("snapshot:" + PIN + ":*");
            // delete should never be called since keys() always failed
            verify(redisTemplate, never()).delete(anyCollection());
        }

        @Test
        @DisplayName("Should handle null keys returned from Redis")
        void shouldHandleNullKeysReturnedFromRedis() {
            // Given
            when(redisTemplate.keys("snapshot:" + PIN + ":*")).thenReturn(null);

            // When
            snapshotService.deleteAllSnapshots(PIN);

            // Then
            verify(redisTemplate).keys("snapshot:" + PIN + ":*");
            verify(redisTemplate, never()).delete(anyCollection());
        }
    }

    @Nested
    @DisplayName("Retry Logic and Fallback Behavior")
    class RetryLogicAndFallback {

        @Test
        @DisplayName("Should attempt exactly 3 retries on store failure before giving up")
        void shouldAttemptExactlyThreeRetriesOnStoreFailure() {
            // Given
            List<ParticipantRoundScore> entries = Collections.singletonList(
                    ParticipantRoundScore.builder()
                            .participantId("p1")
                            .rank(1)
                            .cumulativeScore(1000)
                            .roundScore(500)
                            .build()
            );

            // All calls throw exception
            doThrow(new RedisConnectionFailureException("Connection failed"))
                    .when(hashOperations).putAll(anyString(), anyMap());

            // When
            snapshotService.storeSnapshot(PIN, 1, entries);

            // Then - exactly 3 attempts (initial + 2 retries with backoff at 100ms, 200ms)
            verify(hashOperations, times(3)).putAll(anyString(), anyMap());
            // expire should never be called since putAll always failed
            verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
        }

        @Test
        @DisplayName("Should not propagate exception when all store retries fail")
        void shouldNotPropagateExceptionWhenAllStoreRetriesFail() {
            // Given
            List<ParticipantRoundScore> entries = Collections.singletonList(
                    ParticipantRoundScore.builder()
                            .participantId("p1")
                            .rank(1)
                            .cumulativeScore(1000)
                            .roundScore(500)
                            .build()
            );

            doThrow(new RedisConnectionFailureException("Connection failed"))
                    .when(hashOperations).putAll(anyString(), anyMap());

            // When/Then - should not throw, service degrades gracefully
            org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                    () -> snapshotService.storeSnapshot(PIN, 1, entries)
            );
        }

        @Test
        @DisplayName("Should attempt exactly 3 retries on getSnapshot failure before returning empty")
        void shouldAttemptExactlyThreeRetriesOnGetSnapshotFailure() {
            // Given
            String key = "snapshot:" + PIN + ":1";
            when(hashOperations.entries(key))
                    .thenThrow(new RedisConnectionFailureException("Connection failed"));

            // When
            List<ParticipantRoundScore> result = snapshotService.getSnapshot(PIN, 1);

            // Then - exactly 3 attempts
            verify(hashOperations, times(3)).entries(key);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should succeed on third retry attempt for store")
        void shouldSucceedOnThirdRetryAttemptForStore() {
            // Given
            List<ParticipantRoundScore> entries = Collections.singletonList(
                    ParticipantRoundScore.builder()
                            .participantId("p1")
                            .rank(1)
                            .cumulativeScore(1000)
                            .roundScore(500)
                            .build()
            );

            // First two calls throw, third succeeds
            doThrow(new RedisConnectionFailureException("Connection failed"))
                    .doThrow(new RedisConnectionFailureException("Connection failed"))
                    .doNothing()
                    .when(hashOperations).putAll(anyString(), anyMap());

            // When
            snapshotService.storeSnapshot(PIN, 1, entries);

            // Then - 3 attempts, and expire is called (success on 3rd)
            verify(hashOperations, times(3)).putAll(anyString(), anyMap());
            verify(redisTemplate).expire(anyString(), eq(Duration.ofSeconds(14400)));
        }

        @Test
        @DisplayName("Should succeed on third retry attempt for getSnapshot")
        void shouldSucceedOnThirdRetryAttemptForGetSnapshot() {
            // Given
            String key = "snapshot:" + PIN + ":1";
            Map<Object, Object> hashEntries = new HashMap<>();
            hashEntries.put("p1", "1|1000|500|1500");

            // First two calls throw, third succeeds
            when(hashOperations.entries(key))
                    .thenThrow(new RedisConnectionFailureException("Connection failed"))
                    .thenThrow(new RedisConnectionFailureException("Connection failed"))
                    .thenReturn(hashEntries);

            // When
            List<ParticipantRoundScore> result = snapshotService.getSnapshot(PIN, 1);

            // Then
            verify(hashOperations, times(3)).entries(key);
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getParticipantId()).isEqualTo("p1");
        }

        @Test
        @DisplayName("Should fallback gracefully when computeRankDeltas encounters Redis failure on previous snapshot")
        void shouldFallbackGracefullyWhenComputeRankDeltasEncountersRedisFailure() {
            // Given
            int currentRound = 3;
            List<RankedParticipant> currentRanked = Arrays.asList(
                    createRankedParticipant("p1", 1),
                    createRankedParticipant("p2", 2),
                    createRankedParticipant("p3", 3)
            );
            when(rankingService.getRankedParticipants(PIN)).thenReturn(currentRanked);

            // Redis fails when trying to get previous snapshot (round 2)
            String previousKey = "snapshot:" + PIN + ":2";
            when(hashOperations.entries(previousKey))
                    .thenThrow(new RedisConnectionFailureException("Connection failed"));

            // When
            Map<String, Integer> deltas = snapshotService.computeRankDeltas(PIN, currentRound);

            // Then - fallback: all deltas are 0 (in-memory computation)
            assertThat(deltas).hasSize(3);
            assertThat(deltas.get("p1")).isEqualTo(0);
            assertThat(deltas.get("p2")).isEqualTo(0);
            assertThat(deltas.get("p3")).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("TTL Verification")
    class TtlVerification {

        @Test
        @DisplayName("TTL constant should be exactly 14400 seconds (4 hours)")
        void ttlConstantShouldBeExactlyFourHours() {
            // Verify the TTL value matches the requirement
            // 4 hours = 4 * 60 * 60 = 14400 seconds
            Duration fourHours = Duration.ofHours(4);
            assertThat(fourHours.getSeconds()).isEqualTo(14400);
        }

        @Test
        @DisplayName("Should set TTL on every successful store operation")
        void shouldSetTtlOnEverySuccessfulStoreOperation() {
            // Given - store multiple snapshots for different rounds
            List<ParticipantRoundScore> entries = Collections.singletonList(
                    ParticipantRoundScore.builder()
                            .participantId("p1")
                            .rank(1)
                            .cumulativeScore(1000)
                            .roundScore(500)
                            .build()
            );

            // When - store snapshots for rounds 0, 1, 2
            snapshotService.storeSnapshot(PIN, 0, entries);
            snapshotService.storeSnapshot(PIN, 1, entries);
            snapshotService.storeSnapshot(PIN, 2, entries);

            // Then - TTL should be set for each snapshot
            verify(redisTemplate).expire("snapshot:" + PIN + ":0", Duration.ofSeconds(14400));
            verify(redisTemplate).expire("snapshot:" + PIN + ":1", Duration.ofSeconds(14400));
            verify(redisTemplate).expire("snapshot:" + PIN + ":2", Duration.ofSeconds(14400));
        }

        @Test
        @DisplayName("Should not set TTL when store operation fails")
        void shouldNotSetTtlWhenStoreOperationFails() {
            // Given
            List<ParticipantRoundScore> entries = Collections.singletonList(
                    ParticipantRoundScore.builder()
                            .participantId("p1")
                            .rank(1)
                            .cumulativeScore(1000)
                            .roundScore(500)
                            .build()
            );

            doThrow(new RedisConnectionFailureException("Connection failed"))
                    .when(hashOperations).putAll(anyString(), anyMap());

            // When
            snapshotService.storeSnapshot(PIN, 1, entries);

            // Then - expire should never be called since putAll always failed
            verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
        }
    }

    @Nested
    @DisplayName("Get Previous Ranks")
    class GetPreviousRanks {

        @Test
        @DisplayName("Should return empty map when no snapshots exist")
        void shouldReturnEmptyMapWhenNoSnapshotsExist() {
            // Given
            when(redisTemplate.keys("snapshot:" + PIN + ":*")).thenReturn(Collections.emptySet());

            // When
            Map<String, Integer> result = snapshotService.getPreviousRanks(PIN);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return ranks from most recent snapshot")
        void shouldReturnRanksFromMostRecentSnapshot() {
            // Given
            Set<String> keys = new HashSet<>(Arrays.asList(
                    "snapshot:" + PIN + ":0",
                    "snapshot:" + PIN + ":1",
                    "snapshot:" + PIN + ":2"
            ));
            when(redisTemplate.keys("snapshot:" + PIN + ":*")).thenReturn(keys);

            // Most recent snapshot (round 2)
            Map<Object, Object> snapshot = new HashMap<>();
            snapshot.put("p1", "1|1500|500|1500");
            snapshot.put("p2", "2|1200|400|2000");
            when(hashOperations.entries("snapshot:" + PIN + ":2")).thenReturn(snapshot);

            // When
            Map<String, Integer> result = snapshotService.getPreviousRanks(PIN);

            // Then
            assertThat(result).containsEntry("p1", 1);
            assertThat(result).containsEntry("p2", 2);
        }

        @Test
        @DisplayName("Should retry on Redis failure when getting previous ranks")
        void shouldRetryOnRedisFailureWhenGettingPreviousRanks() {
            // Given
            Set<String> keys = Collections.singleton("snapshot:" + PIN + ":0");

            // First call throws, second succeeds
            when(redisTemplate.keys("snapshot:" + PIN + ":*"))
                    .thenThrow(new RedisConnectionFailureException("Connection failed"))
                    .thenReturn(keys);

            Map<Object, Object> snapshot = new HashMap<>();
            snapshot.put("p1", "1|1000|500|1500");
            when(hashOperations.entries("snapshot:" + PIN + ":0")).thenReturn(snapshot);

            // When
            Map<String, Integer> result = snapshotService.getPreviousRanks(PIN);

            // Then
            assertThat(result).containsEntry("p1", 1);
            verify(redisTemplate, times(2)).keys("snapshot:" + PIN + ":*");
        }

        @Test
        @DisplayName("Should return empty map after all retries exhausted for getPreviousRanks")
        void shouldReturnEmptyMapAfterAllRetriesExhaustedForGetPreviousRanks() {
            // Given - Redis fails on all attempts
            when(redisTemplate.keys("snapshot:" + PIN + ":*"))
                    .thenThrow(new RedisConnectionFailureException("Connection failed"));

            // When
            Map<String, Integer> result = snapshotService.getPreviousRanks(PIN);

            // Then
            assertThat(result).isEmpty();
            verify(redisTemplate, times(3)).keys("snapshot:" + PIN + ":*");
        }
    }

    // Helper methods

    private RankedParticipant createRankedParticipant(String participantId, int rank) {
        return RankedParticipant.builder()
                .participantId(participantId)
                .rank(rank)
                .cumulativeScore(1000 - (rank * 100))
                .avgResponseTimeMs(1500)
                .lastAnswerTime(System.currentTimeMillis())
                .build();
    }
}
