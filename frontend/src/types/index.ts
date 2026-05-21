// ============ Auth Types ============
export interface User {
  id: string;
  email: string;
  displayName: string;
  role: 'ADMIN' | 'HOST';
  createdAt: string;
}

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  displayName: string;
}

// ============ Quiz Types ============
export type ScoringMode = 'SPEED_MATTERS' | 'BALANCED' | 'KNOWLEDGE_FIRST';

export type QuestionType = 'MCQ' | 'TRUE_FALSE' | 'POLL';

export interface QuestionOption {
  id: string;
  text: string;
}

export interface Question {
  id: string;
  quizId: string;
  type: QuestionType;
  text: string;
  options: QuestionOption[];
  correctAnswer: string | null;
  timeLimitSeconds: number;
  points: number;
  position: number;
  mediaUrl?: string;
}

export interface Quiz {
  id: string;
  ownerId: string;
  title: string;
  description: string;
  coverImageUrl?: string;
  isPublished: boolean;
  scoringMode?: ScoringMode;
  questions: Question[];
  createdAt: string;
  updatedAt: string;
}

export interface QuizListItem {
  id: string;
  title: string;
  description: string;
  questionCount: number;
  createdAt: string;
  updatedAt: string;
}

// ============ Session Types ============
export type SessionState =
  | 'CREATED'
  | 'LOBBY'
  | 'QUESTION_OPEN'
  | 'QUESTION_CLOSED'
  | 'REVEAL'
  | 'PAUSED'
  | 'ENDED';

export interface Participant {
  id: string;
  nickname: string;
  score: number;
  streak: number;
  isConnected: boolean;
}

export interface SessionInfo {
  pin: string;
  quizId: string;
  state: SessionState;
  participantCount: number;
  currentQuestionIndex: number;
}

export interface LeaderboardEntry {
  rank: number;
  participantId: string;
  nickname: string;
  score: number;
  rankChange: number;
}

export interface ParticipantLeaderboardView {
  ownRank: number;
  ownScore: number;
  rankChange: number;
  above: LeaderboardEntry | null;
  below: LeaderboardEntry | null;
}

export interface QuestionDisplay {
  questionId: string;
  text: string;
  options: QuestionOption[];
  type: QuestionType;
  timeLimit: number;
  serverTimestamp: number;
  mediaUrl?: string;
}

export interface AnswerRevealData {
  questionId: string;
  correctAnswer: string;
  stats: Record<string, number>;
  yourScore?: number;
  yourStreak?: number;
  yourMultiplier?: number;
}

export interface SessionSummary {
  totalQuestions: number;
  totalParticipants: number;
  durationSeconds: number;
}

// ============ Results & Leaderboard Types ============
export interface FinalLeaderboardEntry {
  rank: number;
  nickname: string;
  score: number;
  correctAnswers: number;
  totalAnswers: number;
  maxStreak: number;
  avgResponseTimeSec: number;
}

export interface PagedLeaderboardResponse {
  sessionId: string;
  entries: FinalLeaderboardEntry[];
  currentPage: number;
  totalPages: number;
  totalParticipants: number;
  pageSize: number;
}

// ============ Results Types ============
export type AnswerStatus = 'CORRECT' | 'INCORRECT' | 'UNANSWERED';

export interface QuestionResultEntry {
  questionNumber: number;
  status: AnswerStatus;
}

export interface ParticipantSelfResult {
  rank: number;
  score: number;
  correctAnswers: number;
  totalQuestions: number;
  maxStreak: number;
  avgResponseTimeSec: number;
  scoreDifference: number;
  aboveAverage: boolean;
  questionBreakdown: QuestionResultEntry[];
}

// ============ Quiz History Types ============
export interface SessionHistoryEntry {
  sessionId: string;
  quizTitle: string;
  endedAt: string;
  participantCount: number;
  durationSeconds: number;
}

export interface PagedHistoryResponse {
  sessions: SessionHistoryEntry[];
  currentPage: number;
  totalPages: number;
  totalSessions: number;
}

// ============ Leaderboard Animation Types ============
export interface LeaderboardUpdateEntry {
  participantId: string;
  nickname: string;
  cumulativeScore: number;
  roundScore: number;
  rank: number;
  rankDelta: number;
  streakCount: number;
  streakMultiplier: number;
}

export interface ScoreBreakdown {
  baseComponent: number;
  speedBonus: number;
  streakMultiplier: number;
  totalScore: number;
  speedPercentage: number;
  isCorrect: boolean;
}

export type AnimationPhase = 'idle' | 'position' | 'score' | 'delta';

export interface LeaderboardAnimationState {
  previousEntries: LeaderboardUpdateEntry[];
  currentEntries: LeaderboardUpdateEntry[];
  lastSequenceNumber: number;
  animationPhase: AnimationPhase;
  roundScoreBreakdown: ScoreBreakdown | null;
  roundNumber: number;
}

// ============ WebSocket Event Types ============
export type WSEventType =
  | 'session.joined'
  | 'session.started'
  | 'question.start'
  | 'question.closed'
  | 'question.reveal'
  | 'leaderboard.update'
  | 'leaderboard.updated'
  | 'session.paused'
  | 'session.resumed'
  | 'session.ended'
  | 'participant.kicked'
  | 'clock.sync_response'
  | 'heartbeat'
  | 'answer.ack'
  | 'error';

export interface WSMessage {
  type: WSEventType;
  payload: unknown;
}
