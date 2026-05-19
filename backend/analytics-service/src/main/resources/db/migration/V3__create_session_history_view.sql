-- Grant analytics schema read access to session schema tables
GRANT USAGE ON SCHEMA session TO analytics_reader;
GRANT SELECT ON session.sessions TO analytics_reader;
GRANT SELECT ON session.session_participants TO analytics_reader;
GRANT SELECT ON session.answer_submissions TO analytics_reader;

-- Index for host-based history queries with date ordering
CREATE INDEX IF NOT EXISTS idx_sessions_host_ended
    ON session.sessions(host_id, ended_at DESC)
    WHERE status = 'ENDED';

-- Index for quiz title search
CREATE INDEX IF NOT EXISTS idx_sessions_quiz_title_search
    ON session.sessions USING gin(to_tsvector('english', quiz_title));
