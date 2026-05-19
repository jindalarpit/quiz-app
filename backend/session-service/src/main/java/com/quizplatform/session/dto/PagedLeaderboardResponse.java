package com.quizplatform.session.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagedLeaderboardResponse {

    private UUID sessionId;
    private List<FinalLeaderboardEntry> entries;
    private int currentPage;
    private int totalPages;
    private int totalParticipants;
    private int pageSize;
}
