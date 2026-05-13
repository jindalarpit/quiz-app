package com.quizplatform.session.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "answer_submissions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnswerSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id", nullable = false)
    private SessionParticipant participant;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(name = "submitted_answer", length = 10)
    private String submittedAnswer;

    @Column(name = "is_correct")
    private Boolean isCorrect;

    @Column(name = "response_time_ms")
    private Integer responseTimeMs;

    @Column(name = "score_awarded")
    private Integer scoreAwarded;

    @Column(name = "streak_at_time")
    private Integer streakAtTime;

    @Column(name = "submitted_at")
    private Instant submittedAt;
}
