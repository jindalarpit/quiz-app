package com.quizplatform.session.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizplatform.session.dto.ParticipantRoundScore;
import com.quizplatform.session.dto.RoundResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Implementation of LeaderboardBroadcaster that handles personalized
 * WebSocket event construction and delivery with retry logic and
 * pending event queuing.
 *
 * Host view: top 5 entries (or all if fewer than 5) with all fields
 * including rank_delta.
 * Participant view: top 5 + if ranked below 5th, own entry with 1
 * above and 1 below.
 *
 * Includes monotonically increasing sequence_number per session.
 * Retry logic: 3 retries with exponential backoff within 2 seconds
 * for failed WebSocket delivery.
 * Queue: Redis list pending_events:{pin}:{participantId} with 4-hour
 * TTL for reconnection delivery.
 * Discards queued events when session transitions to ENDED.
 *
 * Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.7
 */
@Slf4j
@Service
public class LeaderboardBroadcasterImpl implements LeaderboardBroadcaster {

    private static final int TOP_N = 5;
    private static final int MAX_RETRIES = 3;
    private static final long[] BACKOFF_DELAYS_MS = {200L, 600L, 1200L};
    private static final long SESSION_TTL_SECONDS = 14400; // 4 hours
    private static final String EVENT_TYPE = "leaderboard.updated";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Per-session sequence number counters.
     * Monotonically increasing for each session to enable
     * out-of-order event detection on the frontend.
     */
    private final ConcurrentHashMap<String, AtomicLong> sequenceCounters =
            new ConcurrentHashMap<>();

    public LeaderboardBroadcasterImpl(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void broadcastLeaderboardUpdate(String pin, RoundResult result) {
        if (result == null || result.getParticipantScores() == null
                || result.getParticipantScores().isEmpty()) {
            log.debug("No participants to broadcast for session: pin={}", pin);
            return;
        }

        long sequenceNumber = getNextSequenceNumber(pin);
        List<ParticipantRoundScore> allScores = result.getParticipantScores();

        // Broadcast host view
        broadcastHostView(pin, result, allScores, sequenceNumber);

        // Broadcast personalized participant views
        for (ParticipantRoundScore participant : allScores) {
            broadcastParticipantView(
                    pin, result, allScores, participant, sequenceNumber);
        }

        log.info("Broadcast leaderboard update: pin={}, round={}, "
                        + "participants={}, seq={}",
                pin, result.getRoundNumber(),
                allScores.size(), sequenceNumber);
    }

    @Override
    public void publishScoreEvent(String pin, RoundResult result) {
        // Kafka publishing will be fully implemented when Kafka
        // dependency is added (Task 5.3). For now, log the intent.
        log.info("Score event publish requested: pin={}, round={}",
                pin, result.getRoundNumber());
    }

    /**
     * Discard all queued pending events for a session.
     * Called when the session transitions to ENDED state.
     *
     * @param pin session PIN
     */
    public void discardPendingEvents(String pin) {
        try {
            Set<String> keys =
                    redisTemplate.keys("pending_events:" + pin + ":*");
            if (keys != null && !keys.isEmpty()) {
                Long deleted = redisTemplate.delete(keys);
                log.info("Discarded {} pending event queues for ended "
                        + "session: pin={}", deleted, pin);
            }
        } catch (Exception e) {
            log.warn("Failed to discard pending events: pin={}, error={}",
                    pin, e.getMessage());
        }

        // Clean up sequence counter for this session
        sequenceCounters.remove(pin);
    }

    /**
     * Deliver any queued pending events for a participant on
     * reconnection.
     *
     * @param pin session PIN
     * @param participantId participant UUID
     * @return list of queued event payloads delivered
     */
    public List<String> deliverPendingEvents(
            String pin, String participantId) {
        String queueKey = pendingEventsKey(pin, participantId);
        List<String> events = new ArrayList<>();

        try {
            String event;
            while ((event = redisTemplate.opsForList()
                    .leftPop(queueKey)) != null) {
                events.add(event);
            }

            if (!events.isEmpty()) {
                log.info("Delivered {} pending events: pin={}, "
                                + "participantId={}",
                        events.size(), pin, participantId);
            }
        } catch (Exception e) {
            log.warn("Failed to deliver pending events: pin={}, "
                            + "participantId={}, error={}",
                    pin, participantId, e.getMessage());
        }

        return events;
    }

    // ==================== Host View Construction ====================

    /**
     * Construct and broadcast the host view: top 5 entries (or all if
     * fewer than 5) with all fields including rank_delta.
     */
    private void broadcastHostView(String pin, RoundResult result,
            List<ParticipantRoundScore> allScores, long sequenceNumber) {
        List<ParticipantRoundScore> hostEntries =
                constructHostView(allScores);
        String payload = buildEventPayload(
                pin, result, hostEntries, sequenceNumber);

        if (payload != null) {
            deliverWithRetry(pin, "host", payload);
        }
    }

    /**
     * Construct host view: top 5 entries (or all if fewer than 5)
     * ordered by rank ascending.
     */
    List<ParticipantRoundScore> constructHostView(
            List<ParticipantRoundScore> allScores) {
        return allScores.stream()
                .sorted(Comparator.comparingInt(
                        ParticipantRoundScore::getRank))
                .limit(TOP_N)
                .collect(Collectors.toList());
    }

    // ==================== Participant View Construction ==============

    /**
     * Construct and broadcast a personalized participant view.
     * Top 5 + if ranked below 5th, own entry with 1 above and 1 below.
     */
    private void broadcastParticipantView(String pin, RoundResult result,
            List<ParticipantRoundScore> allScores,
            ParticipantRoundScore participant, long sequenceNumber) {
        List<ParticipantRoundScore> participantEntries =
                constructParticipantView(allScores, participant);
        String payload = buildEventPayload(
                pin, result, participantEntries, sequenceNumber);

        if (payload != null) {
            deliverWithRetry(pin, participant.getParticipantId(), payload);
        }
    }

    /**
     * Construct personalized participant view:
     * - Top 5 entries (or all if fewer than 5)
     * - If participant is ranked below 5th: include own entry + 1 above
     *   + 1 below (omitting above if ranked 6th since rank 5 is already
     *   in top 5, omitting below if ranked last)
     */
    List<ParticipantRoundScore> constructParticipantView(
            List<ParticipantRoundScore> allScores,
            ParticipantRoundScore participant) {

        // Sort all scores by rank ascending
        List<ParticipantRoundScore> sorted = allScores.stream()
                .sorted(Comparator.comparingInt(
                        ParticipantRoundScore::getRank))
                .collect(Collectors.toList());

        int totalParticipants = sorted.size();
        int participantRank = participant.getRank();

        // Start with top 5 (or all if fewer than 5)
        List<ParticipantRoundScore> view = new ArrayList<>(
                sorted.stream().limit(TOP_N).collect(Collectors.toList()));

        // If participant is ranked within top 5, the view is complete
        if (participantRank <= TOP_N) {
            return view;
        }

        // Participant is ranked below 5th - add context entries
        Set<String> includedIds = view.stream()
                .map(ParticipantRoundScore::getParticipantId)
                .collect(Collectors.toSet());

        // Find participant's index in the sorted list
        int participantIndex = -1;
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).getParticipantId()
                    .equals(participant.getParticipantId())) {
                participantIndex = i;
                break;
            }
        }

        if (participantIndex < 0) {
            return view;
        }

        // Add 1 above (if not already in top 5)
        if (participantIndex > 0) {
            ParticipantRoundScore above =
                    sorted.get(participantIndex - 1);
            if (!includedIds.contains(above.getParticipantId())) {
                view.add(above);
                includedIds.add(above.getParticipantId());
            }
        }

        // Add participant's own entry
        if (!includedIds.contains(participant.getParticipantId())) {
            view.add(sorted.get(participantIndex));
            includedIds.add(participant.getParticipantId());
        }

        // Add 1 below (if not last)
        if (participantIndex < totalParticipants - 1) {
            ParticipantRoundScore below =
                    sorted.get(participantIndex + 1);
            if (!includedIds.contains(below.getParticipantId())) {
                view.add(below);
                includedIds.add(below.getParticipantId());
            }
        }

        // Sort final view by rank ascending
        view.sort(Comparator.comparingInt(
                ParticipantRoundScore::getRank));

        return view;
    }

    // ==================== Event Payload Construction =================

    /**
     * Build the JSON event payload for a leaderboard.updated event.
     */
    String buildEventPayload(String pin, RoundResult result,
            List<ParticipantRoundScore> entries, long sequenceNumber) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", EVENT_TYPE);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sessionId", pin);
            payload.put("roundNumber", result.getRoundNumber());
            payload.put("sequenceNumber", sequenceNumber);
            payload.put("timestamp",
                    result.getTimestamp().toEpochMilli());

            List<Map<String, Object>> entryList = new ArrayList<>();
            for (ParticipantRoundScore entry : entries) {
                Map<String, Object> entryMap = new LinkedHashMap<>();
                entryMap.put("participantId",
                        entry.getParticipantId());
                entryMap.put("nickname", entry.getNickname());
                entryMap.put("cumulativeScore",
                        entry.getCumulativeScore());
                entryMap.put("roundScore", entry.getRoundScore());
                entryMap.put("rank", entry.getRank());
                entryMap.put("rankDelta", entry.getRankDelta());
                entryMap.put("streakCount", entry.getStreakCount());
                entryMap.put("streakMultiplier",
                        entry.getStreakMultiplier());
                entryList.add(entryMap);
            }
            payload.put("entries", entryList);

            event.put("payload", payload);

            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize leaderboard event: pin={}, "
                    + "error={}", pin, e.getMessage());
            return null;
        }
    }

    // ==================== Delivery with Retry =======================

    /**
     * Deliver an event via Redis pub/sub with retry logic.
     * 3 retries with exponential backoff within 2 seconds.
     * On failure, queue the event for reconnection delivery.
     *
     * @param pin session PIN
     * @param targetId "host" for host channel, or participantId
     * @param payload JSON event payload
     */
    void deliverWithRetry(String pin, String targetId, String payload) {
        String channel = resolveChannel(pin, targetId);

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                redisTemplate.convertAndSend(channel, payload);
                log.debug("Delivered leaderboard event to channel {}",
                        channel);
                return;
            } catch (Exception e) {
                if (attempt < MAX_RETRIES) {
                    log.warn("Failed to deliver event (attempt {}/{}): "
                                    + "channel={}, error={}",
                            attempt + 1, MAX_RETRIES + 1,
                            channel, e.getMessage());
                    sleepWithBackoff(attempt);
                } else {
                    log.error("All delivery attempts exhausted: "
                                    + "channel={}, queuing for reconnection",
                            channel);
                    queuePendingEvent(pin, targetId, payload);
                }
            }
        }
    }

    /**
     * Resolve the Redis pub/sub channel for a target.
     * Host events go to the session host channel.
     * Participant events go to participant-specific channels.
     */
    private String resolveChannel(String pin, String targetId) {
        if ("host".equals(targetId)) {
            return "session:" + pin + ":host";
        }
        return "session:" + pin + ":" + targetId;
    }

    /**
     * Queue a failed event in Redis list for reconnection delivery.
     * Key: pending_events:{pin}:{participantId}
     * TTL: 4 hours
     */
    private void queuePendingEvent(
            String pin, String targetId, String payload) {
        if ("host".equals(targetId)) {
            // Host events are not queued - host is always connected
            log.warn("Host delivery failed for session {}, "
                    + "event will be lost", pin);
            return;
        }

        String queueKey = pendingEventsKey(pin, targetId);
        try {
            redisTemplate.opsForList().rightPush(queueKey, payload);
            redisTemplate.expire(queueKey,
                    Duration.ofSeconds(SESSION_TTL_SECONDS));
            log.info("Queued pending event: pin={}, participantId={}",
                    pin, targetId);
        } catch (Exception e) {
            log.error("Failed to queue pending event: pin={}, "
                            + "participantId={}, error={}",
                    pin, targetId, e.getMessage());
        }
    }

    // ==================== Sequence Number Management =================

    /**
     * Get the next monotonically increasing sequence number for a
     * session. Uses AtomicLong per session for thread-safe increment.
     */
    long getNextSequenceNumber(String pin) {
        return sequenceCounters
                .computeIfAbsent(pin, k -> new AtomicLong(0))
                .incrementAndGet();
    }

    /**
     * Get the current sequence number for a session (for testing).
     */
    long getCurrentSequenceNumber(String pin) {
        AtomicLong counter = sequenceCounters.get(pin);
        return counter != null ? counter.get() : 0;
    }

    // ==================== Helper Methods ============================

    private String pendingEventsKey(String pin, String participantId) {
        return "pending_events:" + pin + ":" + participantId;
    }

    /**
     * Sleep with exponential backoff.
     * Delays: 200ms, 600ms, 1200ms (total 2000ms within 2s budget).
     */
    private void sleepWithBackoff(int attempt) {
        try {
            long delay = BACKOFF_DELAYS_MS[
                    Math.min(attempt, BACKOFF_DELAYS_MS.length - 1)];
            Thread.sleep(delay);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("Retry sleep interrupted");
        }
    }
}
