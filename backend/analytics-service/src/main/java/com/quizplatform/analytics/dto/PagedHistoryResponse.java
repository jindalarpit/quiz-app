package com.quizplatform.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagedHistoryResponse {

    private List<SessionHistoryEntry> sessions;
    private int currentPage;
    private int totalPages;
    private long totalSessions;
}
