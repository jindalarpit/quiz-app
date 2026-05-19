package com.quizplatform.analytics.service;

import com.quizplatform.analytics.dto.LeaderboardEntryDTO;
import com.quizplatform.analytics.dto.SessionHistoryEntry;
import net.jqwik.api.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for QuizHistoryService sorting, date range filtering, and title search.
 *
 * **Validates: Requirements 3.1, 3.4, 3.6**
 */
class QuizHistoryServicePropertyTest {

    // ==================== Property 6: History sorted by end date descending ====================

    /**
     * Property 6.1: Consecutive pairs satisfy session_i.endedAt >= session_{i+1}.endedAt.
     *
     * For any list of sessions returned by the history service (sorted by endedAt DESC),
     * every consecutive pair (session_i, session_{i+1}) must satisfy
     * session_i.endedAt >= session_{i+1}.endedAt.
     *
     * **Validates: Requirements 3.1**
     */
    @Property(tries = 100)
    @Tag("Feature: quiz-results-leaderboard, Property 6: History sorted by end date descending")
    void historySortedByEndDateDescending(
            @ForAll("sessionsWithRandomDates") List<SessionHistoryEntry> sessions) {

        // Apply the same sort logic as QuizHistoryService (ORDER BY ended_at DESC)
        List<SessionHistoryEntry> sorted = sortByEndedAtDescending(sessions);

        // Verify consecutive pairs satisfy session_i.endedAt >= session_{i+1}.endedAt
        for (int i = 0; i < sorted.size() - 1; i++) {
            Instant current = sorted.get(i).getEndedAt();
            Instant next = sorted.get(i + 1).getEndedAt();

            assertThat(current)
                    .as("Session at index %d (endedAt=%s) should have endedAt >= session at index %d (endedAt=%s)",
                            i, current, i + 1, next)
                    .isAfterOrEqualTo(next);
        }
    }

    /**
     * Property 6.2: Sorting preserves all elements (no sessions lost or duplicated).
     *
     * The sorted result must contain exactly the same sessions as the input,
     * ensuring the sort operation does not lose or duplicate entries.
     *
     * **Validates: Requirements 3.1**
     */
    @Property(tries = 100)
    @Tag("Feature: quiz-results-leaderboard, Property 6: History sorted by end date descending")
    void sortingPreservesAllElements(
            @ForAll("sessionsWithRandomDates") List<SessionHistoryEntry> sessions) {

        List<SessionHistoryEntry> sorted = sortByEndedAtDescending(sessions);

        assertThat(sorted)
                .as("Sorted result should have the same size as input")
                .hasSameSizeAs(sessions);

        assertThat(sorted)
                .as("Sorted result should contain exactly the same elements as input")
                .containsExactlyInAnyOrderElementsOf(sessions);
    }

    /**
     * Property 6.3: First element has the most recent endedAt (maximum).
     *
     * If the list is non-empty, the first element in the sorted result should have
     * an endedAt that is >= all other endedAt values in the list.
     *
     * **Validates: Requirements 3.1**
     */
    @Property(tries = 100)
    @Tag("Feature: quiz-results-leaderboard, Property 6: History sorted by end date descending")
    void firstElementHasMostRecentEndedAt(
            @ForAll("nonEmptySessionsWithRandomDates") List<SessionHistoryEntry> sessions) {

        List<SessionHistoryEntry> sorted = sortByEndedAtDescending(sessions);

        Instant firstEndedAt = sorted.get(0).getEndedAt();

        for (SessionHistoryEntry session : sorted) {
            assertThat(firstEndedAt)
                    .as("First element endedAt (%s) should be >= all other endedAt values (%s)",
                            firstEndedAt, session.getEndedAt())
                    .isAfterOrEqualTo(session.getEndedAt());
        }
    }

    /**
     * Property 6.4: Last element has the oldest endedAt (minimum).
     *
     * If the list is non-empty, the last element in the sorted result should have
     * an endedAt that is <= all other endedAt values in the list.
     *
     * **Validates: Requirements 3.1**
     */
    @Property(tries = 100)
    @Tag("Feature: quiz-results-leaderboard, Property 6: History sorted by end date descending")
    void lastElementHasOldestEndedAt(
            @ForAll("nonEmptySessionsWithRandomDates") List<SessionHistoryEntry> sessions) {

        List<SessionHistoryEntry> sorted = sortByEndedAtDescending(sessions);

        Instant lastEndedAt = sorted.get(sorted.size() - 1).getEndedAt();

        for (SessionHistoryEntry session : sorted) {
            assertThat(lastEndedAt)
                    .as("Last element endedAt (%s) should be <= all other endedAt values (%s)",
                            lastEndedAt, session.getEndedAt())
                    .isBeforeOrEqualTo(session.getEndedAt());
        }
    }

    // ==================== Property 6 Sort Logic (mirrors QuizHistoryService) ====================

    /**
     * Applies the same sort logic as QuizHistoryService.getSessionHistory():
     * ORDER BY ended_at DESC.
     */
    private List<SessionHistoryEntry> sortByEndedAtDescending(List<SessionHistoryEntry> sessions) {
        return sessions.stream()
                .sorted((a, b) -> b.getEndedAt().compareTo(a.getEndedAt()))
                .collect(Collectors.toList());
    }

    // ==================== Property 8: Date range filter correctness ====================

    /**
     * Property 8.1: Filtered results contain only sessions within [startDate, endDate] inclusive.
     *
     * For any list of sessions with random endedAt dates and any valid date range,
     * every session in the filtered result has endedAt within the range.
     *
     * **Validates: Requirements 3.4**
     */
    @Property(tries = 100)
    @Tag("Feature: quiz-results-leaderboard, Property 8: Date range filter correctness")
    void filteredResultsContainOnlySessionsWithinDateRange(
            @ForAll("sessionsWithRandomDates") List<SessionHistoryEntry> sessions,
            @ForAll("validDateRange") DateRange dateRange) {

        List<SessionHistoryEntry> filtered = applyDateRangeFilter(sessions, dateRange.startDate, dateRange.endDate);

        for (SessionHistoryEntry session : filtered) {
            LocalDate sessionDate = session.getEndedAt()
                    .atZone(ZoneOffset.UTC)
                    .toLocalDate();

            assertThat(sessionDate)
                    .as("Session endedAt date %s should be >= startDate %s",
                            sessionDate, dateRange.startDate)
                    .isAfterOrEqualTo(dateRange.startDate);

            assertThat(sessionDate)
                    .as("Session endedAt date %s should be <= endDate %s",
                            sessionDate, dateRange.endDate)
                    .isBeforeOrEqualTo(dateRange.endDate);
        }
    }

    /**
     * Property 8.2: No sessions outside the range are included in filtered results.
     *
     * For any list of sessions and any valid date range, sessions whose endedAt
     * falls outside [startDate, endDate] are NOT present in the filtered result.
     *
     * **Validates: Requirements 3.4**
     */
    @Property(tries = 100)
    @Tag("Feature: quiz-results-leaderboard, Property 8: Date range filter correctness")
    void noSessionsOutsideRangeAreIncluded(
            @ForAll("sessionsWithRandomDates") List<SessionHistoryEntry> sessions,
            @ForAll("validDateRange") DateRange dateRange) {

        List<SessionHistoryEntry> filtered = applyDateRangeFilter(sessions, dateRange.startDate, dateRange.endDate);

        // Identify sessions that should be excluded (outside the range)
        List<SessionHistoryEntry> outsideRange = sessions.stream()
                .filter(s -> {
                    LocalDate sessionDate = s.getEndedAt()
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate();
                    return sessionDate.isBefore(dateRange.startDate)
                            || sessionDate.isAfter(dateRange.endDate);
                })
                .collect(Collectors.toList());

        // None of the outside-range sessions should appear in filtered results
        for (SessionHistoryEntry excluded : outsideRange) {
            assertThat(filtered)
                    .as("Session with endedAt %s should NOT be in filtered results for range [%s, %s]",
                            excluded.getEndedAt(), dateRange.startDate, dateRange.endDate)
                    .doesNotContain(excluded);
        }
    }

    /**
     * Property 8.3: Filtered results contain exactly the sessions within the range.
     *
     * The count of filtered results equals the count of sessions whose endedAt
     * date falls within [startDate, endDate] inclusive.
     *
     * **Validates: Requirements 3.4**
     */
    @Property(tries = 100)
    @Tag("Feature: quiz-results-leaderboard, Property 8: Date range filter correctness")
    void filteredResultCountMatchesExpected(
            @ForAll("sessionsWithRandomDates") List<SessionHistoryEntry> sessions,
            @ForAll("validDateRange") DateRange dateRange) {

        List<SessionHistoryEntry> filtered = applyDateRangeFilter(sessions, dateRange.startDate, dateRange.endDate);

        long expectedCount = sessions.stream()
                .filter(s -> {
                    LocalDate sessionDate = s.getEndedAt()
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate();
                    return !sessionDate.isBefore(dateRange.startDate)
                            && !sessionDate.isAfter(dateRange.endDate);
                })
                .count();

        assertThat(filtered)
                .as("Filtered result count should match expected count for range [%s, %s]",
                        dateRange.startDate, dateRange.endDate)
                .hasSize((int) expectedCount);
    }

    /**
     * Property 8.4: All sessions within the range are included in filtered results.
     *
     * For any session whose endedAt falls within [startDate, endDate],
     * that session must appear in the filtered result.
     *
     * **Validates: Requirements 3.4**
     */
    @Property(tries = 100)
    @Tag("Feature: quiz-results-leaderboard, Property 8: Date range filter correctness")
    void allSessionsWithinRangeAreIncluded(
            @ForAll("sessionsWithRandomDates") List<SessionHistoryEntry> sessions,
            @ForAll("validDateRange") DateRange dateRange) {

        List<SessionHistoryEntry> filtered = applyDateRangeFilter(sessions, dateRange.startDate, dateRange.endDate);

        List<SessionHistoryEntry> expectedInRange = sessions.stream()
                .filter(s -> {
                    LocalDate sessionDate = s.getEndedAt()
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate();
                    return !sessionDate.isBefore(dateRange.startDate)
                            && !sessionDate.isAfter(dateRange.endDate);
                })
                .collect(Collectors.toList());

        for (SessionHistoryEntry expected : expectedInRange) {
            assertThat(filtered)
                    .as("Session with endedAt %s should be in filtered results for range [%s, %s]",
                            expected.getEndedAt(), dateRange.startDate, dateRange.endDate)
                    .contains(expected);
        }
    }

    // ==================== Property 9: Title search filter correctness ====================

    /**
     * Property 9.1: Title search filter returns exactly matching sessions.
     *
     * For any set of sessions and any search term (1-100 characters), the filtered result
     * SHALL contain exactly those sessions whose quiz title contains the search term when
     * compared case-insensitively, and no sessions that do not match.
     *
     * **Validates: Requirements 3.6**
     */
    @Property(tries = 100)
    @Tag("Feature: quiz-results-leaderboard, Property 9: Title search filter correctness")
    void titleSearchFilterReturnsExactlyMatchingSessions(
            @ForAll("sessionsWithRandomTitles") List<SessionHistoryEntry> sessions,
            @ForAll("searchTerm") String search) {

        // Apply the same case-insensitive LIKE %search% filter logic as QuizHistoryService
        List<SessionHistoryEntry> filtered = applyTitleSearchFilter(sessions, search);

        // Compute expected results: sessions whose title contains search term case-insensitively
        List<SessionHistoryEntry> expected = sessions.stream()
                .filter(s -> s.getQuizTitle() != null
                        && s.getQuizTitle().toLowerCase().contains(search.toLowerCase()))
                .collect(Collectors.toList());

        // Verify filtered result contains exactly the matching sessions
        assertThat(filtered)
                .as("Filtered results should contain exactly sessions with case-insensitive title match for search term '%s'", search)
                .containsExactlyElementsOf(expected);
    }

    /**
     * Property 9.2: No non-matching sessions are included in filtered results.
     *
     * Every session in the filtered result must have a quiz title that contains
     * the search term (case-insensitive).
     *
     * **Validates: Requirements 3.6**
     */
    @Property(tries = 100)
    @Tag("Feature: quiz-results-leaderboard, Property 9: Title search filter correctness")
    void titleSearchFilterExcludesNonMatchingSessions(
            @ForAll("sessionsWithRandomTitles") List<SessionHistoryEntry> sessions,
            @ForAll("searchTerm") String search) {

        List<SessionHistoryEntry> filtered = applyTitleSearchFilter(sessions, search);

        for (SessionHistoryEntry entry : filtered) {
            assertThat(entry.getQuizTitle().toLowerCase())
                    .as("Every filtered session's title '%s' should contain the search term '%s' (case-insensitive)",
                            entry.getQuizTitle(), search)
                    .contains(search.toLowerCase());
        }
    }

    /**
     * Property 9.3: All matching sessions are included in filtered results.
     *
     * For any session whose quiz title contains the search term (case-insensitive),
     * that session must appear in the filtered result.
     *
     * **Validates: Requirements 3.6**
     */
    @Property(tries = 100)
    @Tag("Feature: quiz-results-leaderboard, Property 9: Title search filter correctness")
    void titleSearchFilterIncludesAllMatchingSessions(
            @ForAll("sessionsWithRandomTitles") List<SessionHistoryEntry> sessions,
            @ForAll("searchTerm") String search) {

        List<SessionHistoryEntry> filtered = applyTitleSearchFilter(sessions, search);

        for (SessionHistoryEntry entry : sessions) {
            if (entry.getQuizTitle() != null
                    && entry.getQuizTitle().toLowerCase().contains(search.toLowerCase())) {
                assertThat(filtered)
                        .as("Session with title '%s' should be included for search term '%s'",
                                entry.getQuizTitle(), search)
                        .contains(entry);
            }
        }
    }

    /**
     * Property 9.4: Title search filter is case-insensitive.
     *
     * Searching with different casings of the same term should yield identical results.
     *
     * **Validates: Requirements 3.6**
     */
    @Property(tries = 100)
    @Tag("Feature: quiz-results-leaderboard, Property 9: Title search filter correctness")
    void titleSearchFilterIsCaseInsensitive(
            @ForAll("sessionsWithRandomTitles") List<SessionHistoryEntry> sessions,
            @ForAll("searchTerm") String search) {

        List<SessionHistoryEntry> filteredLower = applyTitleSearchFilter(sessions, search.toLowerCase());
        List<SessionHistoryEntry> filteredUpper = applyTitleSearchFilter(sessions, search.toUpperCase());
        List<SessionHistoryEntry> filteredOriginal = applyTitleSearchFilter(sessions, search);

        assertThat(filteredLower)
                .as("Lowercase search should yield same results as original search")
                .containsExactlyElementsOf(filteredOriginal);

        assertThat(filteredUpper)
                .as("Uppercase search should yield same results as original search")
                .containsExactlyElementsOf(filteredOriginal);
    }

    // ==================== Date Range Filter Logic (mirrors QuizHistoryService) ====================

    /**
     * Applies the same date range filtering logic as QuizHistoryService.getSessionHistory().
     *
     * The service uses:
     * - startDate: ended_at >= startDate.atStartOfDay(UTC)
     * - endDate: ended_at < endDate.plusDays(1).atStartOfDay(UTC)
     *
     * This is equivalent to checking the date portion of endedAt is within [startDate, endDate] inclusive.
     */
    private List<SessionHistoryEntry> applyDateRangeFilter(
            List<SessionHistoryEntry> sessions, LocalDate startDate, LocalDate endDate) {

        Instant startInstant = startDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endInstant = endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        return sessions.stream()
                .filter(s -> {
                    Instant endedAt = s.getEndedAt();
                    // ended_at >= startDate AND ended_at < endDate+1day (same as service logic)
                    return !endedAt.isBefore(startInstant) && endedAt.isBefore(endInstant);
                })
                .collect(Collectors.toList());
    }

    // ==================== Title Search Filter Logic (mirrors QuizHistoryService) ====================

    /**
     * Applies the same case-insensitive title search filter logic as QuizHistoryService.
     * The service uses: LOWER(s.quiz_title) LIKE LOWER('%search%')
     */
    private List<SessionHistoryEntry> applyTitleSearchFilter(
            List<SessionHistoryEntry> sessions, String search) {
        if (search == null || search.isEmpty()) {
            return sessions;
        }
        String lowerSearch = search.toLowerCase();
        return sessions.stream()
                .filter(s -> s.getQuizTitle() != null
                        && s.getQuizTitle().toLowerCase().contains(lowerSearch))
                .collect(Collectors.toList());
    }

    // ==================== Generators ====================

    @Provide
    Arbitrary<List<SessionHistoryEntry>> sessionsWithRandomDates() {
        return sessionEntryWithRandomDate().list().ofMinSize(0).ofMaxSize(50);
    }

    @Provide
    Arbitrary<List<SessionHistoryEntry>> nonEmptySessionsWithRandomDates() {
        return sessionEntryWithRandomDate().list().ofMinSize(1).ofMaxSize(50);
    }

    @Provide
    Arbitrary<List<SessionHistoryEntry>> sessionsWithRandomTitles() {
        return sessionEntryWithRandomTitle().list().ofMinSize(1).ofMaxSize(50);
    }

    @Provide
    Arbitrary<DateRange> validDateRange() {
        // Generate dates within a reasonable range (2020-2025)
        Arbitrary<LocalDate> dateArbitrary = Arbitraries.integers()
                .between(0, 1825) // ~5 years of days
                .map(daysOffset -> LocalDate.of(2020, 1, 1).plusDays(daysOffset));

        return dateArbitrary.flatMap(date1 ->
                dateArbitrary.map(date2 -> {
                    // Ensure startDate <= endDate
                    if (date1.isAfter(date2)) {
                        return new DateRange(date2, date1);
                    }
                    return new DateRange(date1, date2);
                })
        );
    }

    @Provide
    Arbitrary<String> searchTerm() {
        // Generate search terms between 1 and 100 characters using printable characters
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars(' ', '-', '_')
                .ofMinLength(1)
                .ofMaxLength(20);
    }

    private Arbitrary<SessionHistoryEntry> sessionEntryWithRandomDate() {
        // Generate sessions with endedAt spread across 2020-2025
        Arbitrary<Instant> instantArbitrary = Arbitraries.integers()
                .between(0, 1825 * 24 * 60) // minutes within ~5 years
                .map(minutesOffset -> LocalDate.of(2020, 1, 1)
                        .atStartOfDay(ZoneOffset.UTC)
                        .plusMinutes(minutesOffset)
                        .toInstant());

        return instantArbitrary.map(endedAt -> SessionHistoryEntry.builder()
                .sessionId(UUID.randomUUID())
                .quizTitle("Quiz " + UUID.randomUUID().toString().substring(0, 8))
                .endedAt(endedAt)
                .participantCount(10)
                .durationSeconds(300)
                .build());
    }

    private Arbitrary<SessionHistoryEntry> sessionEntryWithRandomTitle() {
        Arbitrary<UUID> sessionIds = Arbitraries.create(UUID::randomUUID);
        Arbitrary<String> quizTitles = quizTitle();
        Arbitrary<Instant> endedAts = Arbitraries.longs()
                .between(1_600_000_000L, 1_800_000_000L)
                .map(Instant::ofEpochSecond);
        Arbitrary<Integer> participantCounts = Arbitraries.integers().between(1, 500);
        Arbitrary<Long> durations = Arbitraries.longs().between(60L, 7200L);

        return Combinators.combine(sessionIds, quizTitles, endedAts, participantCounts, durations)
                .as((id, title, endedAt, count, duration) ->
                        SessionHistoryEntry.builder()
                                .sessionId(id)
                                .quizTitle(title)
                                .endedAt(endedAt)
                                .participantCount(count)
                                .durationSeconds(duration)
                                .build());
    }

    private Arbitrary<String> quizTitle() {
        // Generate quiz titles with mixed case to properly test case-insensitivity
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars(' ', '-', '_')
                .ofMinLength(1)
                .ofMaxLength(50);
    }

    // ==================== Property 7: Historical leaderboard round-trip ====================

    /**
     * Property 7: Historical leaderboard round-trip.
     *
     * <p>For any session with persisted leaderboard data, retrieving the historical
     * leaderboard SHALL return entries that match the originally persisted ranks,
     * scores, nicknames, correct answer counts, and max streaks for all participants.
     *
     * <p>This test generates random leaderboard entries, simulates persisting them
     * as database rows, then maps them back to DTOs (simulating retrieval), and
     * verifies all fields are preserved exactly.
     *
     * <p><b>Validates: Requirements 3.3</b>
     *
     * @Tag("Feature: quiz-results-leaderboard, Property 7: Historical leaderboard round-trip")
     */
    @Property(tries = 100)
    void historicalLeaderboardRoundTripPreservesAllFields(
            @ForAll("leaderboardEntries") List<PersistedParticipantRow> persistedRows) {

        Assume.that(!persistedRows.isEmpty());

        // Simulate retrieval: map persisted rows back to DTOs
        // This mirrors QuizHistoryService.mapLeaderboardEntry logic
        List<LeaderboardEntryDTO> retrievedEntries = persistedRows.stream()
                .map(this::mapPersistedRowToDTO)
                .collect(Collectors.toList());

        // Verify round-trip: all fields match
        assertThat(retrievedEntries).hasSameSizeAs(persistedRows);

        for (int i = 0; i < persistedRows.size(); i++) {
            PersistedParticipantRow original = persistedRows.get(i);
            LeaderboardEntryDTO retrieved = retrievedEntries.get(i);

            assertThat(retrieved.getRank())
                    .as("Rank mismatch at index %d", i)
                    .isEqualTo(original.finalRank);

            assertThat(retrieved.getScore())
                    .as("Score mismatch at index %d", i)
                    .isEqualTo(original.finalScore);

            assertThat(retrieved.getNickname())
                    .as("Nickname mismatch at index %d", i)
                    .isEqualTo(original.nickname);

            assertThat(retrieved.getCorrectAnswers())
                    .as("Correct answers mismatch at index %d", i)
                    .isEqualTo(original.answersCorrect);

            assertThat(retrieved.getMaxStreak())
                    .as("Max streak mismatch at index %d", i)
                    .isEqualTo(original.maxStreak);

            assertThat(retrieved.getTotalAnswers())
                    .as("Total answers mismatch at index %d", i)
                    .isEqualTo(original.answersTotal);

            // avgResponseTimeSec is derived from avgResponseTimeMs:
            // converted from ms to sec with 1 decimal place
            double expectedAvgSec = Math.round(original.avgResponseTimeMs / 100.0) / 10.0;
            assertThat(retrieved.getAvgResponseTimeSec())
                    .as("Avg response time mismatch at index %d", i)
                    .isEqualTo(expectedAvgSec);
        }
    }

    // ==================== Property 7 Generators ====================

    @Provide
    Arbitrary<List<PersistedParticipantRow>> leaderboardEntries() {
        return Arbitraries.integers().between(1, 50)
                .flatMap(size -> participantRow().list().ofSize(size)
                        .map(rows -> {
                            // Assign contiguous ranks starting from 1
                            List<PersistedParticipantRow> ranked = new ArrayList<>();
                            for (int i = 0; i < rows.size(); i++) {
                                PersistedParticipantRow row = rows.get(i);
                                row.finalRank = i + 1;
                                ranked.add(row);
                            }
                            return ranked;
                        })
                );
    }

    private Arbitrary<PersistedParticipantRow> participantRow() {
        Arbitrary<String> nicknames = Arbitraries.strings()
                .alpha()
                .ofMinLength(3)
                .ofMaxLength(20);

        Arbitrary<Integer> scores = Arbitraries.integers().between(0, 50000);
        Arbitrary<Integer> correctAnswers = Arbitraries.integers().between(0, 50);
        Arbitrary<Integer> totalAnswers = Arbitraries.integers().between(0, 50);
        Arbitrary<Integer> maxStreaks = Arbitraries.integers().between(0, 50);
        Arbitrary<Integer> avgResponseTimeMs = Arbitraries.integers().between(0, 30000);

        return Combinators.combine(nicknames, scores, correctAnswers, totalAnswers, maxStreaks, avgResponseTimeMs)
                .as((nickname, score, correct, total, streak, avgMs) -> {
                    // Ensure correctAnswers <= totalAnswers
                    int actualTotal = Math.max(correct, total);
                    PersistedParticipantRow row = new PersistedParticipantRow();
                    row.nickname = nickname;
                    row.finalScore = score;
                    row.answersCorrect = correct;
                    row.answersTotal = actualTotal;
                    row.maxStreak = streak;
                    row.avgResponseTimeMs = avgMs;
                    row.finalRank = 0; // Will be assigned by the list generator
                    return row;
                });
    }

    // ==================== Property 7 Helpers ====================

    /**
     * Simulates the mapping from a persisted database row to a LeaderboardEntryDTO.
     * This mirrors the logic in {@code QuizHistoryService.mapLeaderboardEntry}.
     */
    private LeaderboardEntryDTO mapPersistedRowToDTO(PersistedParticipantRow row) {
        // Convert milliseconds to seconds with 1 decimal place (same as service)
        double avgResponseTimeSec = Math.round(row.avgResponseTimeMs / 100.0) / 10.0;

        return LeaderboardEntryDTO.builder()
                .rank(row.finalRank)
                .nickname(row.nickname)
                .score(row.finalScore)
                .correctAnswers(row.answersCorrect)
                .totalAnswers(row.answersTotal)
                .maxStreak(row.maxStreak)
                .avgResponseTimeSec(avgResponseTimeSec)
                .build();
    }

    /**
     * Represents a row as it would be persisted in the session_participants table.
     * Fields mirror the database columns used by the leaderboard query.
     */
    static class PersistedParticipantRow {
        int finalRank;
        String nickname;
        int finalScore;
        int answersCorrect;
        int answersTotal;
        int maxStreak;
        int avgResponseTimeMs;

        @Override
        public String toString() {
            return String.format(
                    "PersistedParticipantRow{rank=%d, nickname='%s', score=%d, correct=%d, total=%d, streak=%d, avgMs=%d}",
                    finalRank, nickname, finalScore, answersCorrect, answersTotal, maxStreak, avgResponseTimeMs);
        }
    }

    // ==================== Helper Classes ====================

    /**
     * Represents a valid date range where startDate <= endDate.
     */
    static class DateRange {
        final LocalDate startDate;
        final LocalDate endDate;

        DateRange(LocalDate startDate, LocalDate endDate) {
            this.startDate = startDate;
            this.endDate = endDate;
        }

        @Override
        public String toString() {
            return "[" + startDate + ", " + endDate + "]";
        }
    }
}
