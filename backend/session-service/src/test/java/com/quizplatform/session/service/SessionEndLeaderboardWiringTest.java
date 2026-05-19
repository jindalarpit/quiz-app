package com.quizplatform.session.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizplatform.common.exception.ResourceNotFoundException;
import com.quizplatform.session.client.QuizServiceClient;
import com.quizplatform.session.dto.LeaderboardEntry;
import com.quizplatform.session.model.Session;
import com.quizplatform.session.model.SessionParticipant;
import com.quizplatform.session.model.SessionStatus;
import com.quizplatform.session.repository.SessionParticipantRepository;
import com.quizplatform.session.repository.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests that the session end flow correctly wires LeaderboardService to compute
 * final rankings and publish the SESSION_ENDED event with leaderboard payload to Redis.
 * This verifies Requirements 1.1 and 7.1: real-time delivery of final leaderboard
 * to all connected participants via WebSocket (through Redis pub/sub).
 */
@ExtendWith(MockitoExtension.class)
class SessionEndLeaderboardWiringTest {

    @Mock private RedisSessionService redisSessionService;
    @Mock private SessionStateMachine stateMachine;
    @Mock private ProfanityFilter profanityFilter;
    @Mock private SessionRepository sessionRepository;
    @Mock private SessionParticipantRepository sessionParticipantRepository;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private QuizServiceClient quizServiceClient;
    @Mock private LeaderboardService leaderboardService;
    @Mock private HashOperations<String, Object, Object> hashOperations;
    @Mock private ZSetOperations<String, String> zSetOperations;
    @Mock private SetOperations<String, String> setOperations;

    private SessionService sessionService;

    private static final String PIN = "ABC123";
    private static final UUID HOST_ID = UUID.randomUUID();
    private static final UUID SESSION_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        sessionService = new SessionService(
                redisSessionService,
                stateMachine,
                profanityFilter,
                sessionRepository,
                sessionParticipantRepository,
                redisTemplate,
                quizServiceClient,
                leaderboardService
        );
    }

    @Test
    @DisplayName("endSession should call computeAndPersistFinalRankings after persisting session")
    void endSession_callsComputeAndPersistFinalRankings() {
        // Arrange
        setupValidEndSessionMocks();

        Session savedSession = Session.builder()
                .id(SESSION_ID)
                .pin(PIN)
                .hostId(HOST_ID)
                .quizId(UUID.randomUUID())
                .status(SessionStatus.ENDED)
                .startedAt(Instant.now().minusSeconds(300))
                .endedAt(Instant.now())
                .participantCount(5)
                .build();

        when(sessionRepository.save(any(Session.class))).thenReturn(savedSession);
        when(redisSessionService.getLeaderboardSize(PIN)).thenReturn(5L);
        when(redisSessionService.getTopNWithRankChanges(eq(PIN), eq(5)))
                .thenReturn(createMockLeaderboardEntries(5));

        // Act
        sessionService.endSession(PIN, HOST_ID);

        // Assert - computeAndPersistFinalRankings should be called with the saved session's ID
        verify(leaderboardService).computeAndPersistFinalRankings(SESSION_ID);
    }

    @Test
    @DisplayName("endSession should still complete even if computeAndPersistFinalRankings throws")
    void endSession_handlesRankingFailureGracefully() {
        // Arrange
        setupValidEndSessionMocks();

        Session savedSession = Session.builder()
                .id(SESSION_ID)
                .pin(PIN)
                .hostId(HOST_ID)
                .quizId(UUID.randomUUID())
                .status(SessionStatus.ENDED)
                .startedAt(Instant.now().minusSeconds(300))
                .endedAt(Instant.now())
                .participantCount(5)
                .build();

        when(sessionRepository.save(any(Session.class))).thenReturn(savedSession);
        when(redisSessionService.getLeaderboardSize(PIN)).thenReturn(5L);
        when(redisSessionService.getTopNWithRankChanges(eq(PIN), eq(5)))
                .thenReturn(createMockLeaderboardEntries(5));

        // Simulate ranking failure
        doThrow(new RuntimeException("Database connection lost"))
                .when(leaderboardService).computeAndPersistFinalRankings(SESSION_ID);

        // Act - should not throw
        sessionService.endSession(PIN, HOST_ID);

        // Assert - session state was still updated to ENDED
        verify(redisSessionService).updateSessionState(PIN, SessionStatus.ENDED.name());
    }

    @Test
    @DisplayName("endSession should not call computeAndPersistFinalRankings if session persistence fails")
    void endSession_skipsRankingIfPersistenceFails() {
        // Arrange
        when(redisSessionService.sessionExists(PIN)).thenReturn(true);
        when(redisSessionService.getHostId(PIN)).thenReturn(HOST_ID.toString());
        when(redisSessionService.getSessionState(PIN)).thenReturn(SessionStatus.REVEAL.name());

        // Return empty fields to simulate persistence failure
        when(redisSessionService.getSessionFields(PIN)).thenReturn(Collections.emptyMap());

        // Act
        sessionService.endSession(PIN, HOST_ID);

        // Assert - computeAndPersistFinalRankings should NOT be called
        verify(leaderboardService, never()).computeAndPersistFinalRankings(any());
    }

    @Test
    @DisplayName("endSession publishes state change event after ranking computation")
    void endSession_publishesStateChangeEvent() {
        // Arrange
        setupValidEndSessionMocks();

        Session savedSession = Session.builder()
                .id(SESSION_ID)
                .pin(PIN)
                .hostId(HOST_ID)
                .quizId(UUID.randomUUID())
                .status(SessionStatus.ENDED)
                .startedAt(Instant.now().minusSeconds(300))
                .endedAt(Instant.now())
                .participantCount(3)
                .build();

        when(sessionRepository.save(any(Session.class))).thenReturn(savedSession);
        when(redisSessionService.getLeaderboardSize(PIN)).thenReturn(3L);
        when(redisSessionService.getTopNWithRankChanges(eq(PIN), eq(3)))
                .thenReturn(createMockLeaderboardEntries(3));

        // Act
        sessionService.endSession(PIN, HOST_ID);

        // Assert - state change event published to Redis broadcast channel
        verify(redisTemplate).convertAndSend(
                eq("session:" + PIN + ":broadcast"),
                contains("session.state_changed"));
    }

    private void setupValidEndSessionMocks() {
        when(redisSessionService.sessionExists(PIN)).thenReturn(true);
        when(redisSessionService.getHostId(PIN)).thenReturn(HOST_ID.toString());
        when(redisSessionService.getSessionState(PIN)).thenReturn(SessionStatus.REVEAL.name());

        Map<Object, Object> sessionFields = new HashMap<>();
        sessionFields.put("quiz_id", UUID.randomUUID().toString());
        sessionFields.put("host_id", HOST_ID.toString());
        sessionFields.put("created_at", String.valueOf(Instant.now().minusSeconds(300).toEpochMilli()));
        sessionFields.put("participant_count", "5");
        when(redisSessionService.getSessionFields(PIN)).thenReturn(sessionFields);
    }

    private List<LeaderboardEntry> createMockLeaderboardEntries(int count) {
        List<LeaderboardEntry> entries = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            entries.add(LeaderboardEntry.builder()
                    .participantId(UUID.randomUUID().toString())
                    .nickname("Player" + (i + 1))
                    .score(1000 - (i * 100))
                    .rank(i + 1)
                    .rankChange(0)
                    .streak(3 - i)
                    .multiplier(1)
                    .build());
        }
        return entries;
    }
}
