package com.quizplatform.analytics.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizplatform.analytics.dto.ScoreAwardedEvent;
import com.quizplatform.analytics.model.ScoreEvent;
import com.quizplatform.analytics.repository.ScoreEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScoreEventConsumerTest {

    @Mock
    private ScoreEventRepository scoreEventRepository;

    private ObjectMapper objectMapper;

    private ScoreEventConsumer consumer;

    @Captor
    private ArgumentCaptor<ScoreEvent> scoreEventCaptor;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        consumer = new ScoreEventConsumer(scoreEventRepository, objectMapper);
    }

    @Nested
    @DisplayName("Successful event consumption")
    class SuccessfulConsumption {

        @Test
        @DisplayName("Persists all entries from a valid score.awarded event")
        void persistsAllEntries() throws JsonProcessingException {
            UUID sessionId = UUID.randomUUID();
            UUID participant1 = UUID.randomUUID();
            UUID participant2 = UUID.randomUUID();

            ScoreAwardedEvent event =
                    ScoreAwardedEvent.builder()
                            .sessionId(sessionId.toString())
                            .roundNumber(2)
                            .timestamp(System.currentTimeMillis())
                            .entries(
                                    List.of(
                                            ScoreAwardedEvent.ScoreAwardedEntry.builder()
                                                    .participantId(participant1.toString())
                                                    .roundScore(920)
                                                    .cumulativeScore(4500)
                                                    .rank(1)
                                                    .rankDelta(2)
                                                    .streakCount(4)
                                                    .streakMultiplier(2)
                                                    .timeTakenMs(2340L)
                                                    .correct(true)
                                                    .build(),
                                            ScoreAwardedEvent.ScoreAwardedEntry.builder()
                                                    .participantId(participant2.toString())
                                                    .roundScore(0)
                                                    .cumulativeScore(3200)
                                                    .rank(2)
                                                    .rankDelta(-1)
                                                    .streakCount(0)
                                                    .streakMultiplier(1)
                                                    .timeTakenMs(null)
                                                    .correct(false)
                                                    .build()))
                            .build();

            String message = objectMapper.writeValueAsString(event);
            when(scoreEventRepository.save(any(ScoreEvent.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            consumer.consumeScoreAwardedEvent(message);

            verify(scoreEventRepository, times(2)).save(scoreEventCaptor.capture());
            List<ScoreEvent> savedEvents = scoreEventCaptor.getAllValues();

            ScoreEvent first = savedEvents.get(0);
            assertThat(first.getSessionId()).isEqualTo(sessionId);
            assertThat(first.getRoundNumber()).isEqualTo(2);
            assertThat(first.getParticipantId()).isEqualTo(participant1);
            assertThat(first.getRoundScore()).isEqualTo(920);
            assertThat(first.getCumulativeScore()).isEqualTo(4500);
            assertThat(first.getRank()).isEqualTo(1);
            assertThat(first.getRankDelta()).isEqualTo(2);
            assertThat(first.getStreakCount()).isEqualTo(4);
            assertThat(first.getStreakMultiplier()).isEqualTo(2);
            assertThat(first.getTimeTakenMs()).isEqualTo(2340L);
            assertThat(first.isCorrect()).isTrue();

            ScoreEvent second = savedEvents.get(1);
            assertThat(second.getSessionId()).isEqualTo(sessionId);
            assertThat(second.getParticipantId()).isEqualTo(participant2);
            assertThat(second.getRoundScore()).isEqualTo(0);
            assertThat(second.getCumulativeScore()).isEqualTo(3200);
            assertThat(second.getRank()).isEqualTo(2);
            assertThat(second.getRankDelta()).isEqualTo(-1);
            assertThat(second.getTimeTakenMs()).isNull();
            assertThat(second.isCorrect()).isFalse();
        }

        @Test
        @DisplayName("Persists event with zero round number")
        void persistsEventWithZeroRound() throws JsonProcessingException {
            UUID sessionId = UUID.randomUUID();
            UUID participantId = UUID.randomUUID();

            ScoreAwardedEvent event =
                    ScoreAwardedEvent.builder()
                            .sessionId(sessionId.toString())
                            .roundNumber(0)
                            .timestamp(System.currentTimeMillis())
                            .entries(
                                    List.of(
                                            ScoreAwardedEvent.ScoreAwardedEntry.builder()
                                                    .participantId(participantId.toString())
                                                    .roundScore(1000)
                                                    .cumulativeScore(1000)
                                                    .rank(1)
                                                    .rankDelta(0)
                                                    .streakCount(1)
                                                    .streakMultiplier(1)
                                                    .timeTakenMs(500L)
                                                    .correct(true)
                                                    .build()))
                            .build();

            String message = objectMapper.writeValueAsString(event);
            when(scoreEventRepository.save(any(ScoreEvent.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            consumer.consumeScoreAwardedEvent(message);

            verify(scoreEventRepository, times(1)).save(scoreEventCaptor.capture());
            ScoreEvent saved = scoreEventCaptor.getValue();
            assertThat(saved.getRoundNumber()).isEqualTo(0);
            assertThat(saved.getRankDelta()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Duplicate event handling")
    class DuplicateHandling {

        @Test
        @DisplayName("Handles duplicate events gracefully without throwing")
        void handlesDuplicateGracefully() throws JsonProcessingException {
            UUID sessionId = UUID.randomUUID();
            UUID participantId = UUID.randomUUID();

            ScoreAwardedEvent event =
                    ScoreAwardedEvent.builder()
                            .sessionId(sessionId.toString())
                            .roundNumber(1)
                            .timestamp(System.currentTimeMillis())
                            .entries(
                                    List.of(
                                            ScoreAwardedEvent.ScoreAwardedEntry.builder()
                                                    .participantId(participantId.toString())
                                                    .roundScore(800)
                                                    .cumulativeScore(800)
                                                    .rank(1)
                                                    .rankDelta(0)
                                                    .streakCount(1)
                                                    .streakMultiplier(1)
                                                    .timeTakenMs(1500L)
                                                    .correct(true)
                                                    .build()))
                            .build();

            String message = objectMapper.writeValueAsString(event);
            when(scoreEventRepository.save(any(ScoreEvent.class)))
                    .thenThrow(new DataIntegrityViolationException("Duplicate key"));

            // Should not throw — duplicates are handled gracefully
            consumer.consumeScoreAwardedEvent(message);

            verify(scoreEventRepository, times(1)).save(any(ScoreEvent.class));
        }

        @Test
        @DisplayName("Continues processing remaining entries when one is a duplicate")
        void continuesAfterDuplicate() throws JsonProcessingException {
            UUID sessionId = UUID.randomUUID();
            UUID participant1 = UUID.randomUUID();
            UUID participant2 = UUID.randomUUID();

            ScoreAwardedEvent event =
                    ScoreAwardedEvent.builder()
                            .sessionId(sessionId.toString())
                            .roundNumber(1)
                            .timestamp(System.currentTimeMillis())
                            .entries(
                                    List.of(
                                            ScoreAwardedEvent.ScoreAwardedEntry.builder()
                                                    .participantId(participant1.toString())
                                                    .roundScore(800)
                                                    .cumulativeScore(800)
                                                    .rank(1)
                                                    .rankDelta(0)
                                                    .streakCount(1)
                                                    .streakMultiplier(1)
                                                    .timeTakenMs(1500L)
                                                    .correct(true)
                                                    .build(),
                                            ScoreAwardedEvent.ScoreAwardedEntry.builder()
                                                    .participantId(participant2.toString())
                                                    .roundScore(700)
                                                    .cumulativeScore(700)
                                                    .rank(2)
                                                    .rankDelta(0)
                                                    .streakCount(1)
                                                    .streakMultiplier(1)
                                                    .timeTakenMs(2000L)
                                                    .correct(true)
                                                    .build()))
                            .build();

            String message = objectMapper.writeValueAsString(event);
            when(scoreEventRepository.save(any(ScoreEvent.class)))
                    .thenThrow(new DataIntegrityViolationException("Duplicate key"))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            consumer.consumeScoreAwardedEvent(message);

            verify(scoreEventRepository, times(2)).save(any(ScoreEvent.class));
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("Handles invalid JSON gracefully without throwing")
        void handlesInvalidJson() {
            String invalidMessage = "not valid json {{{";

            // Should not throw
            consumer.consumeScoreAwardedEvent(invalidMessage);

            verify(scoreEventRepository, never()).save(any());
        }

        @Test
        @DisplayName("Handles event with empty entries list")
        void handlesEmptyEntries() throws JsonProcessingException {
            ScoreAwardedEvent event =
                    ScoreAwardedEvent.builder()
                            .sessionId(UUID.randomUUID().toString())
                            .roundNumber(1)
                            .timestamp(System.currentTimeMillis())
                            .entries(List.of())
                            .build();

            String message = objectMapper.writeValueAsString(event);

            consumer.consumeScoreAwardedEvent(message);

            verify(scoreEventRepository, never()).save(any());
        }

        @Test
        @DisplayName("Handles event with null entries")
        void handlesNullEntries() throws JsonProcessingException {
            ScoreAwardedEvent event =
                    ScoreAwardedEvent.builder()
                            .sessionId(UUID.randomUUID().toString())
                            .roundNumber(1)
                            .timestamp(System.currentTimeMillis())
                            .entries(null)
                            .build();

            String message = objectMapper.writeValueAsString(event);

            consumer.consumeScoreAwardedEvent(message);

            verify(scoreEventRepository, never()).save(any());
        }

        @Test
        @DisplayName("Handles invalid session ID gracefully")
        void handlesInvalidSessionId() throws JsonProcessingException {
            ScoreAwardedEvent event =
                    ScoreAwardedEvent.builder()
                            .sessionId("not-a-uuid")
                            .roundNumber(1)
                            .timestamp(System.currentTimeMillis())
                            .entries(
                                    List.of(
                                            ScoreAwardedEvent.ScoreAwardedEntry.builder()
                                                    .participantId(UUID.randomUUID().toString())
                                                    .roundScore(100)
                                                    .cumulativeScore(100)
                                                    .rank(1)
                                                    .rankDelta(0)
                                                    .streakCount(0)
                                                    .streakMultiplier(1)
                                                    .timeTakenMs(1000L)
                                                    .correct(true)
                                                    .build()))
                            .build();

            String message = objectMapper.writeValueAsString(event);

            consumer.consumeScoreAwardedEvent(message);

            verify(scoreEventRepository, never()).save(any());
        }

        @Test
        @DisplayName("Skips entry with invalid participant ID but processes others")
        void skipsInvalidParticipantId() throws JsonProcessingException {
            UUID sessionId = UUID.randomUUID();
            UUID validParticipant = UUID.randomUUID();

            ScoreAwardedEvent event =
                    ScoreAwardedEvent.builder()
                            .sessionId(sessionId.toString())
                            .roundNumber(1)
                            .timestamp(System.currentTimeMillis())
                            .entries(
                                    List.of(
                                            ScoreAwardedEvent.ScoreAwardedEntry.builder()
                                                    .participantId("invalid-uuid")
                                                    .roundScore(100)
                                                    .cumulativeScore(100)
                                                    .rank(1)
                                                    .rankDelta(0)
                                                    .streakCount(0)
                                                    .streakMultiplier(1)
                                                    .timeTakenMs(1000L)
                                                    .correct(true)
                                                    .build(),
                                            ScoreAwardedEvent.ScoreAwardedEntry.builder()
                                                    .participantId(validParticipant.toString())
                                                    .roundScore(200)
                                                    .cumulativeScore(200)
                                                    .rank(2)
                                                    .rankDelta(0)
                                                    .streakCount(0)
                                                    .streakMultiplier(1)
                                                    .timeTakenMs(2000L)
                                                    .correct(true)
                                                    .build()))
                            .build();

            String message = objectMapper.writeValueAsString(event);
            when(scoreEventRepository.save(any(ScoreEvent.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            consumer.consumeScoreAwardedEvent(message);

            verify(scoreEventRepository, times(1)).save(scoreEventCaptor.capture());
            ScoreEvent saved = scoreEventCaptor.getValue();
            assertThat(saved.getParticipantId()).isEqualTo(validParticipant);
        }
    }
}
