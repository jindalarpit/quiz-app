package com.quizplatform.session.repository;

import com.quizplatform.session.model.Session;
import com.quizplatform.session.model.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SessionRepository extends JpaRepository<Session, UUID> {

    Optional<Session> findByPin(String pin);

    List<Session> findByQuizId(UUID quizId);

    List<Session> findByHostId(UUID hostId);

    boolean existsByQuizIdAndStatusNot(UUID quizId, SessionStatus status);
}
