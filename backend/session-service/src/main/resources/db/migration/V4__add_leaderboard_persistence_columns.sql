-- Add tiebreaker column for ranking
ALTER TABLE session_participants
    ADD COLUMN IF NOT EXISTS last_answer_at TIMESTAMP WITH TIME ZONE;

-- Composite index for efficient paginated leaderboard queries
CREATE INDEX IF NOT EXISTS idx_participants_leaderboard
    ON session_participants(session_id, final_rank ASC)
    WHERE final_rank IS NOT NULL;
