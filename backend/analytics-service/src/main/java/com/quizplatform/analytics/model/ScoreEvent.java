package com.quizplatform.analytics.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "score_events",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "idx_score_events_session_round",
                        columnNames = {"session_id", "round_number", "participant_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "UUID DEFAULT gen_random_uuid()")
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "round_number", nullable = false)
    private int roundNumber;

    @Column(name = "participant_id", nullable = false)
    private UUID participantId;

    @Column(name = "round_score", nullable = false)
    private int roundScore;

    @Column(name = "cumulative_score", nullable = false)
    private int cumulativeScore;

    @Column(name = "rank", nullable = false)
    private int rank;

    @Column(name = "rank_delta", nullable = false)
    private int rankDelta;

    @Column(name = "streak_count", nullable = false)
    private int streakCount;

    @Column(name = "streak_multiplier", nullable = false)
    private int streakMultiplier;

    @Column(name = "time_taken_ms")
    private Long timeTakenMs;

    @Column(name = "is_correct", nullable = false)
    private boolean isCorrect;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
