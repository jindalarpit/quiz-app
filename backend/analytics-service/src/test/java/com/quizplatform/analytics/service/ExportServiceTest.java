package com.quizplatform.analytics.service;

import com.quizplatform.analytics.dto.LeaderboardEntryDTO;
import com.quizplatform.analytics.report.CsvGenerator;
import com.quizplatform.analytics.report.PdfGenerator;
import com.quizplatform.common.exception.ResourceNotFoundException;
import com.quizplatform.common.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private CsvGenerator csvGenerator;

    @Mock
    private PdfGenerator pdfGenerator;

    @Mock
    private QuizHistoryService quizHistoryService;

    private ExportService exportService;

    private UUID sessionId;
    private UUID hostId;

    @BeforeEach
    void setUp() {
        exportService = new ExportService(jdbcTemplate, csvGenerator, pdfGenerator, quizHistoryService);
        sessionId = UUID.randomUUID();
        hostId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("Filename formatting")
    class FilenameFormatting {

        @Test
        @DisplayName("CSV filename follows pattern quiz-results-{pin}-{YYYYMMDD}.csv")
        void csvFilenameFormat() {
            LocalDate date = LocalDate.of(2024, 3, 15);
            String filename = exportService.formatCsvFilename("ABC123", date);
            assertThat(filename).isEqualTo("quiz-results-ABC123-20240315.csv");
        }

        @Test
        @DisplayName("PDF filename follows pattern quiz-results-{pin}-{YYYY-MM-DD}.pdf")
        void pdfFilenameFormat() {
            LocalDate date = LocalDate.of(2024, 3, 15);
            String filename = exportService.formatPdfFilename("ABC123", date);
            assertThat(filename).isEqualTo("quiz-results-ABC123-2024-03-15.pdf");
        }

        @Test
        @DisplayName("CSV filename with different date")
        void csvFilenameWithDifferentDate() {
            LocalDate date = LocalDate.of(2023, 12, 1);
            String filename = exportService.formatCsvFilename("XYZ789", date);
            assertThat(filename).isEqualTo("quiz-results-XYZ789-20231201.csv");
        }

        @Test
        @DisplayName("PDF filename with different date")
        void pdfFilenameWithDifferentDate() {
            LocalDate date = LocalDate.of(2023, 12, 1);
            String filename = exportService.formatPdfFilename("XYZ789", date);
            assertThat(filename).isEqualTo("quiz-results-XYZ789-2023-12-01.pdf");
        }
    }

    @Nested
    @DisplayName("CSV export")
    class CsvExport {

        @Test
        @DisplayName("Successful CSV export returns content and formatted filename")
        void successfulCsvExport() throws IOException {
            Instant endedAt = LocalDate.of(2024, 3, 15).atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant startedAt = endedAt.minusSeconds(420);
            Map<String, Object> sessionRow = createSessionRow("ABC123", "Geography Quiz", endedAt, startedAt, 32, "ENDED");
            when(jdbcTemplate.queryForList(anyString(), eq(sessionId))).thenReturn(List.of(sessionRow));

            List<LeaderboardEntryDTO> entries = List.of(
                    LeaderboardEntryDTO.builder().rank(1).nickname("Player1").score(8500)
                            .correctAnswers(8).totalAnswers(10).maxStreak(5).avgResponseTimeSec(3.2).build()
            );
            when(quizHistoryService.getHistoricalLeaderboard(sessionId, hostId)).thenReturn(entries);

            byte[] csvBytes = "csv content".getBytes();
            when(csvGenerator.generate(entries)).thenReturn(csvBytes);

            ExportService.ExportResult result = exportService.exportCsv(sessionId, hostId);

            assertThat(result.getContent()).isEqualTo(csvBytes);
            assertThat(result.getFilename()).isEqualTo("quiz-results-ABC123-20240315.csv");
        }

        @Test
        @DisplayName("CSV export throws ResourceNotFoundException for non-existent session")
        void csvExportSessionNotFound() {
            when(jdbcTemplate.queryForList(anyString(), eq(sessionId))).thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> exportService.exportCsv(sessionId, hostId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("CSV export throws ValidationException for session that has not ended")
        void csvExportSessionNotEnded() {
            Instant now = Instant.now();
            Map<String, Object> sessionRow = createSessionRow("ABC123", "Quiz", now, now.minusSeconds(60), 5, "ACTIVE");
            when(jdbcTemplate.queryForList(anyString(), eq(sessionId))).thenReturn(List.of(sessionRow));

            assertThatThrownBy(() -> exportService.exportCsv(sessionId, hostId))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("not ended");
        }

        @Test
        @DisplayName("CSV export handles IOException from generator")
        void csvExportGenerationFailure() throws IOException {
            Instant endedAt = Instant.now();
            Map<String, Object> sessionRow = createSessionRow("ABC123", "Quiz", endedAt, endedAt.minusSeconds(60), 5, "ENDED");
            when(jdbcTemplate.queryForList(anyString(), eq(sessionId))).thenReturn(List.of(sessionRow));
            when(quizHistoryService.getHistoricalLeaderboard(sessionId, hostId)).thenReturn(List.of());
            when(csvGenerator.generate(any())).thenThrow(new IOException("Write error"));

            assertThatThrownBy(() -> exportService.exportCsv(sessionId, hostId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to generate CSV");
        }
    }

    @Nested
    @DisplayName("PDF export")
    class PdfExport {

        @Test
        @DisplayName("Successful PDF export returns content and formatted filename")
        void successfulPdfExport() {
            Instant endedAt = LocalDate.of(2024, 3, 15).atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant startedAt = endedAt.minusSeconds(420);
            Map<String, Object> sessionRow = createSessionRow("ABC123", "Geography Quiz", endedAt, startedAt, 32, "ENDED");
            when(jdbcTemplate.queryForList(anyString(), eq(sessionId))).thenReturn(List.of(sessionRow));

            List<LeaderboardEntryDTO> entries = List.of(
                    LeaderboardEntryDTO.builder().rank(1).nickname("Player1").score(8500)
                            .correctAnswers(8).totalAnswers(10).maxStreak(5).avgResponseTimeSec(3.2).build()
            );
            when(quizHistoryService.getHistoricalLeaderboard(sessionId, hostId)).thenReturn(entries);

            byte[] pdfBytes = "pdf content".getBytes();
            when(pdfGenerator.generate(
                    eq("Geography Quiz"),
                    eq(LocalDate.of(2024, 3, 15)),
                    eq(32),
                    eq(420L),
                    eq(entries)
            )).thenReturn(pdfBytes);

            ExportService.ExportResult result = exportService.exportPdf(sessionId, hostId);

            assertThat(result.getContent()).isEqualTo(pdfBytes);
            assertThat(result.getFilename()).isEqualTo("quiz-results-ABC123-2024-03-15.pdf");
        }

        @Test
        @DisplayName("PDF export throws ResourceNotFoundException for non-existent session")
        void pdfExportSessionNotFound() {
            when(jdbcTemplate.queryForList(anyString(), eq(sessionId))).thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> exportService.exportPdf(sessionId, hostId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("PDF export throws ValidationException for session that has not ended")
        void pdfExportSessionNotEnded() {
            Instant now = Instant.now();
            Map<String, Object> sessionRow = createSessionRow("ABC123", "Quiz", now, now.minusSeconds(60), 5, "ACTIVE");
            when(jdbcTemplate.queryForList(anyString(), eq(sessionId))).thenReturn(List.of(sessionRow));

            assertThatThrownBy(() -> exportService.exportPdf(sessionId, hostId))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("not ended");
        }

        @Test
        @DisplayName("PDF export throws ResourceNotFoundException for session with no participants")
        void pdfExportNoParticipants() {
            Instant endedAt = Instant.now();
            Map<String, Object> sessionRow = createSessionRow("ABC123", "Quiz", endedAt, endedAt.minusSeconds(60), 0, "ENDED");
            when(jdbcTemplate.queryForList(anyString(), eq(sessionId))).thenReturn(List.of(sessionRow));
            when(quizHistoryService.getHistoricalLeaderboard(sessionId, hostId)).thenReturn(List.of());

            assertThatThrownBy(() -> exportService.exportPdf(sessionId, hostId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("No results available");
        }

        @Test
        @DisplayName("PDF export throws PdfTimeoutException when generation exceeds timeout")
        void pdfExportTimeout() {
            Instant endedAt = Instant.now();
            Map<String, Object> sessionRow = createSessionRow("ABC123", "Quiz", endedAt, endedAt.minusSeconds(60), 5, "ENDED");
            when(jdbcTemplate.queryForList(anyString(), eq(sessionId))).thenReturn(List.of(sessionRow));

            List<LeaderboardEntryDTO> entries = List.of(
                    LeaderboardEntryDTO.builder().rank(1).nickname("Player1").score(100)
                            .correctAnswers(5).totalAnswers(10).maxStreak(3).avgResponseTimeSec(2.0).build()
            );
            when(quizHistoryService.getHistoricalLeaderboard(sessionId, hostId)).thenReturn(entries);

            // Simulate a long-running PDF generation that exceeds timeout
            when(pdfGenerator.generate(any(), any(), any(int.class), any(long.class), any()))
                    .thenAnswer(invocation -> {
                        Thread.sleep(15_000); // Sleep longer than timeout
                        return new byte[0];
                    });

            assertThatThrownBy(() -> exportService.exportPdf(sessionId, hostId))
                    .isInstanceOf(ExportService.PdfTimeoutException.class)
                    .hasMessageContaining("timeout");
        }
    }

    private Map<String, Object> createSessionRow(String pin, String quizTitle, Instant endedAt,
                                                   Instant startedAt, int participantCount, String status) {
        Map<String, Object> row = new HashMap<>();
        row.put("pin", pin);
        row.put("quiz_title", quizTitle);
        row.put("ended_at", Timestamp.from(endedAt));
        row.put("started_at", Timestamp.from(startedAt));
        row.put("participant_count", participantCount);
        row.put("status", status);
        return row;
    }
}
