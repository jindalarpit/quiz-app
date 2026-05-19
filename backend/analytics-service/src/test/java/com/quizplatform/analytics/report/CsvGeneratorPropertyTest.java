package com.quizplatform.analytics.report;

import com.quizplatform.analytics.dto.LeaderboardEntryDTO;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for CsvGenerator.
 *
 * <p><b>Validates: Requirements 4.1, 4.2, 4.3</b>
 */
class CsvGeneratorPropertyTest {

    private static final String EXPECTED_HEADER =
            "rank,nickname,score,correct_answers,total_answers,max_streak,avg_response_time_sec";

    private final CsvGenerator csvGenerator = new CsvGenerator();

    /**
     * Property 10a: The generated CSV header row has the correct columns.
     *
     * <p><b>Validates: Requirements 4.1, 4.2</b>
     */
    @Property(tries = 100)
    @Tag("Feature: quiz-results-leaderboard, Property 10: CSV generation round-trip")
    void headerRowHasCorrectColumns(
            @ForAll("leaderboardEntries") List<LeaderboardEntryDTO> entries) throws IOException {

        byte[] csvBytes = csvGenerator.generate(entries);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);
        String[] lines = csv.split("\n", -1);

        assertThat(lines.length).isGreaterThanOrEqualTo(1);
        assertThat(lines[0]).isEqualTo(EXPECTED_HEADER);
    }

    /**
     * Property 10b: Data rows are sorted by rank in ascending order.
     *
     * <p><b>Validates: Requirements 4.3</b>
     */
    @Property(tries = 100)
    @Tag("Feature: quiz-results-leaderboard, Property 10: CSV generation round-trip")
    void dataRowsSortedByRankAscending(
            @ForAll("leaderboardEntries") List<LeaderboardEntryDTO> entries) throws IOException {

        byte[] csvBytes = csvGenerator.generate(entries);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);
        String[] lines = csv.split("\n", -1);

        // Skip header row and any trailing empty line
        List<Integer> ranks = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isEmpty()) continue;
            String firstField = parseCsvFirstField(lines[i]);
            ranks.add(Integer.parseInt(firstField));
        }

        // Verify ranks are in ascending order
        for (int i = 0; i < ranks.size() - 1; i++) {
            assertThat(ranks.get(i))
                    .as("Rank at row %d should be <= rank at row %d", i, i + 1)
                    .isLessThanOrEqualTo(ranks.get(i + 1));
        }
    }

    /**
     * Property 10c: Parsed CSV values match the original input data
     * (with avg_response_time rounded to 1 decimal place).
     *
     * <p><b>Validates: Requirements 4.1, 4.2, 4.3</b>
     */
    @Property(tries = 100)
    @Tag("Feature: quiz-results-leaderboard, Property 10: CSV generation round-trip")
    void parsedValuesMatchInputData(
            @ForAll("leaderboardEntriesWithSimpleNicknames") List<LeaderboardEntryDTO> entries)
            throws IOException {

        byte[] csvBytes = csvGenerator.generate(entries);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);
        String[] lines = csv.split("\n", -1);

        // Number of data rows should match number of entries
        int dataRowCount = 0;
        for (int i = 1; i < lines.length; i++) {
            if (!lines[i].isEmpty()) dataRowCount++;
        }
        assertThat(dataRowCount).isEqualTo(entries.size());

        // Sort entries by rank to match expected CSV order
        List<LeaderboardEntryDTO> sortedEntries = entries.stream()
                .sorted((a, b) -> Integer.compare(a.getRank(), b.getRank()))
                .toList();

        int dataIndex = 0;
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isEmpty()) continue;

            String[] fields = lines[i].split(",");
            LeaderboardEntryDTO expected = sortedEntries.get(dataIndex);

            assertThat(Integer.parseInt(fields[0]))
                    .as("rank at row %d", dataIndex)
                    .isEqualTo(expected.getRank());
            assertThat(fields[1])
                    .as("nickname at row %d", dataIndex)
                    .isEqualTo(expected.getNickname());
            assertThat(Integer.parseInt(fields[2]))
                    .as("score at row %d", dataIndex)
                    .isEqualTo(expected.getScore());
            assertThat(Integer.parseInt(fields[3]))
                    .as("correctAnswers at row %d", dataIndex)
                    .isEqualTo(expected.getCorrectAnswers());
            assertThat(Integer.parseInt(fields[4]))
                    .as("totalAnswers at row %d", dataIndex)
                    .isEqualTo(expected.getTotalAnswers());
            assertThat(Integer.parseInt(fields[5]))
                    .as("maxStreak at row %d", dataIndex)
                    .isEqualTo(expected.getMaxStreak());

            double parsedAvgTime = Double.parseDouble(fields[6]);
            double expectedRounded = Math.round(expected.getAvgResponseTimeSec() * 10.0) / 10.0;
            assertThat(parsedAvgTime)
                    .as("avgResponseTimeSec at row %d", dataIndex)
                    .isEqualTo(expectedRounded);

            dataIndex++;
        }
    }

    /**
     * Property 10 (empty case): For zero-participant sessions, the CSV contains only the header row.
     *
     * <p><b>Validates: Requirements 4.1, 4.2</b>
     */
    @Property(tries = 100)
    @Tag("Feature: quiz-results-leaderboard, Property 10: CSV generation round-trip")
    void emptyEntriesProduceHeaderOnly() throws IOException {
        byte[] csvBytes = csvGenerator.generate(List.of());
        String csv = new String(csvBytes, StandardCharsets.UTF_8);
        String[] lines = csv.split("\n", -1);

        // Should have header + trailing empty from final newline
        List<String> nonEmptyLines = new ArrayList<>();
        for (String line : lines) {
            if (!line.isEmpty()) nonEmptyLines.add(line);
        }
        assertThat(nonEmptyLines).hasSize(1);
        assertThat(nonEmptyLines.get(0)).isEqualTo(EXPECTED_HEADER);
    }

    // --- Generators ---

    /**
     * Generates a list of LeaderboardEntryDTO with unique ranks and simple nicknames
     * (no commas, quotes, or newlines) for round-trip value verification.
     */
    @Provide
    Arbitrary<List<LeaderboardEntryDTO>> leaderboardEntriesWithSimpleNicknames() {
        return Arbitraries.integers().between(0, 30).flatMap(size -> {
            if (size == 0) {
                return Arbitraries.just(List.of());
            }
            return Arbitraries.integers().between(1, size)
                    .list().ofSize(size)
                    .map(ignored -> {
                        List<LeaderboardEntryDTO> entries = new ArrayList<>();
                        for (int i = 1; i <= size; i++) {
                            entries.add(buildEntry(i, generateSimpleNickname(i)));
                        }
                        return entries;
                    })
                    .flatMap(entries -> shuffleList(entries));
        });
    }

    /**
     * Generates a list of LeaderboardEntryDTO with various nicknames
     * (may include special characters) for header and sort verification.
     */
    @Provide
    Arbitrary<List<LeaderboardEntryDTO>> leaderboardEntries() {
        return Arbitraries.integers().between(0, 30).flatMap(size -> {
            if (size == 0) {
                return Arbitraries.just(List.of());
            }
            Arbitrary<LeaderboardEntryDTO> entryArb = Arbitraries.integers().between(1, 1000)
                    .flatMap(rank -> Arbitraries.strings()
                            .alpha().ofMinLength(1).ofMaxLength(20)
                            .map(nick -> LeaderboardEntryDTO.builder()
                                    .rank(rank)
                                    .nickname(nick)
                                    .score((int) (Math.random() * 10000))
                                    .correctAnswers((int) (Math.random() * 20))
                                    .totalAnswers(20)
                                    .maxStreak((int) (Math.random() * 20))
                                    .avgResponseTimeSec(Math.random() * 30.0)
                                    .build()));
            return entryArb.list().ofSize(size).map(entries -> {
                // Assign unique ranks
                List<LeaderboardEntryDTO> result = new ArrayList<>();
                for (int i = 0; i < entries.size(); i++) {
                    LeaderboardEntryDTO e = entries.get(i);
                    result.add(LeaderboardEntryDTO.builder()
                            .rank(i + 1)
                            .nickname(e.getNickname())
                            .score(e.getScore())
                            .correctAnswers(e.getCorrectAnswers())
                            .totalAnswers(e.getTotalAnswers())
                            .maxStreak(e.getMaxStreak())
                            .avgResponseTimeSec(e.getAvgResponseTimeSec())
                            .build());
                }
                return result;
            });
        });
    }

    // --- Helper methods ---

    private LeaderboardEntryDTO buildEntry(int rank, String nickname) {
        return LeaderboardEntryDTO.builder()
                .rank(rank)
                .nickname(nickname)
                .score((int) (Math.random() * 10000))
                .correctAnswers((int) (Math.random() * 20))
                .totalAnswers(20)
                .maxStreak((int) (Math.random() * 20))
                .avgResponseTimeSec(Math.random() * 30.0)
                .build();
    }

    private String generateSimpleNickname(int index) {
        return "Player" + index;
    }

    private <T> Arbitrary<List<T>> shuffleList(List<T> list) {
        return Arbitraries.shuffle(list).map(ArrayList::new);
    }

    /**
     * Parses the first field from a CSV line, handling quoted fields.
     */
    private String parseCsvFirstField(String line) {
        if (line.startsWith("\"")) {
            int endQuote = 1;
            while (endQuote < line.length()) {
                if (line.charAt(endQuote) == '"') {
                    if (endQuote + 1 < line.length() && line.charAt(endQuote + 1) == '"') {
                        endQuote += 2;
                    } else {
                        break;
                    }
                } else {
                    endQuote++;
                }
            }
            return line.substring(1, endQuote);
        }
        int comma = line.indexOf(',');
        return comma >= 0 ? line.substring(0, comma) : line;
    }
}
