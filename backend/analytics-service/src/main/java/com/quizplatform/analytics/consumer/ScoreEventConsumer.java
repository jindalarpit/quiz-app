package com.quizplatform.analytics.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizplatform.analytics.dto.ScoreAwardedEvent;
import com.quizplatform.analytics.model.ScoreEvent;
import com.quizplatform.analytics.repository.ScoreEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Kafka consumer for score.awarded events published by session-service.
 * Persists score event data to the score_events PostgreSQL table.
 * Handles duplicate events gracefully using the unique constraint on
 * (session_id, round_number, participant_id).
 *
 * Requirements: 5.6
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScoreEventConsumer {

    private final ScoreEventRepository scoreEventRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${kafka.topics.score-awarded:score.awarded}",
            groupId = "${spring.kafka.consumer.group-id:analytics-service}")
    public void consumeScoreAwardedEvent(String message) {
        log.debug("Received score.awarded event: {}", message);

        ScoreAwardedEvent event;
        try {
            event = objectMapper.readValue(message, ScoreAwardedEvent.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize score.awarded event: {}", message, e);
            return;
        }

        if (event.getEntries() == null || event.getEntries().isEmpty()) {
            log.warn(
                    "Received score.awarded event with no entries for session={}, round={}",
                    event.getSessionId(),
                    event.getRoundNumber());
            return;
        }

        UUID sessionId;
        try {
            sessionId = UUID.fromString(event.getSessionId());
        } catch (IllegalArgumentException e) {
            log.error("Invalid session ID in score.awarded event: {}", event.getSessionId(), e);
            return;
        }

        List<ScoreEvent> scoreEvents = new ArrayList<>();
        for (ScoreAwardedEvent.ScoreAwardedEntry entry : event.getEntries()) {
            UUID participantId;
            try {
                participantId = UUID.fromString(entry.getParticipantId());
            } catch (IllegalArgumentException e) {
                log.error(
                        "Invalid participant ID in score.awarded event: {}",
                        entry.getParticipantId(),
                        e);
                continue;
            }

            ScoreEvent scoreEvent =
                    ScoreEvent.builder()
                            .sessionId(sessionId)
                            .roundNumber(event.getRoundNumber())
                            .participantId(participantId)
                            .roundScore(entry.getRoundScore())
                            .cumulativeScore(entry.getCumulativeScore())
                            .rank(entry.getRank())
                            .rankDelta(entry.getRankDelta())
                            .streakCount(entry.getStreakCount())
                            .streakMultiplier(entry.getStreakMultiplier())
                            .timeTakenMs(entry.getTimeTakenMs())
                            .isCorrect(entry.isCorrect())
                            .build();

            scoreEvents.add(scoreEvent);
        }

        persistScoreEvents(scoreEvents, sessionId, event.getRoundNumber());
    }

    private void persistScoreEvents(List<ScoreEvent> scoreEvents, UUID sessionId, int roundNumber) {
        for (ScoreEvent scoreEvent : scoreEvents) {
            try {
                scoreEventRepository.save(scoreEvent);
            } catch (DataIntegrityViolationException e) {
                // Duplicate event — unique constraint on (session_id, round_number, participant_id)
                // This is expected in at-least-once delivery scenarios; log and skip.
                log.info(
                        "Duplicate score event ignored for session={}, round={}, participant={}",
                        sessionId,
                        roundNumber,
                        scoreEvent.getParticipantId());
            }
        }

        log.debug(
                "Persisted {} score events for session={}, round={}",
                scoreEvents.size(),
                sessionId,
                roundNumber);
    }
}
