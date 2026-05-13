package com.quizplatform.analytics.repository;

import com.quizplatform.analytics.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByUserId(UUID userId, Pageable pageable);

    List<AuditLog> findByResourceTypeAndResourceId(String resourceType, UUID resourceId);

    void deleteByCreatedAtBefore(Instant cutoff);

    /**
     * Finds the most recent audit log entry for hash chain continuation.
     */
    Optional<AuditLog> findTopByOrderByIdDesc();
}
