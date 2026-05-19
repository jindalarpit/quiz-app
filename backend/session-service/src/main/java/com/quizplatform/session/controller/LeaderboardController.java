package com.quizplatform.session.controller;

import com.quizplatform.session.dto.PagedLeaderboardResponse;
import com.quizplatform.session.dto.ParticipantSelfResult;
import com.quizplatform.session.service.LeaderboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for leaderboard endpoints.
 * Provides paginated leaderboard retrieval and participant self-result lookup.
 *
 * Exception handling is delegated to the GlobalExceptionHandler:
 * - ResourceNotFoundException → 404
 * - ValidationException → 400
 */
@RestController
@RequestMapping("/api/sessions/{sessionId}/leaderboard")
@RequiredArgsConstructor
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    /**
     * GET /api/sessions/{sessionId}/leaderboard?page=0&size=20
     * Returns paginated final leaderboard for the given session.
     *
     * @param sessionId the session UUID
     * @param page      zero-based page number (default: 0)
     * @param size      page size (default: 20)
     * @return paginated leaderboard response
     */
    @GetMapping
    public ResponseEntity<PagedLeaderboardResponse> getLeaderboard(
            @PathVariable UUID sessionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PagedLeaderboardResponse response = leaderboardService.getPagedLeaderboard(sessionId, page, size);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/sessions/{sessionId}/leaderboard/self
     * Returns the requesting participant's individual results including per-question breakdown.
     *
     * @param sessionId     the session UUID
     * @param participantId the participant UUID from X-Participant-Id header
     * @return participant self-result
     */
    @GetMapping("/self")
    public ResponseEntity<ParticipantSelfResult> getSelfResult(
            @PathVariable UUID sessionId,
            @RequestHeader("X-Participant-Id") UUID participantId) {

        ParticipantSelfResult result = leaderboardService.getParticipantResult(sessionId, participantId);
        return ResponseEntity.ok(result);
    }
}
