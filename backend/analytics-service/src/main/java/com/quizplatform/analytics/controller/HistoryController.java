package com.quizplatform.analytics.controller;

import com.quizplatform.analytics.dto.LeaderboardEntryDTO;
import com.quizplatform.analytics.dto.PagedHistoryResponse;
import com.quizplatform.analytics.service.QuizHistoryService;
import com.quizplatform.common.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for quiz session history and historical leaderboard retrieval.
 * Delegates to QuizHistoryService for business logic.
 * Validation errors (invalid date range, search term length) result in 400 responses.
 * Resource not found errors result in 404 responses.
 * Both are handled by the GlobalExceptionHandler via thrown exceptions.
 */
@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
@Slf4j
public class HistoryController {

    private final QuizHistoryService quizHistoryService;

    /**
     * GET /api/history?page=0&size=20&startDate=...&endDate=...&search=...
     * Returns paginated session history for the authenticated host.
     *
     * @param hostId    the host's user ID from X-User-Id header
     * @param page      zero-based page number (default 0)
     * @param size      page size (default 20)
     * @param startDate optional start date filter (inclusive, ISO format)
     * @param endDate   optional end date filter (inclusive, ISO format)
     * @param search    optional case-insensitive quiz title search term (1-100 chars)
     * @return paginated history response
     */
    @GetMapping
    public ResponseEntity<PagedHistoryResponse> getSessionHistory(
            @RequestHeader("X-User-Id") UUID hostId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String search) {

        log.debug("Getting session history for host={}, page={}, size={}, startDate={}, endDate={}, search={}",
                hostId, page, size, startDate, endDate, search);

        validatePaginationParams(page, size);

        PagedHistoryResponse response = quizHistoryService.getSessionHistory(
                hostId, page, size, startDate, endDate, search);

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/history/{sessionId}/leaderboard
     * Returns the historical leaderboard for a specific past session.
     *
     * @param sessionId the session ID
     * @param hostId    the host's user ID from X-User-Id header (for ownership verification)
     * @return list of leaderboard entries ordered by rank ascending
     */
    @GetMapping("/{sessionId}/leaderboard")
    public ResponseEntity<List<LeaderboardEntryDTO>> getHistoricalLeaderboard(
            @PathVariable UUID sessionId,
            @RequestHeader("X-User-Id") UUID hostId) {

        log.debug("Getting historical leaderboard for session={}, host={}", sessionId, hostId);

        List<LeaderboardEntryDTO> entries = quizHistoryService.getHistoricalLeaderboard(sessionId, hostId);

        return ResponseEntity.ok(entries);
    }

    private void validatePaginationParams(int page, int size) {
        if (page < 0) {
            throw new ValidationException("Page number must be non-negative");
        }
        if (size < 1 || size > 100) {
            throw new ValidationException("Page size must be between 1 and 100");
        }
    }
}
