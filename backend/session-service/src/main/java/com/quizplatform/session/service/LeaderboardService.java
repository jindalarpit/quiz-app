package com.quizplatform.session.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizplatform.common.dto.QuestionDTO;
import com.quizplatform.common.exception.ResourceNotFoundException;
import com.quizplatform.common.exception.ValidationException;
import com.quizplatform.session.client.QuizServiceClient;
import com.quizplatform.session.dto.AnswerStatus;
import com.quizplatform.session.dto.FinalLeaderboardEntry;
import com.quizplatform.session.dto.PagedLeaderboardResponse;
import com.quizplatform.session.dto.ParticipantSelfResult;
import com.quizplatform.session.dto.QuestionResult;
import com.quizplatform.session.model.AnswerSubmission;
import com.quizplatform.session.model.Session;
import com.quizplatform.session.model.SessionParticipant;
import com.quizplatform.session.repository.AnswerSubmissionRepository;
import com.quizplatform.session.repository.SessionParticipantRepository;
import com.quizplatform.session.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaderboardService {

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long[] BACKOFF_DELAYS_MS = {1000L, 2000L, 4000L};

    private final SessionRepository sessionRepository;
    private final SessionParticipantRepository sessionParticipantRepository;
    private final AnswerSubmissionRepository answerSubmissionRepository;
    private final QuizServiceClient quizServiceClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Computes final rankings for all participants in a session and persists them atomically.
     * Implements retry with exponential backoff (1s, 2s, 4s) up to 3 attempts.
     * Publishes SESSION_ENDED event to Redis with the final leaderboard payload on success.
     *
     * Sort order: score desc → avg response time asc → last_answer_at asc
     *
     * @param sessionId the session to compute rankings for
     * @throws ResourceNotFoundException if the session does not exist
     */
    public void computeAndPersistFinalRankings(UUID sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session", sessionId.toString()));

        List<SessionParticipant> participants = sessionParticipantRepository.findBySessionId(sessionId);

        if (participants.isEmpty()) {
            log.info("No participants to rank for session: {}", sessionId);
            publishSessionEndedEvent(session, Collections.emptyList());
            return;
        }

        // Sort participants using the ranking algorithm:
        // 1. Score descending (higher score = better rank)
        // 2. Avg response time ascending (faster = higher rank)
        // 3. Last answer at ascending (earlier = higher rank)
        List<SessionParticipant> sorted = new ArrayList<>(participants);
        sorted.sort(
                Comparator.comparingInt((SessionParticipant p) -> p.getFinalScore() != null ? p.getFinalScore() : 0).reversed()
                        .thenComparingInt(p -> p.getAvgResponseTimeMs() != null ? p.getAvgResponseTimeMs() : Integer.MAX_VALUE)
                        .thenComparing(p -> p.getLastAnswerAt() != null ? p.getLastAnswerAt() : Instant.MAX)
        );

        // Assign 1-based ranks
        for (int i = 0; i < sorted.size(); i++) {
            sorted.get(i).setFinalRank(i + 1);
        }

        // Persist with retry and exponential backoff
        boolean persisted = persistWithRetry(sorted);

        if (persisted) {
            List<FinalLeaderboardEntry> entries = sorted.stream()
                    .map(this::toLeaderboardEntry)
                    .collect(Collectors.toList());
            publishSessionEndedEvent(session, entries);
        } else {
            log.error("All retry attempts exhausted for session: {}", session.getId());
            publishPersistenceFailureEvent(session);
        }
    }

    /**
     * Attempts to persist rankings with retry and exponential backoff (1s, 2s, 4s).
     *
     * @param participants the ranked participants to persist
     * @return true if persistence succeeded, false if all retries exhausted
     */
    private boolean persistWithRetry(List<SessionParticipant> participants) {
        for (int attempt = 0; attempt < MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                persistRankingsAtomically(participants);
                log.info("Successfully persisted rankings on attempt {}", attempt + 1);
                return true;
            } catch (Exception e) {
                log.warn("Persistence attempt {} of {} failed: {}",
                        attempt + 1, MAX_RETRY_ATTEMPTS, e.getMessage());
                if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                    try {
                        Thread.sleep(BACKOFF_DELAYS_MS[attempt]);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("Retry interrupted during backoff");
                        return false;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Persists all participant rankings in a single atomic transaction.
     */
    @Transactional
    public void persistRankingsAtomically(List<SessionParticipant> participants) {
        sessionParticipantRepository.saveAll(participants);
        sessionParticipantRepository.flush();
    }

    /**
     * Retrieves paginated leaderboard from DB.
     * Queries session_participants for the given sessionId where final_rank IS NOT NULL,
     * ordered by final_rank ASC, using LIMIT/OFFSET pagination on the indexed final_rank column.
     *
     * @param sessionId the session UUID
     * @param page      zero-based page number (must be non-negative)
     * @param size      page size (must be positive)
     * @return PagedLeaderboardResponse with entries, currentPage, totalPages, totalParticipants, pageSize
     * @throws ValidationException if page is negative or size is not positive
     * @throws ResourceNotFoundException if the session does not exist
     */
    public PagedLeaderboardResponse getPagedLeaderboard(UUID sessionId, int page, int size) {
        // Validate inputs
        if (page < 0) {
            throw new ValidationException("Page number must be non-negative");
        }
        if (size <= 0) {
            throw new ValidationException("Page size must be positive");
        }

        // Verify session exists
        sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session", sessionId.toString()));

        // Count total ranked participants
        int totalParticipants = sessionParticipantRepository.countRankedBySessionId(sessionId);

        // Calculate total pages using ceiling division
        int totalPages = totalParticipants == 0 ? 0 : (int) Math.ceil((double) totalParticipants / size);

        // Query the page of participants (LIMIT/OFFSET on indexed final_rank column)
        Page<SessionParticipant> participantPage = sessionParticipantRepository
                .findRankedBySessionId(sessionId, PageRequest.of(page, size));

        // Map each participant to FinalLeaderboardEntry DTO
        List<FinalLeaderboardEntry> entries = participantPage.getContent().stream()
                .map(this::toLeaderboardEntry)
                .collect(Collectors.toList());

        return PagedLeaderboardResponse.builder()
                .sessionId(sessionId)
                .entries(entries)
                .currentPage(page)
                .totalPages(totalPages)
                .totalParticipants(totalParticipants)
                .pageSize(size)
                .build();
    }

    /**
     * Retrieves individual participant results with per-question breakdown.
     *
     * @param sessionId     the session UUID
     * @param participantId the participant UUID
     * @return ParticipantSelfResult with all fields populated
     */
    public ParticipantSelfResult getParticipantResult(UUID sessionId, UUID participantId) {
        // 1. Fetch the session
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session", sessionId.toString()));

        // 2. Fetch the participant's record
        SessionParticipant participant = sessionParticipantRepository
                .findBySessionIdAndId(sessionId, participantId)
                .orElseThrow(() -> new ResourceNotFoundException("Participant", participantId.toString()));

        // 3. Fetch all participants' scores for the session to compute the session average
        List<SessionParticipant> allParticipants = sessionParticipantRepository
                .findBySessionIdOrderByFinalRankAsc(sessionId);

        // Compute session average: mean of all scores where at least one answer was submitted
        double sessionAverage = allParticipants.stream()
                .filter(p -> p.getAnswersTotal() != null && p.getAnswersTotal() > 0)
                .mapToInt(p -> p.getFinalScore() != null ? p.getFinalScore() : 0)
                .average()
                .orElse(0.0);

        // 4. Compute score difference and aboveAverage flag
        int participantScore = participant.getFinalScore() != null ? participant.getFinalScore() : 0;
        int scoreDifference = (int) Math.round(participantScore - sessionAverage);
        boolean aboveAverage = scoreDifference > 0;

        // 5. Fetch all questions for the session's quiz
        List<QuestionDTO> questions = quizServiceClient.getQuestions(session.getQuizId(), session.getHostId());

        // 6. Fetch the participant's answer submissions
        List<AnswerSubmission> submissions = answerSubmissionRepository
                .findBySessionIdAndParticipantId(sessionId, participantId);

        // Build a map of questionId -> AnswerSubmission for quick lookup
        Map<UUID, AnswerSubmission> submissionsByQuestionId = submissions.stream()
                .collect(Collectors.toMap(AnswerSubmission::getQuestionId, s -> s, (a, b) -> a));

        // 7. Map each question to CORRECT, INCORRECT, or UNANSWERED status
        List<QuestionResult> questionBreakdown = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            QuestionDTO question = questions.get(i);
            AnswerSubmission submission = submissionsByQuestionId.get(question.getId());

            AnswerStatus status;
            if (submission == null) {
                status = AnswerStatus.UNANSWERED;
            } else if (Boolean.TRUE.equals(submission.getIsCorrect())) {
                status = AnswerStatus.CORRECT;
            } else {
                status = AnswerStatus.INCORRECT;
            }

            questionBreakdown.add(QuestionResult.builder()
                    .questionNumber(i + 1)
                    .status(status)
                    .build());
        }

        // 8. Build and return ParticipantSelfResult
        int totalQuestions = questions.size();
        int correctAnswers = participant.getAnswersCorrect() != null ? participant.getAnswersCorrect() : 0;
        int maxStreak = participant.getMaxStreak() != null ? participant.getMaxStreak() : 0;
        double avgResponseTimeSec = participant.getAvgResponseTimeMs() != null
                ? Math.round(participant.getAvgResponseTimeMs() / 100.0) / 10.0
                : 0.0;
        int rank = participant.getFinalRank() != null ? participant.getFinalRank() : 0;

        return ParticipantSelfResult.builder()
                .rank(rank)
                .score(participantScore)
                .correctAnswers(correctAnswers)
                .totalQuestions(totalQuestions)
                .maxStreak(maxStreak)
                .avgResponseTimeSec(avgResponseTimeSec)
                .scoreDifference(scoreDifference)
                .aboveAverage(aboveAverage)
                .questionBreakdown(questionBreakdown)
                .build();
    }

    private FinalLeaderboardEntry toLeaderboardEntry(SessionParticipant participant) {
        return FinalLeaderboardEntry.builder()
                .rank(participant.getFinalRank() != null ? participant.getFinalRank() : 0)
                .nickname(participant.getNickname())
                .score(participant.getFinalScore() != null ? participant.getFinalScore() : 0)
                .correctAnswers(participant.getAnswersCorrect() != null ? participant.getAnswersCorrect() : 0)
                .totalAnswers(participant.getAnswersTotal() != null ? participant.getAnswersTotal() : 0)
                .maxStreak(participant.getMaxStreak() != null ? participant.getMaxStreak() : 0)
                .avgResponseTimeSec(participant.getAvgResponseTimeMs() != null
                        ? Math.round(participant.getAvgResponseTimeMs() / 100.0) / 10.0
                        : 0.0)
                .build();
    }

    private void publishSessionEndedEvent(Session session, List<FinalLeaderboardEntry> entries) {
        try {
            String channel = "session:" + session.getPin() + ":broadcast";
            Map<String, Object> payload = new HashMap<>();
            payload.put("sessionId", session.getId().toString());
            payload.put("leaderboard", entries);

            Map<String, Object> event = new HashMap<>();
            event.put("type", "session.ended");
            event.put("payload", payload);

            String eventJson = objectMapper.writeValueAsString(event);
            redisTemplate.convertAndSend(channel, eventJson);
            log.info("SESSION_ENDED event published for session: {}", session.getId());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize SESSION_ENDED event for session: {}", session.getId(), e);
        }
    }

    private void publishPersistenceFailureEvent(Session session) {
        try {
            String channel = "session:" + session.getPin() + ":broadcast";
            Map<String, Object> payload = new HashMap<>();
            payload.put("sessionId", session.getId().toString());
            payload.put("error", "Results could not be saved. Session data may be unavailable in quiz history.");

            Map<String, Object> event = new HashMap<>();
            event.put("type", "session.persistence_failed");
            event.put("payload", payload);

            String eventJson = objectMapper.writeValueAsString(event);
            redisTemplate.convertAndSend(channel, eventJson);
        } catch (JsonProcessingException e) {
            log.error("Failed to publish persistence failure event for session: {}", session.getId(), e);
        }
    }
}
