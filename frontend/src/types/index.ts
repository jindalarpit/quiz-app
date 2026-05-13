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

// ============ WebSocket Event Types ============
export type WSEventType =
  | 'session.joined'
  | 'session.started'
  | 'question.start'
  | 'question.closed'
  | 'question.reveal'
  | 'leaderboard.update'
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
