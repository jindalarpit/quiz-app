package com.quizplatform.auth.repository;

import com.quizplatform.auth.model.RefreshToken;
import com.quizplatform.auth.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByUserAndRevokedAtIsNull(User user);

    void deleteByUser(User user);

    void deleteByExpiresAtBefore(Instant now);
}
