CREATE TABLE IF NOT EXISTS score_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL,
    round_number INTEGER NOT NULL,
    participant_id UUID NOT NULL,
    round_score INTEGER NOT NULL,
    cumulative_score INTEGER NOT NULL,
    rank INTEGER NOT NULL,
    rank_delta INTEGER NOT NULL DEFAULT 0,
    streak_count INTEGER NOT NULL DEFAULT 0,
    streak_multiplier INTEGER NOT NULL DEFAULT 1,
    time_taken_ms BIGINT,
    is_correct BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT idx_score_events_session_round
        UNIQUE (session_id, round_number, participant_id)
);

CREATE INDEX IF NOT EXISTS idx_score_events_session ON score_events(session_id);
CREATE INDEX IF NOT EXISTS idx_score_events_participant ON score_events(participant_id);
