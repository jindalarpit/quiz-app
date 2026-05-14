package com.quizplatform.session.service;

import com.quizplatform.common.dto.QuestionDTO;
import com.quizplatform.common.exception.DuplicateResourceException;
import com.quizplatform.common.exception.ForbiddenException;
import com.quizplatform.common.exception.ResourceNotFoundException;
import com.quizplatform.common.exception.SessionFullException;
import com.quizplatform.common.exception.ValidationException;
import com.quizplatform.session.client.QuizServiceClient;
import com.quizplatform.session.dto.CreateSessionRequest;
import com.quizplatform.session.dto.JoinSessionRequest;
import com.quizplatform.session.dto.LeaderboardEntry;
import com.quizplatform.session.dto.ParticipantResponse;
import com.quizplatform.session.dto.SessionResponse;
import com.quizplatform.session.model.Session;
import com.quizplatform.session.model.SessionParticipant;
import com.quizplatform.session.model.SessionStatus;
import com.quizplatform.session.repository.SessionParticipantRepository;
import com.quizplatform.session.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private static final int MAX_PARTICIPANTS = 1000;
    private static final String STATE_CHANGE_EVENT_TYPE = "session.state_changed";

    private final RedisSessionService redisSessionService;
    private final SessionStateMachine stateMachine;
    private final ProfanityFilter profanityFilter;
    private final SessionRepository sessionRepository;
    private final SessionParticipantRepository sessionParticipantRepository;
    private final StringRedisTemplate redisTemplate;
    private final QuizServiceClient quizServiceClient;

    /**
     * Start a new session: generate PIN, store in Redis, return response.
     */
    public SessionResponse startSession(UUID hostId, CreateSessionRequest request) {
        String pin = redisSessionService.generateUniquePin();

        Map<String, Object> settings = request.getSettings();
        redisSessionService.createSession(pin, request.getQuizId(), hostId, settings);

        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();

        log.info("Session started: pin={}, quizId={}, hostId={}", pin, request.getQuizId(), hostId);

        return SessionResponse.builder()
                .id(sessionId)
                .quizId(request.getQuizId())
                .hostId(hostId)
                .pin(pin)
                .state(SessionStatus.LOBBY.name())
                .participantCount(0)
                .settings(settings)
                .createdAt(now)
                .build();
    }

    /**
     * Join a session: validate PIN, check nickname uniqueness, profanity filter, participant limit.
     * For late-join participants, includes current question state in the response.
     */
    public ParticipantResponse joinSession(String pin, JoinSessionRequest request) {
        // Validate session exists
        if (!redisSessionService.sessionExists(pin)) {
            throw new ResourceNotFoundException("Session", pin);
        }

        // Check session state allows joining
        String state = redisSessionService.getSessionState(pin);
        if (SessionStatus.ENDED.name().equals(state)) {
            throw new ValidationException("Session has ended");
        }

        // Check late join if session is past LOBBY
        boolean isLateJoin = !SessionStatus.LOBBY.name().equals(state);
        if (isLateJoin && !redisSessionService.isLateJoinAllowed(pin)) {
            throw new ValidationException("Session is no longer accepting participants");
        }

        String nickname = request.getNickname().trim();

        // Profanity filter
        if (profanityFilter.containsProfanity(nickname)) {
            throw new ValidationException("Nickname contains inappropriate content");
        }

        // Check nickname uniqueness (case-insensitive)
        if (redisSessionService.isNicknameTaken(pin, nickname)) {
            throw new DuplicateResourceException(
                    "Nickname '" + nickname + "' is already taken in this session"
            );
        }

        // Check participant limit
        int currentCount = redisSessionService.getParticipantCount(pin);
        if (currentCount >= MAX_PARTICIPANTS) {
            throw new SessionFullException(pin);
        }

        // Add participant
        UUID participantId = UUID.randomUUID();
        Instant joinedAt = Instant.now();
        redisSessionService.addParticipant(pin, participantId, nickname);

        // Publish session.joined event to broadcast channel for real-time UI updates
        int newCount = redisSessionService.getParticipantCount(pin);
        publishJoinEvent(pin, participantId.toString(), nickname, newCount);

        log.info("Participant joined: pin={}, nickname={}, participantId={}, lateJoin={}",
                pin, nickname, participantId, isLateJoin);

        // Build response with late-join context if applicable
        ParticipantResponse.ParticipantResponseBuilder responseBuilder = ParticipantResponse.builder()
                .id(participantId)
                .nickname(nickname)
                .joinedAt(joinedAt);

        // If late-join and session is in QUESTION_OPEN, provide current question context
        if (isLateJoin && SessionStatus.QUESTION_OPEN.name().equals(state)) {
            int currentQuestionIndex = redisSessionService.getCurrentQuestionIndex(pin);
            long questionStartTime = redisSessionService.getQuestionStartTime(pin);
            int questionDurationMs = redisSessionService.getQuestionDurationMs(pin);

            responseBuilder
                    .currentQuestionIndex(currentQuestionIndex)
                    .questionStartTime(questionStartTime)
                    .questionDurationMs(questionDurationMs)
                    .sessionState(state);
        }

        return responseBuilder.build();
    }

    /**
     * Advance to the next question (host only).
     */
    public void advanceToNextQuestion(String pin, UUID hostId) {
        validateHost(pin, hostId);

        String currentState = redisSessionService.getSessionState(pin);
        SessionStatus current = SessionStatus.valueOf(currentState);

        // Valid transitions: LOBBY → QUESTION_OPEN, REVEAL → QUESTION_OPEN
        stateMachine.validateTransition(current, SessionStatus.QUESTION_OPEN);

        redisSessionService.updateSessionState(pin, SessionStatus.QUESTION_OPEN.name());

        // Get current question index (0-based) before incrementing
        int questionIndex = redisSessionService.getCurrentQuestionIndex(pin);
        redisSessionService.incrementQuestionIndex(pin);

        // Set question timing
        long startTime = System.currentTimeMillis();
        int durationMs = redisSessionService.getQuestionDurationMs(pin);
        redisSessionService.setQuestionTiming(pin, startTime, durationMs);

        // Fetch question from quiz service and broadcast
        Map<Object, Object> sessionFields = redisSessionService.getSessionFields(pin);
        String quizIdStr = sessionFields.get("quiz_id") != null ? sessionFields.get("quiz_id").toString() : null;
        if (quizIdStr != null) {
            try {
                UUID quizId = UUID.fromString(quizIdStr);
                List<QuestionDTO> questions = quizServiceClient.getQuestions(quizId);
                if (questions != null && questionIndex < questions.size()) {
                    QuestionDTO question = questions.get(questionIndex);
                    // Store correct answer for scoring
                    redisSessionService.storeCorrectAnswer(pin, questionIndex, question.getCorrectAnswer());
                    // Broadcast question.start event
                    publishQuestionStartEvent(pin, question, durationMs, startTime);
                }
            } catch (Exception e) {
                log.warn("Failed to fetch/broadcast question for pin={}: {}", pin, e.getMessage());
            }
        }

        publishStateChangeEvent(pin, currentState, SessionStatus.QUESTION_OPEN.name());

        log.info("Advanced to next question: pin={}, hostId={}, questionIndex={}", pin, hostId, questionIndex);
    }

    /**
     * Pause the session (host only).
     */
    public void pauseSession(String pin, UUID hostId) {
        validateHost(pin, hostId);

        String currentState = redisSessionService.getSessionState(pin);
        SessionStatus current = SessionStatus.valueOf(currentState);

        stateMachine.validateTransition(current, SessionStatus.PAUSED);

        // Store previous state for resume
        redisSessionService.storePreviousState(pin, currentState);
        redisSessionService.updateSessionState(pin, SessionStatus.PAUSED.name());

        publishStateChangeEvent(pin, currentState, SessionStatus.PAUSED.name());

        log.info("Session paused: pin={}, previousState={}", pin, currentState);
    }

    /**
     * Resume the session (host only) — restore previous state.
     */
    public void resumeSession(String pin, UUID hostId) {
        validateHost(pin, hostId);

        String currentState = redisSessionService.getSessionState(pin);
        if (!SessionStatus.PAUSED.name().equals(currentState)) {
            throw new ValidationException("Session is not paused");
        }

        String previousState = redisSessionService.getPreviousState(pin);
        if (previousState == null || previousState.isEmpty()) {
            previousState = SessionStatus.LOBBY.name();
        }

        SessionStatus target = SessionStatus.valueOf(previousState);
        stateMachine.validateTransition(SessionStatus.PAUSED, target);

        redisSessionService.updateSessionState(pin, previousState);
        redisSessionService.storePreviousState(pin, "");

        publishStateChangeEvent(pin, SessionStatus.PAUSED.name(), previousState);

        log.info("Session resumed: pin={}, restoredState={}", pin, previousState);
    }

    /**
     * Skip the current question (host only).
     */
    public void skipQuestion(String pin, UUID hostId) {
        validateHost(pin, hostId);

        String currentState = redisSessionService.getSessionState(pin);
        SessionStatus current = SessionStatus.valueOf(currentState);

        // Skip closes the current question and advances
        if (current == SessionStatus.QUESTION_OPEN) {
            stateMachine.validateTransition(current, SessionStatus.QUESTION_CLOSED);
            redisSessionService.updateSessionState(pin, SessionStatus.QUESTION_CLOSED.name());

            publishStateChangeEvent(pin, currentState, SessionStatus.QUESTION_CLOSED.name());
        }

        log.info("Question skipped: pin={}, hostId={}", pin, hostId);
    }

    /**
     * End the session (host only) — transition to ENDED, persist to PostgreSQL.
     */
    public void endSession(String pin, UUID hostId) {
        validateHost(pin, hostId);

        String currentState = redisSessionService.getSessionState(pin);
        SessionStatus current = SessionStatus.valueOf(currentState);

        stateMachine.validateTransition(current, SessionStatus.ENDED);

        redisSessionService.updateSessionState(pin, SessionStatus.ENDED.name());

        // Persist session and participants to PostgreSQL
        persistSession(pin);

        publishStateChangeEvent(pin, currentState, SessionStatus.ENDED.name());

        log.info("Session ended: pin={}, hostId={}", pin, hostId);
    }

    private void validateHost(String pin, UUID hostId) {
        validateHostAccess(pin, hostId);
    }

    /**
     * Validate that the given user is the host of the session.
     * Public method for use by controllers.
     */
    public void validateHostAccess(String pin, UUID hostId) {
        if (!redisSessionService.sessionExists(pin)) {
            throw new ResourceNotFoundException("Session", pin);
        }

        String sessionHostId = redisSessionService.getHostId(pin);
        if (!hostId.toString().equals(sessionHostId)) {
            throw new ForbiddenException("Only the host can perform this action");
        }
    }

    private void persistSession(String pin) {
        try {
            Map<Object, Object> fields = redisSessionService.getSessionFields(pin);
            if (fields == null || fields.isEmpty()) {
                return;
            }

            Session session = Session.builder()
                    .quizId(UUID.fromString(fields.get("quiz_id").toString()))
                    .hostId(UUID.fromString(fields.get("host_id").toString()))
                    .pin(pin)
                    .status(SessionStatus.ENDED)
                    .startedAt(Instant.ofEpochMilli(Long.parseLong(fields.get("created_at").toString())))
                    .endedAt(Instant.now())
                    .participantCount(Integer.parseInt(fields.getOrDefault("participant_count", "0").toString()))
                    .build();

            Session savedSession = sessionRepository.save(session);

            // Persist participants with their scores from the leaderboard
            persistParticipants(pin, savedSession);

            log.info("Session persisted to PostgreSQL: pin={}", pin);
        } catch (Exception e) {
            log.error("Failed to persist session to PostgreSQL: pin={}", pin, e);
        }
    }

    private void persistParticipants(String pin, Session session) {
        try {
            List<LeaderboardEntry> leaderboard = redisSessionService.getTopNWithRankChanges(pin,
                    (int) redisSessionService.getLeaderboardSize(pin));

            if (leaderboard.isEmpty()) {
                log.debug("No participants to persist for session: pin={}", pin);
                return;
            }

            List<SessionParticipant> participants = new ArrayList<>();
            for (LeaderboardEntry entry : leaderboard) {
                SessionParticipant participant = SessionParticipant.builder()
                        .session(session)
                        .nickname(entry.getNickname())
                        .finalScore((int) entry.getScore())
                        .finalRank((int) entry.getRank())
                        .maxStreak(entry.getStreak())
                        .joinedAt(Instant.now()) // approximate; exact join time not tracked in leaderboard
                        .build();
                participants.add(participant);
            }

            sessionParticipantRepository.saveAll(participants);
            log.info("Persisted {} participants for session: pin={}", participants.size(), pin);
        } catch (Exception e) {
            log.error("Failed to persist participants for session: pin={}", pin, e);
        }
    }

    /**
     * Publish a state change event to Redis Pub/Sub for real-time UI updates.
     * Event is published to the session's broadcast channel.
     */
    private void publishStateChangeEvent(String pin, String previousState, String newState) {
        try {
            String channel = "session:" + pin + ":broadcast";
            String event = String.format(
                    "{\"type\":\"%s\",\"payload\":{\"state\":\"%s\",\"previousState\":\"%s\"}}",
                    STATE_CHANGE_EVENT_TYPE, newState, previousState
            );
            redisTemplate.convertAndSend(channel, event);
            log.debug("State change event published: pin={}, {} → {}", pin, previousState, newState);
        } catch (Exception e) {
            log.warn("Failed to publish state change event: pin={}, {} → {}",
                    pin, previousState, newState, e);
        }
    }

    /**
     * Publish a session.joined event to Redis Pub/Sub so the host UI updates in real-time.
     */
    private void publishJoinEvent(String pin, String participantId, String nickname, int count) {
        try {
            String channel = "session:" + pin + ":broadcast";
            String event = String.format(
                    "{\"type\":\"session.joined\",\"payload\":{\"participantId\":\"%s\",\"nickname\":\"%s\",\"count\":%d}}",
                    participantId, nickname, count
            );
            redisTemplate.convertAndSend(channel, event);
            log.debug("Join event published: pin={}, nickname={}, count={}", pin, nickname, count);
        } catch (Exception e) {
            log.warn("Failed to publish join event: pin={}, nickname={}", pin, nickname, e);
        }
    }

    private void publishQuestionStartEvent(String pin, QuestionDTO question, int durationMs, long serverTimestamp) {
        try {
            String channel = "session:" + pin + ":broadcast";
            StringBuilder optionsJson = new StringBuilder("[");
            if (question.getOptions() != null) {
                for (int i = 0; i < question.getOptions().size(); i++) {
                    QuestionDTO.OptionDTO opt = question.getOptions().get(i);
                    if (i > 0) optionsJson.append(",");
                    optionsJson.append(String.format("{\"id\":\"%s\",\"text\":\"%s\"}",
                            opt.getId() != null ? opt.getId() : String.valueOf(i),
                            opt.getText().replace("\"", "\\\"")));
                }
            }
            optionsJson.append("]");

            String event = String.format(
                    "{\"type\":\"question.start\",\"payload\":{\"questionId\":\"%s\",\"text\":\"%s\",\"options\":%s,\"type\":\"%s\",\"timeLimit\":%d,\"serverTimestamp\":%d}}",
                    question.getId(),
                    question.getText().replace("\"", "\\\""),
                    optionsJson,
                    question.getType(),
                    question.getTimeLimitSeconds() != null ? question.getTimeLimitSeconds() : (durationMs / 1000),
                    serverTimestamp
            );
            redisTemplate.convertAndSend(channel, event);
            log.debug("Question start event published: pin={}, questionId={}", pin, question.getId());
        } catch (Exception e) {
            log.warn("Failed to publish question start event: pin={}", pin, e);
        }
    }
}
