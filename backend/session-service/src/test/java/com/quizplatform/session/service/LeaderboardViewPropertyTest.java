package com.quizplatform.session.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizplatform.session.dto.ParticipantRoundScore;
import net.jqwik.api.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for personalized leaderboard view construction (Property 8).
 *
 * Feature: dynamic-scoring-leaderboard, Property 8: Personalized Leaderboard View Construction
 *
 * For any session with P participants (P >= 1) and a participant at rank R:
 * - Host view contains min(5, P) entries ordered by rank ascending
 * - Participant view contains top-5 plus context entries
 * - Total entries in participant view never exceed 8
 *
 * **Validates: Requirements 3.6, 5.3**
 */
class LeaderboardViewPropertyTest {

    private final LeaderboardBroadcasterImpl broadcaster =
            new LeaderboardBroadcasterImpl(null, new ObjectMapper());

    // ==================== Property 8: Host View ====================

    /**
     * Feature: dynamic-scoring-leaderboard, Property 8: Personalized Leaderboard View Construction
     *
     * Host view SHALL contain min(5, P) entries where P is the total number of participants.
     *
     * **Validates: Requirements 3.6, 5.3**
     */
    @Property(tries = 100)
    void hostViewContainsMinOf5OrTotalParticipants(
            @ForAll("participantLists") List<ParticipantRoundScore> allScores) {

        List<ParticipantRoundScore> hostView = broadcaster.constructHostView(allScores);

        int expectedSize = Math.min(5, allScores.size());
        assertThat(hostView).hasSize(expectedSize);
    }

    /**
     * Feature: dynamic-scoring-leaderboard, Property 8: Personalized Leaderboard View Construction
     *
     * Host view entries SHALL be ordered by rank ascending.
     *
     * **Validates: Requirements 3.6, 5.3**
     */
    @Property(tries = 100)
    void hostViewIsOrderedByRankAscending(
            @ForAll("participantLists") List<ParticipantRoundScore> allScores) {

        List<ParticipantRoundScore> hostView = broadcaster.constructHostView(allScores);

        for (int i = 1; i < hostView.size(); i++) {
            assertThat(hostView.get(i).getRank())
                    .isGreaterThanOrEqualTo(hostView.get(i - 1).getRank());
        }
    }

    /**
     * Feature: dynamic-scoring-leaderboard, Property 8: Personalized Leaderboard View Construction
     *
     * Host view SHALL contain the top-ranked entries (ranks 1 through min(5, P)).
     *
     * **Validates: Requirements 3.6, 5.3**
     */
    @Property(tries = 100)
    void hostViewContainsTopRankedEntries(
            @ForAll("participantLists") List<ParticipantRoundScore> allScores) {

        List<ParticipantRoundScore> hostView = broadcaster.constructHostView(allScores);

        // The host view should contain the entries with the lowest rank values
        List<Integer> allRanksSorted = allScores.stream()
                .map(ParticipantRoundScore::getRank)
                .sorted()
                .limit(5)
                .collect(Collectors.toList());

        List<Integer> hostViewRanks = hostView.stream()
                .map(ParticipantRoundScore::getRank)
                .collect(Collectors.toList());

        assertThat(hostViewRanks).containsExactlyElementsOf(allRanksSorted);
    }

    // ==================== Property 8: Participant View ====================

    /**
     * Feature: dynamic-scoring-leaderboard, Property 8: Personalized Leaderboard View Construction
     *
     * Participant view total entries SHALL never exceed 8.
     *
     * **Validates: Requirements 3.6, 5.3**
     */
    @Property(tries = 100)
    void participantViewNeverExceeds8Entries(
            @ForAll("participantWithContext") ParticipantViewInput input) {

        List<ParticipantRoundScore> participantView =
                broadcaster.constructParticipantView(input.allScores, input.participant);

        assertThat(participantView.size()).isLessThanOrEqualTo(8);
    }

    /**
     * Feature: dynamic-scoring-leaderboard, Property 8: Personalized Leaderboard View Construction
     *
     * Participant view SHALL always contain the participant's own entry.
     *
     * **Validates: Requirements 3.6, 5.3**
     */
    @Property(tries = 100)
    void participantViewAlwaysContainsOwnEntry(
            @ForAll("participantWithContext") ParticipantViewInput input) {

        List<ParticipantRoundScore> participantView =
                broadcaster.constructParticipantView(input.allScores, input.participant);

        boolean containsSelf = participantView.stream()
                .anyMatch(e -> e.getParticipantId()
                        .equals(input.participant.getParticipantId()));
        assertThat(containsSelf).isTrue();
    }

    /**
     * Feature: dynamic-scoring-leaderboard, Property 8: Personalized Leaderboard View Construction
     *
     * Participant view SHALL contain the top-5 entries (or all if fewer than 5 participants).
     *
     * **Validates: Requirements 3.6, 5.3**
     */
    @Property(tries = 100)
    void participantViewContainsTop5(
            @ForAll("participantWithContext") ParticipantViewInput input) {

        List<ParticipantRoundScore> participantView =
                broadcaster.constructParticipantView(input.allScores, input.participant);

        int topN = Math.min(5, input.allScores.size());
        Set<Integer> topRanks = input.allScores.stream()
                .map(ParticipantRoundScore::getRank)
                .sorted()
                .limit(topN)
                .collect(Collectors.toSet());

        Set<Integer> viewRanks = participantView.stream()
                .map(ParticipantRoundScore::getRank)
                .collect(Collectors.toSet());

        assertThat(viewRanks).containsAll(topRanks);
    }

    /**
     * Feature: dynamic-scoring-leaderboard, Property 8: Personalized Leaderboard View Construction
     *
     * When participant is ranked within top 5, the view SHALL contain exactly min(5, P) entries.
     *
     * **Validates: Requirements 3.6, 5.3**
     */
    @Property(tries = 100)
    void participantInTop5GetsMinOf5OrTotalEntries(
            @ForAll("participantInTop5") ParticipantViewInput input) {

        List<ParticipantRoundScore> participantView =
                broadcaster.constructParticipantView(input.allScores, input.participant);

        int expectedSize = Math.min(5, input.allScores.size());
        assertThat(participantView).hasSize(expectedSize);
    }

    /**
     * Feature: dynamic-scoring-leaderboard, Property 8: Personalized Leaderboard View Construction
     *
     * When participant is ranked below 5th, the view SHALL include context entries
     * (one above and one below when available), and the total SHALL not exceed 8.
     *
     * **Validates: Requirements 3.6, 5.3**
     */
    @Property(tries = 100)
    void participantBelowTop5GetsContextEntries(
            @ForAll("participantBelowTop5") ParticipantViewInput input) {

        List<ParticipantRoundScore> participantView =
                broadcaster.constructParticipantView(input.allScores, input.participant);

        int participantRank = input.participant.getRank();
        int totalParticipants = input.allScores.size();

        // Must contain top 5
        assertThat(participantView.size()).isGreaterThanOrEqualTo(5);

        // Must contain own entry
        boolean containsSelf = participantView.stream()
                .anyMatch(e -> e.getParticipantId()
                        .equals(input.participant.getParticipantId()));
        assertThat(containsSelf).isTrue();

        // If participant rank > 6, should contain entry at rank-1 (one above)
        if (participantRank > 6) {
            boolean containsAbove = participantView.stream()
                    .anyMatch(e -> e.getRank() == participantRank - 1);
            assertThat(containsAbove)
                    .as("Should contain entry one rank above (rank %d) for participant at rank %d",
                            participantRank - 1, participantRank)
                    .isTrue();
        }

        // If participant is not last, should contain entry at rank+1 (one below)
        if (participantRank < totalParticipants) {
            boolean containsBelow = participantView.stream()
                    .anyMatch(e -> e.getRank() == participantRank + 1);
            assertThat(containsBelow)
                    .as("Should contain entry one rank below (rank %d) for participant at rank %d",
                            participantRank + 1, participantRank)
                    .isTrue();
        }

        // Total never exceeds 8
        assertThat(participantView.size()).isLessThanOrEqualTo(8);
    }

    /**
     * Feature: dynamic-scoring-leaderboard, Property 8: Personalized Leaderboard View Construction
     *
     * Participant view entries SHALL be ordered by rank ascending.
     *
     * **Validates: Requirements 3.6, 5.3**
     */
    @Property(tries = 100)
    void participantViewIsOrderedByRankAscending(
            @ForAll("participantWithContext") ParticipantViewInput input) {

        List<ParticipantRoundScore> participantView =
                broadcaster.constructParticipantView(input.allScores, input.participant);

        for (int i = 1; i < participantView.size(); i++) {
            assertThat(participantView.get(i).getRank())
                    .isGreaterThanOrEqualTo(participantView.get(i - 1).getRank());
        }
    }

    // ==================== Arbitraries / Providers ====================

    /**
     * Generate a list of participants with unique IDs and contiguous ranks from 1 to P.
     */
    @Provide
    Arbitrary<List<ParticipantRoundScore>> participantLists() {
        return Arbitraries.integers().between(1, 30).flatMap(count ->
                Arbitraries.just(generateParticipants(count))
        );
    }

    /**
     * Generate a participant view input with any participant from the list.
     */
    @Provide
    Arbitrary<ParticipantViewInput> participantWithContext() {
        return Arbitraries.integers().between(1, 30).flatMap(count -> {
            List<ParticipantRoundScore> participants = generateParticipants(count);
            return Arbitraries.integers().between(0, count - 1).map(index ->
                    new ParticipantViewInput(participants, participants.get(index))
            );
        });
    }

    /**
     * Generate a participant view input where the participant is in the top 5.
     */
    @Provide
    Arbitrary<ParticipantViewInput> participantInTop5() {
        return Arbitraries.integers().between(1, 30).flatMap(count -> {
            List<ParticipantRoundScore> participants = generateParticipants(count);
            int maxIndex = Math.min(5, count) - 1;
            return Arbitraries.integers().between(0, maxIndex).map(index ->
                    new ParticipantViewInput(participants, participants.get(index))
            );
        });
    }

    /**
     * Generate a participant view input where the participant is ranked below 5th.
     * Requires at least 6 participants.
     */
    @Provide
    Arbitrary<ParticipantViewInput> participantBelowTop5() {
        return Arbitraries.integers().between(6, 30).flatMap(count -> {
            List<ParticipantRoundScore> participants = generateParticipants(count);
            // Pick a participant ranked 6th or below (index 5 to count-1)
            return Arbitraries.integers().between(5, count - 1).map(index ->
                    new ParticipantViewInput(participants, participants.get(index))
            );
        });
    }

    // ==================== Helper Methods ====================

    /**
     * Generate a list of participants with contiguous ranks 1..count.
     */
    private List<ParticipantRoundScore> generateParticipants(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(rank -> ParticipantRoundScore.builder()
                        .participantId("participant-" + rank)
                        .nickname("Player " + rank)
                        .rank(rank)
                        .cumulativeScore((count - rank + 1) * 1000)
                        .roundScore((count - rank + 1) * 100)
                        .rankDelta(0)
                        .streakCount(0)
                        .streakMultiplier(1)
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Input holder for participant view property tests.
     */
    static class ParticipantViewInput {
        final List<ParticipantRoundScore> allScores;
        final ParticipantRoundScore participant;

        ParticipantViewInput(List<ParticipantRoundScore> allScores,
                             ParticipantRoundScore participant) {
            this.allScores = allScores;
            this.participant = participant;
        }

        @Override
        public String toString() {
            return String.format("ParticipantViewInput{totalParticipants=%d, participantRank=%d}",
                    allScores.size(), participant.getRank());
        }
    }
}
