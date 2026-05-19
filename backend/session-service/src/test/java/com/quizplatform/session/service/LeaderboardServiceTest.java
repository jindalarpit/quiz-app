package com.quizplatform.session.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizplatform.common.dto.QuestionDTO;
import com.quizplatform.common.exception.ResourceNotFoundException;
import com.quizplatform.session.client.QuizServiceClient;
import com.quizplatform.session.dto.AnswerStatus;
import com.quizplatform.session.dto.FinalLeaderboardEntry;
import com.quizplatform.session.dto.PagedLeaderboardResponse;
import com.quizplatform.session.dto.ParticipantSelfResult;
import com.quizplatform.session.model.AnswerSubmission;
import com.quizplatform.session.model.Session;
import com.quizplatform.session.model.SessionParticipant;
import com.quizplatform.session.repository.AnswerSubmissionRepository;
import com.quizplatform.session.repository.SessionParticipantRepository;
import com.quizplatform.session.repository.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaderboardServiceTest {

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private SessionParticipantRepository sessionParticipantRepository;

    @Mock
    private AnswerSubmissionRepository answerSubmissionRepository;

    @Mock
    private QuizServiceClient quizServiceClient;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private LeaderboardService leaderboardService;

    private UUID sessionId;
    private UUID hostId;
    private UUID quizId;
    private Session session;

    @BeforeEach
    void setUp() {
        sessionId = UUID.randomUUID();
        hostId = UUID.randomUUID();
        quizId = UUID.randomUUID();
        session = Session.builder()
                .id(sessionId)
                .pin("ABC123")
                .hostId(hostId)
                .quizId(quizId)
                .build();
    }

    // ===== getPagedLeaderboard tests =====

    @Test
    @DisplayName("getPagedLeaderboard returns correct pagination metadata")
    void getPagedLeaderboard_returnsPaginationMetadata() {
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(sessionParticipantRepository.countRankedBySessionId(sessionId)).thenReturn(55);

        List<SessionParticipant> participants = List.of(
                createParticipant(1, "Player1", 8500, 8, 10, 5, 3200),
                createParticipant(2, "Player2", 7200, 7, 10, 4, 4100));
        Page<SessionParticipant> page = new PageImpl<>(participants, PageRequest.of(0, 20), 55);
        when(sessionParticipantRepository.findRankedBySessionId(eq(sessionId), any())).thenReturn(page);

        PagedLeaderboardResponse response = leaderboardService.getPagedLeaderboard(sessionId, 0, 20);

        assertThat(response.getSessionId()).isEqualTo(sessionId);
        assertThat(response.getCurrentPage()).isEqualTo(0);
        assertThat(response.getTotalPages()).isEqualTo(3);
        assertThat(response.getTotalParticipants()).isEqualTo(55);
        assertThat(response.getPageSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("getPagedLeaderboard maps participant entries correctly")
    void getPagedLeaderboard_mapsEntriesCorrectly() {
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(sessionParticipantRepository.countRankedBySessionId(sessionId)).thenReturn(2);

        List<SessionParticipant> participants = List.of(
                createParticipant(1, "Player1", 8500, 8, 10, 5, 3200));
        Page<SessionParticipant> page = new PageImpl<>(participants, PageRequest.of(0, 20), 2);
        when(sessionParticipantRepository.findRankedBySessionId(eq(sessionId), any())).thenReturn(page);

        PagedLeaderboardResponse response = leaderboardService.getPagedLeaderboard(sessionId, 0, 20);

        assertThat(response.getEntries()).hasSize(1);
        FinalLeaderboardEntry entry = response.getEntries().get(0);
        assertThat(entry.getRank()).isEqualTo(1);
        assertThat(entry.getNickname()).isEqualTo("Player1");
        assertThat(entry.getScore()).isEqualTo(8500);
        assertThat(entry.getCorrectAnswers()).isEqualTo(8);
        assertThat(entry.getTotalAnswers()).isEqualTo(10);
        assertThat(entry.getMaxStreak()).isEqualTo(5);
        assertThat(entry.getAvgResponseTimeSec()).isEqualTo(3.2);
    }

    @Test
    @DisplayName("getPagedLeaderboard returns empty entries for session with no ranked participants")
    void getPagedLeaderboard_emptySession() {
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(sessionParticipantRepository.countRankedBySessionId(sessionId)).thenReturn(0);

        Page<SessionParticipant> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 20), 0);
        when(sessionParticipantRepository.findRankedBySessionId(eq(sessionId), any())).thenReturn(emptyPage);

        PagedLeaderboardResponse response = leaderboardService.getPagedLeaderboard(sessionId, 0, 20);

        assertThat(response.getEntries()).isEmpty();
        assertThat(response.getTotalPages()).isEqualTo(0);
        assertThat(response.getTotalParticipants()).isEqualTo(0);
    }

    @Test
    @DisplayName("getPagedLeaderboard throws ResourceNotFoundException for non-existent session")
    void getPagedLeaderboard_sessionNotFound() {
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leaderboardService.getPagedLeaderboard(sessionId, 0, 20))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ===== getParticipantResult tests =====

    @Test
    @DisplayName("getParticipantResult returns correct self-result with per-question breakdown")
    void getParticipantResult_returnsCorrectResult() {
        UUID participantId = UUID.randomUUID();
        UUID q1Id = UUID.randomUUID();
        UUID q2Id = UUID.randomUUID();
        UUID q3Id = UUID.randomUUID();

        SessionParticipant participant = createParticipant(3, "TestPlayer", 5200, 2, 3, 2, 4100);
        participant.setId(participantId);
        participant.setAnswersTotal(3);

        SessionParticipant p1 = createParticipant(1, "Player1", 8000, 3, 3, 3, 3000);
        p1.setAnswersTotal(3);
        SessionParticipant p2 = createParticipant(2, "Player2", 6000, 2, 3, 2, 3500);
        p2.setAnswersTotal(3);

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(sessionParticipantRepository.findBySessionIdAndId(sessionId, participantId))
                .thenReturn(Optional.of(participant));
        when(sessionParticipantRepository.findBySessionIdOrderByFinalRankAsc(sessionId))
                .thenReturn(List.of(p1, p2, participant));

        List<QuestionDTO> questions = List.of(
                QuestionDTO.builder().id(q1Id).text("Q1").build(),
                QuestionDTO.builder().id(q2Id).text("Q2").build(),
                QuestionDTO.builder().id(q3Id).text("Q3").build());
        when(quizServiceClient.getQuestions(quizId, hostId)).thenReturn(questions);

        List<AnswerSubmission> submissions = List.of(
                AnswerSubmission.builder().questionId(q1Id).isCorrect(true).build(),
                AnswerSubmission.builder().questionId(q2Id).isCorrect(false).build());
        when(answerSubmissionRepository.findBySessionIdAndParticipantId(sessionId, participantId))
                .thenReturn(submissions);

        ParticipantSelfResult result = leaderboardService.getParticipantResult(sessionId, participantId);

        assertThat(result.getRank()).isEqualTo(3);
        assertThat(result.getScore()).isEqualTo(5200);
        assertThat(result.getCorrectAnswers()).isEqualTo(2);
        assertThat(result.getTotalQuestions()).isEqualTo(3);
        assertThat(result.getMaxStreak()).isEqualTo(2);
        assertThat(result.getAvgResponseTimeSec()).isEqualTo(4.1);

        // Session average = (8000 + 6000 + 5200) / 3 = 6400
        // Score difference = 5200 - 6400 = -1200
        assertThat(result.getScoreDifference()).isEqualTo(-1200);
        assertThat(result.isAboveAverage()).isFalse();

        assertThat(result.getQuestionBreakdown()).hasSize(3);
        assertThat(result.getQuestionBreakdown().get(0).getQuestionNumber()).isEqualTo(1);
        assertThat(result.getQuestionBreakdown().get(0).getStatus()).isEqualTo(AnswerStatus.CORRECT);
        assertThat(result.getQuestionBreakdown().get(1).getQuestionNumber()).isEqualTo(2);
        assertThat(result.getQuestionBreakdown().get(1).getStatus()).isEqualTo(AnswerStatus.INCORRECT);
        assertThat(result.getQuestionBreakdown().get(2).getQuestionNumber()).isEqualTo(3);
        assertThat(result.getQuestionBreakdown().get(2).getStatus()).isEqualTo(AnswerStatus.UNANSWERED);
    }

    @Test
    @DisplayName("getParticipantResult computes aboveAverage correctly when score is above average")
    void getParticipantResult_aboveAverage() {
        UUID participantId = UUID.randomUUID();

        SessionParticipant participant = createParticipant(1, "TopPlayer", 9000, 9, 10, 5, 2500);
        participant.setId(participantId);
        participant.setAnswersTotal(10);

        SessionParticipant p2 = createParticipant(2, "Player2", 3000, 3, 10, 1, 5000);
        p2.setAnswersTotal(10);

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(sessionParticipantRepository.findBySessionIdAndId(sessionId, participantId))
                .thenReturn(Optional.of(participant));
        when(sessionParticipantRepository.findBySessionIdOrderByFinalRankAsc(sessionId))
                .thenReturn(List.of(participant, p2));
        when(quizServiceClient.getQuestions(quizId, hostId)).thenReturn(Collections.emptyList());
        when(answerSubmissionRepository.findBySessionIdAndParticipantId(sessionId, participantId))
                .thenReturn(Collections.emptyList());

        ParticipantSelfResult result = leaderboardService.getParticipantResult(sessionId, participantId);

        // Session average = (9000 + 3000) / 2 = 6000
        // Score difference = 9000 - 6000 = 3000
        assertThat(result.getScoreDifference()).isEqualTo(3000);
        assertThat(result.isAboveAverage()).isTrue();
    }

    @Test
    @DisplayName("getParticipantResult excludes participants with zero answers from average")
    void getParticipantResult_excludesZeroAnswersFromAverage() {
        UUID participantId = UUID.randomUUID();

        SessionParticipant participant = createParticipant(1, "ActivePlayer", 5000, 5, 10, 3, 3000);
        participant.setId(participantId);
        participant.setAnswersTotal(10);

        SessionParticipant inactiveParticipant = createParticipant(2, "InactivePlayer", 0, 0, 0, 0, 0);
        inactiveParticipant.setAnswersTotal(0);

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(sessionParticipantRepository.findBySessionIdAndId(sessionId, participantId))
                .thenReturn(Optional.of(participant));
        when(sessionParticipantRepository.findBySessionIdOrderByFinalRankAsc(sessionId))
                .thenReturn(List.of(participant, inactiveParticipant));
        when(quizServiceClient.getQuestions(quizId, hostId)).thenReturn(Collections.emptyList());
        when(answerSubmissionRepository.findBySessionIdAndParticipantId(sessionId, participantId))
                .thenReturn(Collections.emptyList());

        ParticipantSelfResult result = leaderboardService.getParticipantResult(sessionId, participantId);

        // Only active participant counts: average = 5000 / 1 = 5000
        // Score difference = 5000 - 5000 = 0
        assertThat(result.getScoreDifference()).isEqualTo(0);
        assertThat(result.isAboveAverage()).isFalse();
    }

    @Test
    @DisplayName("getParticipantResult throws ResourceNotFoundException for non-existent session")
    void getParticipantResult_sessionNotFound() {
        UUID participantId = UUID.randomUUID();
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leaderboardService.getParticipantResult(sessionId, participantId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getParticipantResult throws ResourceNotFoundException for non-existent participant")
    void getParticipantResult_participantNotFound() {
        UUID participantId = UUID.randomUUID();
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(sessionParticipantRepository.findBySessionIdAndId(sessionId, participantId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> leaderboardService.getParticipantResult(sessionId, participantId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getParticipantResult handles all questions unanswered")
    void getParticipantResult_allUnanswered() {
        UUID participantId = UUID.randomUUID();
        UUID q1Id = UUID.randomUUID();
        UUID q2Id = UUID.randomUUID();

        SessionParticipant participant = createParticipant(1, "Player", 0, 0, 0, 0, 0);
        participant.setId(participantId);
        participant.setAnswersTotal(0);

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(sessionParticipantRepository.findBySessionIdAndId(sessionId, participantId))
                .thenReturn(Optional.of(participant));
        when(sessionParticipantRepository.findBySessionIdOrderByFinalRankAsc(sessionId))
                .thenReturn(List.of(participant));

        List<QuestionDTO> questions = List.of(
                QuestionDTO.builder().id(q1Id).text("Q1").build(),
                QuestionDTO.builder().id(q2Id).text("Q2").build());
        when(quizServiceClient.getQuestions(quizId, hostId)).thenReturn(questions);
        when(answerSubmissionRepository.findBySessionIdAndParticipantId(sessionId, participantId))
                .thenReturn(Collections.emptyList());

        ParticipantSelfResult result = leaderboardService.getParticipantResult(sessionId, participantId);

        assertThat(result.getQuestionBreakdown()).hasSize(2);
        assertThat(result.getQuestionBreakdown().get(0).getStatus()).isEqualTo(AnswerStatus.UNANSWERED);
        assertThat(result.getQuestionBreakdown().get(1).getStatus()).isEqualTo(AnswerStatus.UNANSWERED);
    }

    @Test
    @DisplayName("getParticipantResult converts avgResponseTimeMs to seconds with 1 decimal place")
    void getParticipantResult_avgResponseTimeConversion() {
        UUID participantId = UUID.randomUUID();

        // 3250ms should become 3.3 seconds (rounded to 1 decimal)
        SessionParticipant participant = createParticipant(1, "Player", 5000, 5, 10, 3, 3250);
        participant.setId(participantId);
        participant.setAnswersTotal(10);

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(sessionParticipantRepository.findBySessionIdAndId(sessionId, participantId))
                .thenReturn(Optional.of(participant));
        when(sessionParticipantRepository.findBySessionIdOrderByFinalRankAsc(sessionId))
                .thenReturn(List.of(participant));
        when(quizServiceClient.getQuestions(quizId, hostId)).thenReturn(Collections.emptyList());
        when(answerSubmissionRepository.findBySessionIdAndParticipantId(sessionId, participantId))
                .thenReturn(Collections.emptyList());

        ParticipantSelfResult result = leaderboardService.getParticipantResult(sessionId, participantId);

        assertThat(result.getAvgResponseTimeSec()).isEqualTo(3.3);
    }

    // ===== Helper methods =====

    private SessionParticipant createParticipant(int rank, String nickname, int score,
                                                  int correct, int total, int streak, int avgTimeMs) {
        return SessionParticipant.builder()
                .session(session)
                .nickname(nickname)
                .finalRank(rank)
                .finalScore(score)
                .answersCorrect(correct)
                .answersTotal(total)
                .maxStreak(streak)
                .avgResponseTimeMs(avgTimeMs)
                .joinedAt(Instant.now())
                .build();
    }
}
