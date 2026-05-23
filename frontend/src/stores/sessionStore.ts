import { create } from 'zustand';

import type {
  AnimationPhase,
  AnswerRevealData,
  LeaderboardAnimationState,
  LeaderboardEntry,
  LeaderboardUpdateEntry,
  Participant,
  QuestionDisplay,
  ScoreBreakdown,
  SessionState,
  SessionSummary,
} from '@/types';

export interface RevealLeaderboardEntry {
  rank: number;
  nickname: string;
  score: number;
}

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

  // Leaderboard animation state
  leaderboardAnimation: LeaderboardAnimationState;

  // Fallback leaderboard from leaderboard.update top5 data
  revealLeaderboard: RevealLeaderboardEntry[] | null;

  // Session end
  finalLeaderboard: LeaderboardEntry[];
  sessionSummary: SessionSummary | null;

  // Auto mode state
  autoModeEnabled: boolean;
  leaderboardDelay: number;
  autoAdvanceCountdown: number | null;

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
  setRevealLeaderboard: (entries: RevealLeaderboardEntry[]) => void;
  resetQuestion: () => void;
  resetSession: () => void;

  // Auto mode actions
  setAutoModeEnabled: (enabled: boolean) => void;
  setLeaderboardDelay: (seconds: number) => boolean;
  setAutoAdvanceCountdown: (remaining: number | null) => void;
  persistAutoModeToLocalStorage: () => void;
  restoreAutoModeFromLocalStorage: () => void;

  // Leaderboard animation actions
  updateLeaderboardEntries: (entries: LeaderboardUpdateEntry[], sequenceNumber: number, roundNumber?: number) => boolean;
  setAnimationPhase: (phase: AnimationPhase) => void;
  setRoundScoreBreakdown: (breakdown: ScoreBreakdown | null) => void;
  resetLeaderboardAnimation: () => void;
}

const initialLeaderboardAnimationState: LeaderboardAnimationState = {
  previousEntries: [],
  currentEntries: [],
  lastSequenceNumber: 0,
  animationPhase: 'idle',
  roundScoreBreakdown: null,
  roundNumber: 0,
};

export const useSessionStore = create<SessionStoreState>((set, get) => ({
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
  leaderboardAnimation: { ...initialLeaderboardAnimationState },
  revealLeaderboard: null,
  finalLeaderboard: [],
  sessionSummary: null,

  // Auto mode state
  autoModeEnabled: false,
  leaderboardDelay: 3,
  autoAdvanceCountdown: null,

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
  setRevealLeaderboard: (entries) => set({ revealLeaderboard: entries }),
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
      leaderboardAnimation: { ...initialLeaderboardAnimationState },
      revealLeaderboard: null,
      finalLeaderboard: [],
      sessionSummary: null,
      autoModeEnabled: false,
      leaderboardDelay: 3,
      autoAdvanceCountdown: null,
    }),

  // Auto mode actions
  setAutoModeEnabled: (enabled) => {
    set({ autoModeEnabled: enabled });
    get().persistAutoModeToLocalStorage();
  },

  setLeaderboardDelay: (seconds) => {
    if (!Number.isInteger(seconds) || seconds < 1 || seconds > 30) {
      return false;
    }
    set({ leaderboardDelay: seconds });
    get().persistAutoModeToLocalStorage();
    return true;
  },

  setAutoAdvanceCountdown: (remaining) => set({ autoAdvanceCountdown: remaining }),

  persistAutoModeToLocalStorage: () => {
    const { pin, autoModeEnabled, leaderboardDelay } = get();
    if (!pin) return;
    try {
      const data = {
        enabled: autoModeEnabled,
        leaderboardDelay,
        timestamp: Date.now(),
      };
      localStorage.setItem(`quiz-auto-mode-${pin}`, JSON.stringify(data));
    } catch (error) {
      console.warn('Failed to persist auto-mode settings to localStorage:', error);
    }
  },

  restoreAutoModeFromLocalStorage: () => {
    const { pin } = get();
    if (!pin) return;
    try {
      const raw = localStorage.getItem(`quiz-auto-mode-${pin}`);
      if (!raw) return;
      const data = JSON.parse(raw);
      if (typeof data.enabled === 'boolean' && typeof data.leaderboardDelay === 'number') {
        set({
          autoModeEnabled: data.enabled,
          leaderboardDelay: Number.isInteger(data.leaderboardDelay) && data.leaderboardDelay >= 1 && data.leaderboardDelay <= 30
            ? data.leaderboardDelay
            : 3,
        });
      }
    } catch (error) {
      console.warn('Failed to restore auto-mode settings from localStorage:', error);
    }
  },

  // Leaderboard animation actions
  updateLeaderboardEntries: (entries, sequenceNumber, roundNumber) => {
    const { leaderboardAnimation } = get();

    // Sequence number validation: discard events with sequence_number ≤ lastSequenceNumber
    if (sequenceNumber <= leaderboardAnimation.lastSequenceNumber) {
      return false;
    }

    set({
      leaderboardAnimation: {
        ...leaderboardAnimation,
        previousEntries: leaderboardAnimation.currentEntries,
        currentEntries: entries,
        lastSequenceNumber: sequenceNumber,
        animationPhase: 'position',
        roundNumber: roundNumber ?? leaderboardAnimation.roundNumber + 1,
      },
    });
    return true;
  },

  setAnimationPhase: (phase) =>
    set((s) => ({
      leaderboardAnimation: {
        ...s.leaderboardAnimation,
        animationPhase: phase,
      },
    })),

  setRoundScoreBreakdown: (breakdown) =>
    set((s) => ({
      leaderboardAnimation: {
        ...s.leaderboardAnimation,
        roundScoreBreakdown: breakdown,
      },
    })),

  resetLeaderboardAnimation: () =>
    set({
      leaderboardAnimation: { ...initialLeaderboardAnimationState },
    }),
}));
