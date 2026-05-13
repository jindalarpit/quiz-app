package com.quizplatform.websocket.registry;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionInfo {

    private String pin;
    private String participantId;
    private boolean isHost;
    private long connectedAt;
}
