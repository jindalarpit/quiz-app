package com.quizplatform.session.service;

import com.quizplatform.common.exception.DuplicateResourceException;
import com.quizplatform.common.exception.ResourceNotFoundException;
import com.quizplatform.common.exception.ValidationException;
import com.quizplatform.session.dto.AnswerResult;
import com.quizplatform.session.dto.AnswerSubmitRequest;
import com.quizplatform.session.dto.LeaderboardEntry;
import com.quizplatform.session.dto.RevealResult;
import com.quizplatform.session.model.SessionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnswerServiceTest {

    @Mock
    private RedisSessionService redisSessionService;

    @Mock
    private ScoreCalculator scoreCalculator;

    @Mock
    private AntiCheatService antiCheatService;

    @InjectMocks
    private AnswerService answerService;

    private static final String PIN = "ABC123";
    private static final String PARTICIPANT_ID = "participant-1";
    private static final UUID HOST_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // Common setup if needed
    }

    @Test
    @DisplayName("Submit answer - successful correct answer")
    void submitAnswer_correctAnswer_success() {
        long now = Instant.now().toEpochMilli();
        long startTime = now - 5000; // started 5 seconds ago

        when(redisSessionService.sessionExists(PIN)).thenReturn(true);
        when(redisSessionService.isParticipantKicked(PIN, PARTICIPANT_ID)).thenReturn(false);
        when(redisSessionService.getSessionState(PIN)).thenReturn(SessionStatus.QUESTION_OPEN.name());
        when(redisSessionService.getQuestionStartTime(PIN)).thenReturn(startTime);
        when(redisSessionService.getQuestionDurationMs(PIN)).thenReturn(20000);
        when(redisSessionService.getCurrentQuestionIndex(PIN)).thenReturn(1);
        when(redisSessionService.getCorrectAnswer(PIN, 1)).thenReturn("B");
        when(redisSessionService.getStreak(PIN, PARTICIPANT_ID)).thenReturn(0);
        when(scoreCalculator.getStreakMultiplier(1)).thenReturn(1);
        when(scoreCalculator.calculateScoreWithStreak(eq(1000), anyLong(), eq(20000), eq(1))).thenReturn(875);
        when(redisSessionService.storeAnswerIfAbsent(eq(PIN), eq(1), eq(PARTICIPANT_ID), anyString())).thenReturn(true);
        when(redisSessionService.getParticipantScore(PIN, PARTICIPANT_ID)).thenReturn(1875.0);
        when(redisSessionService.getParticipantRank(PIN, PARTICIPANT_ID)).thenReturn(2L);

        AnswerSubmitRequest request = AnswerSubmitRequest.builder()
                .participantId(PARTICIPANT_ID)
                .answer("B")
                .clientTimestamp(now)
                .build();

        AnswerResult result = answerService.submitAnswer(PIN, request);

        assertThat(result.isAccepted()).isTrue();
        assertThat(result.getScoreAwarded()).isEqualTo(875);
        assertThat(result.getTotalScore()).isEqualTo(1875);
        assertThat(result.getRank()).isEqualTo(2);
        assertThat(result.getStreak()).isEqualTo(1);
        assertThat(result.getMultiplier()).isEqualTo(1);

        verify(redisSessionService).incrementLeaderboardScore(PIN, PARTICIPANT_ID, 875);
        verify(redisSessionService).updateStreak(PIN, PARTICIPANT_ID, 1, 1);
    }

    @Test
    @DisplayName("Submit answer - incorrect answer awards 0 points")
    void submitAnswer_incorrectAnswer_zeroPoints() {
        long now = Instant.now().toEpochMilli();
        long startTime = now - 5000;

        when(redisSessionService.sessionExists(PIN)).thenReturn(true);
        when(redisSessionService.isParticipantKicked(PIN, PARTICIPANT_ID)).thenReturn(false);
        when(redisSessionService.getSessionState(PIN)).thenReturn(SessionStatus.QUESTION_OPEN.name());
        when(redisSessionService.getQuestionStartTime(PIN)).thenReturn(startTime);
        when(redisSessionService.getQuestionDurationMs(PIN)).thenReturn(20000);
        when(redisSessionService.getCurrentQuestionIndex(PIN)).thenReturn(1);
        when(redisSessionService.getCorrectAnswer(PIN, 1)).thenReturn("B");
        when(redisSessionService.getStreak(PIN, PARTICIPANT_ID)).thenReturn(3);
        when(redisSessionService.storeAnswerIfAbsent(eq(PIN), eq(1), eq(PARTICIPANT_ID), anyString())).thenReturn(true);
        when(redisSessionService.getParticipantScore(PIN, PARTICIPANT_ID)).thenReturn(1000.0);
        when(redisSessionService.getParticipantRank(PIN, PARTICIPANT_ID)).thenReturn(5L);

        AnswerSubmitRequest request = AnswerSubmitRequest.builder()
                .participantId(PARTICIPANT_ID)
                .answer("A") // wrong answer
                .clientTimestamp(now)
                .build();

        AnswerResult result = answerService.submitAnswer(PIN, request);

        assertThat(result.isAccepted()).isTrue();
        assertThat(result.getScoreAwarded()).isEqualTo(0);
        assertThat(result.getStreak()).isEqualTo(0);
        assertThat(result.getMultiplier()).isEqualTo(1);

        verify(redisSessionService, never()).incrementLeaderboardScore(anyString(), anyString(), anyInt());
        verify(redisSessionService).resetStreak(PIN, PARTICIPANT_ID);
    }

    @Test
    @DisplayName("Submit answer - session not found throws ResourceNotFoundException")
    void submitAnswer_sessionNotFound_throwsException() {
        when(redisSessionService.sessionExists(PIN)).thenReturn(false);

        AnswerSubmitRequest request = AnswerSubmitRequest.builder()
                .participantId(PARTICIPANT_ID)
                .answer("A")
                .clientTimestamp(Instant.now().toEpochMilli())
                .build();

        assertThatThrownBy(() -> answerService.submitAnswer(PIN, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Submit answer - session not in QUESTION_OPEN state throws ValidationException")
    void submitAnswer_wrongState_throwsException() {
        when(redisSessionService.sessionExists(PIN)).thenReturn(true);
        when(redisSessionService.isParticipantKicked(PIN, PARTICIPANT_ID)).thenReturn(false);
        when(redisSessionService.getSessionState(PIN)).thenReturn(SessionStatus.LOBBY.name());
        when(redisSessionService.getCurrentQuestionIndex(PIN)).thenReturn(0);

        AnswerSubmitRequest request = AnswerSubmitRequest.builder()
                .participantId(PARTICIPANT_ID)
                .answer("A")
                .clientTimestamp(Instant.now().toEpochMilli())
                .build();

        assertThatThrownBy(() -> answerService.submitAnswer(PIN, request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("not accepting answers");
    }

    @Test
    @DisplayName("Submit answer - empty answer throws ValidationException")
    void submitAnswer_emptyAnswer_throwsException() {
        when(redisSessionService.sessionExists(PIN)).thenReturn(true);
        when(redisSessionService.isParticipantKicked(PIN, PARTICIPANT_ID)).thenReturn(false);
        when(redisSessionService.getSessionState(PIN)).thenReturn(SessionStatus.QUESTION_OPEN.name());

        AnswerSubmitRequest request = AnswerSubmitRequest.builder()
                .participantId(PARTICIPANT_ID)
                .answer("   ")
                .clientTimestamp(Instant.now().toEpochMilli())
                .build();

        assertThatThrownBy(() -> answerService.submitAnswer(PIN, request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("empty");
    }

    @Test
    @DisplayName("Submit answer - after timer expiry throws ValidationException")
    void submitAnswer_afterTimerExpiry_throwsException() {
        long now = Instant.now().toEpochMilli();
        // Question started 25 seconds ago with 20 second limit (+ 500ms grace = 20500ms)
        long startTime = now - 25000;

        when(redisSessionService.sessionExists(PIN)).thenReturn(true);
        when(redisSessionService.isParticipantKicked(PIN, PARTICIPANT_ID)).thenReturn(false);
        when(redisSessionService.getSessionState(PIN)).thenReturn(SessionStatus.QUESTION_OPEN.name());
        when(redisSessionService.getQuestionStartTime(PIN)).thenReturn(startTime);
        when(redisSessionService.getQuestionDurationMs(PIN)).thenReturn(20000);
        when(redisSessionService.getCurrentQuestionIndex(PIN)).thenReturn(0);

        AnswerSubmitRequest request = AnswerSubmitRequest.builder()
                .participantId(PARTICIPANT_ID)
                .answer("A")
                .clientTimestamp(now)
                .build();

        assertThatThrownBy(() -> answerService.submitAnswer(PIN, request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("time has expired");
    }

    @Test
    @DisplayName("Submit answer - duplicate submission throws DuplicateResourceException")
    void submitAnswer_duplicateSubmission_throwsException() {
        long now = Instant.now().toEpochMilli();
        long startTime = now - 5000;

        when(redisSessionService.sessionExists(PIN)).thenReturn(true);
        when(redisSessionService.isParticipantKicked(PIN, PARTICIPANT_ID)).thenReturn(false);
        when(redisSessionService.getSessionState(PIN)).thenReturn(SessionStatus.QUESTION_OPEN.name());
        when(redisSessionService.getQuestionStartTime(PIN)).thenReturn(startTime);
        when(redisSessionService.getQuestionDurationMs(PIN)).thenReturn(20000);
        when(redisSessionService.getCurrentQuestionIndex(PIN)).thenReturn(1);
        when(redisSessionService.getCorrectAnswer(PIN, 1)).thenReturn("B");
        when(redisSessionService.getStreak(PIN, PARTICIPANT_ID)).thenReturn(0);
        when(scoreCalculator.getStreakMultiplier(1)).thenReturn(1);
        when(scoreCalculator.calculateScoreWithStreak(eq(1000), anyLong(), eq(20000), eq(1))).thenReturn(875);
        when(redisSessionService.storeAnswerIfAbsent(eq(PIN), eq(1), eq(PARTICIPANT_ID), anyString())).thenReturn(false);

        AnswerSubmitRequest request = AnswerSubmitRequest.builder()
                .participantId(PARTICIPANT_ID)
                .answer("B")
                .clientTimestamp(now)
                .build();

        assertThatThrownBy(() -> answerService.submitAnswer(PIN, request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already submitted");
    }

    @Test
    @DisplayName("Submit answer - within grace period is accepted")
    void submitAnswer_withinGracePeriod_accepted() {
        long now = Instant.now().toEpochMilli();
        // Question started 20.3 seconds ago with 20 second limit (within 500ms grace)
        long startTime = now - 20300;

        when(redisSessionService.sessionExists(PIN)).thenReturn(true);
        when(redisSessionService.isParticipantKicked(PIN, PARTICIPANT_ID)).thenReturn(false);
        when(redisSessionService.getSessionState(PIN)).thenReturn(SessionStatus.QUESTION_OPEN.name());
        when(redisSessionService.getQuestionStartTime(PIN)).thenReturn(startTime);
        when(redisSessionService.getQuestionDurationMs(PIN)).thenReturn(20000);
        when(redisSessionService.getCurrentQuestionIndex(PIN)).thenReturn(0);
        when(redisSessionService.getCorrectAnswer(PIN, 0)).thenReturn("A");
        when(redisSessionService.getStreak(PIN, PARTICIPANT_ID)).thenReturn(0);
        when(scoreCalculator.getStreakMultiplier(1)).thenReturn(1);
        when(scoreCalculator.calculateScoreWithStreak(eq(1000), anyLong(), eq(20000), eq(1))).thenReturn(500);
        when(redisSessionService.storeAnswerIfAbsent(eq(PIN), eq(0), eq(PARTICIPANT_ID), anyString())).thenReturn(true);
        when(redisSessionService.getParticipantScore(PIN, PARTICIPANT_ID)).thenReturn(500.0);
        when(redisSessionService.getParticipantRank(PIN, PARTICIPANT_ID)).thenReturn(1L);

        AnswerSubmitRequest request = AnswerSubmitRequest.builder()
                .participantId(PARTICIPANT_ID)
                .answer("A")
                .clientTimestamp(now)
                .build();

        AnswerResult result = answerService.submitAnswer(PIN, request);
        assertThat(result.isAccepted()).isTrue();
    }

    // ==================== Reveal Answer Tests ====================

    @Test
    @DisplayName("Reveal answer - returns correct answer, stats, and leaderboard")
    void revealAnswer_success() {
        when(redisSessionService.sessionExists(PIN)).thenReturn(true);
        when(redisSessionService.getHostId(PIN)).thenReturn(HOST_ID.toString());
        when(redisSessionService.getCurrentQuestionIndex(PIN)).thenReturn(1);
        when(redisSessionService.getCorrectAnswer(PIN, 1)).thenReturn("B");

        Map<Object, Object> answers = new HashMap<>();
        answers.put("p1", "B|1000|875");
        answers.put("p2", "A|1200|0");
        answers.put("p3", "B|1500|750");
        when(redisSessionService.getAnswersForQuestion(PIN, 1)).thenReturn(answers);

        List<LeaderboardEntry> top5 = List.of(
                LeaderboardEntry.builder().participantId("p1").nickname("Player1").score(875).rank(1).build(),
                LeaderboardEntry.builder().participantId("p3").nickname("Player3").score(750).rank(2).build()
        );
        when(redisSessionService.getTopN(PIN, 5)).thenReturn(top5);

        RevealResult result = answerService.revealAnswer(PIN, HOST_ID);

        assertThat(result.getCorrectAnswer()).isEqualTo("B");
        assertThat(result.getStats()).containsEntry("B", 2);
        assertThat(result.getStats()).containsEntry("A", 1);
        assertThat(result.getAccuracyRate()).isCloseTo(0.6667, org.assertj.core.data.Offset.offset(0.001));
        assertThat(result.getLeaderboard()).hasSize(2);

        verify(redisSessionService).updateSessionState(PIN, SessionStatus.REVEAL.name());
    }

    @Test
    @DisplayName("Reveal answer - non-host throws ValidationException")
    void revealAnswer_nonHost_throwsException() {
        UUID otherUser = UUID.randomUUID();
        when(redisSessionService.sessionExists(PIN)).thenReturn(true);
        when(redisSessionService.getHostId(PIN)).thenReturn(HOST_ID.toString());

        assertThatThrownBy(() -> answerService.revealAnswer(PIN, otherUser))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Only the host");
    }

    @Test
    @DisplayName("Reveal answer - session not found throws ResourceNotFoundException")
    void revealAnswer_sessionNotFound_throwsException() {
        when(redisSessionService.sessionExists(PIN)).thenReturn(false);

        assertThatThrownBy(() -> answerService.revealAnswer(PIN, HOST_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Reveal answer - no answers submitted returns empty stats")
    void revealAnswer_noAnswers_emptyStats() {
        when(redisSessionService.sessionExists(PIN)).thenReturn(true);
        when(redisSessionService.getHostId(PIN)).thenReturn(HOST_ID.toString());
        when(redisSessionService.getCurrentQuestionIndex(PIN)).thenReturn(0);
        when(redisSessionService.getCorrectAnswer(PIN, 0)).thenReturn("C");
        when(redisSessionService.getAnswersForQuestion(PIN, 0)).thenReturn(Collections.emptyMap());
        when(redisSessionService.getTopN(PIN, 5)).thenReturn(Collections.emptyList());

        RevealResult result = answerService.revealAnswer(PIN, HOST_ID);

        assertThat(result.getCorrectAnswer()).isEqualTo("C");
        assertThat(result.getStats()).isEmpty();
        assertThat(result.getAccuracyRate()).isEqualTo(0.0);
        assertThat(result.getLeaderboard()).isEmpty();
    }
}
