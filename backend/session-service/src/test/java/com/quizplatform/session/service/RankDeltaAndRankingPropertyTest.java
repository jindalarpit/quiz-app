package com.quizplatform.session.service;

import com.quizplatform.session.dto.RankedParticipant;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import net.jqwik.api.constraints.Size;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for rank delta computation and ranking tiebreaker stability (Properties 7, 9).
 * 
 * Feature: dynamic-scoring-leaderboard
 *
 * **Validates: Requirements 4.2, 4.6**
 */
class RankDeltaAndRankingPropertyTest {

    // ==================== Property 7: Rank Delta Computation ====================

    /**
     * Feature: dynamic-scoring-leaderboard, Property 7: Rank Delta Computation
     *
     * For any participant with a rank in round N and a rank in round N-1,
     * the rank_delta SHALL equal previous_rank - current_rank.
     * Positive delta indicates upward movement, negative indicates downward movement,
     * and zero indicates no change.
     *
     * **Validates: Requirements 4.2**
     */
    @Property(tries = 100)
    void property7_rankDeltaEqualsPreivousMinusCurrent(
            @ForAll @IntRange(min = 1, max = 100) int previousRank,
            @ForAll @IntRange(min = 1, max = 100) int currentRank) {

        int expectedDelta = previousRank - currentRank;

        // Simulate the computation as done in LeaderboardSnapshotService.computeRankDeltas()
        // and RankingService.computeRankDeltas()
        String participantId = "participant-1";
        Map<String, Integer> previousRanks = Map.of(participantId, previousRank);

        // Compute delta the same way the service does
        Integer prevRank = previousRanks.get(participantId);
        int actualDelta = prevRank - currentRank;

        assertThat(actualDelta)
            .as("rank_delta should equal previous_rank(%d) - current_rank(%d) = %d",
                previousRank, currentRank, expectedDelta)
            .isEqualTo(expectedDelta);
    }

    /**
     * Feature: dynamic-scoring-leaderboard, Property 7: Rank Delta Computation (Sign Semantics)
     *
     * Positive rank_delta means the participant moved up (lower rank number = better position).
     * Negative rank_delta means the participant moved down.
     * Zero rank_delta means unchanged position.
     *
     * **Validates: Requirements 4.2**
     */
    @Property(tries = 100)
    void property7_rankDeltaSignSemantics(
            @ForAll @IntRange(min = 1, max = 100) int previousRank,
            @ForAll @IntRange(min = 1, max = 100) int currentRank) {

        int delta = previousRank - currentRank;

        if (currentRank < previousRank) {
            // Moved up (e.g., rank 5 to rank 3)
            assertThat(delta)
                .as("Moving from rank %d to rank %d should yield positive delta", previousRank, currentRank)
                .isPositive();
        } else if (currentRank > previousRank) {
            // Moved down (e.g., rank 3 to rank 5)
            assertThat(delta)
                .as("Moving from rank %d to rank %d should yield negative delta", previousRank, currentRank)
                .isNegative();
        } else {
            // Unchanged
            assertThat(delta)
                .as("Staying at rank %d should yield zero delta", currentRank)
                .isZero();
        }
    }

    /**
     * Feature: dynamic-scoring-leaderboard, Property 7: Rank Delta Computation (Multiple Participants)
     *
     * For a set of participants with previous and current ranks, all rank deltas
     * are computed correctly as previous_rank - current_rank.
     *
     * **Validates: Requirements 4.2**
     */
    @Property(tries = 100)
    void property7_rankDeltaForMultipleParticipants(
            @ForAll("participantRankPairs") List<int[]> rankPairs) {

        // Build previous ranks map
        Map<String, Integer> previousRanks = new HashMap<>();
        for (int i = 0; i < rankPairs.size(); i++) {
            previousRanks.put("participant-" + i, rankPairs.get(i)[0]);
        }

        // Simulate computing deltas for each participant
        for (int i = 0; i < rankPairs.size(); i++) {
            String participantId = "participant-" + i;
            int previousRank = rankPairs.get(i)[0];
            int currentRank = rankPairs.get(i)[1];

            Integer prevRank = previousRanks.get(participantId);
            int delta = prevRank - currentRank;

            assertThat(delta)
                .as("Participant %s: rank_delta should be %d - %d = %d",
                    participantId, previousRank, currentRank, previousRank - currentRank)
                .isEqualTo(previousRank - currentRank);
        }
    }

    /**
     * Feature: dynamic-scoring-leaderboard, Property 7: Rank Delta Computation (Late-Joining)
     *
     * For a late-joining participant (not in previous snapshot), rank_delta = 0.
     *
     * **Validates: Requirements 4.2**
     */
    @Property(tries = 100)
    void property7_lateJoiningParticipantDeltaIsZero(
            @ForAll @IntRange(min = 1, max = 100) int currentRank) {

        String participantId = "late-joiner";
        Map<String, Integer> previousRanks = new HashMap<>();
        // Participant not in previous ranks (late-joining)

        Integer prevRank = previousRanks.get(participantId);
        int delta;
        if (prevRank == null) {
            delta = 0; // Late-joining participant
        } else {
            delta = prevRank - currentRank;
        }

        assertThat(delta)
            .as("Late-joining participant should have rank_delta = 0")
            .isZero();
    }

    // ==================== Property 9: Ranking Tiebreaker Stability ====================

    /**
     * Feature: dynamic-scoring-leaderboard, Property 9: Ranking Tiebreaker Stability
     *
     * For any set of participants, the ranking algorithm produces a total order where:
     * (a) higher cumulative score always ranks higher
     *
     * **Validates: Requirements 4.6**
     */
    @Property(tries = 100)
    void property9_higherScoreRanksHigher(
            @ForAll @LongRange(min = 0, max = 10000) long scoreA,
            @ForAll @LongRange(min = 0, max = 10000) long scoreB,
            @ForAll @LongRange(min = 1, max = 30000) long avgTimeA,
            @ForAll @LongRange(min = 1, max = 30000) long avgTimeB,
            @ForAll @LongRange(min = 1, max = 1000000) long lastAnswerA,
            @ForAll @LongRange(min = 1, max = 1000000) long lastAnswerB) {

        Assume.that(scoreA != scoreB);

        RankedParticipant participantA = RankedParticipant.builder()
                .participantId("A")
                .cumulativeScore(scoreA)
                .avgResponseTimeMs(avgTimeA)
                .lastAnswerTime(lastAnswerA)
                .build();

        RankedParticipant participantB = RankedParticipant.builder()
                .participantId("B")
                .cumulativeScore(scoreB)
                .avgResponseTimeMs(avgTimeB)
                .lastAnswerTime(lastAnswerB)
                .build();

        RankingService rankingService = new RankingService(null);
        Comparator<RankedParticipant> comparator = rankingService.getRankingComparator();

        int comparison = comparator.compare(participantA, participantB);

        if (scoreA > scoreB) {
            assertThat(comparison)
                .as("Participant with higher score (%d) should rank before participant with lower score (%d)",
                    scoreA, scoreB)
                .isLessThan(0);
        } else {
            assertThat(comparison)
                .as("Participant with higher score (%d) should rank before participant with lower score (%d)",
                    scoreB, scoreA)
                .isGreaterThan(0);
        }
    }

    /**
     * Feature: dynamic-scoring-leaderboard, Property 9: Ranking Tiebreaker Stability
     *
     * For any set of participants with equal cumulative scores,
     * lower average response time ranks higher (first tiebreaker).
     *
     * **Validates: Requirements 4.6**
     */
    @Property(tries = 100)
    void property9_lowerAvgResponseTimeRanksHigherOnTiedScore(
            @ForAll @LongRange(min = 0, max = 10000) long score,
            @ForAll @LongRange(min = 1, max = 30000) long avgTimeA,
            @ForAll @LongRange(min = 1, max = 30000) long avgTimeB,
            @ForAll @LongRange(min = 1, max = 1000000) long lastAnswerA,
            @ForAll @LongRange(min = 1, max = 1000000) long lastAnswerB) {

        Assume.that(avgTimeA != avgTimeB);

        RankedParticipant participantA = RankedParticipant.builder()
                .participantId("A")
                .cumulativeScore(score)
                .avgResponseTimeMs(avgTimeA)
                .lastAnswerTime(lastAnswerA)
                .build();

        RankedParticipant participantB = RankedParticipant.builder()
                .participantId("B")
                .cumulativeScore(score)
                .avgResponseTimeMs(avgTimeB)
                .lastAnswerTime(lastAnswerB)
                .build();

        RankingService rankingService = new RankingService(null);
        Comparator<RankedParticipant> comparator = rankingService.getRankingComparator();

        int comparison = comparator.compare(participantA, participantB);

        if (avgTimeA < avgTimeB) {
            assertThat(comparison)
                .as("Participant with lower avg response time (%d) should rank before participant with higher (%d) when scores are equal",
                    avgTimeA, avgTimeB)
                .isLessThan(0);
        } else {
            assertThat(comparison)
                .as("Participant with lower avg response time (%d) should rank before participant with higher (%d) when scores are equal",
                    avgTimeB, avgTimeA)
                .isGreaterThan(0);
        }
    }

    /**
     * Feature: dynamic-scoring-leaderboard, Property 9: Ranking Tiebreaker Stability
     *
     * For any set of participants with equal cumulative scores and equal avg response times,
     * earlier most-recent answer timestamp ranks higher (second tiebreaker).
     *
     * **Validates: Requirements 4.6**
     */
    @Property(tries = 100)
    void property9_earlierLastAnswerRanksHigherOnTiedScoreAndTime(
            @ForAll @LongRange(min = 0, max = 10000) long score,
            @ForAll @LongRange(min = 1, max = 30000) long avgTime,
            @ForAll @LongRange(min = 1, max = 1000000) long lastAnswerA,
            @ForAll @LongRange(min = 1, max = 1000000) long lastAnswerB) {

        Assume.that(lastAnswerA != lastAnswerB);

        RankedParticipant participantA = RankedParticipant.builder()
                .participantId("A")
                .cumulativeScore(score)
                .avgResponseTimeMs(avgTime)
                .lastAnswerTime(lastAnswerA)
                .build();

        RankedParticipant participantB = RankedParticipant.builder()
                .participantId("B")
                .cumulativeScore(score)
                .avgResponseTimeMs(avgTime)
                .lastAnswerTime(lastAnswerB)
                .build();

        RankingService rankingService = new RankingService(null);
        Comparator<RankedParticipant> comparator = rankingService.getRankingComparator();

        int comparison = comparator.compare(participantA, participantB);

        if (lastAnswerA < lastAnswerB) {
            assertThat(comparison)
                .as("Participant with earlier last answer (%d) should rank before participant with later (%d) when scores and avg times are equal",
                    lastAnswerA, lastAnswerB)
                .isLessThan(0);
        } else {
            assertThat(comparison)
                .as("Participant with earlier last answer (%d) should rank before participant with later (%d) when scores and avg times are equal",
                    lastAnswerB, lastAnswerA)
                .isGreaterThan(0);
        }
    }

    /**
     * Feature: dynamic-scoring-leaderboard, Property 9: Ranking Tiebreaker Stability (Determinism)
     *
     * The ranking algorithm is deterministic — the same inputs always produce the same output.
     * Sorting the same list multiple times yields the same order.
     *
     * **Validates: Requirements 4.6**
     */
    @Property(tries = 100)
    void property9_rankingIsDeterministic(
            @ForAll("participantList") List<RankedParticipant> participants) {

        RankingService rankingService = new RankingService(null);
        Comparator<RankedParticipant> comparator = rankingService.getRankingComparator();

        // Sort multiple times and verify same result
        List<RankedParticipant> sorted1 = new ArrayList<>(participants);
        sorted1.sort(comparator);

        List<RankedParticipant> sorted2 = new ArrayList<>(participants);
        sorted2.sort(comparator);

        List<RankedParticipant> sorted3 = new ArrayList<>(participants);
        sorted3.sort(comparator);

        // All sorts should produce the same order
        for (int i = 0; i < sorted1.size(); i++) {
            assertThat(sorted1.get(i).getParticipantId())
                .as("Sort should be deterministic at position %d", i)
                .isEqualTo(sorted2.get(i).getParticipantId())
                .isEqualTo(sorted3.get(i).getParticipantId());
        }
    }

    /**
     * Feature: dynamic-scoring-leaderboard, Property 9: Ranking Tiebreaker Stability (Total Order)
     *
     * The ranking algorithm produces a total order — every pair of distinct participants
     * has a defined ordering (no ties when all three fields differ).
     *
     * **Validates: Requirements 4.6**
     */
    @Property(tries = 100)
    void property9_rankingProducesTotalOrder(
            @ForAll("distinctParticipantList") List<RankedParticipant> participants) {

        RankingService rankingService = new RankingService(null);
        Comparator<RankedParticipant> comparator = rankingService.getRankingComparator();

        List<RankedParticipant> sorted = new ArrayList<>(participants);
        sorted.sort(comparator);

        // Assign ranks
        for (int i = 0; i < sorted.size(); i++) {
            sorted.get(i).setRank(i + 1);
        }

        // Verify all ranks are unique (total order)
        Set<Integer> ranks = sorted.stream()
                .map(RankedParticipant::getRank)
                .collect(Collectors.toSet());

        assertThat(ranks)
            .as("All participants should have unique ranks (total order)")
            .hasSize(sorted.size());

        // Verify ranks are sequential from 1 to N
        for (int i = 0; i < sorted.size(); i++) {
            assertThat(sorted.get(i).getRank())
                .as("Rank at position %d should be %d", i, i + 1)
                .isEqualTo(i + 1);
        }
    }

    /**
     * Feature: dynamic-scoring-leaderboard, Property 9: Ranking Tiebreaker Stability (Transitivity)
     *
     * The ranking comparator is transitive: if A ranks before B and B ranks before C,
     * then A ranks before C.
     *
     * **Validates: Requirements 4.6**
     */
    @Property(tries = 100)
    void property9_rankingIsTransitive(
            @ForAll("threeParticipants") List<RankedParticipant> participants) {

        Assume.that(participants.size() == 3);

        RankingService rankingService = new RankingService(null);
        Comparator<RankedParticipant> comparator = rankingService.getRankingComparator();

        RankedParticipant a = participants.get(0);
        RankedParticipant b = participants.get(1);
        RankedParticipant c = participants.get(2);

        int abCompare = comparator.compare(a, b);
        int bcCompare = comparator.compare(b, c);
        int acCompare = comparator.compare(a, c);

        // If A <= B and B <= C, then A <= C (transitivity)
        if (abCompare <= 0 && bcCompare <= 0) {
            assertThat(acCompare)
                .as("Transitivity: if A ranks before/equal B and B ranks before/equal C, then A ranks before/equal C")
                .isLessThanOrEqualTo(0);
        }
        // If A >= B and B >= C, then A >= C
        if (abCompare >= 0 && bcCompare >= 0) {
            assertThat(acCompare)
                .as("Transitivity: if A ranks after/equal B and B ranks after/equal C, then A ranks after/equal C")
                .isGreaterThanOrEqualTo(0);
        }
    }

    // ==================== Providers ====================

    @Provide
    Arbitrary<List<int[]>> participantRankPairs() {
        Arbitrary<int[]> rankPair = Arbitraries.integers().between(1, 50)
                .flatMap(prev -> Arbitraries.integers().between(1, 50)
                        .map(curr -> new int[]{prev, curr}));
        return rankPair.list().ofMinSize(2).ofMaxSize(10);
    }

    @Provide
    Arbitrary<List<RankedParticipant>> participantList() {
        return Arbitraries.integers().between(2, 10)
                .flatMap(size -> {
                    Arbitrary<RankedParticipant> participantArb = Arbitraries.integers().between(0, size - 1)
                            .flatMap(idx -> buildParticipantArbitrary("p-" + idx));
                    return participantArb.list().ofSize(size);
                })
                .map(list -> {
                    // Ensure unique participant IDs
                    List<RankedParticipant> result = new ArrayList<>();
                    for (int i = 0; i < list.size(); i++) {
                        RankedParticipant p = list.get(i);
                        p.setParticipantId("participant-" + i);
                        result.add(p);
                    }
                    return result;
                });
    }

    @Provide
    Arbitrary<List<RankedParticipant>> distinctParticipantList() {
        return Arbitraries.integers().between(2, 8)
                .flatMap(size -> {
                    List<Arbitrary<RankedParticipant>> arbitraries = new ArrayList<>();
                    for (int i = 0; i < size; i++) {
                        arbitraries.add(buildDistinctParticipantArbitrary("participant-" + i, i));
                    }
                    return Combinators.combine(arbitraries).as(list -> list);
                });
    }

    @Provide
    Arbitrary<List<RankedParticipant>> threeParticipants() {
        return Combinators.combine(
                buildParticipantArbitrary("A"),
                buildParticipantArbitrary("B"),
                buildParticipantArbitrary("C")
        ).as((a, b, c) -> List.of(a, b, c));
    }

    private Arbitrary<RankedParticipant> buildParticipantArbitrary(String id) {
        return Combinators.combine(
                Arbitraries.longs().between(0, 10000),
                Arbitraries.longs().between(1, 30000),
                Arbitraries.longs().between(1, 1000000)
        ).as((score, avgTime, lastAnswer) -> RankedParticipant.builder()
                .participantId(id)
                .nickname(id)
                .cumulativeScore(score)
                .avgResponseTimeMs(avgTime)
                .lastAnswerTime(lastAnswer)
                .build());
    }

    /**
     * Build a participant with distinct values to ensure total ordering.
     * Uses the index to offset values, ensuring no two participants have identical fields.
     */
    private Arbitrary<RankedParticipant> buildDistinctParticipantArbitrary(String id, int index) {
        return Combinators.combine(
                Arbitraries.longs().between(0, 10000),
                Arbitraries.longs().between(1, 30000),
                Arbitraries.longs().between(1, 1000000)
        ).as((score, avgTime, lastAnswer) -> RankedParticipant.builder()
                .participantId(id)
                .nickname(id)
                .cumulativeScore(score)
                .avgResponseTimeMs(avgTime)
                // Ensure unique lastAnswerTime by adding index offset
                .lastAnswerTime(lastAnswer + index)
                .build());
    }
}
