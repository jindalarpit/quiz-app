package com.quizplatform.analytics.service;

import com.quizplatform.analytics.dto.LeaderboardEntryDTO;
import com.quizplatform.analytics.report.CsvGenerator;
import com.quizplatform.analytics.report.PdfGenerator;
import com.quizplatform.common.exception.ResourceNotFoundException;
import com.quizplatform.common.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Service responsible for generating downloadable result files in CSV and PDF formats.
 * Validates session existence and status before generating exports.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExportService {

    private static final long PDF_TIMEOUT_SECONDS = 10;
    private static final DateTimeFormatter CSV_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter PDF_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final JdbcTemplate jdbcTemplate;
    private final CsvGenerator csvGenerator;
    private final PdfGenerator pdfGenerator;
    private final QuizHistoryService quizHistoryService;

    /**
     * Generates a CSV export for the given session.
     *
     * @param sessionId the session ID
     * @param hostId    the host's user ID (for ownership verification)
     * @return an ExportResult containing the CSV bytes and formatted filename
     * @throws ResourceNotFoundException if the session does not exist or has not ended
     * @throws ValidationException       if the session is unavailable for export
     */
    public ExportResult exportCsv(UUID sessionId, UUID hostId) {
        SessionExportData sessionData = getValidatedSessionData(sessionId, hostId);
        List<LeaderboardEntryDTO> entries = quizHistoryService.getHistoricalLeaderboard(sessionId, hostId);

        try {
            byte[] csvContent = csvGenerator.generate(entries);
            String filename = formatCsvFilename(sessionData.getPin(), sessionData.getEndDate());
            return new ExportResult(csvContent, filename);
        } catch (IOException e) {
            log.error("Failed to generate CSV for session {}: {}", sessionId, e.getMessage(), e);
            throw new RuntimeException("Failed to generate CSV file: " + e.getMessage(), e);
        }
    }

    /**
     * Generates a PDF export for the given session with a 10-second timeout.
     *
     * @param sessionId the session ID
     * @param hostId    the host's user ID (for ownership verification)
     * @return an ExportResult containing the PDF bytes and formatted filename
     * @throws ResourceNotFoundException if the session does not exist or has not ended
     * @throws ValidationException       if the session is unavailable for export
     * @throws PdfTimeoutException       if PDF generation exceeds 10 seconds
     */
    public ExportResult exportPdf(UUID sessionId, UUID hostId) {
        SessionExportData sessionData = getValidatedSessionData(sessionId, hostId);
        List<LeaderboardEntryDTO> entries = quizHistoryService.getHistoricalLeaderboard(sessionId, hostId);

        if (entries.isEmpty()) {
            throw new ResourceNotFoundException("No results available for export for session: " + sessionId);
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Callable<byte[]> pdfTask = () -> pdfGenerator.generate(
                    sessionData.getQuizTitle(),
                    sessionData.getEndDate(),
                    sessionData.getParticipantCount(),
                    sessionData.getDurationSeconds(),
                    entries
            );

            Future<byte[]> future = executor.submit(pdfTask);
            byte[] pdfContent = future.get(PDF_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            String filename = formatPdfFilename(sessionData.getPin(), sessionData.getEndDate());
            return new ExportResult(pdfContent, filename);
        } catch (TimeoutException e) {
            log.error("PDF generation timed out for session {}", sessionId);
            throw new PdfTimeoutException("PDF generation exceeded " + PDF_TIMEOUT_SECONDS + " second timeout");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("PDF generation interrupted for session {}", sessionId);
            throw new RuntimeException("PDF generation was interrupted", e);
        } catch (ExecutionException e) {
            log.error("PDF generation failed for session {}: {}", sessionId, e.getCause().getMessage(), e);
            throw new RuntimeException("Failed to generate PDF file: " + e.getCause().getMessage(), e.getCause());
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Validates that the session exists, belongs to the host, and has ended.
     * Returns session metadata needed for export.
     */
    private SessionExportData getValidatedSessionData(UUID sessionId, UUID hostId) {
        String query = "SELECT s.pin, s.quiz_title, s.ended_at, s.started_at, s.participant_count, s.status " +
                "FROM session.sessions s WHERE s.id = ?";

        List<Map<String, Object>> results = jdbcTemplate.queryForList(query, sessionId);

        if (results.isEmpty()) {
            throw new ResourceNotFoundException("Session", sessionId.toString());
        }

        Map<String, Object> row = results.get(0);
        String status = (String) row.get("status");

        if (!"ENDED".equals(status)) {
            throw new ValidationException("Session has not ended yet and is unavailable for export");
        }

        String pin = (String) row.get("pin");
        String quizTitle = (String) row.get("quiz_title");
        Timestamp endedAtTs = (Timestamp) row.get("ended_at");
        Timestamp startedAtTs = (Timestamp) row.get("started_at");
        int participantCount = ((Number) row.get("participant_count")).intValue();

        Instant endedAt = endedAtTs != null ? endedAtTs.toInstant() : Instant.now();
        Instant startedAt = startedAtTs != null ? startedAtTs.toInstant() : endedAt;
        LocalDate endDate = endedAt.atZone(ZoneOffset.UTC).toLocalDate();
        long durationSeconds = java.time.Duration.between(startedAt, endedAt).getSeconds();

        return new SessionExportData(pin, quizTitle, endDate, participantCount, durationSeconds);
    }

    /**
     * Formats the CSV filename: quiz-results-{pin}-{YYYYMMDD}.csv
     */
    String formatCsvFilename(String pin, LocalDate endDate) {
        return "quiz-results-" + pin + "-" + endDate.format(CSV_DATE_FORMATTER) + ".csv";
    }

    /**
     * Formats the PDF filename: quiz-results-{pin}-{YYYY-MM-DD}.pdf
     */
    String formatPdfFilename(String pin, LocalDate endDate) {
        return "quiz-results-" + pin + "-" + endDate.format(PDF_DATE_FORMATTER) + ".pdf";
    }

    /**
     * Holds the result of an export operation: content bytes and filename.
     */
    public static class ExportResult {
        private final byte[] content;
        private final String filename;

        public ExportResult(byte[] content, String filename) {
            this.content = content;
            this.filename = filename;
        }

        public byte[] getContent() {
            return content;
        }

        public String getFilename() {
            return filename;
        }
    }

    /**
     * Holds session metadata needed for export operations.
     */
    private static class SessionExportData {
        private final String pin;
        private final String quizTitle;
        private final LocalDate endDate;
        private final int participantCount;
        private final long durationSeconds;

        SessionExportData(String pin, String quizTitle, LocalDate endDate,
                          int participantCount, long durationSeconds) {
            this.pin = pin;
            this.quizTitle = quizTitle;
            this.endDate = endDate;
            this.participantCount = participantCount;
            this.durationSeconds = durationSeconds;
        }

        public String getPin() {
            return pin;
        }

        public String getQuizTitle() {
            return quizTitle;
        }

        public LocalDate getEndDate() {
            return endDate;
        }

        public int getParticipantCount() {
            return participantCount;
        }

        public long getDurationSeconds() {
            return durationSeconds;
        }
    }

    /**
     * Exception thrown when PDF generation exceeds the timeout limit.
     * Should be mapped to HTTP 504 Gateway Timeout by the controller.
     */
    public static class PdfTimeoutException extends RuntimeException {
        public PdfTimeoutException(String message) {
            super(message);
        }
    }
}
