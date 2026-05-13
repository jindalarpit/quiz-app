package com.quizplatform.session.repository;

import com.quizplatform.session.model.SessionParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SessionParticipantRepository extends JpaRepository<SessionParticipant, UUID> {

    List<SessionParticipant> findBySessionIdOrderByFinalRankAsc(UUID sessionId);

    int countBySessionId(UUID sessionId);
}
