CREATE TABLE IF NOT EXISTS session_participants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    nickname VARCHAR(20) NOT NULL,
    final_score INTEGER DEFAULT 0,
    final_rank INTEGER,
    max_streak INTEGER DEFAULT 0,
    answers_correct INTEGER DEFAULT 0,
    answers_total INTEGER DEFAULT 0,
    avg_response_time_ms INTEGER,
    is_flagged BOOLEAN DEFAULT FALSE,
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_participants_session ON session_participants(session_id);
CREATE INDEX IF NOT EXISTS idx_participants_rank ON session_participants(session_id, final_rank);
