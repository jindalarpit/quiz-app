package com.quizplatform.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionHistoryEntry {

    private UUID sessionId;
    private String quizTitle;
    private Instant endedAt;
    private int participantCount;
    private long durationSeconds;
}
