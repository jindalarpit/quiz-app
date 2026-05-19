package com.quizplatform.analytics.controller;

import com.quizplatform.analytics.dto.LeaderboardEntryDTO;
import com.quizplatform.analytics.dto.PagedHistoryResponse;
import com.quizplatform.analytics.dto.SessionHistoryEntry;
import com.quizplatform.analytics.service.QuizHistoryService;
import com.quizplatform.common.exception.GlobalExceptionHandler;
import com.quizplatform.common.exception.ResourceNotFoundException;
import com.quizplatform.common.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for HistoryController.
 * Tests paginated history, date range filter, title search, historical leaderboard,
 * and error cases (invalid date range, empty results, session not found).
 *
 * Requirements: 3.5, 3.7, 3.8
 */
@ExtendWith(MockitoExtension.class)
class HistoryControllerTest {

    private MockMvc mockMvc;

    @Mock
    private QuizHistoryService quizHistoryService;

    @InjectMocks
    private HistoryController historyController;

    private static final UUID HOST_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SESSION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(historyController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ==================== Paginated History Tests ====================

    @Test
    void getSessionHistory_shouldReturnPaginatedResults() throws Exception {
        List<SessionHistoryEntry> sessions = List.of(
                SessionHistoryEntry.builder()
                        .sessionId(UUID.randomUUID())
                        .quizTitle("Geography Quiz")
                        .endedAt(Instant.parse("2024-03-15T14:30:00Z"))
                        .participantCount(32)
                        .durationSeconds(420)
                        .build(),
                SessionHistoryEntry.builder()
                        .sessionId(UUID.randomUUID())
                        .quizTitle("Math Quiz")
                        .endedAt(Instant.parse("2024-03-14T10:00:00Z"))
                        .participantCount(25)
                        .durationSeconds(600)
                        .build()
        );

        PagedHistoryResponse response = PagedHistoryResponse.builder()
                .sessions(sessions)
                .currentPage(0)
                .totalPages(5)
                .totalSessions(98)
                .build();

        when(quizHistoryService.getSessionHistory(eq(HOST_ID), eq(0), eq(20), isNull(), isNull(), isNull()))
                .thenReturn(response);

        mockMvc.perform(get("/api/history")
                        .header("X-User-Id", HOST_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions").isArray())
                .andExpect(jsonPath("$.sessions.length()").value(2))
                .andExpect(jsonPath("$.sessions[0].quizTitle").value("Geography Quiz"))
                .andExpect(jsonPath("$.sessions[0].participantCount").value(32))
                .andExpect(jsonPath("$.sessions[0].durationSeconds").value(420))
                .andExpect(jsonPath("$.sessions[1].quizTitle").value("Math Quiz"))
                .andExpect(jsonPath("$.currentPage").value(0))
                .andExpect(jsonPath("$.totalPages").value(5))
                .andExpect(jsonPath("$.totalSessions").value(98));
    }

    @Test
    void getSessionHistory_shouldSupportCustomPageAndSize() throws Exception {
        PagedHistoryResponse response = PagedHistoryResponse.builder()
                .sessions(Collections.emptyList())
                .currentPage(2)
                .totalPages(5)
                .totalSessions(98)
                .build();

        when(quizHistoryService.getSessionHistory(eq(HOST_ID), eq(2), eq(10), isNull(), isNull(), isNull()))
                .thenReturn(response);

        mockMvc.perform(get("/api/history")
                        .header("X-User-Id", HOST_ID.toString())
                        .param("page", "2")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPage").value(2))
                .andExpect(jsonPath("$.totalPages").value(5));
    }

    @Test
    void getSessionHistory_shouldReturn400ForNegativePage() throws Exception {
        mockMvc.perform(get("/api/history")
                        .header("X-User-Id", HOST_ID.toString())
                        .param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getSessionHistory_shouldReturn400ForInvalidPageSize() throws Exception {
        mockMvc.perform(get("/api/history")
                        .header("X-User-Id", HOST_ID.toString())
                        .param("size", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getSessionHistory_shouldReturn400ForPageSizeExceeding100() throws Exception {
        mockMvc.perform(get("/api/history")
                        .header("X-User-Id", HOST_ID.toString())
                        .param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    // ==================== Date Range Filter Tests ====================

    @Test
    void getSessionHistory_shouldFilterByDateRange() throws Exception {
        LocalDate startDate = LocalDate.of(2024, 3, 1);
        LocalDate endDate = LocalDate.of(2024, 3, 31);

        List<SessionHistoryEntry> sessions = List.of(
                SessionHistoryEntry.builder()
                        .sessionId(UUID.randomUUID())
                        .quizTitle("March Quiz")
                        .endedAt(Instant.parse("2024-03-15T14:30:00Z"))
                        .participantCount(20)
                        .durationSeconds(300)
                        .build()
        );

        PagedHistoryResponse response = PagedHistoryResponse.builder()
                .sessions(sessions)
                .currentPage(0)
                .totalPages(1)
                .totalSessions(1)
                .build();

        when(quizHistoryService.getSessionHistory(eq(HOST_ID), eq(0), eq(20), eq(startDate), eq(endDate), isNull()))
                .thenReturn(response);

        mockMvc.perform(get("/api/history")
                        .header("X-User-Id", HOST_ID.toString())
                        .param("startDate", "2024-03-01")
                        .param("endDate", "2024-03-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions.length()").value(1))
                .andExpect(jsonPath("$.sessions[0].quizTitle").value("March Quiz"))
                .andExpect(jsonPath("$.totalSessions").value(1));
    }

    @Test
    void getSessionHistory_shouldReturn400ForInvalidDateRange() throws Exception {
        LocalDate startDate = LocalDate.of(2024, 3, 31);
        LocalDate endDate = LocalDate.of(2024, 3, 1);

        when(quizHistoryService.getSessionHistory(eq(HOST_ID), eq(0), eq(20), eq(startDate), eq(endDate), isNull()))
                .thenThrow(new ValidationException("Invalid date range: start date must be on or before end date"));

        mockMvc.perform(get("/api/history")
                        .header("X-User-Id", HOST_ID.toString())
                        .param("startDate", "2024-03-31")
                        .param("endDate", "2024-03-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getSessionHistory_shouldSupportStartDateOnly() throws Exception {
        LocalDate startDate = LocalDate.of(2024, 1, 1);

        PagedHistoryResponse response = PagedHistoryResponse.builder()
                .sessions(Collections.emptyList())
                .currentPage(0)
                .totalPages(0)
                .totalSessions(0)
                .build();

        when(quizHistoryService.getSessionHistory(eq(HOST_ID), eq(0), eq(20), eq(startDate), isNull(), isNull()))
                .thenReturn(response);

        mockMvc.perform(get("/api/history")
                        .header("X-User-Id", HOST_ID.toString())
                        .param("startDate", "2024-01-01"))
                .andExpect(status().isOk());
    }

    // ==================== Title Search Tests ====================

    @Test
    void getSessionHistory_shouldFilterByTitleSearch() throws Exception {
        List<SessionHistoryEntry> sessions = List.of(
                SessionHistoryEntry.builder()
                        .sessionId(UUID.randomUUID())
                        .quizTitle("Geography Quiz")
                        .endedAt(Instant.parse("2024-03-15T14:30:00Z"))
                        .participantCount(20)
                        .durationSeconds(300)
                        .build()
        );

        PagedHistoryResponse response = PagedHistoryResponse.builder()
                .sessions(sessions)
                .currentPage(0)
                .totalPages(1)
                .totalSessions(1)
                .build();

        when(quizHistoryService.getSessionHistory(eq(HOST_ID), eq(0), eq(20), isNull(), isNull(), eq("geography")))
                .thenReturn(response);

        mockMvc.perform(get("/api/history")
                        .header("X-User-Id", HOST_ID.toString())
                        .param("search", "geography"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions.length()").value(1))
                .andExpect(jsonPath("$.sessions[0].quizTitle").value("Geography Quiz"));
    }

    @Test
    void getSessionHistory_shouldReturn400ForSearchTermExceeding100Chars() throws Exception {
        String longSearch = "a".repeat(101);

        when(quizHistoryService.getSessionHistory(eq(HOST_ID), eq(0), eq(20), isNull(), isNull(), eq(longSearch)))
                .thenThrow(new ValidationException("Search term must be between 1 and 100 characters"));

        mockMvc.perform(get("/api/history")
                        .header("X-User-Id", HOST_ID.toString())
                        .param("search", longSearch))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getSessionHistory_shouldCombineDateRangeAndSearch() throws Exception {
        LocalDate startDate = LocalDate.of(2024, 3, 1);
        LocalDate endDate = LocalDate.of(2024, 3, 31);

        PagedHistoryResponse response = PagedHistoryResponse.builder()
                .sessions(Collections.emptyList())
                .currentPage(0)
                .totalPages(0)
                .totalSessions(0)
                .build();

        when(quizHistoryService.getSessionHistory(eq(HOST_ID), eq(0), eq(20), eq(startDate), eq(endDate), eq("math")))
                .thenReturn(response);

        mockMvc.perform(get("/api/history")
                        .header("X-User-Id", HOST_ID.toString())
                        .param("startDate", "2024-03-01")
                        .param("endDate", "2024-03-31")
                        .param("search", "math"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSessions").value(0));
    }

    // ==================== Empty Results Tests ====================

    @Test
    void getSessionHistory_shouldReturnEmptyListWhenNoSessionsMatch() throws Exception {
        PagedHistoryResponse response = PagedHistoryResponse.builder()
                .sessions(Collections.emptyList())
                .currentPage(0)
                .totalPages(0)
                .totalSessions(0)
                .build();

        when(quizHistoryService.getSessionHistory(eq(HOST_ID), eq(0), eq(20), isNull(), isNull(), isNull()))
                .thenReturn(response);

        mockMvc.perform(get("/api/history")
                        .header("X-User-Id", HOST_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions").isArray())
                .andExpect(jsonPath("$.sessions").isEmpty())
                .andExpect(jsonPath("$.totalSessions").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    // ==================== Historical Leaderboard Tests ====================

    @Test
    void getHistoricalLeaderboard_shouldReturnLeaderboardEntries() throws Exception {
        List<LeaderboardEntryDTO> entries = List.of(
                LeaderboardEntryDTO.builder()
                        .rank(1)
                        .nickname("Player1")
                        .score(8500)
                        .correctAnswers(8)
                        .totalAnswers(10)
                        .maxStreak(5)
                        .avgResponseTimeSec(3.2)
                        .build(),
                LeaderboardEntryDTO.builder()
                        .rank(2)
                        .nickname("Player2")
                        .score(7200)
                        .correctAnswers(7)
                        .totalAnswers(10)
                        .maxStreak(4)
                        .avgResponseTimeSec(4.1)
                        .build(),
                LeaderboardEntryDTO.builder()
                        .rank(3)
                        .nickname("Player3")
                        .score(6000)
                        .correctAnswers(6)
                        .totalAnswers(10)
                        .maxStreak(3)
                        .avgResponseTimeSec(5.0)
                        .build()
        );

        when(quizHistoryService.getHistoricalLeaderboard(eq(SESSION_ID), eq(HOST_ID)))
                .thenReturn(entries);

        mockMvc.perform(get("/api/history/{sessionId}/leaderboard", SESSION_ID)
                        .header("X-User-Id", HOST_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].rank").value(1))
                .andExpect(jsonPath("$[0].nickname").value("Player1"))
                .andExpect(jsonPath("$[0].score").value(8500))
                .andExpect(jsonPath("$[0].correctAnswers").value(8))
                .andExpect(jsonPath("$[0].totalAnswers").value(10))
                .andExpect(jsonPath("$[0].maxStreak").value(5))
                .andExpect(jsonPath("$[0].avgResponseTimeSec").value(3.2))
                .andExpect(jsonPath("$[1].rank").value(2))
                .andExpect(jsonPath("$[1].nickname").value("Player2"))
                .andExpect(jsonPath("$[2].rank").value(3))
                .andExpect(jsonPath("$[2].nickname").value("Player3"));
    }

    @Test
    void getHistoricalLeaderboard_shouldReturn404WhenSessionNotFound() throws Exception {
        UUID unknownSessionId = UUID.randomUUID();

        when(quizHistoryService.getHistoricalLeaderboard(eq(unknownSessionId), eq(HOST_ID)))
                .thenThrow(new ResourceNotFoundException("Session", unknownSessionId.toString()));

        mockMvc.perform(get("/api/history/{sessionId}/leaderboard", unknownSessionId)
                        .header("X-User-Id", HOST_ID.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getHistoricalLeaderboard_shouldReturnEmptyListForSessionWithNoParticipants() throws Exception {
        when(quizHistoryService.getHistoricalLeaderboard(eq(SESSION_ID), eq(HOST_ID)))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/history/{sessionId}/leaderboard", SESSION_ID)
                        .header("X-User-Id", HOST_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ==================== Error Cases ====================

    @Test
    void getSessionHistory_shouldReturn500WhenServiceThrowsUnexpectedException() throws Exception {
        when(quizHistoryService.getSessionHistory(eq(HOST_ID), eq(0), eq(20), isNull(), isNull(), isNull()))
                .thenThrow(new RuntimeException("Database connection failed"));

        mockMvc.perform(get("/api/history")
                        .header("X-User-Id", HOST_ID.toString()))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void getSessionHistory_shouldReturnErrorWhenHostIdHeaderMissing() throws Exception {
        mockMvc.perform(get("/api/history"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void getHistoricalLeaderboard_shouldReturnErrorWhenHostIdHeaderMissing() throws Exception {
        mockMvc.perform(get("/api/history/{sessionId}/leaderboard", SESSION_ID))
                .andExpect(status().isInternalServerError());
    }
}
