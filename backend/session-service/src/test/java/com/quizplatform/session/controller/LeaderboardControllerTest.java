package com.quizplatform.session.controller;

import com.quizplatform.common.exception.GlobalExceptionHandler;
import com.quizplatform.common.exception.ResourceNotFoundException;
import com.quizplatform.common.exception.ValidationException;
import com.quizplatform.session.dto.*;
import com.quizplatform.session.service.LeaderboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for LeaderboardController.
 * Tests HTTP layer behavior: status codes, response structure, header handling, and error mapping.
 * Validates: Requirements 1.6, 2.5
 */
@ExtendWith(MockitoExtension.class)
class LeaderboardControllerTest {

    @Mock
    private LeaderboardService leaderboardService;

    @InjectMocks
    private LeaderboardController leaderboardController;

    private MockMvc mockMvc;

    private UUID sessionId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(leaderboardController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        sessionId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("GET /api/sessions/{sessionId}/leaderboard")
    class GetLeaderboard {

        @Test
        @DisplayName("returns 200 with paginated leaderboard on success")
        void returnsLeaderboardSuccessfully() throws Exception {
            PagedLeaderboardResponse response = PagedLeaderboardResponse.builder()
                    .sessionId(sessionId)
                    .entries(List.of(
                            FinalLeaderboardEntry.builder()
                                    .rank(1)
                                    .nickname("Player1")
                                    .score(8500)
                                    .correctAnswers(8)
                                    .totalAnswers(10)
                                    .maxStreak(5)
                                    .avgResponseTimeSec(3.2)
                                    .build(),
                            FinalLeaderboardEntry.builder()
                                    .rank(2)
                                    .nickname("Player2")
                                    .score(7200)
                                    .correctAnswers(7)
                                    .totalAnswers(10)
                                    .maxStreak(4)
                                    .avgResponseTimeSec(4.1)
                                    .build()))
                    .currentPage(0)
                    .totalPages(3)
                    .totalParticipants(55)
                    .pageSize(20)
                    .build();

            when(leaderboardService.getPagedLeaderboard(sessionId, 0, 20)).thenReturn(response);

            mockMvc.perform(get("/api/sessions/{sessionId}/leaderboard", sessionId)
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.sessionId").value(sessionId.toString()))
                    .andExpect(jsonPath("$.currentPage").value(0))
                    .andExpect(jsonPath("$.totalPages").value(3))
                    .andExpect(jsonPath("$.totalParticipants").value(55))
                    .andExpect(jsonPath("$.pageSize").value(20))
                    .andExpect(jsonPath("$.entries", hasSize(2)))
                    .andExpect(jsonPath("$.entries[0].rank").value(1))
                    .andExpect(jsonPath("$.entries[0].nickname").value("Player1"))
                    .andExpect(jsonPath("$.entries[0].score").value(8500))
                    .andExpect(jsonPath("$.entries[0].correctAnswers").value(8))
                    .andExpect(jsonPath("$.entries[0].maxStreak").value(5))
                    .andExpect(jsonPath("$.entries[0].avgResponseTimeSec").value(3.2))
                    .andExpect(jsonPath("$.entries[1].rank").value(2))
                    .andExpect(jsonPath("$.entries[1].nickname").value("Player2"));

            verify(leaderboardService).getPagedLeaderboard(sessionId, 0, 20);
        }

        @Test
        @DisplayName("uses default page=0 and size=20 when not specified")
        void usesDefaultPaginationParams() throws Exception {
            PagedLeaderboardResponse response = PagedLeaderboardResponse.builder()
                    .sessionId(sessionId)
                    .entries(List.of())
                    .currentPage(0)
                    .totalPages(0)
                    .totalParticipants(0)
                    .pageSize(20)
                    .build();

            when(leaderboardService.getPagedLeaderboard(sessionId, 0, 20)).thenReturn(response);

            mockMvc.perform(get("/api/sessions/{sessionId}/leaderboard", sessionId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.currentPage").value(0))
                    .andExpect(jsonPath("$.pageSize").value(20));

            verify(leaderboardService).getPagedLeaderboard(sessionId, 0, 20);
        }

        @Test
        @DisplayName("returns 404 when session does not exist")
        void returns404ForMissingSession() throws Exception {
            when(leaderboardService.getPagedLeaderboard(sessionId, 0, 20))
                    .thenThrow(new ResourceNotFoundException("Session", sessionId.toString()));

            mockMvc.perform(get("/api/sessions/{sessionId}/leaderboard", sessionId)
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
                    .andExpect(jsonPath("$.message", containsString("Session")));
        }

        @Test
        @DisplayName("returns 400 for negative page number")
        void returns400ForNegativePage() throws Exception {
            when(leaderboardService.getPagedLeaderboard(sessionId, -1, 20))
                    .thenThrow(new ValidationException("Page number must be non-negative"));

            mockMvc.perform(get("/api/sessions/{sessionId}/leaderboard", sessionId)
                            .param("page", "-1")
                            .param("size", "20"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.message", containsString("Page number")));
        }

        @Test
        @DisplayName("returns 400 for zero page size")
        void returns400ForZeroPageSize() throws Exception {
            when(leaderboardService.getPagedLeaderboard(sessionId, 0, 0))
                    .thenThrow(new ValidationException("Page size must be positive"));

            mockMvc.perform(get("/api/sessions/{sessionId}/leaderboard", sessionId)
                            .param("page", "0")
                            .param("size", "0"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.message", containsString("Page size")));
        }

        @Test
        @DisplayName("returns 500 when service throws unexpected exception (simulates retry exhaustion)")
        void returns500OnUnexpectedError() throws Exception {
            when(leaderboardService.getPagedLeaderboard(sessionId, 0, 20))
                    .thenThrow(new RuntimeException("Database connection failed after retries"));

            mockMvc.perform(get("/api/sessions/{sessionId}/leaderboard", sessionId)
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"));
        }

        @Test
        @DisplayName("passes custom page and size parameters to service")
        void passesCustomPaginationParams() throws Exception {
            PagedLeaderboardResponse response = PagedLeaderboardResponse.builder()
                    .sessionId(sessionId)
                    .entries(List.of())
                    .currentPage(2)
                    .totalPages(5)
                    .totalParticipants(100)
                    .pageSize(10)
                    .build();

            when(leaderboardService.getPagedLeaderboard(sessionId, 2, 10)).thenReturn(response);

            mockMvc.perform(get("/api/sessions/{sessionId}/leaderboard", sessionId)
                            .param("page", "2")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.currentPage").value(2))
                    .andExpect(jsonPath("$.totalPages").value(5))
                    .andExpect(jsonPath("$.pageSize").value(10));

            verify(leaderboardService).getPagedLeaderboard(sessionId, 2, 10);
        }
    }

    @Nested
    @DisplayName("GET /api/sessions/{sessionId}/leaderboard/self")
    class GetSelfResult {

        @Test
        @DisplayName("returns 200 with participant self-result on success")
        void returnsSelfResultSuccessfully() throws Exception {
            UUID participantId = UUID.randomUUID();

            ParticipantSelfResult result = ParticipantSelfResult.builder()
                    .rank(5)
                    .score(6200)
                    .correctAnswers(7)
                    .totalQuestions(10)
                    .maxStreak(4)
                    .avgResponseTimeSec(3.5)
                    .scoreDifference(320)
                    .aboveAverage(true)
                    .questionBreakdown(List.of(
                            QuestionResult.builder().questionNumber(1).status(AnswerStatus.CORRECT).build(),
                            QuestionResult.builder().questionNumber(2).status(AnswerStatus.INCORRECT).build(),
                            QuestionResult.builder().questionNumber(3).status(AnswerStatus.UNANSWERED).build()))
                    .build();

            when(leaderboardService.getParticipantResult(sessionId, participantId)).thenReturn(result);

            mockMvc.perform(get("/api/sessions/{sessionId}/leaderboard/self", sessionId)
                            .header("X-Participant-Id", participantId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.rank").value(5))
                    .andExpect(jsonPath("$.score").value(6200))
                    .andExpect(jsonPath("$.correctAnswers").value(7))
                    .andExpect(jsonPath("$.totalQuestions").value(10))
                    .andExpect(jsonPath("$.maxStreak").value(4))
                    .andExpect(jsonPath("$.avgResponseTimeSec").value(3.5))
                    .andExpect(jsonPath("$.scoreDifference").value(320))
                    .andExpect(jsonPath("$.aboveAverage").value(true))
                    .andExpect(jsonPath("$.questionBreakdown", hasSize(3)))
                    .andExpect(jsonPath("$.questionBreakdown[0].questionNumber").value(1))
                    .andExpect(jsonPath("$.questionBreakdown[0].status").value("CORRECT"))
                    .andExpect(jsonPath("$.questionBreakdown[1].status").value("INCORRECT"))
                    .andExpect(jsonPath("$.questionBreakdown[2].status").value("UNANSWERED"));

            verify(leaderboardService).getParticipantResult(sessionId, participantId);
        }

        @Test
        @DisplayName("returns 404 when session does not exist")
        void returns404ForMissingSession() throws Exception {
            UUID participantId = UUID.randomUUID();

            when(leaderboardService.getParticipantResult(sessionId, participantId))
                    .thenThrow(new ResourceNotFoundException("Session", sessionId.toString()));

            mockMvc.perform(get("/api/sessions/{sessionId}/leaderboard/self", sessionId)
                            .header("X-Participant-Id", participantId.toString()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
                    .andExpect(jsonPath("$.message", containsString("Session")));
        }

        @Test
        @DisplayName("returns 404 when participant does not exist")
        void returns404ForMissingParticipant() throws Exception {
            UUID participantId = UUID.randomUUID();

            when(leaderboardService.getParticipantResult(sessionId, participantId))
                    .thenThrow(new ResourceNotFoundException("Participant", participantId.toString()));

            mockMvc.perform(get("/api/sessions/{sessionId}/leaderboard/self", sessionId)
                            .header("X-Participant-Id", participantId.toString()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
                    .andExpect(jsonPath("$.message", containsString("Participant")));
        }

        @Test
        @DisplayName("returns error when X-Participant-Id header is missing")
        void returnsErrorWhenHeaderMissing() throws Exception {
            mockMvc.perform(get("/api/sessions/{sessionId}/leaderboard/self", sessionId))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @DisplayName("returns 500 when service throws unexpected exception (simulates retry exhaustion)")
        void returns500OnUnexpectedError() throws Exception {
            UUID participantId = UUID.randomUUID();

            when(leaderboardService.getParticipantResult(sessionId, participantId))
                    .thenThrow(new RuntimeException("Service unavailable after retries"));

            mockMvc.perform(get("/api/sessions/{sessionId}/leaderboard/self", sessionId)
                            .header("X-Participant-Id", participantId.toString()))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"));
        }

        @Test
        @DisplayName("returns self-result with negative score difference for below-average participant")
        void returnsBelowAverageResult() throws Exception {
            UUID participantId = UUID.randomUUID();

            ParticipantSelfResult result = ParticipantSelfResult.builder()
                    .rank(15)
                    .score(3200)
                    .correctAnswers(3)
                    .totalQuestions(10)
                    .maxStreak(1)
                    .avgResponseTimeSec(5.8)
                    .scoreDifference(-1800)
                    .aboveAverage(false)
                    .questionBreakdown(List.of())
                    .build();

            when(leaderboardService.getParticipantResult(sessionId, participantId)).thenReturn(result);

            mockMvc.perform(get("/api/sessions/{sessionId}/leaderboard/self", sessionId)
                            .header("X-Participant-Id", participantId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.scoreDifference").value(-1800))
                    .andExpect(jsonPath("$.aboveAverage").value(false));
        }
    }
}
