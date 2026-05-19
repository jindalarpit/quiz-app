package com.quizplatform.session.repository;

import com.quizplatform.session.model.SessionParticipant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SessionParticipantRepository extends JpaRepository<SessionParticipant, UUID> {

    List<SessionParticipant> findBySessionId(UUID sessionId);

    Optional<SessionParticipant> findBySessionIdAndId(UUID sessionId, UUID participantId);

    List<SessionParticipant> findBySessionIdOrderByFinalRankAsc(UUID sessionId);

    int countBySessionId(UUID sessionId);

    @Query("SELECT sp FROM SessionParticipant sp WHERE sp.session.id = :sessionId AND sp.finalRank IS NOT NULL ORDER BY sp.finalRank ASC")
    Page<SessionParticipant> findRankedBySessionId(@Param("sessionId") UUID sessionId, Pageable pageable);

    @Query("SELECT COUNT(sp) FROM SessionParticipant sp WHERE sp.session.id = :sessionId AND sp.finalRank IS NOT NULL")
    int countRankedBySessionId(@Param("sessionId") UUID sessionId);
}
