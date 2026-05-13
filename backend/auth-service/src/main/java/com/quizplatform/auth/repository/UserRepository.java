package com.quizplatform.auth.repository;

import com.quizplatform.auth.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    Optional<User> findByOauthProviderAndOauthProviderId(String provider, String providerId);

    boolean existsByEmail(String email);

    /**
     * Finds users whose deletedAt timestamp is before the given cutoff.
     * Used by DeletionService to permanently delete users after 30-day grace period.
     */
    List<User> findByDeletedAtBefore(Instant cutoff);
}
