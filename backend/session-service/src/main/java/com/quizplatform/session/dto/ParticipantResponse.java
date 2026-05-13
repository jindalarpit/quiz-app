package com.quizplatform.session.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ParticipantResponse {

    private UUID id;
    private String nickname;
    private Instant joinedAt;

    // Late-join context fields (only populated when joining mid-session)
    private Integer currentQuestionIndex;
    private Long questionStartTime;
    private Integer questionDurationMs;
    private String sessionState;
}
