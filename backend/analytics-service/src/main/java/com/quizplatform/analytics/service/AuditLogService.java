package com.quizplatform.analytics.service;

import com.quizplatform.analytics.model.AuditLog;
import com.quizplatform.analytics.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for creating tamper-evident audit log entries with hash chain integrity.
 * Each entry includes a SHA-256 hash of the previous entry, forming an immutable chain.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private static final String GENESIS_HASH = "GENESIS";

    private final AuditLogRepository auditLogRepository;

    /**
     * Creates a new audit log entry with hash chain integrity.
     *
     * @param userId       the user who performed the action
     * @param action       the action performed (e.g., "USER_DELETED", "QUIZ_CREATED")
     * @param resourceType the type of resource affected
     * @param resourceId   the ID of the resource affected
     * @param details      additional details as key-value pairs
     * @param ipAddress    the IP address of the request
     * @return the persisted audit log entry
     */
    @Transactional
    public AuditLog createAuditEntry(UUID userId, String action, String resourceType,
                                     UUID resourceId, Map<String, Object> details, String ipAddress) {
        // Get the hash of the last entry in the chain
        String previousHash = getLastEntryHash();

        AuditLog entry = AuditLog.builder()
                .userId(userId)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .details(details)
                .ipAddress(ipAddress)
                .previousHash(previousHash)
                .createdAt(Instant.now())
                .build();

        // Compute the hash of this entry
        String entryHash = computeEntryHash(entry);
        entry.setEntryHash(entryHash);

        return auditLogRepository.save(entry);
    }

    /**
     * Verifies the integrity of the audit log hash chain.
     *
     * @return true if the chain is intact, false if tampering is detected
     */
    @Transactional(readOnly = true)
    public boolean verifyChainIntegrity() {
        var allEntries = auditLogRepository.findAll();
        if (allEntries.isEmpty()) {
            return true;
        }

        // Sort by ID to maintain insertion order
        allEntries.sort((a, b) -> Long.compare(a.getId(), b.getId()));

        String expectedPreviousHash = GENESIS_HASH;

        for (AuditLog entry : allEntries) {
            // Verify previous hash matches
            if (!expectedPreviousHash.equals(entry.getPreviousHash())) {
                log.error("Hash chain broken at entry {}: expected previousHash={}, found={}",
                        entry.getId(), expectedPreviousHash, entry.getPreviousHash());
                return false;
            }

            // Verify entry hash is correct
            String computedHash = computeEntryHash(entry);
            if (!computedHash.equals(entry.getEntryHash())) {
                log.error("Entry hash mismatch at entry {}: computed={}, stored={}",
                        entry.getId(), computedHash, entry.getEntryHash());
                return false;
            }

            expectedPreviousHash = entry.getEntryHash();
        }

        return true;
    }

    private String getLastEntryHash() {
        Optional<AuditLog> lastEntry = auditLogRepository.findTopByOrderByIdDesc();
        return lastEntry.map(AuditLog::getEntryHash).orElse(GENESIS_HASH);
    }

    /**
     * Computes SHA-256 hash of an audit log entry's content.
     * Hash includes: action + userId + resourceType + resourceId + createdAt + previousHash
     */
    String computeEntryHash(AuditLog entry) {
        String content = String.join("|",
                nullSafe(entry.getAction()),
                nullSafe(entry.getUserId()),
                nullSafe(entry.getResourceType()),
                nullSafe(entry.getResourceId()),
                nullSafe(entry.getCreatedAt()),
                nullSafe(entry.getPreviousHash())
        );

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private String nullSafe(Object value) {
        return value == null ? "" : value.toString();
    }
}
