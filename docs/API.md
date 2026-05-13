# API Reference

## Service Endpoints

### Auth Service (port 8081)

| Endpoint | Method | Description |
|----------|--------|-------------|
| /api/auth/register | POST | Register new host account |
| /api/auth/login | POST | Email/password login, returns JWT + refresh token |
| /api/auth/refresh | POST | Refresh access token |
| /api/auth/oauth/google | GET | Initiate Google OAuth flow |
| /api/auth/oauth/github | GET | Initiate GitHub OAuth flow |
| /api/auth/oauth/callback | GET | OAuth callback handler |
| /api/auth/password/reset | POST | Request password reset |
| /api/auth/me | GET | Get current user profile |

### Quiz Service (port 8082)

| Endpoint | Method | Description |
|----------|--------|-------------|
| /api/quizzes | POST | Create new quiz |
| /api/quizzes | GET | List host's quizzes (paginated) |
| /api/quizzes/{id} | GET | Get quiz details |
| /api/quizzes/{id} | PUT | Update quiz |
| /api/quizzes/{id} | DELETE | Delete quiz |
| /api/quizzes/{id}/duplicate | POST | Duplicate quiz |
| /api/quizzes/{id}/questions | POST | Add question |
| /api/quizzes/{id}/questions/reorder | PUT | Reorder questions |
| /api/quizzes/{id}/questions/{qId} | PUT | Update question |
| /api/quizzes/{id}/questions/{qId} | DELETE | Delete question |

### Session Service (port 8083)

| Endpoint | Method | Description |
|----------|--------|-------------|
| /api/sessions | POST | Start new session (generates PIN) |
| /api/sessions/{pin}/join | POST | Join session with nickname |
| /api/sessions/{pin}/state | GET | Get current session state |
| /api/sessions/{pin}/next | POST | Advance to next question (host) |
| /api/sessions/{pin}/pause | POST | Pause session (host) |
| /api/sessions/{pin}/resume | POST | Resume session (host) |
| /api/sessions/{pin}/skip | POST | Skip current question (host) |
| /api/sessions/{pin}/end | POST | End session (host) |
| /api/sessions/{pin}/kick/{participantId} | POST | Remove participant (host) |

### WebSocket Service (port 8084)

Connection: `ws://host:8084/ws?token=<jwt>&pin=<session_pin>`

#### Server → Client Events

| Event | Description |
|-------|-------------|
| `session.joined` | Participant joined lobby |
| `session.started` | Host started the quiz |
| `question.start` | Question broadcast with options and timer |
| `question.timer_tick` | Timer sync correction |
| `question.closed` | Timer expired |
| `question.reveal` | Answer revealed with stats |
| `leaderboard.update` | Leaderboard data (top 5 + your rank) |
| `session.paused` | Session paused |
| `session.resumed` | Session resumed |
| `session.ended` | Final leaderboard and summary |
| `participant.kicked` | Participant removed |
| `clock.sync_response` | Clock sync reply |
| `heartbeat` | Keep-alive (every 15s) |

#### Client → Server Events

| Event | Description |
|-------|-------------|
| `answer.submit` | Submit answer for current question |
| `clock.sync_request` | Request clock synchronization |
| `heartbeat_ack` | Heartbeat acknowledgment |

### Analytics Service (port 8085)

| Endpoint | Method | Description |
|----------|--------|-------------|
| /api/analytics/sessions/{sessionId}/report | GET | Get session report |
| /api/analytics/sessions/{sessionId}/export | POST | Export report (CSV/PDF) |
| /api/analytics/quizzes/{quizId}/history | GET | Quiz session history |
| /api/analytics/admin/metrics | GET | Platform-wide metrics |
| /api/analytics/admin/retention/cleanup | POST | Trigger data cleanup |

## Authentication

All authenticated endpoints require a Bearer token in the Authorization header:

```
Authorization: Bearer <jwt_access_token>
```

JWT tokens expire after 24 hours. Use the `/api/auth/refresh` endpoint with a refresh token to obtain a new access token.

### Participant Authentication

Participants joining a session do not need a JWT. They authenticate via:
- Session PIN (6-digit alphanumeric code)
- Nickname (unique within the session)

## Rate Limiting

| Endpoint Category | Limit | Window |
|-------------------|-------|--------|
| Auth (login/register) | 5 requests | 1 minute |
| Authenticated API | 100 requests | 1 minute |
| Unauthenticated API | 20 requests | 1 minute |
| WebSocket messages | 10 messages | 1 second |
| Answer submission | 1 per question | Per participant |

## Error Response Format

```json
{
  "error": "RESOURCE_NOT_FOUND",
  "message": "Quiz with id 'abc-123' not found",
  "timestamp": "2024-01-15T10:30:00.123Z",
  "correlationId": "req-abc-123"
}
```

Common error codes:
- `VALIDATION_ERROR` (400)
- `UNAUTHORIZED` (401)
- `FORBIDDEN` (403)
- `RESOURCE_NOT_FOUND` (404)
- `DUPLICATE_RESOURCE` (409)
- `RATE_LIMIT_EXCEEDED` (429)
- `INTERNAL_ERROR` (500)
