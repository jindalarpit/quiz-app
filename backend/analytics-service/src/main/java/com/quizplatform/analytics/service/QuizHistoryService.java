package com.quizplatform.analytics.service;

import com.quizplatform.analytics.dto.LeaderboardEntryDTO;
import com.quizplatform.analytics.dto.PagedHistoryResponse;
import com.quizplatform.analytics.dto.SessionHistoryEntry;
import com.quizplatform.common.exception.ResourceNotFoundException;
import com.quizplatform.common.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service for retrieving quiz session history and historical leaderboard data.
 * Performs cross-schema queries to the session schema tables (granted via V3 migration).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QuizHistoryService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Retrieves paginated session history for a host, ordered by ended_at DESC.
     * Supports optional date range filtering and case-insensitive quiz title search.
     *
     * @param hostId    the host's user ID
     * @param page      zero-based page number
     * @param size      page size
     * @param startDate optional start date filter (inclusive)
     * @param endDate   optional end date filter (inclusive)
     * @param search    optional case-insensitive quiz title search term (1-100 chars)
     * @return paginated history response
     */
    @Transactional(readOnly = true)
    public PagedHistoryResponse getSessionHistory(UUID hostId, int page, int size,
                                                   LocalDate startDate, LocalDate endDate,
                                                   String search) {
        // Validate date range
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new ValidationException("Invalid date range: start date must be on or before end date");
        }

        // Validate search term
        if (search != null && !search.isEmpty()) {
            if (search.length() > 100) {
                throw new ValidationException("Search term must be between 1 and 100 characters");
            }
        }

        // Build dynamic query
        StringBuilder whereClause = new StringBuilder("WHERE s.host_id = ? AND s.status = 'ENDED'");
        List<Object> params = new ArrayList<>();
        params.add(hostId);

        if (startDate != null) {
            whereClause.append(" AND s.ended_at >= ?");
            params.add(Timestamp.from(startDate.atStartOfDay(ZoneOffset.UTC).toInstant()));
        }

        if (endDate != null) {
            whereClause.append(" AND s.ended_at < ?");
            // End date is inclusive, so we use the start of the next day
            params.add(Timestamp.from(endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()));
        }

        if (search != null && !search.isEmpty()) {
            whereClause.append(" AND LOWER(s.quiz_title) LIKE LOWER(?)");
            params.add("%" + search + "%");
        }

        // Count total matching sessions
        String countSql = "SELECT COUNT(*) FROM session.sessions s " + whereClause;
        Long totalSessions = jdbcTemplate.queryForObject(countSql, Long.class, params.toArray());
        if (totalSessions == null) {
            totalSessions = 0L;
        }

        // Calculate pagination
        int totalPages = size > 0 ? (int) Math.ceil((double) totalSessions / size) : 0;
        int offset = page * size;

        // Fetch paginated results
        String querySql = "SELECT s.id, s.quiz_title, s.ended_at, s.participant_count, " +
                "EXTRACT(EPOCH FROM (s.ended_at - s.started_at)) AS duration_seconds " +
                "FROM session.sessions s " + whereClause +
                " ORDER BY s.ended_at DESC LIMIT ? OFFSET ?";

        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add(size);
        queryParams.add(offset);

        List<SessionHistoryEntry> sessions = jdbcTemplate.query(
                querySql,
                (rs, rowNum) -> mapSessionHistoryEntry(rs),
                queryParams.toArray()
        );

        return PagedHistoryResponse.builder()
                .sessions(sessions)
                .currentPage(page)
                .totalPages(totalPages)
                .totalSessions(totalSessions)
                .build();
    }

    /**
     * Retrieves the historical leaderboard for a specific session.
     * Verifies the session belongs to the requesting host.
     *
     * @param sessionId the session ID
     * @param hostId    the host's user ID (for ownership verification)
     * @return list of leaderboard entries ordered by rank ascending
     */
    @Transactional(readOnly = true)
    public List<LeaderboardEntryDTO> getHistoricalLeaderboard(UUID sessionId, UUID hostId) {
        // Verify session exists and belongs to the host
        String verifyQuery = "SELECT COUNT(*) FROM session.sessions " +
                "WHERE id = ? AND host_id = ? AND status = 'ENDED'";
        Integer count = jdbcTemplate.queryForObject(verifyQuery, Integer.class, sessionId, hostId);

        if (count == null || count == 0) {
            throw new ResourceNotFoundException("Session", sessionId.toString());
        }

        // Retrieve leaderboard entries ordered by rank
        String leaderboardQuery = "SELECT sp.final_rank, sp.nickname, sp.final_score, " +
                "sp.answers_correct, sp.answers_total, sp.max_streak, sp.avg_response_time_ms " +
                "FROM session.session_participants sp " +
                "WHERE sp.session_id = ? AND sp.final_rank IS NOT NULL " +
                "ORDER BY sp.final_rank ASC";

        return jdbcTemplate.query(
                leaderboardQuery,
                (rs, rowNum) -> mapLeaderboardEntry(rs),
                sessionId
        );
    }

    private SessionHistoryEntry mapSessionHistoryEntry(ResultSet rs) throws SQLException {
        UUID sessionId = UUID.fromString(rs.getString("id"));
        String quizTitle = rs.getString("quiz_title");
        Timestamp endedAtTs = rs.getTimestamp("ended_at");
        Instant endedAt = endedAtTs != null ? endedAtTs.toInstant() : null;
        int participantCount = rs.getInt("participant_count");
        long durationSeconds = rs.getLong("duration_seconds");

        return SessionHistoryEntry.builder()
                .sessionId(sessionId)
                .quizTitle(quizTitle)
                .endedAt(endedAt)
                .participantCount(participantCount)
                .durationSeconds(durationSeconds)
                .build();
    }

    private LeaderboardEntryDTO mapLeaderboardEntry(ResultSet rs) throws SQLException {
        int rank = rs.getInt("final_rank");
        String nickname = rs.getString("nickname");
        int score = rs.getInt("final_score");
        int correctAnswers = rs.getInt("answers_correct");
        int totalAnswers = rs.getInt("answers_total");
        int maxStreak = rs.getInt("max_streak");
        int avgResponseTimeMs = rs.getInt("avg_response_time_ms");
        // Convert milliseconds to seconds with 1 decimal place
        double avgResponseTimeSec = Math.round(avgResponseTimeMs / 100.0) / 10.0;

        return LeaderboardEntryDTO.builder()
                .rank(rank)
                .nickname(nickname)
                .score(score)
                .correctAnswers(correctAnswers)
                .totalAnswers(totalAnswers)
                .maxStreak(maxStreak)
                .avgResponseTimeSec(avgResponseTimeSec)
                .build();
    }
}
