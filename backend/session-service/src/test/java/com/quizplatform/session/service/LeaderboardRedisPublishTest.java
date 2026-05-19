package com.quizplatform.session.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizplatform.session.client.QuizServiceClient;
import com.quizplatform.session.model.Session;
import com.quizplatform.session.model.SessionParticipant;
import com.quizplatform.session.model.SessionStatus;
import com.quizplatform.session.repository.AnswerSubmissionRepository;
import com.quizplatform.session.repository.SessionParticipantRepository;
import com.quizplatform.session.repository.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests that LeaderboardService publishes the SESSION_ENDED event to Redis
 * with the correct channel and leaderboard payload structure.
 *
 * This verifies the session-service side of the WebSocket wiring:
 * - Publishes to the correct Redis channel (session:{pin}:broadcast)
 * - Includes the full leaderboard payload with all required fields
 * - Event type is "session.ended" which the WebSocket service routes to broadcastLeaderboardUpdate
 *
 * Validates Requirements 1.1 and 7.1.
 */
@ExtendWith(MockitoExtension.class)
class LeaderboardRedisPublishTest {

    @Mock private SessionRepository sessionRepository;
    @Mock private SessionParticipantRepository sessionParticipantRepository;
    @Mock private AnswerSubmissionRepository answerSubmissionRepository;
    @Mock private QuizServiceClient quizServiceClient;
    @Mock private StringRedisTemplate redisTemplate;

    private LeaderboardService leaderboardService;
    private ObjectMapper objectMapper;

    private static final String PIN = "XYZ789";
    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final UUID HOST_ID = UUID.randomUUID();
    private static final UUID QUIZ_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        leaderboardService = new LeaderboardService(
                sessionRepository,
                sessionParticipantRepository,
                answerSubmissionRepository,
                quizServiceClient,
                redisTemplate,
                objectMapper
        );
    }

    @Test
    @DisplayName("computeAndPersistFinalRankings publishes session.ended event to correct Redis channel")
    void publishesToCorrectChannel() {
        // Arrange
        Session session = createSession();
        List<SessionParticipant> participants = List.of(
                createParticipant(session, "Player1", 8500, 8, 10, 5, 3200, Instant.now().minusSeconds(10)),
                createParticipant(session, "Player2", 7200, 7, 10, 4, 4100, Instant.now().minusSeconds(5))
        );

        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(sessionParticipantRepository.findBySessionId(SESSION_ID)).thenReturn(participants);

        // Act
        leaderboardService.computeAndPersistFinalRankings(SESSION_ID);

        // Assert - published to the correct channel
        ArgumentCaptor<String> channelCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(channelCaptor.capture(), messageCaptor.capture());

        assertThat(channelCaptor.getValue()).isEqualTo("session:" + PIN + ":broadcast");
    }

    @Test
    @DisplayName("Published event has type 'session.ended' for WebSocket service routing")
    void publishedEventHasCorrectType() throws Exception {
        // Arrange
        Session session = createSession();
        List<SessionParticipant> participants = List.of(
                createParticipant(session, "Player1", 5000, 5, 10, 3, 3000, Instant.now())
        );

        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(sessionParticipantRepository.findBySessionId(SESSION_ID)).thenReturn(participants);

        // Act
        leaderboardService.computeAndPersistFinalRankings(SESSION_ID);

        // Assert
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(anyString(), messageCaptor.capture());

        JsonNode event = objectMapper.readTree(messageCaptor.getValue());
        assertThat(event.get("type").asText()).isEqualTo("session.ended");
    }

    @Test
    @DisplayName("Published event includes sessionId in payload")
    void publishedEventIncludesSessionId() throws Exception {
        // Arrange
        Session session = createSession();
        List<SessionParticipant> participants = List.of(
                createParticipant(session, "Player1", 5000, 5, 10, 3, 3000, Instant.now())
        );

        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(sessionParticipantRepository.findBySessionId(SESSION_ID)).thenReturn(participants);

        // Act
        leaderboardService.computeAndPersistFinalRankings(SESSION_ID);

        // Assert
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(anyString(), messageCaptor.capture());

        JsonNode event = objectMapper.readTree(messageCaptor.getValue());
        assertThat(event.get("payload").get("sessionId").asText()).isEqualTo(SESSION_ID.toString());
    }

    @Test
    @DisplayName("Published leaderboard payload contains all required fields per entry")
    void publishedLeaderboardContainsAllFields() throws Exception {
        // Arrange
        Session session = createSession();
        List<SessionParticipant> participants = List.of(
                createParticipant(session, "TopPlayer", 9500, 9, 10, 7, 2100, Instant.now().minusSeconds(20)),
                createParticipant(session, "SecondPlayer", 8200, 8, 10, 5, 3400, Instant.now().minusSeconds(15)),
                createParticipant(session, "ThirdPlayer", 7100, 7, 10, 4, 4200, Instant.now().minusSeconds(10))
        );

        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(sessionParticipantRepository.findBySessionId(SESSION_ID)).thenReturn(participants);

        // Act
        leaderboardService.computeAndPersistFinalRankings(SESSION_ID);

        // Assert
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(anyString(), messageCaptor.capture());

        JsonNode event = objectMapper.readTree(messageCaptor.getValue());
        JsonNode leaderboard = event.get("payload").get("leaderboard");

        assertThat(leaderboard.isArray()).isTrue();
        assertThat(leaderboard.size()).isEqualTo(3);

        // Verify first entry (highest score) has all required fields
        JsonNode firstEntry = leaderboard.get(0);
        assertThat(firstEntry.get("rank").asInt()).isEqualTo(1);
        assertThat(firstEntry.get("nickname").asText()).isEqualTo("TopPlayer");
        assertThat(firstEntry.get("score").asInt()).isEqualTo(9500);
        assertThat(firstEntry.get("correctAnswers").asInt()).isEqualTo(9);
        assertThat(firstEntry.get("totalAnswers").asInt()).isEqualTo(10);
        assertThat(firstEntry.get("maxStreak").asInt()).isEqualTo(7);
        assertThat(firstEntry.get("avgResponseTimeSec").asDouble()).isEqualTo(2.1);

        // Verify ranking order is correct (score descending)
        assertThat(leaderboard.get(0).get("rank").asInt()).isEqualTo(1);
        assertThat(leaderboard.get(1).get("rank").asInt()).isEqualTo(2);
        assertThat(leaderboard.get(2).get("rank").asInt()).isEqualTo(3);
    }

    @Test
    @DisplayName("Empty participant list still publishes session.ended with empty leaderboard")
    void emptyParticipants_publishesEmptyLeaderboard() throws Exception {
        // Arrange
        Session session = createSession();
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(sessionParticipantRepository.findBySessionId(SESSION_ID)).thenReturn(List.of());

        // Act
        leaderboardService.computeAndPersistFinalRankings(SESSION_ID);

        // Assert
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(anyString(), messageCaptor.capture());

        JsonNode event = objectMapper.readTree(messageCaptor.getValue());
        assertThat(event.get("type").asText()).isEqualTo("session.ended");
        assertThat(event.get("payload").get("leaderboard").size()).isEqualTo(0);
    }

    @Test
    @DisplayName("Persistence failure publishes session.persistence_failed event instead")
    void persistenceFailure_publishesFailureEvent() throws Exception {
        // Arrange
        Session session = createSession();
        List<SessionParticipant> participants = List.of(
                createParticipant(session, "Player1", 5000, 5, 10, 3, 3000, Instant.now())
        );

        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(sessionParticipantRepository.findBySessionId(SESSION_ID)).thenReturn(participants);

        // Simulate persistence failure on all retry attempts
        doThrow(new RuntimeException("Database unavailable"))
                .when(sessionParticipantRepository).saveAll(any());

        // Act
        leaderboardService.computeAndPersistFinalRankings(SESSION_ID);

        // Assert - persistence_failed event published (not session.ended)
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(anyString(), messageCaptor.capture());

        JsonNode event = objectMapper.readTree(messageCaptor.getValue());
        assertThat(event.get("type").asText()).isEqualTo("session.persistence_failed");
        assertThat(event.get("payload").get("error").asText())
                .contains("Results could not be saved");
    }

    private Session createSession() {
        return Session.builder()
                .id(SESSION_ID)
                .pin(PIN)
                .hostId(HOST_ID)
                .quizId(QUIZ_ID)
                .status(SessionStatus.ENDED)
                .startedAt(Instant.now().minusSeconds(300))
                .endedAt(Instant.now())
                .participantCount(3)
                .build();
    }

    private SessionParticipant createParticipant(Session session, String nickname, int score,
                                                  int correct, int total, int streak,
                                                  int avgTimeMs, Instant lastAnswerAt) {
        return SessionParticipant.builder()
                .session(session)
                .nickname(nickname)
                .finalScore(score)
                .answersCorrect(correct)
                .answersTotal(total)
                .maxStreak(streak)
                .avgResponseTimeMs(avgTimeMs)
                .lastAnswerAt(lastAnswerAt)
                .joinedAt(Instant.now().minusSeconds(600))
                .build();
    }
}
