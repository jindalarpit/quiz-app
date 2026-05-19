package com.quizplatform.analytics.controller;

import com.quizplatform.analytics.service.ExportService;
import com.quizplatform.analytics.service.ExportService.ExportResult;
import com.quizplatform.analytics.service.ExportService.PdfTimeoutException;
import com.quizplatform.common.exception.GlobalExceptionHandler;
import com.quizplatform.common.exception.ResourceNotFoundException;
import com.quizplatform.common.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for ExportController.
 * Tests CSV and PDF export endpoints including success cases, error handling,
 * filename format in Content-Disposition, and timeout handling.
 *
 * Requirements: 4.5, 4.6, 4.7, 5.5, 5.6, 5.7
 */
@ExtendWith(MockitoExtension.class)
class ExportControllerTest {

    private MockMvc mockMvc;

    private ExportService exportService;

    private static final UUID SESSION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID HOST_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @BeforeEach
    void setUp() {
        exportService = mock(ExportService.class);
        ExportController exportController = new ExportController(exportService);
        mockMvc = MockMvcBuilders.standaloneSetup(exportController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ==================== CSV Export Tests ====================

    @Nested
    @DisplayName("CSV Export - GET /api/export/{sessionId}/csv")
    class CsvExportTests {

        @Test
        @DisplayName("Should return CSV file with correct content type and filename")
        void exportCsv_success() throws Exception {
            byte[] csvContent = "rank,nickname,score\n1,Alice,8500\n".getBytes();
            ExportResult result = new ExportResult(csvContent, "quiz-results-ABC123-20240315.csv");

            when(exportService.exportCsv(eq(SESSION_ID), eq(HOST_ID))).thenReturn(result);

            mockMvc.perform(get("/api/export/{sessionId}/csv", SESSION_ID)
                            .header("X-User-Id", HOST_ID.toString()))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType("text/csv"))
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"quiz-results-ABC123-20240315.csv\""))
                    .andExpect(content().bytes(csvContent));
        }

        @Test
        @DisplayName("Should return CSV with correct filename format containing session pin and date")
        void exportCsv_filenameFormat() throws Exception {
            byte[] csvContent = "header\n".getBytes();
            ExportResult result = new ExportResult(csvContent, "quiz-results-XYZ789-20231201.csv");

            when(exportService.exportCsv(eq(SESSION_ID), eq(HOST_ID))).thenReturn(result);

            mockMvc.perform(get("/api/export/{sessionId}/csv", SESSION_ID)
                            .header("X-User-Id", HOST_ID.toString()))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"quiz-results-XYZ789-20231201.csv\""));
        }

        @Test
        @DisplayName("Should return 404 when session does not exist")
        void exportCsv_sessionNotFound() throws Exception {
            when(exportService.exportCsv(eq(SESSION_ID), eq(HOST_ID)))
                    .thenThrow(new ResourceNotFoundException("Session", SESSION_ID.toString()));

            mockMvc.perform(get("/api/export/{sessionId}/csv", SESSION_ID)
                            .header("X-User-Id", HOST_ID.toString()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 400 when session has not ended")
        void exportCsv_sessionNotEnded() throws Exception {
            when(exportService.exportCsv(eq(SESSION_ID), eq(HOST_ID)))
                    .thenThrow(new ValidationException("Session has not ended yet and is unavailable for export"));

            mockMvc.perform(get("/api/export/{sessionId}/csv", SESSION_ID)
                            .header("X-User-Id", HOST_ID.toString()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 500 when CSV generation fails")
        void exportCsv_generationFailure() throws Exception {
            when(exportService.exportCsv(eq(SESSION_ID), eq(HOST_ID)))
                    .thenThrow(new RuntimeException("Failed to generate CSV file: Write error"));

            mockMvc.perform(get("/api/export/{sessionId}/csv", SESSION_ID)
                            .header("X-User-Id", HOST_ID.toString()))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @DisplayName("Should return header-only CSV for zero-participant session")
        void exportCsv_emptySession() throws Exception {
            String headerOnly = "rank,nickname,score,correct_answers,total_answers,max_streak,avg_response_time_sec\n";
            byte[] csvContent = headerOnly.getBytes();
            ExportResult result = new ExportResult(csvContent, "quiz-results-ABC123-20240315.csv");

            when(exportService.exportCsv(eq(SESSION_ID), eq(HOST_ID))).thenReturn(result);

            mockMvc.perform(get("/api/export/{sessionId}/csv", SESSION_ID)
                            .header("X-User-Id", HOST_ID.toString()))
                    .andExpect(status().isOk())
                    .andExpect(content().bytes(csvContent));
        }

        @Test
        @DisplayName("Should return error when X-User-Id header is missing")
        void exportCsv_missingHostIdHeader() throws Exception {
            mockMvc.perform(get("/api/export/{sessionId}/csv", SESSION_ID))
                    .andExpect(status().isInternalServerError());
        }
    }

    // ==================== PDF Export Tests ====================

    @Nested
    @DisplayName("PDF Export - GET /api/export/{sessionId}/pdf")
    class PdfExportTests {

        @Test
        @DisplayName("Should return PDF file with correct content type and filename")
        void exportPdf_success() throws Exception {
            byte[] pdfContent = new byte[]{0x25, 0x50, 0x44, 0x46}; // %PDF magic bytes
            ExportResult result = new ExportResult(pdfContent, "quiz-results-ABC123-2024-03-15.pdf");

            when(exportService.exportPdf(eq(SESSION_ID), eq(HOST_ID))).thenReturn(result);

            mockMvc.perform(get("/api/export/{sessionId}/pdf", SESSION_ID)
                            .header("X-User-Id", HOST_ID.toString()))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"quiz-results-ABC123-2024-03-15.pdf\""))
                    .andExpect(content().bytes(pdfContent));
        }

        @Test
        @DisplayName("Should return PDF with correct filename format containing session pin and date")
        void exportPdf_filenameFormat() throws Exception {
            byte[] pdfContent = new byte[]{0x25, 0x50, 0x44, 0x46};
            ExportResult result = new ExportResult(pdfContent, "quiz-results-XYZ789-2023-12-01.pdf");

            when(exportService.exportPdf(eq(SESSION_ID), eq(HOST_ID))).thenReturn(result);

            mockMvc.perform(get("/api/export/{sessionId}/pdf", SESSION_ID)
                            .header("X-User-Id", HOST_ID.toString()))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"quiz-results-XYZ789-2023-12-01.pdf\""));
        }

        @Test
        @DisplayName("Should return 404 when session does not exist")
        void exportPdf_sessionNotFound() throws Exception {
            when(exportService.exportPdf(eq(SESSION_ID), eq(HOST_ID)))
                    .thenThrow(new ResourceNotFoundException("Session", SESSION_ID.toString()));

            mockMvc.perform(get("/api/export/{sessionId}/pdf", SESSION_ID)
                            .header("X-User-Id", HOST_ID.toString()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 400 when session has not ended")
        void exportPdf_sessionNotEnded() throws Exception {
            when(exportService.exportPdf(eq(SESSION_ID), eq(HOST_ID)))
                    .thenThrow(new ValidationException("Session has not ended yet and is unavailable for export"));

            mockMvc.perform(get("/api/export/{sessionId}/pdf", SESSION_ID)
                            .header("X-User-Id", HOST_ID.toString()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 404 when session has no participant results")
        void exportPdf_noResults() throws Exception {
            when(exportService.exportPdf(eq(SESSION_ID), eq(HOST_ID)))
                    .thenThrow(new ResourceNotFoundException("No results available for export for session: " + SESSION_ID));

            mockMvc.perform(get("/api/export/{sessionId}/pdf", SESSION_ID)
                            .header("X-User-Id", HOST_ID.toString()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 504 when PDF generation exceeds timeout")
        void exportPdf_timeout() throws Exception {
            when(exportService.exportPdf(eq(SESSION_ID), eq(HOST_ID)))
                    .thenThrow(new PdfTimeoutException("PDF generation exceeded 10 second timeout"));

            mockMvc.perform(get("/api/export/{sessionId}/pdf", SESSION_ID)
                            .header("X-User-Id", HOST_ID.toString()))
                    .andExpect(status().isGatewayTimeout())
                    .andExpect(jsonPath("$.status").value(504))
                    .andExpect(jsonPath("$.errorCode").value("PDF_TIMEOUT"))
                    .andExpect(jsonPath("$.message").value("PDF generation exceeded 10 second timeout"));
        }

        @Test
        @DisplayName("Should return 500 when PDF generation fails unexpectedly")
        void exportPdf_generationFailure() throws Exception {
            when(exportService.exportPdf(eq(SESSION_ID), eq(HOST_ID)))
                    .thenThrow(new RuntimeException("Failed to generate PDF file: rendering error"));

            mockMvc.perform(get("/api/export/{sessionId}/pdf", SESSION_ID)
                            .header("X-User-Id", HOST_ID.toString()))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @DisplayName("Should return error when X-User-Id header is missing")
        void exportPdf_missingHostIdHeader() throws Exception {
            mockMvc.perform(get("/api/export/{sessionId}/pdf", SESSION_ID))
                    .andExpect(status().isInternalServerError());
        }
    }
}
