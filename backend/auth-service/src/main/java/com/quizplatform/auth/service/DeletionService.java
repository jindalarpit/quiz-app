package com.quizplatform.auth.service;

import com.quizplatform.auth.model.User;
import com.quizplatform.auth.repository.RefreshTokenRepository;
import com.quizplatform.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Scheduled service that permanently deletes users who have been soft-deleted
 * for more than 30 days (GDPR compliance).
 *
 * Cascade deletion to quizzes, sessions, and analytics data is handled by
 * database foreign key cascades (ON DELETE CASCADE).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeletionService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    /**
     * Runs daily at 2:00 AM to permanently delete users whose deletedAt
     * timestamp is older than 30 days.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void purgeDeletedUsers() {
        Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);
        List<User> usersToDelete = userRepository.findByDeletedAtBefore(cutoff);

        if (usersToDelete.isEmpty()) {
            log.debug("No users eligible for permanent deletion");
            return;
        }

        log.info("Permanently deleting {} users with deletedAt before {}", usersToDelete.size(), cutoff);

        for (User user : usersToDelete) {
            try {
                // Delete refresh tokens first (no FK cascade on this table)
                refreshTokenRepository.deleteByUser(user);
                // Permanently delete the user — FK cascades handle related data
                userRepository.delete(user);
                log.info("Permanently deleted user: {}", user.getId());
            } catch (Exception e) {
                log.error("Failed to permanently delete user {}: {}", user.getId(), e.getMessage(), e);
            }
        }
    }
}
