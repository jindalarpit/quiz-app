package com.quizplatform.session.repository;

import com.quizplatform.session.model.AnswerSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AnswerSubmissionRepository extends JpaRepository<AnswerSubmission, UUID> {

    List<AnswerSubmission> findBySessionIdAndQuestionId(UUID sessionId, UUID questionId);

    List<AnswerSubmission> findBySessionIdAndParticipantId(UUID sessionId, UUID participantId);
}
