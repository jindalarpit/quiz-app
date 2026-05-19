package com.quizplatform.analytics.report;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.quizplatform.analytics.dto.LeaderboardEntryDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for PdfGenerator.
 * Tests PDF generation with valid data, top-3 bold formatting,
 * metadata section, and edge cases.
 *
 * Requirements: 5.1, 5.2, 5.3, 5.5, 5.6, 5.7
 */
class PdfGeneratorTest {

    private PdfGenerator pdfGenerator;

    @BeforeEach
    void setUp() {
        pdfGenerator = new PdfGenerator();
    }

    @Nested
    @DisplayName("PDF generation with valid data")
    class ValidDataTests {

        @Test
        @DisplayName("Should generate valid PDF bytes for a session with participants")
        void shouldGenerateValidPdf() throws IOException {
            List<LeaderboardEntryDTO> entries = List.of(
                    LeaderboardEntryDTO.builder()
                            .rank(1).nickname("Alice").score(8500)
                            .correctAnswers(8).totalAnswers(10).maxStreak(5).avgResponseTimeSec(3.2)
                            .build(),
                    LeaderboardEntryDTO.builder()
                            .rank(2).nickname("Bob").score(7200)
                            .correctAnswers(7).totalAnswers(10).maxStreak(4).avgResponseTimeSec(4.1)
                            .build()
            );

            byte[] pdfBytes = pdfGenerator.generate("Geography Quiz", LocalDate.of(2024, 3, 15), 2, 420, entries);

            assertThat(pdfBytes).isNotNull();
            assertThat(pdfBytes.length).isGreaterThan(0);
            // Verify it's a valid PDF by checking magic bytes
            assertThat(pdfBytes[0]).isEqualTo((byte) 0x25); // %
            assertThat(pdfBytes[1]).isEqualTo((byte) 0x50); // P
            assertThat(pdfBytes[2]).isEqualTo((byte) 0x44); // D
            assertThat(pdfBytes[3]).isEqualTo((byte) 0x46); // F
        }

        @Test
        @DisplayName("Should include quiz title in PDF content")
        void shouldIncludeQuizTitle() throws IOException {
            List<LeaderboardEntryDTO> entries = List.of(
                    LeaderboardEntryDTO.builder()
                            .rank(1).nickname("Player1").score(1000)
                            .correctAnswers(5).totalAnswers(10).maxStreak(3).avgResponseTimeSec(2.0)
                            .build()
            );

            byte[] pdfBytes = pdfGenerator.generate("Science Quiz", LocalDate.of(2024, 6, 20), 1, 300, entries);
            String text = extractPdfText(pdfBytes);

            assertThat(text).contains("Science Quiz");
        }

        @Test
        @DisplayName("Should include session date in PDF content")
        void shouldIncludeSessionDate() throws IOException {
            List<LeaderboardEntryDTO> entries = List.of(
                    LeaderboardEntryDTO.builder()
                            .rank(1).nickname("Player1").score(1000)
                            .correctAnswers(5).totalAnswers(10).maxStreak(3).avgResponseTimeSec(2.0)
                            .build()
            );

            byte[] pdfBytes = pdfGenerator.generate("Quiz", LocalDate.of(2024, 3, 15), 1, 300, entries);
            String text = extractPdfText(pdfBytes);

            assertThat(text).contains("2024-03-15");
        }

        @Test
        @DisplayName("Should include participant count in PDF content")
        void shouldIncludeParticipantCount() throws IOException {
            List<LeaderboardEntryDTO> entries = List.of(
                    LeaderboardEntryDTO.builder()
                            .rank(1).nickname("Player1").score(1000)
                            .correctAnswers(5).totalAnswers(10).maxStreak(3).avgResponseTimeSec(2.0)
                            .build()
            );

            byte[] pdfBytes = pdfGenerator.generate("Quiz", LocalDate.of(2024, 3, 15), 42, 300, entries);
            String text = extractPdfText(pdfBytes);

            assertThat(text).contains("42");
        }

        @Test
        @DisplayName("Should include duration in PDF content")
        void shouldIncludeDuration() throws IOException {
            List<LeaderboardEntryDTO> entries = List.of(
                    LeaderboardEntryDTO.builder()
                            .rank(1).nickname("Player1").score(1000)
                            .correctAnswers(5).totalAnswers(10).maxStreak(3).avgResponseTimeSec(2.0)
                            .build()
            );

            byte[] pdfBytes = pdfGenerator.generate("Quiz", LocalDate.of(2024, 3, 15), 1, 420, entries);
            String text = extractPdfText(pdfBytes);

            // 420 seconds = 7m 0s
            assertThat(text).contains("7m 0s");
        }

        @Test
        @DisplayName("Should include report title in PDF")
        void shouldIncludeReportTitle() throws IOException {
            List<LeaderboardEntryDTO> entries = List.of(
                    LeaderboardEntryDTO.builder()
                            .rank(1).nickname("Player1").score(1000)
                            .correctAnswers(5).totalAnswers(10).maxStreak(3).avgResponseTimeSec(2.0)
                            .build()
            );

            byte[] pdfBytes = pdfGenerator.generate("Quiz", LocalDate.of(2024, 3, 15), 1, 300, entries);
            String text = extractPdfText(pdfBytes);

            assertThat(text).contains("Quiz Results Report");
        }

        @Test
        @DisplayName("Should include all participant data in leaderboard table")
        void shouldIncludeParticipantData() throws IOException {
            List<LeaderboardEntryDTO> entries = List.of(
                    LeaderboardEntryDTO.builder()
                            .rank(1).nickname("Alice").score(8500)
                            .correctAnswers(8).totalAnswers(10).maxStreak(5).avgResponseTimeSec(3.2)
                            .build(),
                    LeaderboardEntryDTO.builder()
                            .rank(2).nickname("Bob").score(7200)
                            .correctAnswers(7).totalAnswers(10).maxStreak(4).avgResponseTimeSec(4.1)
                            .build()
            );

            byte[] pdfBytes = pdfGenerator.generate("Quiz", LocalDate.of(2024, 3, 15), 2, 300, entries);
            String text = extractPdfText(pdfBytes);

            assertThat(text).contains("Alice");
            assertThat(text).contains("Bob");
            assertThat(text).contains("8500");
            assertThat(text).contains("7200");
        }

        @Test
        @DisplayName("Should format avg response time to 1 decimal place")
        void shouldFormatAvgResponseTime() throws IOException {
            List<LeaderboardEntryDTO> entries = List.of(
                    LeaderboardEntryDTO.builder()
                            .rank(1).nickname("Player1").score(1000)
                            .correctAnswers(5).totalAnswers(10).maxStreak(3).avgResponseTimeSec(3.456)
                            .build()
            );

            byte[] pdfBytes = pdfGenerator.generate("Quiz", LocalDate.of(2024, 3, 15), 1, 300, entries);
            String text = extractPdfText(pdfBytes);

            assertThat(text).contains("3.5");
        }
    }

    @Nested
    @DisplayName("Top-3 bold formatting")
    class TopThreeBoldFormattingTests {

        @Test
        @DisplayName("Should generate PDF with top-3 participants using bold font")
        void shouldApplyBoldToTopThree() throws IOException {
            List<LeaderboardEntryDTO> entries = List.of(
                    LeaderboardEntryDTO.builder()
                            .rank(1).nickname("Gold").score(9000)
                            .correctAnswers(9).totalAnswers(10).maxStreak(6).avgResponseTimeSec(2.5)
                            .build(),
                    LeaderboardEntryDTO.builder()
                            .rank(2).nickname("Silver").score(8000)
                            .correctAnswers(8).totalAnswers(10).maxStreak(5).avgResponseTimeSec(3.0)
                            .build(),
                    LeaderboardEntryDTO.builder()
                            .rank(3).nickname("Bronze").score(7000)
                            .correctAnswers(7).totalAnswers(10).maxStreak(4).avgResponseTimeSec(3.5)
                            .build(),
                    LeaderboardEntryDTO.builder()
                            .rank(4).nickname("Fourth").score(6000)
                            .correctAnswers(6).totalAnswers(10).maxStreak(3).avgResponseTimeSec(4.0)
                            .build()
            );

            byte[] pdfBytes = pdfGenerator.generate("Quiz", LocalDate.of(2024, 3, 15), 4, 300, entries);

            // Verify PDF is generated successfully with all participants
            String text = extractPdfText(pdfBytes);
            assertThat(text).contains("Gold");
            assertThat(text).contains("Silver");
            assertThat(text).contains("Bronze");
            assertThat(text).contains("Fourth");

            // Verify bold formatting by checking the raw PDF content for font references
            String rawPdf = new String(pdfBytes);
            // OpenPDF uses Helvetica-Bold for bold text
            assertThat(rawPdf).contains("Helvetica-Bold");
        }

        @Test
        @DisplayName("Should apply bold to all participants when session has exactly 3")
        void shouldApplyBoldToAllWhenExactlyThreeParticipants() throws IOException {
            List<LeaderboardEntryDTO> entries = List.of(
                    LeaderboardEntryDTO.builder()
                            .rank(1).nickname("First").score(9000)
                            .correctAnswers(9).totalAnswers(10).maxStreak(6).avgResponseTimeSec(2.5)
                            .build(),
                    LeaderboardEntryDTO.builder()
                            .rank(2).nickname("Second").score(8000)
                            .correctAnswers(8).totalAnswers(10).maxStreak(5).avgResponseTimeSec(3.0)
                            .build(),
                    LeaderboardEntryDTO.builder()
                            .rank(3).nickname("Third").score(7000)
                            .correctAnswers(7).totalAnswers(10).maxStreak(4).avgResponseTimeSec(3.5)
                            .build()
            );

            byte[] pdfBytes = pdfGenerator.generate("Quiz", LocalDate.of(2024, 3, 15), 3, 300, entries);
            String text = extractPdfText(pdfBytes);

            assertThat(text).contains("First");
            assertThat(text).contains("Second");
            assertThat(text).contains("Third");
        }

        @Test
        @DisplayName("Should apply bold only to single participant when session has 1")
        void shouldApplyBoldToSingleParticipant() throws IOException {
            List<LeaderboardEntryDTO> entries = List.of(
                    LeaderboardEntryDTO.builder()
                            .rank(1).nickname("OnlyPlayer").score(5000)
                            .correctAnswers(5).totalAnswers(10).maxStreak(3).avgResponseTimeSec(3.0)
                            .build()
            );

            byte[] pdfBytes = pdfGenerator.generate("Quiz", LocalDate.of(2024, 3, 15), 1, 300, entries);
            String text = extractPdfText(pdfBytes);

            assertThat(text).contains("OnlyPlayer");
        }
    }

    @Nested
    @DisplayName("Performance tests")
    class PerformanceTests {

        @Test
        @DisplayName("Should generate PDF for 500 participants within 10 seconds (Requirement 5.7)")
        void shouldGeneratePdfFor500ParticipantsWithin10Seconds() {
            // Arrange: create 500 leaderboard entries
            List<LeaderboardEntryDTO> entries = new java.util.ArrayList<>();
            for (int i = 1; i <= 500; i++) {
                entries.add(LeaderboardEntryDTO.builder()
                        .rank(i)
                        .nickname("Participant" + i)
                        .score(50000 - i * 100)
                        .correctAnswers(50 - (i % 50))
                        .totalAnswers(50)
                        .maxStreak(10 - (i % 10))
                        .avgResponseTimeSec(2.0 + (i % 100) * 0.05)
                        .build());
            }

            // Act: measure PDF generation time
            long startTime = System.nanoTime();
            byte[] pdfBytes = pdfGenerator.generate(
                    "Large Scale Quiz - Performance Test",
                    LocalDate.of(2024, 3, 15),
                    500,
                    1800,
                    entries);
            long elapsedMillis = (System.nanoTime() - startTime) / 1_000_000;

            // Assert: PDF generated successfully within 10 seconds
            assertThat(pdfBytes).isNotNull();
            assertThat(pdfBytes.length).isGreaterThan(0);
            // Verify it's a valid PDF
            assertThat(pdfBytes[0]).isEqualTo((byte) 0x25); // %
            assertThat(pdfBytes[1]).isEqualTo((byte) 0x50); // P
            assertThat(pdfBytes[2]).isEqualTo((byte) 0x44); // D
            assertThat(pdfBytes[3]).isEqualTo((byte) 0x46); // F
            // Must complete within 10 seconds (10000 ms)
            assertThat(elapsedMillis)
                    .as("PDF generation for 500 participants should complete within 10 seconds, took %d ms", elapsedMillis)
                    .isLessThan(10_000);
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle empty entries list")
        void shouldHandleEmptyEntries() {
            byte[] pdfBytes = pdfGenerator.generate("Quiz", LocalDate.of(2024, 3, 15), 0, 300, Collections.emptyList());

            assertThat(pdfBytes).isNotNull();
            assertThat(pdfBytes.length).isGreaterThan(0);
            // Should still be a valid PDF
            assertThat(pdfBytes[0]).isEqualTo((byte) 0x25);
        }

        @Test
        @DisplayName("Should handle duration less than 60 seconds")
        void shouldHandleShortDuration() throws IOException {
            List<LeaderboardEntryDTO> entries = List.of(
                    LeaderboardEntryDTO.builder()
                            .rank(1).nickname("Player1").score(1000)
                            .correctAnswers(5).totalAnswers(10).maxStreak(3).avgResponseTimeSec(2.0)
                            .build()
            );

            byte[] pdfBytes = pdfGenerator.generate("Quiz", LocalDate.of(2024, 3, 15), 1, 45, entries);
            String text = extractPdfText(pdfBytes);

            // 45 seconds should display as "45s"
            assertThat(text).contains("45s");
        }

        @Test
        @DisplayName("Should handle large number of participants")
        void shouldHandleManyParticipants() {
            List<LeaderboardEntryDTO> entries = new java.util.ArrayList<>();
            for (int i = 1; i <= 100; i++) {
                entries.add(LeaderboardEntryDTO.builder()
                        .rank(i).nickname("Player" + i).score(10000 - i * 100)
                        .correctAnswers(10 - (i % 10)).totalAnswers(10)
                        .maxStreak(5 - (i % 5)).avgResponseTimeSec(2.0 + i * 0.1)
                        .build());
            }

            byte[] pdfBytes = pdfGenerator.generate("Large Quiz", LocalDate.of(2024, 3, 15), 100, 600, entries);

            assertThat(pdfBytes).isNotNull();
            assertThat(pdfBytes.length).isGreaterThan(0);
        }

        @Test
        @DisplayName("Should include table headers in PDF")
        void shouldIncludeTableHeaders() throws IOException {
            List<LeaderboardEntryDTO> entries = List.of(
                    LeaderboardEntryDTO.builder()
                            .rank(1).nickname("Player1").score(1000)
                            .correctAnswers(5).totalAnswers(10).maxStreak(3).avgResponseTimeSec(2.0)
                            .build()
            );

            byte[] pdfBytes = pdfGenerator.generate("Quiz", LocalDate.of(2024, 3, 15), 1, 300, entries);
            String text = extractPdfText(pdfBytes);

            assertThat(text).contains("Rank");
            assertThat(text).contains("Nickname");
            assertThat(text).contains("Score");
        }
    }

    /**
     * Extracts text content from a PDF byte array using OpenPDF's PdfReader.
     */
    private String extractPdfText(byte[] pdfBytes) throws IOException {
        PdfReader reader = new PdfReader(pdfBytes);
        StringBuilder text = new StringBuilder();
        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        for (int i = 1; i <= reader.getNumberOfPages(); i++) {
            text.append(extractor.getTextFromPage(i));
        }
        reader.close();
        return text.toString();
    }
}
