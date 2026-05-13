CREATE TABLE IF NOT EXISTS questions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    quiz_id UUID NOT NULL REFERENCES quizzes(id) ON DELETE CASCADE,
    type VARCHAR(20) NOT NULL,
    text VARCHAR(500) NOT NULL,
    options JSONB NOT NULL,
    correct_answer VARCHAR(10),
    time_limit_seconds INTEGER NOT NULL DEFAULT 20,
    points INTEGER NOT NULL DEFAULT 1000,
    position INTEGER NOT NULL,
    media_url VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_questions_quiz ON questions(quiz_id);
CREATE INDEX IF NOT EXISTS idx_questions_position ON questions(quiz_id, position);
