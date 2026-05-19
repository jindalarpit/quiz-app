package com.quizplatform.analytics.controller;

import com.quizplatform.analytics.service.ExportService;
import com.quizplatform.analytics.service.ExportService.ExportResult;
import com.quizplatform.analytics.service.ExportService.PdfTimeoutException;
import com.quizplatform.common.exception.ResourceNotFoundException;
import com.quizplatform.common.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for exporting quiz session results as CSV or PDF files.
 * Delegates to ExportService for generation logic and file formatting.
 *
 * Error handling:
 * - ResourceNotFoundException → 404 (session not found)
 * - ValidationException → 400 (session not ended)
 * - PdfTimeoutException → 504 (PDF generation timeout)
 * - RuntimeException → 500 (generation failure)
 *
 * Requirements: 4.4, 4.5, 4.6, 5.4, 5.5, 5.6
 */
@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
@Slf4j
public class ExportController {

    private static final MediaType TEXT_CSV = MediaType.parseMediaType("text/csv");

    private final ExportService exportService;

    /**
     * GET /api/export/{sessionId}/csv
     * Generates and returns a downloadable CSV file with quiz results.
     *
     * @param sessionId the session ID
     * @param hostId    the host's user ID from X-User-Id header
     * @return CSV file as a downloadable attachment
     */
    @GetMapping("/{sessionId}/csv")
    public ResponseEntity<byte[]> exportCsv(
            @PathVariable UUID sessionId,
            @RequestHeader("X-User-Id") UUID hostId) {

        log.debug("CSV export requested for session={}, host={}", sessionId, hostId);

        ExportResult result = exportService.exportCsv(sessionId, hostId);

        return ResponseEntity.ok()
                .contentType(TEXT_CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + result.getFilename() + "\"")
                .body(result.getContent());
    }

    /**
     * GET /api/export/{sessionId}/pdf
     * Generates and returns a downloadable PDF file with quiz results.
     *
     * @param sessionId the session ID
     * @param hostId    the host's user ID from X-User-Id header
     * @return PDF file as a downloadable attachment
     */
    @GetMapping("/{sessionId}/pdf")
    public ResponseEntity<byte[]> exportPdf(
            @PathVariable UUID sessionId,
            @RequestHeader("X-User-Id") UUID hostId) {

        log.debug("PDF export requested for session={}, host={}", sessionId, hostId);

        ExportResult result = exportService.exportPdf(sessionId, hostId);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + result.getFilename() + "\"")
                .body(result.getContent());
    }

    /**
     * Handles PdfTimeoutException by returning HTTP 504 Gateway Timeout.
     */
    @ExceptionHandler(PdfTimeoutException.class)
    public ResponseEntity<ErrorBody> handlePdfTimeout(PdfTimeoutException ex) {
        log.error("PDF generation timed out: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(new ErrorBody(HttpStatus.GATEWAY_TIMEOUT.value(), "PDF_TIMEOUT", ex.getMessage()));
    }

    /**
     * Simple error body for controller-level exception handling.
     */
    record ErrorBody(int status, String errorCode, String message) {}
}
