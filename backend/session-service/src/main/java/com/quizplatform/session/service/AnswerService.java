package com.quizplatform.session.service;

import com.quizplatform.common.exception.DuplicateResourceException;
import com.quizplatform.common.exception.ResourceNotFoundException;
import com.quizplatform.common.exception.ValidationException;
import com.quizplatform.session.dto.AnswerResult;
import com.quizplatform.session.dto.AnswerSubmitRequest;
import com.quizplatform.session.dto.LeaderboardEntry;
import com.quizplatform.session.dto.RevealResult;
import com.quizplatform.session.model.SessionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Service handling answer submission, validation, scoring, and reveal logic.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnswerService {

    private static final long TIMER_GRACE_PERIOD_MS = 500;
    private static final int DEFAULT_BASE_POINTS = 1000;

    private final RedisSessionService redisSessionService;
    private final ScoreCalculator scoreCalculator;
    private final AntiCheatService antiCheatService;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    /**
     * Submit an answer for a participant.
     *
     * Validates:
     * - Session is in QUESTION_OPEN state
     * - Answer is a valid (non-empty) option
     * - No duplicate submission (HSETNX)
     * - Server-side timer enforcement (reject if > 500ms after expiry)
     *
     * Then calculates score and updates leaderboard.
     */
    public AnswerResult submitAnswer(String pin, AnswerSubmitRequest request) {
        // 1. Validate session exists
        if (!redisSessionService.sessionExists(pin)) {
            throw new ResourceNotFoundException("Session", pin);
        }

        // 2. Check if participant has been kicked
        if (redisSessionService.isParticipantKicked(pin, request.getParticipantId())) {
            int questionIndex = redisSessionService.getCurrentQuestionIndex(pin);
            redisSessionService.logAuditEvent(pin, request.getParticipantId(), questionIndex,
                    request.getAnswer(), Instant.now().toEpochMilli(), false, "participant_kicked");
            throw new ValidationException("You have been removed from this session");
        }

        // 3. Validate session is in QUESTION_OPEN state
        String state = redisSessionService.getSessionState(pin);
        if (!SessionStatus.QUESTION_OPEN.name().equals(state)) {
            int questionIndex = redisSessionService.getCurrentQuestionIndex(pin);
            redisSessionService.logAuditEvent(pin, request.getParticipantId(), questionIndex,
                    request.getAnswer(), Instant.now().toEpochMilli(), false, "invalid_state");
            throw new ValidationException("Session is not accepting answers (current state: " + state + ")");
        }

        // 4. Validate answer is not empty
        String answer = request.getAnswer();
        if (answer == null || answer.isBlank()) {
            throw new ValidationException("Answer cannot be empty");
        }

        // 5. Enforce server-side timer
        long now = Instant.now().toEpochMilli();
        long questionStartTime = redisSessionService.getQuestionStartTime(pin);
        int questionDurationMs = redisSessionService.getQuestionDurationMs(pin);

        if (questionStartTime > 0) {
            long deadline = questionStartTime + questionDurationMs + TIMER_GRACE_PERIOD_MS;
            if (now > deadline) {
                int questionIndex = redisSessionService.getCurrentQuestionIndex(pin);
                redisSessionService.logAuditEvent(pin, request.getParticipantId(), questionIndex,
                        answer, now, false, "time_expired");
                throw new ValidationException("Answer rejected: time has expired");
            }
        }

        // 6. Get current question index (already incremented by advanceToNextQuestion, so subtract 1)
        int questionIndex = redisSessionService.getCurrentQuestionIndex(pin) - 1;

        // 7. Check for duplicate submission using HSETNX
        long submissionTimestamp = now;
        String correctAnswer = redisSessionService.getCorrectAnswer(pin, questionIndex);
        boolean isCorrect = correctAnswer != null && answer.equalsIgnoreCase(correctAnswer);

        // Get current streak before scoring
        int currentStreak = redisSessionService.getStreak(pin, request.getParticipantId());

        // Calculate score with streak
        int score = 0;
        int newStreak;
        int newMultiplier;

        if (isCorrect) {
            // Increment streak
            newStreak = currentStreak + 1;
            newMultiplier = scoreCalculator.getStreakMultiplier(newStreak);

            if (questionStartTime > 0) {
                long timeTakenMs = submissionTimestamp - questionStartTime;
                score = scoreCalculator.calculateScoreWithStreak(
                        DEFAULT_BASE_POINTS, timeTakenMs, questionDurationMs, newStreak);
            }

            // Update streak and multiplier in Redis
            redisSessionService.updateStreak(pin, request.getParticipantId(), newStreak, newMultiplier);
        } else {
            // Reset streak on incorrect answer
            newStreak = 0;
            newMultiplier = 1;
            redisSessionService.resetStreak(pin, request.getParticipantId());
        }

        // Build the answer value: {answer}|{timestamp}|{score}
        String answerValue = answer + "|" + submissionTimestamp + "|" + score;

        // Atomic duplicate prevention
        boolean stored = redisSessionService.storeAnswerIfAbsent(
                pin, questionIndex, request.getParticipantId(), answerValue);

        if (!stored) {
            redisSessionService.logAuditEvent(pin, request.getParticipantId(), questionIndex,
                    answer, submissionTimestamp, false, "duplicate_submission");
            throw new DuplicateResourceException(
                    "Answer already submitted for this question by participant " + request.getParticipantId());
        }

        // 8. Update leaderboard if score > 0
        if (score > 0) {
            redisSessionService.incrementLeaderboardScore(pin, request.getParticipantId(), score);
        }

        // Store last answer time for tie-breaking
        redisSessionService.storeLastAnswerTime(pin, request.getParticipantId(), submissionTimestamp);

        // 9. Fast-answer detection (anti-cheat)
        if (questionStartTime > 0) {
            long responseTimeMs = submissionTimestamp - questionStartTime;
            antiCheatService.checkFastAnswer(pin, request.getParticipantId(), responseTimeMs);
        }

        // 10. Audit logging — log accepted answer
        redisSessionService.logAuditEvent(pin, request.getParticipantId(), questionIndex,
                answer, submissionTimestamp, true, null);

        // 11. Get updated totals
        double totalScore = redisSessionService.getParticipantScore(pin, request.getParticipantId());
        long rank = redisSessionService.getParticipantRank(pin, request.getParticipantId());

        log.info("Answer submitted: pin={}, participant={}, question={}, correct={}, score={}, streak={}, multiplier={}",
                pin, request.getParticipantId(), questionIndex, isCorrect, score, newStreak, newMultiplier);

        return AnswerResult.builder()
                .accepted(true)
                .scoreAwarded(score)
                .totalScore((int) totalScore)
                .rank(rank)
                .streak(newStreak)
                .multiplier(newMultiplier)
                .build();
    }

    /**
     * Reveal the answer for the current question.
     * Transitions state to REVEAL, computes answer statistics, and returns top 5 leaderboard.
     */
    public RevealResult revealAnswer(String pin, UUID hostId) {
        // Validate session exists
        if (!redisSessionService.sessionExists(pin)) {
            throw new ResourceNotFoundException("Session", pin);
        }

        // Validate host
        String sessionHostId = redisSessionService.getHostId(pin);
        if (!hostId.toString().equals(sessionHostId)) {
            throw new ValidationException("Only the host can reveal answers");
        }

        // Transition state to REVEAL
        redisSessionService.updateSessionState(pin, SessionStatus.REVEAL.name());

        // Get current question index (already incremented by advanceToNextQuestion, so subtract 1)
        int questionIndex = redisSessionService.getCurrentQuestionIndex(pin) - 1;

        // Get correct answer
        String correctAnswer = redisSessionService.getCorrectAnswer(pin, questionIndex);

        // Compute answer statistics
        Map<Object, Object> answers = redisSessionService.getAnswersForQuestion(pin, questionIndex);
        Map<String, Integer> stats = new HashMap<>();
        int correctCount = 0;
        int totalCount = 0;

        for (Map.Entry<Object, Object> entry : answers.entrySet()) {
            String value = entry.getValue().toString();
            // Format: {answer}|{timestamp}|{score}
            String[] parts = value.split("\\|");
            if (parts.length >= 1) {
                String submittedAnswer = parts[0];
                stats.merge(submittedAnswer, 1, Integer::sum);
                totalCount++;
                if (submittedAnswer.equalsIgnoreCase(correctAnswer)) {
                    correctCount++;
                }
            }
        }

        double accuracyRate = totalCount > 0 ? (double) correctCount / totalCount : 0.0;

        // Get top 5 leaderboard with rank changes
        List<LeaderboardEntry> leaderboard = redisSessionService.getTopNWithRankChanges(pin, 5);

        log.info("Answer revealed: pin={}, question={}, correctAnswer={}, accuracy={}",
                pin, questionIndex, correctAnswer, accuracyRate);

        // Publish question.reveal event via Redis pub/sub for WebSocket delivery
        publishRevealEvent(pin, correctAnswer, stats, leaderboard);

        return RevealResult.builder()
                .correctAnswer(correctAnswer)
                .stats(stats)
                .accuracyRate(accuracyRate)
                .leaderboard(leaderboard)
                .build();
    }

    private void publishRevealEvent(String pin, String correctAnswer,
                                     Map<String, Integer> stats, List<LeaderboardEntry> leaderboard) {
        try {
            String channel = "session:" + pin + ":broadcast";

            // Build leaderboard JSON array
            StringBuilder lb = new StringBuilder("[");
            for (int i = 0; i < leaderboard.size(); i++) {
                LeaderboardEntry e = leaderboard.get(i);
                if (i > 0) lb.append(",");
                lb.append(String.format(
                    "{\"rank\":%d,\"participantId\":\"%s\",\"nickname\":\"%s\",\"score\":%.0f,\"rankChange\":%d}",
                    e.getRank(), e.getParticipantId(),
                    e.getNickname().replace("\"", "\\\""),
                    e.getScore(), e.getRankChange()));
            }
            lb.append("]");

            // Build stats JSON
            StringBuilder statsJson = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Integer> entry : stats.entrySet()) {
                if (!first) statsJson.append(",");
                statsJson.append(String.format("\"%s\":%d", entry.getKey().replace("\"", "\\\""), entry.getValue()));
                first = false;
            }
            statsJson.append("}");

            String event = String.format(
                "{\"type\":\"question.reveal\",\"payload\":{\"questionId\":\"current\",\"correctAnswer\":\"%s\",\"stats\":%s}}",
                correctAnswer != null ? correctAnswer.replace("\"", "\\\"") : "",
                statsJson);
            redisTemplate.convertAndSend(channel, event);

            // Also publish leaderboard update
            String leaderboardEvent = String.format(
                "{\"type\":\"leaderboard.update\",\"payload\":{\"top5\":%s}}",
                lb);
            redisTemplate.convertAndSend(channel, leaderboardEvent);

            // Publish state change
            String stateEvent = String.format(
                "{\"type\":\"session.state_changed\",\"payload\":{\"state\":\"REVEAL\",\"previousState\":\"QUESTION_CLOSED\"}}");
            redisTemplate.convertAndSend(channel, stateEvent);
        } catch (Exception e) {
            log.error("Failed to publish reveal event for pin={}: {}", pin, e.getMessage());
        }
    }

    /**
     * Snapshot current ranks before processing answers for a new question.
     * Should be called when a question opens (before any answers come in for this round).
     */
    public void snapshotRanksForRound(String pin) {
        redisSessionService.snapshotCurrentRanks(pin);
    }
}
