package com.quizplatform.session.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "session_participants")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @Column(nullable = false, length = 20)
    private String nickname;

    @Column(name = "final_score")
    private Integer finalScore;

    @Column(name = "final_rank")
    private Integer finalRank;

    @Column(name = "max_streak")
    private Integer maxStreak;

    @Column(name = "answers_correct")
    private Integer answersCorrect;

    @Column(name = "answers_total")
    private Integer answersTotal;

    @Column(name = "avg_response_time_ms")
    private Integer avgResponseTimeMs;

    @Column(name = "is_flagged")
    private Boolean isFlagged;

    @Column(name = "last_answer_at")
    private Instant lastAnswerAt;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;
}
