package com.quizplatform.analytics.repository;

import com.quizplatform.analytics.model.ScoreEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ScoreEventRepository extends JpaRepository<ScoreEvent, UUID> {

    List<ScoreEvent> findBySessionId(UUID sessionId);

    List<ScoreEvent> findByParticipantId(UUID participantId);

    List<ScoreEvent> findBySessionIdAndRoundNumber(UUID sessionId, int roundNumber);
}
