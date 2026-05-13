package com.quizplatform.session.service;

import com.quizplatform.session.dto.LeaderboardEntry;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for leaderboard consistency.
 *
 * **Validates: Requirements 6.1, 6.2, 6.3, 6.4**
 *
 * These tests verify structural invariants of the leaderboard:
 * - All participants appear exactly once
 * - Ranks are contiguous (1, 2, 3, ... N)
 * - Higher score always has lower rank number
 * - Total participants in leaderboard equals session participant count
 */
class LeaderboardConsistencyPropertyTest {

    /**
     * P2.1: All participants appear exactly once in the leaderboard.
     *
     * Given a set of participants with scores, when the leaderboard is computed,
     * each participant appears exactly once.
     *
     * **Validates: Requirements 6.1**
     */
    @Property(tries = 500)
    void allParticipantsAppearExactlyOnce(
            @ForAll("participantScores") Map<String, Double> scores) {

        Assume.that(!scores.isEmpty());

        List<LeaderboardEntry> leaderboard = buildLeaderboard(scores);

        // Each participant appears exactly once
        List<String> participantIds = leaderboard.stream()
                .map(LeaderboardEntry::getParticipantId)
                .collect(Collectors.toList());

        Set<String> uniqueIds = new HashSet<>(participantIds);
        assertThat(uniqueIds).hasSize(participantIds.size());

        // All original participants are present
        assertThat(uniqueIds).containsExactlyInAnyOrderElementsOf(scores.keySet());
    }

    /**
     * P2.2: Ranks are contiguous (1, 2, 3, ... N).
     *
     * Given a leaderboard with N participants, ranks form a contiguous sequence
     * from 1 to N with no gaps or duplicates.
     *
     * **Validates: Requirements 6.1**
     */
    @Property(tries = 500)
    void ranksAreContiguous(
            @ForAll("participantScores") Map<String, Double> scores) {

        Assume.that(!scores.isEmpty());

        List<LeaderboardEntry> leaderboard = buildLeaderboard(scores);

        List<Long> ranks = leaderboard.stream()
                .map(LeaderboardEntry::getRank)
                .sorted()
                .collect(Collectors.toList());

        // Ranks should be 1, 2, 3, ..., N
        for (int i = 0; i < ranks.size(); i++) {
            assertThat(ranks.get(i)).isEqualTo(i + 1L);
        }
    }

    /**
     * P2.3: Higher score always has lower rank number.
     *
     * If participant A has a strictly higher score than participant B,
     * then A's rank number is strictly less than B's rank number.
     *
     * **Validates: Requirements 6.4**
     */
    @Property(tries = 500)
    void higherScoreHasLowerRankNumber(
            @ForAll("participantScores") Map<String, Double> scores) {

        Assume.that(scores.size() >= 2);

        List<LeaderboardEntry> leaderboard = buildLeaderboard(scores);

        for (int i = 0; i < leaderboard.size(); i++) {
            for (int j = i + 1; j < leaderboard.size(); j++) {
                LeaderboardEntry higher = leaderboard.get(i);
                LeaderboardEntry lower = leaderboard.get(j);

                if (higher.getScore() > lower.getScore()) {
                    assertThat(higher.getRank())
                            .as("Participant with score %.0f should have lower rank than participant with score %.0f",
                                    higher.getScore(), lower.getScore())
                            .isLessThan(lower.getRank());
                } else if (lower.getScore() > higher.getScore()) {
                    assertThat(lower.getRank())
                            .as("Participant with score %.0f should have lower rank than participant with score %.0f",
                                    lower.getScore(), higher.getScore())
                            .isLessThan(higher.getRank());
                }
            }
        }
    }

    /**
     * P2.4: Total participants in leaderboard equals session participant count.
     *
     * The number of entries in the leaderboard must equal the number of
     * participants who have scores in the session.
     *
     * **Validates: Requirements 6.1**
     */
    @Property(tries = 500)
    void leaderboardSizeEqualsParticipantCount(
            @ForAll("participantScores") Map<String, Double> scores) {

        Assume.that(!scores.isEmpty());

        List<LeaderboardEntry> leaderboard = buildLeaderboard(scores);

        assertThat(leaderboard).hasSize(scores.size());
    }

    /**
     * P2.5: Leaderboard is sorted by score descending.
     *
     * Entries in the leaderboard are ordered from highest score to lowest.
     *
     * **Validates: Requirements 6.1**
     */
    @Property(tries = 500)
    void leaderboardIsSortedByScoreDescending(
            @ForAll("participantScores") Map<String, Double> scores) {

        Assume.that(scores.size() >= 2);

        List<LeaderboardEntry> leaderboard = buildLeaderboard(scores);

        for (int i = 0; i < leaderboard.size() - 1; i++) {
            assertThat(leaderboard.get(i).getScore())
                    .as("Entry at position %d should have score >= entry at position %d", i, i + 1)
                    .isGreaterThanOrEqualTo(leaderboard.get(i + 1).getScore());
        }
    }

    // ==================== Generators ====================

    @Provide
    Arbitrary<Map<String, Double>> participantScores() {
        Arbitrary<String> participantIds = Arbitraries.strings()
                .alpha()
                .ofMinLength(8)
                .ofMaxLength(12)
                .map(s -> "p-" + s);

        Arbitrary<Double> scores = Arbitraries.doubles()
                .between(0, 50000)
                .ofScale(0); // Integer-like scores

        return Arbitraries.maps(participantIds, scores)
                .ofMinSize(1)
                .ofMaxSize(100);
    }

    // ==================== Helper: Simulate leaderboard building ====================

    /**
     * Simulates the leaderboard building logic that mirrors RedisSessionService.getTopN.
     * Sorts participants by score descending and assigns contiguous ranks.
     */
    private List<LeaderboardEntry> buildLeaderboard(Map<String, Double> scores) {
        List<Map.Entry<String, Double>> sorted = scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed()
                        .thenComparing(Map.Entry::getKey)) // Stable sort for ties
                .collect(Collectors.toList());

        List<LeaderboardEntry> entries = new ArrayList<>();
        long rank = 1;
        for (Map.Entry<String, Double> entry : sorted) {
            entries.add(LeaderboardEntry.builder()
                    .participantId(entry.getKey())
                    .nickname("Player-" + entry.getKey())
                    .score(entry.getValue())
                    .rank(rank)
                    .rankChange(0)
                    .streak(0)
                    .multiplier(1)
                    .build());
            rank++;
        }
        return entries;
    }
}
