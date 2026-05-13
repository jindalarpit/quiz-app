import { create } from 'zustand';

import type {
  AnswerRevealData,
  LeaderboardEntry,
  Participant,
  QuestionDisplay,
  SessionState,
  SessionSummary,
} from '@/types';

interface SessionStoreState {
  // Session info
  pin: string | null;
  state: SessionState | null;
  participantId: string | null;
  nickname: string | null;

  // Participants (host view)
  participants: Participant[];
  participantCount: number;

  // Question state
  currentQuestion: QuestionDisplay | null;
  selectedAnswer: string | null;
  answerAcknowledged: boolean;
  timeRemaining: number;

  // Reveal state
  revealData: AnswerRevealData | null;

  // Leaderboard
  leaderboard: LeaderboardEntry[];
  myRank: number | null;
  myScore: number | null;

  // Session end
  finalLeaderboard: LeaderboardEntry[];
  sessionSummary: SessionSummary | null;

  // Actions
  setPin: (pin: string) => void;
  setState: (state: SessionState) => void;
  setParticipantId: (id: string) => void;
  setNickname: (nickname: string) => void;
  addParticipant: (participant: Participant) => void;
  setParticipants: (participants: Participant[]) => void;
  setParticipantCount: (count: number) => void;
  setCurrentQuestion: (question: QuestionDisplay) => void;
  setSelectedAnswer: (answer: string) => void;
  setAnswerAcknowledged: (ack: boolean) => void;
  setTimeRemaining: (time: number) => void;
  setRevealData: (data: AnswerRevealData) => void;
  setLeaderboard: (entries: LeaderboardEntry[]) => void;
  setMyRank: (rank: number) => void;
  setMyScore: (score: number) => void;
  setFinalLeaderboard: (entries: LeaderboardEntry[]) => void;
  setSessionSummary: (summary: SessionSummary) => void;
  resetQuestion: () => void;
  resetSession: () => void;
}

export const useSessionStore = create<SessionStoreState>((set) => ({
  pin: null,
  state: null,
  participantId: null,
  nickname: null,
  participants: [],
  participantCount: 0,
  currentQuestion: null,
  selectedAnswer: null,
  answerAcknowledged: false,
  timeRemaining: 0,
  revealData: null,
  leaderboard: [],
  myRank: null,
  myScore: null,
  finalLeaderboard: [],
  sessionSummary: null,

  setPin: (pin) => set({ pin }),
  setState: (state) => set({ state }),
  setParticipantId: (id) => set({ participantId: id }),
  setNickname: (nickname) => set({ nickname }),
  addParticipant: (participant) =>
    set((s) => ({
      participants: [...s.participants.filter((p) => p.id !== participant.id), participant],
      participantCount: s.participantCount + 1,
    })),
  setParticipants: (participants) => set({ participants }),
  setParticipantCount: (count) => set({ participantCount: count }),
  setCurrentQuestion: (question) =>
    set({ currentQuestion: question, selectedAnswer: null, answerAcknowledged: false, revealData: null }),
  setSelectedAnswer: (answer) => set({ selectedAnswer: answer }),
  setAnswerAcknowledged: (ack) => set({ answerAcknowledged: ack }),
  setTimeRemaining: (time) => set({ timeRemaining: time }),
  setRevealData: (data) => set({ revealData: data }),
  setLeaderboard: (entries) => set({ leaderboard: entries }),
  setMyRank: (rank) => set({ myRank: rank }),
  setMyScore: (score) => set({ myScore: score }),
  setFinalLeaderboard: (entries) => set({ finalLeaderboard: entries }),
  setSessionSummary: (summary) => set({ sessionSummary: summary }),
  resetQuestion: () =>
    set({ currentQuestion: null, selectedAnswer: null, answerAcknowledged: false, timeRemaining: 0, revealData: null }),
  resetSession: () =>
    set({
      pin: null,
      state: null,
      participantId: null,
      nickname: null,
      participants: [],
      participantCount: 0,
      currentQuestion: null,
      selectedAnswer: null,
      answerAcknowledged: false,
      timeRemaining: 0,
      revealData: null,
      leaderboard: [],
      myRank: null,
      myScore: null,
      finalLeaderboard: [],
      sessionSummary: null,
    }),
}));
