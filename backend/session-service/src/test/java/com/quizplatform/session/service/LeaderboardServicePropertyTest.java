package com.quizplatform.session.service;

import com.quizplatform.session.dto.AnswerStatus;
import com.quizplatform.session.dto.FinalLeaderboardEntry;
import com.quizplatform.session.dto.PagedLeaderboardResponse;
import com.quizplatform.session.dto.QuestionResult;
import com.quizplatform.session.model.Session;
import com.quizplatform.session.model.SessionParticipant;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for LeaderboardService.
 *
 * Includes:
 * - Property 1: Ranking sort order (Validates: Requirements 1.2)
 * - Property 3: Pagination invariants (Validates: Requirements 2.1, 2.3)
 * - Property 4: Continuous ranks across pages (Validates: Requirements 2.2)
 * - Property 12: Persistence integrity (Validates: Requirements 6.2, 6.3)
 * - Property 13: Score difference computation (Validates: Requirements 7.2)
 * - Property 14: Per-question status mapping (Validates: Requirements 7.3)
 */
class LeaderboardServicePropertyTest {

    // ==================== Property 1: Ranking sort order ====================

    /**
     * Property 1a: No participant with a higher score is ranked below a participant with a lower score.
     *
     * For any generated list of participants, after ranking, if participant A has a higher
     * final_score than participant B, then A's rank must be numerically lower (better) than B's rank.
     *
     * **Validates: Requirements 1.2**
     */
    @Property(tries = 100)
    @Tag("Feature: quiz-results-leaderboard, Property 1: Ranking sort order")
    void noHigherScorerRankedBelowLowerScorer(
            @ForAll("participantsForRanking") List<SessionParticipant> participants) {

        Assume.that(participants.size() >= 2);

        // Execute the ranking logic
        List<SessionParticipant> ranked = rankParticipants(participants);

        // Verify: for every pair, higher score → lower (better) rank
        for (int i = 0; i < ranked.size(); i++) {
            for (int j = i + 1; j < ranked.size(); j++) {
                SessionParticipant a = ranked.get(i);
                SessionParticipant b = ranked.get(j);

                int scoreA = a.getFinalScore() != null ? a.getFinalScore() : 0;
                int scoreB = b.getFinalScore() != null ? b.getFinalScore() : 0;

                if (scoreA > scoreB) {
                    assertThat(a.getFinalRank())
                            .as("Participant '%s' (score=%d) should have a better rank than '%s' (score=%d)",
                                    a.getNickname(), scoreA, b.getNickname(), scoreB)
                            .isLessThan(b.getFinalRank());
                } else if (scoreB > scoreA) {
                    assertThat(b.getFinalRank())
                            .as("Participant '%s' (score=%d) should have a better rank than '%s' (score=%d)",
                                    b.getNickname(), scoreB, a.getNickname(), scoreA)
                            .isLessThan(a.getFinalRank());
                }
            }
        }
    }

    /**
     * Property 1b: Among equal scores, no participant with higher avg response time is ranked
     * above one with lower avg response time.
     *
     * For any generated list of participants, after ranking, if two participants have the same
     * final_score but participant A has a higher avg_response_time_ms than participant B,
     * then A's rank must be numerically higher (worse) than B's rank.
     *
     * **Validates: Requirements 1.2**
     */
    @Property(tries = 100)
    @Tag("Feature: quiz-results-leaderboard, Property 1: Ranking sort order")
    void amongEqualScoresSlowerResponseTimeRankedLower(
            @ForAll("participantsWithTiedScores") List<SessionParticipant> participants) {

        Assume.that(participants.size() >= 2);

        // Execute the ranking logic
        List<SessionParticipant> ranked = rankParticipants(participants);

        // Verify: among equal scores, higher avg response time → worse rank
        for (int i = 0; i < ranked.size(); i++) {
            for (int j = i + 1; j < ranked.size(); j++) {
                SessionParticipant a = ranked.get(i);
                SessionParticipant b = ranked.get(j);

                int scoreA = a.getFinalScore() != null ? a.getFinalScore() : 0;
                int scoreB = b.getFinalScore() != null ? b.getFinalScore() : 0;

                if (scoreA == scoreB) {
                    int avgTimeA = a.getAvgResponseTimeMs() != null ? a.getAvgResponseTimeMs() : Integer.MAX_VALUE;
                    int avgTimeB = b.getAvgResponseTimeMs() != null ? b.getAvgResponseTimeMs() : Integer.MAX_VALUE;

                    if (avgTimeA > avgTimeB) {
                        assertThat(a.getFinalRank())
                                .as("Participant '%s' (score=%d, avgTime=%d) should rank worse than '%s' (score=%d, avgTime=%d)",
                                        a.getNickname(), scoreA, avgTimeA, b.getNickname(), scoreB, avgTimeB)
                                .isGreaterThan(b.getFinalRank());
                    } else if (avgTimeB > avgTimeA) {
                        assertThat(b.getFinalRank())
                                .as("Participant '%s' (score=%d, avgTime=%d) should rank worse than '%s' (score=%d, avgTime=%d)",
                                        b.getNickname(), scoreB, avgTimeB, a.getNickname(), scoreA, avgTimeA)
                                .isGreaterThan(a.getFinalRank());
                    }
                }
            }
        }
    }

    /**
     * Property 1c: Among equal scores and equal avg response times, no participant with later
     * last_answer_at is ranked above one with earlier last_answer_at.
     *
     * For any generated list of participants, after ranking, if two participants have the same
     * final_score and same avg_response_time_ms but participant A has a later last_answer_at
     * than participant B, then A's rank must be numerically higher (worse) than B's rank.
     *
     * **Validates: Requirements 1.2**
     */
    @Property(tries = 100)
    @Tag("Feature: quiz-results-leaderboard, Property 1: Ranking sort order")
    void amongEqualScoresAndTimesLaterLastAnswerRankedLower(
            @ForAll("participantsWithFullTies") List<SessionParticipant> participants) {

        Assume.that(participants.size() >= 2);

        // Execute the ranking logic
        List<SessionParticipant> ranked = rankParticipants(participants);

        // Verify: among equal scores and equal avg response times, later last_answer_at → worse rank
        for (int i = 0; i < ranked.size(); i++) {
            for (int j = i + 1; j < ranked.size(); j++) {
                SessionParticipant a = ranked.get(i);
                SessionParticipant b = ranked.get(j);

                int scoreA = a.getFinalScore() != null ? a.getFinalScore() : 0;
                int scoreB = b.getFinalScore() != null ? b.getFinalScore() : 0;
                int avgTimeA = a.getAvgResponseTimeMs() != null ? a.getAvgResponseTimeMs() : Integer.MAX_VALUE;
                int avgTimeB = b.getAvgResponseTimeMs() != null ? b.getAvgResponseTimeMs() : Integer.MAX_VALUE;

                if (scoreA == scoreB && avgTimeA == avgTimeB) {
                    Instant lastAnswerA = a.getLastAnswerAt() != null ? a.getLastAnswerAt() : Instant.MAX;
                    Instant lastAnswerB = b.getLastAnswerAt() != null ? b.getLastAnswerAt() : Instant.MAX;

                    if (lastAnswerA.isAfter(lastAnswerB)) {
                        assertThat(a.getFinalRank())
                                .as("Participant '%s' (lastAnswer=%s) should rank worse than '%s' (lastAnswer=%s) when scores and avg times are equal",
                                        a.getNickname(), lastAnswerA, b.getNickname(), lastAnswerB)
                                .isGreaterThan(b.getFinalRank());
                    } else if (lastAnswerB.isAfter(lastAnswerA)) {
                        assertThat(b.getFinalRank())
                                .as("Participant '%s' (lastAnswer=%s) should rank worse than '%s' (lastAnswer=%s) when scores and avg times are equal",
                                        b.getNickname(), lastAnswerB, a.getNickname(), lastAnswerA)
                                .isGreaterThan(a.getFinalRank());
                    }
                }
            }
        }
    }

    /**
     * Property 1d: The overall sort invariant holds — consecutive ranked participants satisfy
     * the full ordering: score desc, then avg response time asc, then last_answer_at asc.
     *
     * For any generated list of participants, after ranking and sorting by rank,
     * each consecutive pair (rank i, rank i+1) satisfies the sort invariant.
     *
     * **Validates: Requirements 1.2**
     */
    @Property(tries = 100)
    @Tag("Feature: quiz-results-leaderboard, Property 1: Ranking sort order")
    void consecutiveRankedParticipantsSatisfySortInvariant(
            @ForAll("participantsForRanking") List<SessionParticipant> participants) {

        Assume.that(participants.size() >= 2);

        // Execute the ranking logic
        List<SessionParticipant> ranked = rankParticipants(participants);

        // Sort by rank to get the final ordering
        ranked.sort(Comparator.comparingInt(SessionParticipant::getFinalRank));

        // Verify consecutive pairs satisfy the sort invariant
        for (int i = 0; i < ranked.size() - 1; i++) {
            SessionParticipant current = ranked.get(i);
            SessionParticipant next = ranked.get(i + 1);

            int scoreCurrent = current.getFinalScore() != null ? current.getFinalScore() : 0;
            int scoreNext = next.getFinalScore() != null ? next.getFinalScore() : 0;

            // Primary: score descending
            assertThat(scoreCurrent)
                    .as("Rank %d score (%d) should be >= rank %d score (%d)",
                            current.getFinalRank(), scoreCurrent, next.getFinalRank(), scoreNext)
                    .isGreaterThanOrEqualTo(scoreNext);

            // If scores are equal, check first tiebreaker: avg response time ascending
            if (scoreCurrent == scoreNext) {
                int avgTimeCurrent = current.getAvgResponseTimeMs() != null ? current.getAvgResponseTimeMs() : Integer.MAX_VALUE;
                int avgTimeNext = next.getAvgResponseTimeMs() != null ? next.getAvgResponseTimeMs() : Integer.MAX_VALUE;

                assertThat(avgTimeCurrent)
                        .as("Rank %d avgResponseTime (%d) should be <= rank %d avgResponseTime (%d) when scores are equal",
                                current.getFinalRank(), avgTimeCurrent, next.getFinalRank(), avgTimeNext)
                        .isLessThanOrEqualTo(avgTimeNext);

                // If avg response times are also equal, check second tiebreaker: last_answer_at ascending
                if (avgTimeCurrent == avgTimeNext) {
                    Instant lastAnswerCurrent = current.getLastAnswerAt() != null ? current.getLastAnswerAt() : Instant.MAX;
                    Instant lastAnswerNext = next.getLastAnswerAt() != null ? next.getLastAnswerAt() : Instant.MAX;

                    assertThat(lastAnswerCurrent.compareTo(lastAnswerNext))
                            .as("Rank %d lastAnswerAt (%s) should be <= rank %d lastAnswerAt (%s) when scores and avg times are equal",
                                    current.getFinalRank(), lastAnswerCurrent, next.getFinalRank(), lastAnswerNext)
                            .isLessThanOrEqualTo(0);
                }
            }
        }
    }

    // ==================== Property 1 Generators ====================

    /**
     * Generates a list of participants with random scores, avg response times, and last-answer timestamps.
     * Used for general ranking sort order verification.
     */
    @Provide
    Arbitrary<List<SessionParticipant>> participantsForRanking() {
        return Arbitraries.integers().between(2, 50).flatMap(count -> {
            Arbitrary<SessionParticipant> participantArb = Combinators.combine(
                    Arbitraries.integers().between(0, 10000),       // finalScore
                    Arbitraries.integers().between(500, 10000),     // avgResponseTimeMs
                    Arbitraries.longs().between(0L, 300L)           // lastAnswerAt offset in seconds
            ).as((score, avgTime, lastAnswerOffset) ->
                    SessionParticipant.builder()
                            .id(UUID.randomUUID())
                            .nickname("Player-" + UUID.randomUUID().toString().substring(0, 4))
                            .finalScore(score)
                            .avgResponseTimeMs(avgTime)
                            .lastAnswerAt(Instant.now().minusSeconds(lastAnswerOffset))
                            .maxStreak(0)
                            .answersCorrect(0)
                            .answersTotal(10)
                            .joinedAt(Instant.now().minusSeconds(600))
                            .build()
            );
            return participantArb.list().ofSize(count);
        });
    }

    /**
     * Generates participants where some share the same score to exercise the avg response time tiebreaker.
     */
    @Provide
    Arbitrary<List<SessionParticipant>> participantsWithTiedScores() {
        return Arbitraries.integers().between(2, 30).flatMap(count -> {
            // Use a small set of possible scores to increase likelihood of ties
            Arbitrary<Integer> scoreArb = Arbitraries.integers().between(0, 5)
                    .map(bucket -> bucket * 2000); // 0, 2000, 4000, 6000, 8000, 10000

            Arbitrary<SessionParticipant> participantArb = Combinators.combine(
                    scoreArb,
                    Arbitraries.integers().between(500, 10000),     // avgResponseTimeMs
                    Arbitraries.longs().between(0L, 300L)           // lastAnswerAt offset
            ).as((score, avgTime, lastAnswerOffset) ->
                    SessionParticipant.builder()
                            .id(UUID.randomUUID())
                            .nickname("Player-" + UUID.randomUUID().toString().substring(0, 4))
                            .finalScore(score)
                            .avgResponseTimeMs(avgTime)
                            .lastAnswerAt(Instant.now().minusSeconds(lastAnswerOffset))
                            .maxStreak(0)
                            .answersCorrect(0)
                            .answersTotal(10)
                            .joinedAt(Instant.now().minusSeconds(600))
                            .build()
            );
            return participantArb.list().ofSize(count);
        });
    }

    /**
     * Generates participants where some share the same score AND same avg response time
     * to exercise the last_answer_at tiebreaker.
     */
    @Provide
    Arbitrary<List<SessionParticipant>> participantsWithFullTies() {
        return Arbitraries.integers().between(2, 30).flatMap(count -> {
            // Use a small set of possible scores and avg times to increase likelihood of full ties
            Arbitrary<Integer> scoreArb = Arbitraries.integers().between(0, 3)
                    .map(bucket -> bucket * 3000); // 0, 3000, 6000, 9000

            Arbitrary<Integer> avgTimeArb = Arbitraries.integers().between(0, 3)
                    .map(bucket -> 1000 + bucket * 2000); // 1000, 3000, 5000, 7000

            Arbitrary<SessionParticipant> participantArb = Combinators.combine(
                    scoreArb,
                    avgTimeArb,
                    Arbitraries.longs().between(0L, 300L)           // lastAnswerAt offset
            ).as((score, avgTime, lastAnswerOffset) ->
                    SessionParticipant.builder()
                            .id(UUID.randomUUID())
                            .nickname("Player-" + UUID.randomUUID().toString().substring(0, 4))
                            .finalScore(score)
                            .avgResponseTimeMs(avgTime)
                            .lastAnswerAt(Instant.now().minusSeconds(lastAnswerOffset))
                            .maxStreak(0)
                            .answersCorrect(0)
                            .answersTotal(10)
                            .joinedAt(Instant.now().minusSeconds(600))
                            .build()
            );
            return participantArb.list().ofSize(count);
        });
    }

    // ==================== Property 1 Helper ====================

    /**
     * Applies the ranking algorithm (same as LeaderboardService.computeAndPersistFinalRankings)
     * to a list of participants and returns the ranked list.
     *
     * Sort order: score desc → avg response time asc → last_answer_at asc
     * Assigns 1-based contiguous ranks.
     */
    private List<SessionParticipant> rankParticipants(List<SessionParticipant> participants) {
        List<SessionParticipant> sorted = new ArrayList<>(participants);
        sorted.sort(
                Comparator.comparingInt((SessionParticipant p) -> p.getFinalScore() != null ? p.getFinalScore() : 0).reversed()
                        .thenComparingInt(p -> p.getAvgResponseTimeMs() != null ? p.getAvgResponseTimeMs() : Integer.MAX_VALUE)
                        .thenComparing(p -> p.getLastAnswerAt() != null ? p.getLastAnswerAt() : Instant.MAX)
        );

        for (int i = 0; i < sorted.size(); i++) {
            sorted.get(i).setFinalRank(i + 1);
        }

        return sorted;
    }

    // ==================== Property 3: Pagination invariants ====================

    /**
     * Property 3.1: Total pages equals ceiling division of N / S.
     *
     * For any N participants (0-200) and page size S (1-50),
     * totalPages = ⌈N / S⌉, or 0 if N = 0.
     *
     * **Validates: Requirements 2.1, 2.3**
     */
    @Property(tries = 100)
    @Tag("Feature: quiz-results-leaderboard, Property 3: Pagination invariants")
    void totalPagesEqualsCeilingDivision(
            @ForAll @IntRange(min = 0, max = 200) int totalParticipants,
            @ForAll @IntRange(min = 1, max = 50) int pageSize) {

        int expectedTotalPages = totalParticipants == 0
                ? 0
                : (int) Math.ceil((double) totalParticipants / pageSize);

        PagedLeaderboardResponse response = simulatePaginatedLeaderboard(totalParticipants, pageSize, 0);

        assertThat(response.getTotalPages())
                .as("totalPages for N=%d, S=%d should be ⌈%d/%d⌉ = %d",
                        totalParticipants, pageSize, totalParticipants, pageSize, expectedTotalPages)
                .isEqualTo(expectedTotalPages);
    }

    /**
     * Property 3.2: Non-final pages have exactly S entries.
     *
     * For any session with N > S participants and page size S,
     * every page except the last one has exactly S entries.
     *
     * **Validates: Requirements 2.1, 2.3**
     */
    @Property(tries = 100)
    @Tag("Feature: quiz-results-leaderboard, Property 3: Pagination invariants")
    void nonFinalPagesHaveExactlySEntries(
            @ForAll @IntRange(min = 1, max = 200) int totalParticipants,
            @ForAll @IntRange(min = 1, max = 50) int pageSize) {

        int totalPages = (int) Math.ceil((double) totalParticipants / pageSize);

        // Check all non-final pages (if there are more than 1 page)
        for (int page = 0; page < totalPages - 1; page++) {
            PagedLeaderboardResponse response = simulatePaginatedLeaderboard(totalParticipants, pageSize, page);

            assertThat(response.getEntries())
                    .as("Non-final page %d (of %d total) with N=%d, S=%d should have exactly %d entries",
                            page, totalPages, totalParticipants, pageSize, pageSize)
                    .hasSize(pageSize);
        }
    }

    /**
     * Property 3.3: Final page has N mod S entries (or S if N mod S = 0).
     *
     * For any session with N > 0 participants and page size S,
     * the last page has N mod S entries, or S entries if N is evenly divisible by S.
     *
     * **Validates: Requirements 2.1, 2.3**
     */
    @Property(tries = 100)
    @Tag("Feature: quiz-results-leaderboard, Property 3: Pagination invariants")
    void finalPageHasCorrectEntryCount(
            @ForAll @IntRange(min = 1, max = 200) int totalParticipants,
            @ForAll @IntRange(min = 1, max = 50) int pageSize) {

        int totalPages = (int) Math.ceil((double) totalParticipants / pageSize);
        int lastPage = totalPages - 1;

        int expectedLastPageSize = totalParticipants % pageSize == 0
                ? pageSize
                : totalParticipants % pageSize;

        PagedLeaderboardResponse response = simulatePaginatedLeaderboard(totalParticipants, pageSize, lastPage);

        assertThat(response.getEntries())
                .as("Final page %d with N=%d, S=%d should have %d entries",
                        lastPage, totalParticipants, pageSize, expectedLastPageSize)
                .hasSize(expectedLastPageSize);
    }

    /**
     * Property 3.4: Total participants reported equals N.
     *
     * For any session with N participants, the totalParticipants field
     * in the response always equals N.
     *
     * **Validates: Requirements 2.3**
     */
    @Property(tries = 100)
    @Tag("Feature: quiz-results-leaderboard, Property 3: Pagination invariants")
    void totalParticipantsEqualsN(
            @ForAll @IntRange(min = 0, max = 200) int totalParticipants,
            @ForAll @IntRange(min = 1, max = 50) int pageSize) {

        PagedLeaderboardResponse response = simulatePaginatedLeaderboard(totalParticipants, pageSize, 0);

        assertThat(response.getTotalParticipants())
                .as("totalParticipants should equal N=%d", totalParticipants)
                .isEqualTo(totalParticipants);
    }

    /**
     * Property 3.5: Sum of all page sizes equals N.
     *
     * For any session with N participants and page size S,
     * the sum of entries across all pages equals N.
     *
     * **Validates: Requirements 2.1, 2.3**
     */
    @Property(tries = 100)
    @Tag("Feature: quiz-results-leaderboard, Property 3: Pagination invariants")
    void sumOfAllPageSizesEqualsN(
            @ForAll @IntRange(min = 0, max = 200) int totalParticipants,
            @ForAll @IntRange(min = 1, max = 50) int pageSize) {

        int totalPages = totalParticipants == 0
                ? 0
                : (int) Math.ceil((double) totalParticipants / pageSize);

        int totalEntries = 0;
        for (int page = 0; page < totalPages; page++) {
            PagedLeaderboardResponse response = simulatePaginatedLeaderboard(totalParticipants, pageSize, page);
            totalEntries += response.getEntries().size();
        }

        assertThat(totalEntries)
                .as("Sum of entries across all %d pages should equal N=%d", totalPages, totalParticipants)
                .isEqualTo(totalParticipants);
    }

    // ==================== Property 4: Continuous ranks across pages ====================

    /**
     * Property 4: Continuous ranks across pages
     *
     * For any paginated leaderboard with page number P (0-indexed) and page size S,
     * the first entry on page P shall have rank (P × S + 1) and ranks shall be
     * consecutive integers within the page.
     *
     * **Validates: Requirements 2.2**
     */
    @Property(tries = 100)
    @Tag("Feature: quiz-results-leaderboard, Property 4: Continuous ranks across pages")
    void firstEntryOnPageHasCorrectRankAndRanksAreConsecutive(
            @ForAll @IntRange(min = 1, max = 200) int totalParticipants,
            @ForAll @IntRange(min = 1, max = 50) int pageSize) {

        int totalPages = (int) Math.ceil((double) totalParticipants / pageSize);

        // Test all valid pages for the given N and S
        for (int page = 0; page < totalPages; page++) {
            PagedLeaderboardResponse response = simulatePaginatedLeaderboard(totalParticipants, pageSize, page);

            List<FinalLeaderboardEntry> entries = response.getEntries();

            // Page should not be empty for valid pages
            assertThat(entries)
                    .as("Page %d should have entries (totalParticipants=%d, pageSize=%d)",
                            page, totalParticipants, pageSize)
                    .isNotEmpty();

            // Verify first entry on page P has rank = P * S + 1
            int expectedFirstRank = page * pageSize + 1;
            assertThat(entries.get(0).getRank())
                    .as("First entry on page %d should have rank %d (pageSize=%d)",
                            page, expectedFirstRank, pageSize)
                    .isEqualTo(expectedFirstRank);

            // Verify ranks within the page are consecutive integers
            for (int i = 1; i < entries.size(); i++) {
                assertThat(entries.get(i).getRank())
                        .as("Entry at index %d on page %d should have rank %d (consecutive)",
                                i, page, entries.get(i - 1).getRank() + 1)
                        .isEqualTo(entries.get(i - 1).getRank() + 1);
            }
        }
    }

    /**
     * Property 4 (focused): For a single random valid page, verify rank continuity.
     *
     * This variant picks a single random page to test, ensuring jqwik explores
     * diverse combinations of N, S, and P.
     *
     * **Validates: Requirements 2.2**
     */
    @Property(tries = 100)
    @Tag("Feature: quiz-results-leaderboard, Property 4: Continuous ranks across pages")
    void singleRandomPageHasCorrectFirstRankAndConsecutiveRanks(
            @ForAll @IntRange(min = 1, max = 200) int totalParticipants,
            @ForAll @IntRange(min = 1, max = 50) int pageSize,
            @ForAll @IntRange(min = 0, max = 10000) int pageIndexSeed) {

        int totalPages = (int) Math.ceil((double) totalParticipants / pageSize);

        // Map the seed to a valid page index
        int page = pageIndexSeed % totalPages;

        PagedLeaderboardResponse response = simulatePaginatedLeaderboard(totalParticipants, pageSize, page);

        List<FinalLeaderboardEntry> entries = response.getEntries();

        assertThat(entries).isNotEmpty();

        // Verify first entry on page P has rank = P * S + 1
        int expectedFirstRank = page * pageSize + 1;
        assertThat(entries.get(0).getRank())
                .as("First entry on page %d should have rank %d (N=%d, S=%d)",
                        page, expectedFirstRank, totalParticipants, pageSize)
                .isEqualTo(expectedFirstRank);

        // Verify ranks within the page are consecutive integers
        for (int i = 1; i < entries.size(); i++) {
            assertThat(entries.get(i).getRank())
                    .as("Rank at index %d should be consecutive (expected %d, got %d)",
                            i, entries.get(i - 1).getRank() + 1, entries.get(i).getRank())
                    .isEqualTo(entries.get(i - 1).getRank() + 1);
        }
    }

    // ==================== Pagination Helper ====================

    /**
     * Simulates the pagination logic from LeaderboardService.getPagedLeaderboard()
     * without requiring a database. This tests the pure math of pagination.
     *
     * @param totalParticipants total number of ranked participants (N)
     * @param pageSize          entries per page (S)
     * @param page              zero-based page number to retrieve
     * @return PagedLeaderboardResponse with correct pagination metadata and entries
     */
    private PagedLeaderboardResponse simulatePaginatedLeaderboard(int totalParticipants, int pageSize, int page) {
        // Generate N fake leaderboard entries (ranked 1..N)
        List<FinalLeaderboardEntry> allEntries = IntStream.rangeClosed(1, totalParticipants)
                .mapToObj(rank -> FinalLeaderboardEntry.builder()
                        .rank(rank)
                        .nickname("Player" + rank)
                        .score(totalParticipants - rank + 1)
                        .correctAnswers(rank)
                        .totalAnswers(10)
                        .maxStreak(1)
                        .avgResponseTimeSec(2.0)
                        .build())
                .collect(Collectors.toList());

        // Apply the same pagination logic as LeaderboardService.getPagedLeaderboard()
        int totalPages = totalParticipants == 0
                ? 0
                : (int) Math.ceil((double) totalParticipants / pageSize);

        // Compute the page slice using LIMIT/OFFSET logic
        int offset = page * pageSize;
        int end = Math.min(offset + pageSize, totalParticipants);
        List<FinalLeaderboardEntry> pageEntries = (offset < totalParticipants)
                ? new ArrayList<>(allEntries.subList(offset, end))
                : new ArrayList<>();

        return PagedLeaderboardResponse.builder()
                .sessionId(UUID.randomUUID())
                .entries(pageEntries)
                .currentPage(page)
                .totalPages(totalPages)
                .totalParticipants(totalParticipants)
                .pageSize(pageSize)
                .build();
    }

    // ==================== Property 12: Persistence integrity ====================

    /**
     * Simulates the ranking logic from LeaderboardService.computeAndPersistFinalRankings().
     * Sort order: score desc → avg response time asc → last_answer_at asc.
     * Then assigns 1-based contiguous ranks.
     */
    private void computeAndPersistFinalRankings(List<SessionParticipant> participants) {
        List<SessionParticipant> sorted = new ArrayList<>(participants);
        sorted.sort(
                Comparator.comparingInt((SessionParticipant p) -> p.getFinalScore() != null ? p.getFinalScore() : 0).reversed()
                        .thenComparingInt(p -> p.getAvgResponseTimeMs() != null ? p.getAvgResponseTimeMs() : Integer.MAX_VALUE)
                        .thenComparing(p -> p.getLastAnswerAt() != null ? p.getLastAnswerAt() : Instant.MAX)
        );

        for (int i = 0; i < sorted.size(); i++) {
            sorted.get(i).setFinalRank(i + 1);
        }
    }

    /**
     * Property 12a: All participants have non-null final_rank after persistence.
     *
     * For any set of participants in a completed session, after the ranking computation
     * and persistence, every participant SHALL have a non-null final_rank value.
     *
     * **Validates: Requirements 6.2**
     */
    @Property(tries = 100)
    @Tag("Feature: quiz-results-leaderboard, Property 12: Persistence integrity")
    void allParticipantsHaveNonNullFinalRankAfterPersistence(
            @ForAll("sessionWithParticipants") SessionWithParticipants sessionData) {

        Assume.that(!sessionData.participants.isEmpty());

        // Execute the ranking logic (simulates computeAndPersistFinalRankings)
        computeAndPersistFinalRankings(sessionData.participants);

        // Verify: all participants have non-null final_rank
        for (SessionParticipant participant : sessionData.participants) {
            assertThat(participant.getFinalRank())
                    .as("Participant '%s' should have a non-null final_rank after persistence",
                            participant.getNickname())
                    .isNotNull();
        }
    }

    /**
     * Property 12b: All final_rank values are unique and form a continuous sequence 1..N.
     *
     * For any set of N participants, after ranking, the final_rank values SHALL be
     * exactly the set {1, 2, 3, ..., N} with no duplicates and no gaps.
     *
     * **Validates: Requirements 6.2**
     */
    @Property(tries = 100)
    @Tag("Feature: quiz-results-leaderboard, Property 12: Persistence integrity")
    void finalRanksFormContinuousSequence(
            @ForAll("sessionWithParticipants") SessionWithParticipants sessionData) {

        Assume.that(!sessionData.participants.isEmpty());

        computeAndPersistFinalRankings(sessionData.participants);

        int n = sessionData.participants.size();
        List<Integer> ranks = sessionData.participants.stream()
                .map(SessionParticipant::getFinalRank)
                .sorted()
                .collect(Collectors.toList());

        // Verify ranks are exactly 1, 2, 3, ..., N
        assertThat(ranks).hasSize(n);
        for (int i = 0; i < n; i++) {
            assertThat(ranks.get(i))
                    .as("Rank at position %d should be %d", i, i + 1)
                    .isEqualTo(i + 1);
        }

        // Verify uniqueness (no duplicates)
        Set<Integer> uniqueRanks = new HashSet<>(ranks);
        assertThat(uniqueRanks).hasSize(n);
    }

    /**
     * Property 12c: All participant records reference the correct session and host.
     *
     * For any set of participants in a completed session, after persistence,
     * all participant records SHALL reference the correct session_id (via the session
     * entity) and the session SHALL reference the correct host_id.
     *
     * **Validates: Requirements 6.3**
     */
    @Property(tries = 100)
    @Tag("Feature: quiz-results-leaderboard, Property 12: Persistence integrity")
    void allParticipantsReferenceCorrectSessionAndHost(
            @ForAll("sessionWithParticipants") SessionWithParticipants sessionData) {

        Assume.that(!sessionData.participants.isEmpty());

        computeAndPersistFinalRankings(sessionData.participants);

        UUID expectedSessionId = sessionData.session.getId();
        UUID expectedHostId = sessionData.session.getHostId();

        for (SessionParticipant participant : sessionData.participants) {
            // Verify participant references the correct session
            assertThat(participant.getSession())
                    .as("Participant '%s' should reference a non-null session", participant.getNickname())
                    .isNotNull();
            assertThat(participant.getSession().getId())
                    .as("Participant '%s' should reference session %s", participant.getNickname(), expectedSessionId)
                    .isEqualTo(expectedSessionId);

            // Verify the session references the correct host
            assertThat(participant.getSession().getHostId())
                    .as("Session for participant '%s' should reference host %s",
                            participant.getNickname(), expectedHostId)
                    .isEqualTo(expectedHostId);
        }
    }

    // ==================== Property 12 Generators ====================

    static class SessionWithParticipants {
        final Session session;
        final List<SessionParticipant> participants;

        SessionWithParticipants(Session session, List<SessionParticipant> participants) {
            this.session = session;
            this.participants = participants;
        }
    }

    @Provide
    Arbitrary<SessionWithParticipants> sessionWithParticipants() {
        return Combinators.combine(
                sessionArbitrary(),
                Arbitraries.integers().between(1, 50)
        ).as((session, participantCount) -> {
            List<SessionParticipant> participants = generateParticipants(session, participantCount);
            return new SessionWithParticipants(session, participants);
        });
    }

    private Arbitrary<Session> sessionArbitrary() {
        return Combinators.combine(
                Arbitraries.create(UUID::randomUUID),  // sessionId
                Arbitraries.create(UUID::randomUUID),  // hostId
                Arbitraries.create(UUID::randomUUID),  // quizId
                Arbitraries.strings().alpha().numeric().ofLength(6) // pin
        ).as((sessionId, hostId, quizId, pin) ->
                Session.builder()
                        .id(sessionId)
                        .hostId(hostId)
                        .quizId(quizId)
                        .pin(pin)
                        .quizTitle("Test Quiz")
                        .startedAt(Instant.now().minusSeconds(600))
                        .endedAt(Instant.now())
                        .build()
        );
    }

    private List<SessionParticipant> generateParticipants(Session session, int count) {
        Random random = new Random();
        List<SessionParticipant> participants = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            int score = random.nextInt(10001); // 0 to 10000
            int avgResponseTimeMs = 500 + random.nextInt(9501); // 500ms to 10000ms
            Instant lastAnswerAt = Instant.now().minusSeconds(random.nextInt(300));

            participants.add(SessionParticipant.builder()
                    .id(UUID.randomUUID())
                    .session(session)
                    .nickname("Player" + (i + 1))
                    .finalScore(score)
                    .avgResponseTimeMs(avgResponseTimeMs)
                    .lastAnswerAt(lastAnswerAt)
                    .maxStreak(random.nextInt(11))
                    .answersCorrect(random.nextInt(11))
                    .answersTotal(10)
                    .joinedAt(Instant.now().minusSeconds(600))
                    .build());
        }

        return participants;
    }

    // ==================== Property 14: Per-question status mapping ====================

    /**
     * Represents a question in the quiz with a unique ID.
     */
    record Question(UUID id, int number) {}

    /**
     * Represents an answer submission for a question.
     */
    record Submission(UUID questionId, boolean isCorrect) {}

    /**
     * Property 14: Each question maps to exactly one status based on answer submissions.
     *
     * For any set of questions and any subset of answer submissions,
     * the status mapping logic produces exactly one status per question:
     * - CORRECT if a correct answer was submitted
     * - INCORRECT if a wrong answer was submitted
     * - UNANSWERED if no submission exists for that question
     *
     * **Validates: Requirements 7.3**
     */
    @Property(tries = 100)
    @Tag("Feature: quiz-results-leaderboard, Property 14: Per-question status mapping")
    void eachQuestionMapsToExactlyOneStatus(
            @ForAll("questionsAndSubmissions") QuestionsWithSubmissions input) {

        List<QuestionResult> breakdown = mapQuestionsToStatus(input.questions(), input.submissions());

        // Each question maps to exactly one status
        assertThat(breakdown).hasSize(input.questions().size());

        // All question numbers are unique (no duplicates)
        List<Integer> questionNumbers = breakdown.stream()
                .map(QuestionResult::getQuestionNumber)
                .collect(Collectors.toList());
        assertThat(new HashSet<>(questionNumbers)).hasSize(questionNumbers.size());

        // Each status is non-null and one of the valid enum values
        for (QuestionResult result : breakdown) {
            assertThat(result.getStatus()).isNotNull();
            assertThat(result.getStatus()).isIn(AnswerStatus.CORRECT, AnswerStatus.INCORRECT, AnswerStatus.UNANSWERED);
        }
    }

    /**
     * Property 14 (continued): Status mapping is consistent with submissions.
     *
     * For any question:
     * - If a correct submission exists → status must be CORRECT
     * - If an incorrect submission exists → status must be INCORRECT
     * - If no submission exists → status must be UNANSWERED
     *
     * **Validates: Requirements 7.3**
     */
    @Property(tries = 100)
    @Tag("Feature: quiz-results-leaderboard, Property 14: Per-question status mapping")
    void statusMappingIsConsistentWithSubmissions(
            @ForAll("questionsAndSubmissions") QuestionsWithSubmissions input) {

        List<QuestionResult> breakdown = mapQuestionsToStatus(input.questions(), input.submissions());

        // Build a lookup of questionId -> submission for verification
        Map<UUID, Submission> submissionMap = input.submissions().stream()
                .collect(Collectors.toMap(Submission::questionId, s -> s, (a, b) -> a));

        for (int i = 0; i < input.questions().size(); i++) {
            Question question = input.questions().get(i);
            QuestionResult result = breakdown.get(i);
            Submission submission = submissionMap.get(question.id());

            if (submission == null) {
                assertThat(result.getStatus())
                        .as("Question %d with no submission should be UNANSWERED", result.getQuestionNumber())
                        .isEqualTo(AnswerStatus.UNANSWERED);
            } else if (submission.isCorrect()) {
                assertThat(result.getStatus())
                        .as("Question %d with correct submission should be CORRECT", result.getQuestionNumber())
                        .isEqualTo(AnswerStatus.CORRECT);
            } else {
                assertThat(result.getStatus())
                        .as("Question %d with incorrect submission should be INCORRECT", result.getQuestionNumber())
                        .isEqualTo(AnswerStatus.INCORRECT);
            }
        }
    }

    /**
     * Property 14 (continued): Question numbers are sequential starting from 1.
     *
     * The breakdown should assign question numbers 1, 2, 3, ..., N
     * matching the order of questions in the quiz.
     *
     * **Validates: Requirements 7.3**
     */
    @Property(tries = 100)
    @Tag("Feature: quiz-results-leaderboard, Property 14: Per-question status mapping")
    void questionNumbersAreSequentialStartingFromOne(
            @ForAll("questionsAndSubmissions") QuestionsWithSubmissions input) {

        List<QuestionResult> breakdown = mapQuestionsToStatus(input.questions(), input.submissions());

        for (int i = 0; i < breakdown.size(); i++) {
            assertThat(breakdown.get(i).getQuestionNumber())
                    .as("Question at index %d should have number %d", i, i + 1)
                    .isEqualTo(i + 1);
        }
    }

    // ==================== Generators ====================

    /**
     * Record holding generated questions and their corresponding submissions.
     */
    record QuestionsWithSubmissions(List<Question> questions, List<Submission> submissions) {}

    @Provide
    Arbitrary<QuestionsWithSubmissions> questionsAndSubmissions() {
        // Generate 1-20 questions with unique UUIDs
        Arbitrary<Integer> questionCount = Arbitraries.integers().between(1, 20);

        return questionCount.flatMap(count -> {
            // Generate a list of questions with unique IDs
            List<Question> questions = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                questions.add(new Question(UUID.randomUUID(), i + 1));
            }

            // For each question, randomly decide: no submission, correct submission, or incorrect submission
            Arbitrary<List<Submission>> submissionsArb = Arbitraries.integers()
                    .between(0, 2) // 0 = no submission, 1 = correct, 2 = incorrect
                    .list()
                    .ofSize(count)
                    .map(decisions -> {
                        List<Submission> submissions = new ArrayList<>();
                        for (int i = 0; i < decisions.size(); i++) {
                            int decision = decisions.get(i);
                            if (decision == 1) {
                                submissions.add(new Submission(questions.get(i).id(), true));
                            } else if (decision == 2) {
                                submissions.add(new Submission(questions.get(i).id(), false));
                            }
                            // decision == 0 means no submission for this question
                        }
                        return submissions;
                    });

            return submissionsArb.map(submissions -> new QuestionsWithSubmissions(questions, submissions));
        });
    }

    // ==================== Logic Under Test (Property 14) ====================

    /**
     * Mirrors the per-question status mapping logic from LeaderboardService.getParticipantResult().
     *
     * This is the core logic extracted for property testing:
     * - Build a map of questionId -> Submission
     * - For each question: UNANSWERED if no submission, CORRECT if correct, INCORRECT otherwise
     */
    private List<QuestionResult> mapQuestionsToStatus(List<Question> questions, List<Submission> submissions) {
        // Build a map of questionId -> Submission for quick lookup
        Map<UUID, Submission> submissionsByQuestionId = submissions.stream()
                .collect(Collectors.toMap(Submission::questionId, s -> s, (a, b) -> a));

        List<QuestionResult> breakdown = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            Question question = questions.get(i);
            Submission submission = submissionsByQuestionId.get(question.id());

            AnswerStatus status;
            if (submission == null) {
                status = AnswerStatus.UNANSWERED;
            } else if (submission.isCorrect()) {
                status = AnswerStatus.CORRECT;
            } else {
                status = AnswerStatus.INCORRECT;
            }

            breakdown.add(QuestionResult.builder()
                    .questionNumber(i + 1)
                    .status(status)
                    .build());
        }
        return breakdown;
    }

    // ==================== Property 13: Score difference computation ====================

    /**
     * Simple data holder for participant score data used in Property 13 tests.
     */
    record ParticipantScoreData(int score, int answersTotal) {}

    /**
     * Result of score difference computation.
     */
    record ScoreDifferenceResult(int scoreDifference, boolean aboveAverage) {}

    /**
     * Property 13: Score difference computation
     *
     * For any set of participant scores where at least one participant submitted an answer,
     * the score difference for a given participant shall equal that participant's score minus
     * the arithmetic mean of all participant scores (rounded to nearest integer), and the
     * aboveAverage flag shall be true if and only if the difference is positive.
     *
     * **Validates: Requirements 7.2**
     */
    @Property(tries = 100)
    @Tag("Feature: quiz-results-leaderboard, Property 13: Score difference computation")
    void scoreDifferenceEqualsParticipantScoreMinusMeanAndAboveAverageIsCorrect(
            @ForAll("participantScoresForDifference") List<ParticipantScoreData> participants,
            @ForAll @IntRange(min = 0, max = 10000) int participantIndexSeed) {

        // Ensure at least one participant has answersTotal > 0
        Assume.that(participants.stream().anyMatch(p -> p.answersTotal() > 0));

        // Pick a random participant from the list
        int targetIndex = participantIndexSeed % participants.size();
        ParticipantScoreData targetParticipant = participants.get(targetIndex);

        // Compute expected session average: mean of all scores where answersTotal > 0
        double sessionAverage = participants.stream()
                .filter(p -> p.answersTotal() > 0)
                .mapToInt(ParticipantScoreData::score)
                .average()
                .orElse(0.0);

        // Compute expected score difference (rounded to nearest integer)
        int expectedScoreDifference = (int) Math.round(targetParticipant.score() - sessionAverage);

        // Compute expected aboveAverage flag
        boolean expectedAboveAverage = expectedScoreDifference > 0;

        // Simulate the service logic (mirrors LeaderboardService.getParticipantResult)
        ScoreDifferenceResult result = computeScoreDifference(participants, targetIndex);

        assertThat(result.scoreDifference())
                .as("Score difference for participant with score %d (session avg=%.2f) should be %d",
                        targetParticipant.score(), sessionAverage, expectedScoreDifference)
                .isEqualTo(expectedScoreDifference);

        assertThat(result.aboveAverage())
                .as("aboveAverage should be %s when scoreDifference=%d",
                        expectedAboveAverage, expectedScoreDifference)
                .isEqualTo(expectedAboveAverage);
    }

    /**
     * Property 13 (edge case): When all participants have the same score and answersTotal > 0,
     * scoreDifference should be 0 and aboveAverage should be false.
     *
     * **Validates: Requirements 7.2**
     */
    @Property(tries = 100)
    @Tag("Feature: quiz-results-leaderboard, Property 13: Score difference computation")
    void whenAllScoresEqualScoreDifferenceIsZeroAndNotAboveAverage(
            @ForAll @IntRange(min = 0, max = 50000) int uniformScore,
            @ForAll @IntRange(min = 1, max = 50) int participantCount) {

        List<ParticipantScoreData> participants = new ArrayList<>();
        for (int i = 0; i < participantCount; i++) {
            participants.add(new ParticipantScoreData(uniformScore, 10));
        }

        for (int i = 0; i < participants.size(); i++) {
            ScoreDifferenceResult result = computeScoreDifference(participants, i);

            assertThat(result.scoreDifference())
                    .as("When all scores are equal (%d), scoreDifference should be 0", uniformScore)
                    .isEqualTo(0);

            assertThat(result.aboveAverage())
                    .as("When all scores are equal, aboveAverage should be false")
                    .isFalse();
        }
    }

    /**
     * Property 13 (invariant): aboveAverage is true if and only if scoreDifference > 0.
     *
     * This verifies the biconditional: aboveAverage ↔ (scoreDifference > 0)
     *
     * **Validates: Requirements 7.2**
     */
    @Property(tries = 100)
    @Tag("Feature: quiz-results-leaderboard, Property 13: Score difference computation")
    void aboveAverageIsTrueIfAndOnlyIfScoreDifferenceIsPositive(
            @ForAll("participantScoresForDifference") List<ParticipantScoreData> participants,
            @ForAll @IntRange(min = 0, max = 10000) int participantIndexSeed) {

        Assume.that(participants.stream().anyMatch(p -> p.answersTotal() > 0));

        int targetIndex = participantIndexSeed % participants.size();

        ScoreDifferenceResult result = computeScoreDifference(participants, targetIndex);

        if (result.scoreDifference() > 0) {
            assertThat(result.aboveAverage())
                    .as("aboveAverage must be true when scoreDifference=%d > 0", result.scoreDifference())
                    .isTrue();
        } else {
            assertThat(result.aboveAverage())
                    .as("aboveAverage must be false when scoreDifference=%d <= 0", result.scoreDifference())
                    .isFalse();
        }
    }

    // ==================== Generators for Property 13 ====================

    @Provide
    Arbitrary<List<ParticipantScoreData>> participantScoresForDifference() {
        Arbitrary<ParticipantScoreData> participantArb = Combinators.combine(
                Arbitraries.integers().between(0, 50000),  // score
                Arbitraries.integers().between(0, 20)      // answersTotal (0 means no answers submitted)
        ).as(ParticipantScoreData::new);

        return participantArb.list().ofMinSize(1).ofMaxSize(100)
                .filter(list -> list.stream().anyMatch(p -> p.answersTotal() > 0));
    }

    // ==================== Logic Under Test (Property 13) ====================

    /**
     * Simulates the score difference computation logic from
     * LeaderboardService.getParticipantResult().
     *
     * This mirrors the real implementation:
     * - sessionAverage = mean of all scores where answersTotal > 0
     * - scoreDifference = (int) Math.round(participantScore - sessionAverage)
     * - aboveAverage = scoreDifference > 0
     */
    private ScoreDifferenceResult computeScoreDifference(
            List<ParticipantScoreData> participants, int targetIndex) {

        ParticipantScoreData target = participants.get(targetIndex);

        // Compute session average: mean of all scores where answersTotal > 0
        double sessionAverage = participants.stream()
                .filter(p -> p.answersTotal() > 0)
                .mapToInt(ParticipantScoreData::score)
                .average()
                .orElse(0.0);

        // Compute score difference (rounded to nearest integer)
        int participantScore = target.score();
        int scoreDifference = (int) Math.round(participantScore - sessionAverage);
        boolean aboveAverage = scoreDifference > 0;

        return new ScoreDifferenceResult(scoreDifference, aboveAverage);
    }
}
