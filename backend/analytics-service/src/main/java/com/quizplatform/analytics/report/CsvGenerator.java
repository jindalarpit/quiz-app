package com.quizplatform.analytics.report;

import com.quizplatform.analytics.dto.LeaderboardEntryDTO;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;

/**
 * Generates UTF-8 CSV files from leaderboard data.
 *
 * <p>The CSV contains a header row followed by data rows sorted by rank ascending.
 * Average response time is rounded to 1 decimal place.
 * Zero-participant sessions produce a header-only CSV.
 */
@Component
public class CsvGenerator {

    private static final String HEADER = "rank,nickname,score,correct_answers,total_answers,max_streak,avg_response_time_sec";
    private static final String LINE_SEPARATOR = "\n";

    /**
     * Generates a UTF-8 encoded CSV byte array from the given leaderboard entries.
     *
     * @param entries list of leaderboard entries (may be empty for zero-participant sessions)
     * @return UTF-8 encoded CSV content as a byte array
     * @throws IOException if an I/O error occurs during generation
     */
    public byte[] generate(List<LeaderboardEntryDTO> entries) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (OutputStreamWriter writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8)) {
            // Write header row
            writer.write(HEADER);
            writer.write(LINE_SEPARATOR);

            // Sort entries by rank ascending and write data rows
            if (entries != null && !entries.isEmpty()) {
                List<LeaderboardEntryDTO> sorted = entries.stream()
                        .sorted(Comparator.comparingInt(LeaderboardEntryDTO::getRank))
                        .toList();

                for (LeaderboardEntryDTO entry : sorted) {
                    writer.write(formatRow(entry));
                    writer.write(LINE_SEPARATOR);
                }
            }

            writer.flush();
        }
        return baos.toByteArray();
    }

    private String formatRow(LeaderboardEntryDTO entry) {
        String nickname = escapeCsvField(entry.getNickname());
        String avgResponseTime = String.format("%.1f", entry.getAvgResponseTimeSec());

        return String.join(",",
                String.valueOf(entry.getRank()),
                nickname,
                String.valueOf(entry.getScore()),
                String.valueOf(entry.getCorrectAnswers()),
                String.valueOf(entry.getTotalAnswers()),
                String.valueOf(entry.getMaxStreak()),
                avgResponseTime
        );
    }

    /**
     * Escapes a CSV field value. If the value contains a comma, double quote,
     * or newline, it is enclosed in double quotes with internal quotes doubled.
     */
    private String escapeCsvField(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
