package com.quizplatform.session.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Service responsible for anti-cheating detection and notification.
 * Handles fast-answer detection, suspicious activity notifications to host,
 * and participant kick operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AntiCheatService {

    private static final long FAST_ANSWER_THRESHOLD_MS = 200;
    private static final int FAST_ANSWER_FLAG_COUNT = 3;

    private final RedisSessionService redisSessionService;
    private final StringRedisTemplate redisTemplate;

    /**
     * Check if an answer was submitted suspiciously fast and handle flagging.
     * Called after an answer is accepted.
     *
     * @param pin the session PIN
     * @param participantId the participant's ID
     * @param responseTimeMs the time taken to answer in milliseconds
     */
    public void checkFastAnswer(String pin, String participantId, long responseTimeMs) {
        if (responseTimeMs >= FAST_ANSWER_THRESHOLD_MS) {
            return; // Normal response time, no action needed
        }

        // Increment fast answer count
        long newCount = redisSessionService.incrementFastAnswerCount(pin, participantId);
        log.debug("Fast answer detected: pin={}, participant={}, responseTime={}ms, count={}",
                pin, participantId, responseTimeMs, newCount);

        // Flag participant if threshold reached
        if (newCount >= FAST_ANSWER_FLAG_COUNT) {
            flagAndNotifyHost(pin, participantId, newCount);
        }
    }

    /**
     * Flag a participant and notify the host about suspicious activity.
     */
    private void flagAndNotifyHost(String pin, String participantId, long fastAnswerCount) {
        // Set is_flagged = true
        redisSessionService.flagParticipant(pin, participantId);

        // Get participant nickname for the notification
        String nickname = redisSessionService.getParticipantNickname(pin, participantId);
        if (nickname == null) {
            nickname = participantId;
        }

        // Publish suspicious_activity event to host channel
        notifyHostSuspiciousActivity(pin, participantId, nickname, fastAnswerCount);
    }

    /**
     * Notify the host about suspicious activity via Redis Pub/Sub.
     * Delivered to the host channel within 5 seconds of flagging.
     */
    private void notifyHostSuspiciousActivity(String pin, String participantId,
                                               String nickname, long count) {
        try {
            String hostChannel = "session:" + pin + ":host";
            String event = String.format(
                    "{\"type\":\"suspicious_activity\",\"payload\":{\"participantId\":\"%s\",\"nickname\":\"%s\",\"reason\":\"fast_answers\",\"count\":%d}}",
                    participantId, nickname, count
            );
            redisTemplate.convertAndSend(hostChannel, event);
            log.info("Suspicious activity notification sent to host: pin={}, participant={}, reason=fast_answers, count={}",
                    pin, participantId, count);
        } catch (Exception e) {
            log.error("Failed to notify host about suspicious activity: pin={}, participant={}",
                    pin, participantId, e);
        }
    }

    /**
     * Kick a participant from the session.
     * Marks them as kicked, removes from leaderboard, and notifies via Pub/Sub.
     *
     * @param pin the session PIN
     * @param participantId the participant to kick
     */
    public void kickParticipant(String pin, String participantId) {
        // Mark as kicked in Redis
        redisSessionService.kickParticipant(pin, participantId);

        // Remove from leaderboard
        redisSessionService.removeFromLeaderboard(pin, participantId);

        // Publish participant.kicked event to the participant's channel
        notifyParticipantKicked(pin, participantId);

        log.info("Participant kicked from session: pin={}, participantId={}", pin, participantId);
    }

    /**
     * Notify a participant that they have been kicked via Redis Pub/Sub.
     */
    private void notifyParticipantKicked(String pin, String participantId) {
        try {
            String participantChannel = "session:" + pin + ":participant:" + participantId;
            String event = "{\"type\":\"participant.kicked\",\"payload\":{\"reason\":\"Removed by host\"}}";
            redisTemplate.convertAndSend(participantChannel, event);
            log.debug("Kick notification sent to participant: pin={}, participantId={}", pin, participantId);
        } catch (Exception e) {
            log.error("Failed to notify participant about kick: pin={}, participant={}",
                    pin, participantId, e);
        }
    }
}
