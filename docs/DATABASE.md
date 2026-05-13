# Database Schema

## PostgreSQL Schema

### Users

```sql
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255),
    display_name VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'HOST', -- ADMIN, HOST
    oauth_provider VARCHAR(20),               -- google, github, null
    oauth_provider_id VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    deleted_at TIMESTAMP WITH TIME ZONE       -- soft delete for GDPR
);
```

### Quizzes

```sql
CREATE TABLE quizzes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    cover_image_url VARCHAR(500),
    is_published BOOLEAN DEFAULT FALSE,
    settings JSONB DEFAULT '{}', -- {allowLateJoin: true, shuffleQuestions: false}
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
```

### Questions

```sql
CREATE TABLE questions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    quiz_id UUID NOT NULL REFERENCES quizzes(id) ON DELETE CASCADE,
    type VARCHAR(20) NOT NULL,        -- MCQ, TRUE_FALSE, POLL
    text VARCHAR(500) NOT NULL,
    options JSONB NOT NULL,            -- [{id: "A", text: "Option 1"}, ...]
    correct_answer VARCHAR(10),        -- null for POLL type
    time_limit_seconds INTEGER NOT NULL DEFAULT 20,
    points INTEGER NOT NULL DEFAULT 1000,
    position INTEGER NOT NULL,
    media_url VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
```

### Sessions (persisted after session ends)

```sql
CREATE TABLE sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    quiz_id UUID NOT NULL REFERENCES quizzes(id),
    host_id UUID NOT NULL REFERENCES users(id),
    pin VARCHAR(6) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ENDED',
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ended_at TIMESTAMP WITH TIME ZONE,
    participant_count INTEGER DEFAULT 0,
    settings JSONB DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
```

### Session Participants

```sql
CREATE TABLE session_participants (
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
```

### Answer Submissions

```sql
CREATE TABLE answer_submissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    participant_id UUID NOT NULL REFERENCES session_participants(id),
    question_id UUID NOT NULL REFERENCES questions(id),
    submitted_answer VARCHAR(10),
    is_correct BOOLEAN,
    response_time_ms INTEGER,
    score_awarded INTEGER DEFAULT 0,
    streak_at_time INTEGER DEFAULT 0,
    submitted_at TIMESTAMP WITH TIME ZONE
);
```

### Refresh Tokens

```sql
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    revoked_at TIMESTAMP WITH TIME ZONE
);
```

### Audit Log

```sql
CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID REFERENCES users(id),
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(50),
    resource_id UUID,
    details JSONB,
    ip_address INET,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
```

---

## Redis Data Structures

### Session State (Hash)
```
Key: session:{pin}
Fields: state, quiz_id, host_id, current_question_index, question_start_time,
        question_duration_ms, participant_count, previous_state, allow_late_join
TTL: 14400 (4 hours)
```

### Leaderboard (Sorted Set)
```
Key: leaderboard:{pin}
Members: participant_id
Scores: total_score (higher = better)
TTL: 14400
```

### Participant Data (Hash per participant)
```
Key: participant:{pin}:{participant_id}
Fields: nickname, score, streak, multiplier, last_answer_time, is_connected,
        disconnect_time, is_flagged, fast_answer_count
TTL: 14400
```

### Session Participants Set
```
Key: session_participants:{pin}
Type: SET
Members: participant_id values
TTL: 14400
```

### Nickname Registry (uniqueness enforcement)
```
Key: nicknames:{pin}
Type: SET
Members: lowercase nicknames
TTL: 14400
```

### Answer Tracking (Hash per question)
```
Key: answers:{pin}:{question_index}
Fields: {participant_id}: "{answer}|{timestamp_ms}|{score}"
TTL: 14400
```

### Question Queue (List)
```
Key: questions:{pin}
Type: LIST
Values: JSON-serialized question objects (without correct_answer)
TTL: 14400
```

### Correct Answers (server-side only)
```
Key: correct_answers:{pin}
Fields: {question_index}: "B"
TTL: 14400
```

### Pending Messages for Disconnected Clients
```
Key: pending_messages:{pin}:{participant_id}
Type: LIST
TTL: 120 (2 minutes)
```

### Pub/Sub Channels
```
session:{pin}:broadcast     -- messages to all participants
session:{pin}:host          -- messages to host only
session:{pin}:participant:{id} -- targeted messages
```

### Redis Streams (Event Bus)
```
events:analytics    -- consumed by Analytics Service
events:audit        -- consumed by Audit Logger
```
