package com.quizplatform.common.event;

/**
 * Enumeration of all event types used in Redis Streams inter-service communication.
 * These events are published by services and consumed by the Analytics Service
 * and Audit Logger.
 */
public enum EventType {

    /**
     * Emitted when a new quiz session is created.
     * Payload: session_id, quiz_id, host_id, pin
     */
    SESSION_CREATED,

    /**
     * Emitted when a session transitions from LOBBY to ACTIVE (first question starts).
     * Payload: session_id, quiz_id, participant_count
     */
    SESSION_STARTED,

    /**
     * Emitted when a session transitions to ENDED state.
     * Payload: session_id, duration_seconds, participant_count, questions_completed
     */
    SESSION_ENDED,

    /**
     * Emitted when a participant joins a session.
     * Payload: session_id, participant_id, nickname, count
     */
    PARTICIPANT_JOINED,

    /**
     * Emitted when a participant submits an answer.
     * Payload: session_id, participant_id, question_id, answer, response_time_ms, is_correct, score
     */
    ANSWER_SUBMITTED,

    /**
     * Emitted when a participant's score is calculated after an answer.
     * Payload: session_id, participant_id, new_total, new_rank, streak, multiplier
     */
    SCORE_CALCULATED,

    /**
     * Emitted when the leaderboard is updated and broadcast.
     * Payload: session_id, question_index, top5
     */
    LEADERBOARD_UPDATED,

    /**
     * Emitted when suspicious activity is detected for a participant.
     * Payload: session_id, participant_id, reason, details
     */
    SUSPICIOUS_ACTIVITY_DETECTED
}
