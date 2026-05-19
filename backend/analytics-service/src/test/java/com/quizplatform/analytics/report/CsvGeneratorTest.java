package com.quizplatform.analytics.report;

import com.quizplatform.analytics.dto.LeaderboardEntryDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for CsvGenerator.
 *
 * Validates: Requirements 4.1, 4.2, 4.3, 4.7
 */
class CsvGeneratorTest {

    private CsvGenerator csvGenerator;

    @BeforeEach
    void setUp() {
        csvGenerator = new CsvGenerator();
    }

    @Test
    void shouldGenerateHeaderOnly_whenNoParticipants() throws IOException {
        byte[] result = csvGenerator.generate(Collections.emptyList());
        String csv = new String(result, StandardCharsets.UTF_8);

        assertThat(csv).isEqualTo(
                "rank,nickname,score,correct_answers,total_answers,max_streak,avg_response_time_sec\n");
    }

    @Test
    void shouldGenerateHeaderOnly_whenNullList() throws IOException {
        byte[] result = csvGenerator.generate(null);
        String csv = new String(result, StandardCharsets.UTF_8);

        assertThat(csv).isEqualTo(
                "rank,nickname,score,correct_answers,total_answers,max_streak,avg_response_time_sec\n");
    }

    @Test
    void shouldGenerateValidCsv_withSingleParticipant() throws IOException {
        LeaderboardEntryDTO entry = LeaderboardEntryDTO.builder()
                .rank(1)
                .nickname("Alice")
                .score(8500)
                .correctAnswers(8)
                .totalAnswers(10)
                .maxStreak(5)
                .avgResponseTimeSec(3.24)
                .build();

        byte[] result = csvGenerator.generate(List.of(entry));
        String csv = new String(result, StandardCharsets.UTF_8);
        String[] lines = csv.split("\n");

        assertThat(lines).hasSize(2);
        assertThat(lines[0]).isEqualTo("rank,nickname,score,correct_answers,total_answers,max_streak,avg_response_time_sec");
        assertThat(lines[1]).isEqualTo("1,Alice,8500,8,10,5,3.2");
    }

    @Test
    void shouldSortRowsByRankAscending() throws IOException {
        LeaderboardEntryDTO entry1 = LeaderboardEntryDTO.builder()
                .rank(3).nickname("Charlie").score(5000)
                .correctAnswers(5).totalAnswers(10).maxStreak(2).avgResponseTimeSec(4.5)
                .build();
        LeaderboardEntryDTO entry2 = LeaderboardEntryDTO.builder()
                .rank(1).nickname("Alice").score(8500)
                .correctAnswers(8).totalAnswers(10).maxStreak(5).avgResponseTimeSec(3.2)
                .build();
        LeaderboardEntryDTO entry3 = LeaderboardEntryDTO.builder()
                .rank(2).nickname("Bob").score(7000)
                .correctAnswers(7).totalAnswers(10).maxStreak(4).avgResponseTimeSec(3.8)
                .build();

        byte[] result = csvGenerator.generate(Arrays.asList(entry1, entry2, entry3));
        String csv = new String(result, StandardCharsets.UTF_8);
        String[] lines = csv.split("\n");

        assertThat(lines).hasSize(4);
        assertThat(lines[1]).startsWith("1,Alice");
        assertThat(lines[2]).startsWith("2,Bob");
        assertThat(lines[3]).startsWith("3,Charlie");
    }

    @Test
    void shouldRoundAvgResponseTimeTo1DecimalPlace() throws IOException {
        LeaderboardEntryDTO entry = LeaderboardEntryDTO.builder()
                .rank(1).nickname("Player").score(1000)
                .correctAnswers(5).totalAnswers(10).maxStreak(3)
                .avgResponseTimeSec(3.456)
                .build();

        byte[] result = csvGenerator.generate(List.of(entry));
        String csv = new String(result, StandardCharsets.UTF_8);
        String[] lines = csv.split("\n");

        // 3.456 rounded to 1 decimal place = 3.5
        assertThat(lines[1]).endsWith("3.5");
    }

    @Test
    void shouldEscapeNicknameWithComma() throws IOException {
        LeaderboardEntryDTO entry = LeaderboardEntryDTO.builder()
                .rank(1).nickname("Last, First").score(1000)
                .correctAnswers(5).totalAnswers(10).maxStreak(3).avgResponseTimeSec(2.0)
                .build();

        byte[] result = csvGenerator.generate(List.of(entry));
        String csv = new String(result, StandardCharsets.UTF_8);
        String[] lines = csv.split("\n");

        assertThat(lines[1]).isEqualTo("1,\"Last, First\",1000,5,10,3,2.0");
    }

    @Test
    void shouldEscapeNicknameWithDoubleQuote() throws IOException {
        LeaderboardEntryDTO entry = LeaderboardEntryDTO.builder()
                .rank(1).nickname("The \"Great\"").score(1000)
                .correctAnswers(5).totalAnswers(10).maxStreak(3).avgResponseTimeSec(2.0)
                .build();

        byte[] result = csvGenerator.generate(List.of(entry));
        String csv = new String(result, StandardCharsets.UTF_8);
        String[] lines = csv.split("\n");

        assertThat(lines[1]).isEqualTo("1,\"The \"\"Great\"\"\",1000,5,10,3,2.0");
    }

    @Test
    void shouldProduceUtf8EncodedOutput() throws IOException {
        LeaderboardEntryDTO entry = LeaderboardEntryDTO.builder()
                .rank(1).nickname("Ñoño").score(1000)
                .correctAnswers(5).totalAnswers(10).maxStreak(3).avgResponseTimeSec(2.0)
                .build();

        byte[] result = csvGenerator.generate(List.of(entry));
        String csv = new String(result, StandardCharsets.UTF_8);

        assertThat(csv).contains("Ñoño");
    }
}
