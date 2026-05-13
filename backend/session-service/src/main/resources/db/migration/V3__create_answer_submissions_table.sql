CREATE TABLE IF NOT EXISTS answer_submissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    participant_id UUID NOT NULL REFERENCES session_participants(id),
    question_id UUID NOT NULL,
    submitted_answer VARCHAR(10),
    is_correct BOOLEAN,
    response_time_ms INTEGER,
    score_awarded INTEGER DEFAULT 0,
    streak_at_time INTEGER DEFAULT 0,
    submitted_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_answers_session ON answer_submissions(session_id);
CREATE INDEX IF NOT EXISTS idx_answers_question ON answer_submissions(session_id, question_id);
