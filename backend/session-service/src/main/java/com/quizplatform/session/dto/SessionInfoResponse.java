package com.quizplatform.session.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionInfoResponse {

    private String pin;
    private String state;
    private int participantCount;
    private List<ParticipantInfo> participants;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParticipantInfo {
        private String id;
        private String nickname;
    }
}
