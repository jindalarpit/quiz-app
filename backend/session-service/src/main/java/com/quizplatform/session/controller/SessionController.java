package com.quizplatform.session.controller;

import com.quizplatform.session.dto.*;
import com.quizplatform.session.service.AnswerService;
import com.quizplatform.session.service.AntiCheatService;
import com.quizplatform.session.service.SessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;
    private final AnswerService answerService;
    private final AntiCheatService antiCheatService;

    /**
     * Start a new session.
     * Host ID is passed via X-User-Id header (set by API Gateway after JWT validation).
     */
    @PostMapping
    public ResponseEntity<SessionResponse> startSession(
            @RequestHeader("X-User-Id") UUID hostId,
            @Valid @RequestBody CreateSessionRequest request) {

        SessionResponse response = sessionService.startSession(hostId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Join a session with a nickname. No authentication required.
     */
    @PostMapping("/{pin}/join")
    public ResponseEntity<ParticipantResponse> joinSession(
            @PathVariable String pin,
            @Valid @RequestBody JoinSessionRequest request) {

        ParticipantResponse response = sessionService.joinSession(pin, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Advance to the next question (host only).
     */
    @PostMapping("/{pin}/next")
    public ResponseEntity<Void> advanceToNextQuestion(
            @PathVariable String pin,
            @RequestHeader("X-User-Id") UUID hostId) {

        sessionService.advanceToNextQuestion(pin, hostId);
        return ResponseEntity.ok().build();
    }

    /**
     * Pause the session (host only).
     */
    @PostMapping("/{pin}/pause")
    public ResponseEntity<Void> pauseSession(
            @PathVariable String pin,
            @RequestHeader("X-User-Id") UUID hostId) {

        sessionService.pauseSession(pin, hostId);
        return ResponseEntity.ok().build();
    }

    /**
     * Resume the session (host only).
     */
    @PostMapping("/{pin}/resume")
    public ResponseEntity<Void> resumeSession(
            @PathVariable String pin,
            @RequestHeader("X-User-Id") UUID hostId) {

        sessionService.resumeSession(pin, hostId);
        return ResponseEntity.ok().build();
    }

    /**
     * Skip the current question (host only).
     */
    @PostMapping("/{pin}/skip")
    public ResponseEntity<Void> skipQuestion(
            @PathVariable String pin,
            @RequestHeader("X-User-Id") UUID hostId) {

        sessionService.skipQuestion(pin, hostId);
        return ResponseEntity.ok().build();
    }

    /**
     * End the session (host only).
     */
    @PostMapping("/{pin}/end")
    public ResponseEntity<Void> endSession(
            @PathVariable String pin,
            @RequestHeader("X-User-Id") UUID hostId) {

        sessionService.endSession(pin, hostId);
        return ResponseEntity.ok().build();
    }

    /**
     * Submit an answer for a question. No authentication required —
     * participant is identified by participantId in the request body.
     */
    @PostMapping("/{pin}/answer")
    public ResponseEntity<AnswerResult> submitAnswer(
            @PathVariable String pin,
            @Valid @RequestBody AnswerSubmitRequest request) {

        AnswerResult result = answerService.submitAnswer(pin, request);
        return ResponseEntity.ok(result);
    }

    /**
     * Reveal the answer for the current question (host only).
     * Transitions state to REVEAL, computes stats, returns leaderboard top 5.
     */
    @PostMapping("/{pin}/reveal")
    public ResponseEntity<RevealResult> revealAnswer(
            @PathVariable String pin,
            @RequestHeader("X-User-Id") UUID hostId) {

        RevealResult result = answerService.revealAnswer(pin, hostId);
        return ResponseEntity.ok(result);
    }

    /**
     * Kick a participant from the session (host only).
     * Marks participant as kicked, removes from leaderboard, disconnects WebSocket.
     */
    @PostMapping("/{pin}/kick/{participantId}")
    public ResponseEntity<Void> kickParticipant(
            @PathVariable String pin,
            @PathVariable String participantId,
            @RequestHeader("X-User-Id") UUID hostId) {

        sessionService.validateHostAccess(pin, hostId);
        antiCheatService.kickParticipant(pin, participantId);
        return ResponseEntity.ok().build();
    }
}
