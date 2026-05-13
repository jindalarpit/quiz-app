package com.quizplatform.session.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionResponse {

    private UUID id;
    private UUID quizId;
    private UUID hostId;
    private String pin;
    private String state;
    private int participantCount;
    private Map<String, Object> settings;
    private Instant createdAt;
}
