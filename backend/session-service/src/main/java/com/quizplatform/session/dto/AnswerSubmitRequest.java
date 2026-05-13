package com.quizplatform.session.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnswerSubmitRequest {

    @NotBlank(message = "Participant ID is required")
    private String participantId;

    @NotBlank(message = "Answer is required")
    private String answer;

    private long clientTimestamp;
}
