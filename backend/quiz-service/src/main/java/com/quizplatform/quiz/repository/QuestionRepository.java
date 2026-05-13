package com.quizplatform.quiz.repository;

import com.quizplatform.quiz.model.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QuestionRepository extends JpaRepository<Question, UUID> {

    List<Question> findByQuizIdOrderByPositionAsc(UUID quizId);

    int countByQuizId(UUID quizId);

    Optional<Question> findByIdAndQuizId(UUID id, UUID quizId);

    @Modifying
    @Query("UPDATE Question q SET q.position = q.position - 1 WHERE q.quiz.id = :quizId AND q.position > :position")
    void decrementPositionsAfter(@Param("quizId") UUID quizId, @Param("position") int position);
}
