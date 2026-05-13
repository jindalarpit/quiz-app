package com.quizplatform.analytics.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "resource_type", length = 50)
    private String resourceType;

    @Column(name = "resource_id")
    private UUID resourceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", columnDefinition = "jsonb")
    private Map<String, Object> details;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /**
     * SHA-256 hash of the previous audit log entry, forming a tamper-evident hash chain.
     * The first entry in the chain has previousHash = "GENESIS".
     */
    @Column(name = "previous_hash", length = 64)
    private String previousHash;

    /**
     * SHA-256 hash of this entry's content (action + userId + resourceType + resourceId + createdAt + previousHash).
     * Used as the previousHash for the next entry in the chain.
     */
    @Column(name = "entry_hash", length = 64)
    private String entryHash;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
