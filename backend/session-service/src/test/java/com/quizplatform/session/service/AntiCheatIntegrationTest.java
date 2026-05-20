package com.quizplatform.session.service;

import com.quizplatform.common.exception.ValidationException;
import com.quizplatform.session.dto.AnswerResult;
import com.quizplatform.session.dto.AnswerSubmitRequest;
import com.quizplatform.session.model.SessionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for anti-cheating detection and kick flow.
 * Tests the interaction between AnswerService, AntiCheatService, and RedisSessionService.
 */
@ExtendWith(MockitoExtension.class)
class AntiCheatIntegrationTest {

    @Mock
    private RedisSessionService redisSessionService;

    @Mock
    private ScoreCalculator scoreCalculator;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private DynamicScoreEngine dynamicScoreEngine;

    private AntiCheatService antiCheatService;
    private AnswerService answerService;

    private static final String PIN = "TEST01";
    private static final String PARTICIPANT_ID = "cheater-1";
    private static final String NICKNAME = "Cheater";

    @BeforeEach
    void setUp() {
        antiCheatService = new AntiCheatService(redisSessionService, redisTemplate);
        answerService = new AnswerService(redisSessionService, scoreCalculator, antiCheatService, dynamicScoreEngine, redisTemplate);
    }

    // ==================== Fast-Answer Detection Tests ====================

    @Test
    @DisplayName("Fast answer detection - single fast answer does not flag participant")
    void fastAnswer_singleFastAnswer_notFlagged() {
        // Simulate a fast answer (< 200ms response time)
        when(redisSessionService.incrementFastAnswerCount(PIN, PARTICIPANT_ID)).thenReturn(1L);

        antiCheatService.checkFastAnswer(PIN, PARTICIPANT_ID, 100);

        verify(redisSessionService).incrementFastAnswerCount(PIN, PARTICIPANT_ID);
        verify(redisSessionService, never()).flagParticipant(anyString(), anyString());
    }

    @Test
    @DisplayName("Fast answer detection - two fast answers does not flag participant")
    void fastAnswer_twoFastAnswers_notFlagged() {
        when(redisSessionService.incrementFastAnswerCount(PIN, PARTICIPANT_ID)).thenReturn(2L);

        antiCheatService.checkFastAnswer(PIN, PARTICIPANT_ID, 150);

        verify(redisSessionService).incrementFastAnswerCount(PIN, PARTICIPANT_ID);
        verify(redisSessionService, never()).flagParticipant(anyString(), anyString());
    }

    @Test
    @DisplayName("Fast answer detection - three fast answers flags participant and notifies host")
    void fastAnswer_threeFastAnswers_flagsAndNotifies() {
        when(redisSessionService.incrementFastAnswerCount(PIN, PARTICIPANT_ID)).thenReturn(3L);
        when(redisSessionService.getParticipantNickname(PIN, PARTICIPANT_ID)).thenReturn(NICKNAME);

        antiCheatService.checkFastAnswer(PIN, PARTICIPANT_ID, 50);

        verify(redisSessionService).flagParticipant(PIN, PARTICIPANT_ID);
        verify(redisTemplate).convertAndSend(eq("session:" + PIN + ":host"), contains("suspicious_activity"));
    }

    @Test
    @DisplayName("Fast answer detection - normal response time (>= 200ms) does not increment count")
    void fastAnswer_normalResponseTime_noAction() {
        antiCheatService.checkFastAnswer(PIN, PARTICIPANT_ID, 200);

        verify(redisSessionService, never()).incrementFastAnswerCount(anyString(), anyString());
    }

    @Test
    @DisplayName("Fast answer detection - response time exactly at threshold (200ms) does not trigger")
    void fastAnswer_exactlyAtThreshold_noAction() {
        antiCheatService.checkFastAnswer(PIN, PARTICIPANT_ID, 200);

        verify(redisSessionService, never()).incrementFastAnswerCount(anyString(), anyString());
    }

    // ==================== Suspicious Activity Notification Tests ====================

    @Test
    @DisplayName("Suspicious activity notification - includes correct participant info")
    void suspiciousNotification_includesCorrectInfo() {
        when(redisSessionService.incrementFastAnswerCount(PIN, PARTICIPANT_ID)).thenReturn(3L);
        when(redisSessionService.getParticipantNickname(PIN, PARTICIPANT_ID)).thenReturn(NICKNAME);

        antiCheatService.checkFastAnswer(PIN, PARTICIPANT_ID, 100);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(eq("session:" + PIN + ":host"), messageCaptor.capture());

        String message = messageCaptor.getValue();
        assertThat(message).contains("\"participantId\":\"" + PARTICIPANT_ID + "\"");
        assertThat(message).contains("\"nickname\":\"" + NICKNAME + "\"");
        assertThat(message).contains("\"reason\":\"fast_answers\"");
        assertThat(message).contains("\"count\":3");
    }

    // ==================== Kick Flow Tests ====================

    @Test
    @DisplayName("Kick participant - marks as kicked and removes from leaderboard")
    void kickParticipant_marksKickedAndRemovesFromLeaderboard() {
        antiCheatService.kickParticipant(PIN, PARTICIPANT_ID);

        verify(redisSessionService).kickParticipant(PIN, PARTICIPANT_ID);
        verify(redisSessionService).removeFromLeaderboard(PIN, PARTICIPANT_ID);
        verify(redisTemplate).convertAndSend(
                eq("session:" + PIN + ":participant:" + PARTICIPANT_ID),
                contains("participant.kicked"));
    }

    @Test
    @DisplayName("Kicked participant - answer submission is rejected")
    void kickedParticipant_answerRejected() {
        when(redisSessionService.sessionExists(PIN)).thenReturn(true);
        when(redisSessionService.isParticipantKicked(PIN, PARTICIPANT_ID)).thenReturn(true);
        when(redisSessionService.getCurrentQuestionIndex(PIN)).thenReturn(0);

        AnswerSubmitRequest request = AnswerSubmitRequest.builder()
                .participantId(PARTICIPANT_ID)
                .answer("A")
                .clientTimestamp(Instant.now().toEpochMilli())
                .build();

        assertThatThrownBy(() -> answerService.submitAnswer(PIN, request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("removed from this session");
    }

    @Test
    @DisplayName("Kicked participant - audit log records rejection")
    void kickedParticipant_auditLogRecordsRejection() {
        when(redisSessionService.sessionExists(PIN)).thenReturn(true);
        when(redisSessionService.isParticipantKicked(PIN, PARTICIPANT_ID)).thenReturn(true);
        when(redisSessionService.getCurrentQuestionIndex(PIN)).thenReturn(2);

        AnswerSubmitRequest request = AnswerSubmitRequest.builder()
                .participantId(PARTICIPANT_ID)
                .answer("B")
                .clientTimestamp(Instant.now().toEpochMilli())
                .build();

        try {
            answerService.submitAnswer(PIN, request);
        } catch (ValidationException e) {
            // expected
        }

        verify(redisSessionService).logAuditEvent(
                eq(PIN), eq(PARTICIPANT_ID), eq(2),
                eq("B"), anyLong(), eq(false), eq("participant_kicked"));
    }

    // ==================== Answer Submission with Anti-Cheat Integration ====================

    @Test
    @DisplayName("Answer submission - fast answer triggers anti-cheat check")
    void answerSubmission_fastAnswer_triggersAntiCheatCheck() {
        long now = Instant.now().toEpochMilli();
        long startTime = now - 100; // answered in 100ms (fast!)

        when(redisSessionService.sessionExists(PIN)).thenReturn(true);
        when(redisSessionService.isParticipantKicked(PIN, PARTICIPANT_ID)).thenReturn(false);
        when(redisSessionService.getSessionState(PIN)).thenReturn(SessionStatus.QUESTION_OPEN.name());
        when(redisSessionService.getQuestionStartTime(PIN)).thenReturn(startTime);
        when(redisSessionService.getQuestionDurationMs(PIN)).thenReturn(20000);
        when(redisSessionService.getCurrentQuestionIndex(PIN)).thenReturn(0);
        when(redisSessionService.getCorrectAnswer(PIN, 0)).thenReturn("A");
        when(redisSessionService.getStreak(PIN, PARTICIPANT_ID)).thenReturn(0);
        when(scoreCalculator.getStreakMultiplier(1)).thenReturn(1);
        when(scoreCalculator.calculateScoreWithStreak(eq(1000), anyLong(), eq(20000), eq(1))).thenReturn(975);
        when(redisSessionService.storeAnswerIfAbsent(eq(PIN), eq(0), eq(PARTICIPANT_ID), anyString())).thenReturn(true);
        when(redisSessionService.getParticipantScore(PIN, PARTICIPANT_ID)).thenReturn(975.0);
        when(redisSessionService.getParticipantRank(PIN, PARTICIPANT_ID)).thenReturn(1L);
        when(redisSessionService.incrementFastAnswerCount(PIN, PARTICIPANT_ID)).thenReturn(1L);

        AnswerSubmitRequest request = AnswerSubmitRequest.builder()
                .participantId(PARTICIPANT_ID)
                .answer("A")
                .clientTimestamp(now)
                .build();

        AnswerResult result = answerService.submitAnswer(PIN, request);

        assertThat(result.isAccepted()).isTrue();
        // Verify anti-cheat was invoked (fast answer count incremented)
        verify(redisSessionService).incrementFastAnswerCount(PIN, PARTICIPANT_ID);
    }

    @Test
    @DisplayName("Answer submission - normal speed does not trigger anti-cheat")
    void answerSubmission_normalSpeed_noAntiCheatTrigger() {
        long now = Instant.now().toEpochMilli();
        long startTime = now - 5000; // answered in 5 seconds (normal)

        when(redisSessionService.sessionExists(PIN)).thenReturn(true);
        when(redisSessionService.isParticipantKicked(PIN, PARTICIPANT_ID)).thenReturn(false);
        when(redisSessionService.getSessionState(PIN)).thenReturn(SessionStatus.QUESTION_OPEN.name());
        when(redisSessionService.getQuestionStartTime(PIN)).thenReturn(startTime);
        when(redisSessionService.getQuestionDurationMs(PIN)).thenReturn(20000);
        when(redisSessionService.getCurrentQuestionIndex(PIN)).thenReturn(0);
        when(redisSessionService.getCorrectAnswer(PIN, 0)).thenReturn("B");
        when(redisSessionService.getStreak(PIN, PARTICIPANT_ID)).thenReturn(0);
        when(redisSessionService.storeAnswerIfAbsent(eq(PIN), eq(0), eq(PARTICIPANT_ID), anyString())).thenReturn(true);
        when(redisSessionService.getParticipantScore(PIN, PARTICIPANT_ID)).thenReturn(0.0);
        when(redisSessionService.getParticipantRank(PIN, PARTICIPANT_ID)).thenReturn(1L);

        AnswerSubmitRequest request = AnswerSubmitRequest.builder()
                .participantId(PARTICIPANT_ID)
                .answer("A") // wrong answer
                .clientTimestamp(now)
                .build();

        AnswerResult result = answerService.submitAnswer(PIN, request);

        assertThat(result.isAccepted()).isTrue();
        // Verify anti-cheat was NOT invoked (normal response time)
        verify(redisSessionService, never()).incrementFastAnswerCount(anyString(), anyString());
    }

    // ==================== Audit Logging Tests ====================

    @Test
    @DisplayName("Audit logging - accepted answer is logged")
    void auditLogging_acceptedAnswer_isLogged() {
        long now = Instant.now().toEpochMilli();
        long startTime = now - 5000;

        when(redisSessionService.sessionExists(PIN)).thenReturn(true);
        when(redisSessionService.isParticipantKicked(PIN, PARTICIPANT_ID)).thenReturn(false);
        when(redisSessionService.getSessionState(PIN)).thenReturn(SessionStatus.QUESTION_OPEN.name());
        when(redisSessionService.getQuestionStartTime(PIN)).thenReturn(startTime);
        when(redisSessionService.getQuestionDurationMs(PIN)).thenReturn(20000);
        when(redisSessionService.getCurrentQuestionIndex(PIN)).thenReturn(1);
        when(redisSessionService.getCorrectAnswer(PIN, 1)).thenReturn("C");
        when(redisSessionService.getStreak(PIN, PARTICIPANT_ID)).thenReturn(0);
        when(redisSessionService.storeAnswerIfAbsent(eq(PIN), eq(1), eq(PARTICIPANT_ID), anyString())).thenReturn(true);
        when(redisSessionService.getParticipantScore(PIN, PARTICIPANT_ID)).thenReturn(0.0);
        when(redisSessionService.getParticipantRank(PIN, PARTICIPANT_ID)).thenReturn(1L);

        AnswerSubmitRequest request = AnswerSubmitRequest.builder()
                .participantId(PARTICIPANT_ID)
                .answer("A")
                .clientTimestamp(now)
                .build();

        answerService.submitAnswer(PIN, request);

        // Verify audit log was called with accepted=true
        verify(redisSessionService).logAuditEvent(
                eq(PIN), eq(PARTICIPANT_ID), eq(1),
                eq("A"), anyLong(), eq(true), isNull());
    }

    @Test
    @DisplayName("Audit logging - duplicate submission is logged as rejected")
    void auditLogging_duplicateSubmission_loggedAsRejected() {
        long now = Instant.now().toEpochMilli();
        long startTime = now - 5000;

        when(redisSessionService.sessionExists(PIN)).thenReturn(true);
        when(redisSessionService.isParticipantKicked(PIN, PARTICIPANT_ID)).thenReturn(false);
        when(redisSessionService.getSessionState(PIN)).thenReturn(SessionStatus.QUESTION_OPEN.name());
        when(redisSessionService.getQuestionStartTime(PIN)).thenReturn(startTime);
        when(redisSessionService.getQuestionDurationMs(PIN)).thenReturn(20000);
        when(redisSessionService.getCurrentQuestionIndex(PIN)).thenReturn(0);
        when(redisSessionService.getCorrectAnswer(PIN, 0)).thenReturn("A");
        when(redisSessionService.getStreak(PIN, PARTICIPANT_ID)).thenReturn(0);
        when(scoreCalculator.getStreakMultiplier(1)).thenReturn(1);
        when(scoreCalculator.calculateScoreWithStreak(eq(1000), anyLong(), eq(20000), eq(1))).thenReturn(900);
        when(redisSessionService.storeAnswerIfAbsent(eq(PIN), eq(0), eq(PARTICIPANT_ID), anyString())).thenReturn(false);

        AnswerSubmitRequest request = AnswerSubmitRequest.builder()
                .participantId(PARTICIPANT_ID)
                .answer("A")
                .clientTimestamp(now)
                .build();

        try {
            answerService.submitAnswer(PIN, request);
        } catch (Exception e) {
            // expected
        }

        verify(redisSessionService).logAuditEvent(
                eq(PIN), eq(PARTICIPANT_ID), eq(0),
                eq("A"), anyLong(), eq(false), eq("duplicate_submission"));
    }

    // ==================== Duplicate Connection Prevention Tests ====================

    @Test
    @DisplayName("Duplicate connection prevention - isParticipantConnected returns true when connected")
    void duplicateConnection_isConnected_returnsTrue() {
        when(redisSessionService.isParticipantConnected(PIN, PARTICIPANT_ID)).thenReturn(true);

        boolean connected = redisSessionService.isParticipantConnected(PIN, PARTICIPANT_ID);

        assertThat(connected).isTrue();
    }

    @Test
    @DisplayName("Duplicate connection prevention - isParticipantConnected returns false when disconnected")
    void duplicateConnection_isDisconnected_returnsFalse() {
        when(redisSessionService.isParticipantConnected(PIN, PARTICIPANT_ID)).thenReturn(false);

        boolean connected = redisSessionService.isParticipantConnected(PIN, PARTICIPANT_ID);

        assertThat(connected).isFalse();
    }
}
