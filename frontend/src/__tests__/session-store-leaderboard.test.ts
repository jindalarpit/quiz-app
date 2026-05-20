import { describe, it, expect, beforeEach } from 'vitest';
import { useSessionStore } from '@/stores/sessionStore';
import type { LeaderboardUpdateEntry } from '@/types';

/**
 * Unit tests for SessionStore leaderboard animation state.
 *
 * Tests the LeaderboardAnimationState extension including:
 * - Initial state values
 * - Sequence number validation (discard events with sequence_number ≤ lastSequenceNumber)
 * - Animation phase transitions
 * - Score breakdown management
 * - Reset behavior
 *
 * _Requirements: 5.7_
 */

function createMockEntry(overrides: Partial<LeaderboardUpdateEntry> = {}): LeaderboardUpdateEntry {
  return {
    participantId: 'participant-1',
    nickname: 'Player1',
    cumulativeScore: 1000,
    roundScore: 500,
    rank: 1,
    rankDelta: 0,
    streakCount: 0,
    streakMultiplier: 1,
    ...overrides,
  };
}

describe('SessionStore - Leaderboard Animation State', () => {
  beforeEach(() => {
    useSessionStore.getState().resetSession();
  });

  describe('Initial state', () => {
    it('should have empty leaderboard animation state on initialization', () => {
      const { leaderboardAnimation } = useSessionStore.getState();

      expect(leaderboardAnimation.previousEntries).toEqual([]);
      expect(leaderboardAnimation.currentEntries).toEqual([]);
      expect(leaderboardAnimation.lastSequenceNumber).toBe(0);
      expect(leaderboardAnimation.animationPhase).toBe('idle');
      expect(leaderboardAnimation.roundScoreBreakdown).toBeNull();
    });
  });

  describe('updateLeaderboardEntries', () => {
    it('should accept events with sequence_number > lastSequenceNumber', () => {
      const entries = [createMockEntry()];
      const result = useSessionStore.getState().updateLeaderboardEntries(entries, 1);

      expect(result).toBe(true);
      const { leaderboardAnimation } = useSessionStore.getState();
      expect(leaderboardAnimation.currentEntries).toEqual(entries);
      expect(leaderboardAnimation.lastSequenceNumber).toBe(1);
    });

    it('should discard events with sequence_number equal to lastSequenceNumber', () => {
      const entries1 = [createMockEntry({ cumulativeScore: 1000 })];
      const entries2 = [createMockEntry({ cumulativeScore: 2000 })];

      useSessionStore.getState().updateLeaderboardEntries(entries1, 5);
      const result = useSessionStore.getState().updateLeaderboardEntries(entries2, 5);

      expect(result).toBe(false);
      const { leaderboardAnimation } = useSessionStore.getState();
      expect(leaderboardAnimation.currentEntries).toEqual(entries1);
      expect(leaderboardAnimation.lastSequenceNumber).toBe(5);
    });

    it('should discard events with sequence_number less than lastSequenceNumber', () => {
      const entries1 = [createMockEntry({ cumulativeScore: 1000 })];
      const entries2 = [createMockEntry({ cumulativeScore: 2000 })];

      useSessionStore.getState().updateLeaderboardEntries(entries1, 5);
      const result = useSessionStore.getState().updateLeaderboardEntries(entries2, 3);

      expect(result).toBe(false);
      const { leaderboardAnimation } = useSessionStore.getState();
      expect(leaderboardAnimation.currentEntries).toEqual(entries1);
      expect(leaderboardAnimation.lastSequenceNumber).toBe(5);
    });

    it('should move currentEntries to previousEntries on update', () => {
      const entries1 = [createMockEntry({ cumulativeScore: 1000 })];
      const entries2 = [createMockEntry({ cumulativeScore: 2000 })];

      useSessionStore.getState().updateLeaderboardEntries(entries1, 1);
      useSessionStore.getState().updateLeaderboardEntries(entries2, 2);

      const { leaderboardAnimation } = useSessionStore.getState();
      expect(leaderboardAnimation.previousEntries).toEqual(entries1);
      expect(leaderboardAnimation.currentEntries).toEqual(entries2);
    });

    it('should set animationPhase to position on successful update', () => {
      const entries = [createMockEntry()];
      useSessionStore.getState().updateLeaderboardEntries(entries, 1);

      const { leaderboardAnimation } = useSessionStore.getState();
      expect(leaderboardAnimation.animationPhase).toBe('position');
    });

    it('should not change animationPhase on discarded event', () => {
      useSessionStore.getState().updateLeaderboardEntries([createMockEntry()], 5);
      useSessionStore.getState().setAnimationPhase('idle');

      useSessionStore.getState().updateLeaderboardEntries([createMockEntry()], 3);

      const { leaderboardAnimation } = useSessionStore.getState();
      expect(leaderboardAnimation.animationPhase).toBe('idle');
    });

    it('should handle multiple entries in a single update', () => {
      const entries = [
        createMockEntry({ participantId: 'p1', rank: 1, cumulativeScore: 3000 }),
        createMockEntry({ participantId: 'p2', rank: 2, cumulativeScore: 2000 }),
        createMockEntry({ participantId: 'p3', rank: 3, cumulativeScore: 1000 }),
      ];

      useSessionStore.getState().updateLeaderboardEntries(entries, 1);

      const { leaderboardAnimation } = useSessionStore.getState();
      expect(leaderboardAnimation.currentEntries).toHaveLength(3);
      expect(leaderboardAnimation.currentEntries[0].participantId).toBe('p1');
      expect(leaderboardAnimation.currentEntries[2].participantId).toBe('p3');
    });
  });

  describe('setAnimationPhase', () => {
    it('should update the animation phase', () => {
      useSessionStore.getState().setAnimationPhase('score');

      const { leaderboardAnimation } = useSessionStore.getState();
      expect(leaderboardAnimation.animationPhase).toBe('score');
    });

    it('should cycle through all animation phases', () => {
      const phases: Array<'idle' | 'position' | 'score' | 'delta'> = ['idle', 'position', 'score', 'delta'];

      for (const phase of phases) {
        useSessionStore.getState().setAnimationPhase(phase);
        const { leaderboardAnimation } = useSessionStore.getState();
        expect(leaderboardAnimation.animationPhase).toBe(phase);
      }
    });
  });

  describe('setRoundScoreBreakdown', () => {
    it('should set the score breakdown', () => {
      const breakdown = {
        baseComponent: 300,
        speedBonus: 620,
        streakMultiplier: 2,
        totalScore: 1840,
        speedPercentage: 92,
        isCorrect: true,
      };

      useSessionStore.getState().setRoundScoreBreakdown(breakdown);

      const { leaderboardAnimation } = useSessionStore.getState();
      expect(leaderboardAnimation.roundScoreBreakdown).toEqual(breakdown);
    });

    it('should allow setting breakdown to null', () => {
      const breakdown = {
        baseComponent: 300,
        speedBonus: 620,
        streakMultiplier: 1,
        totalScore: 920,
        speedPercentage: 92,
        isCorrect: true,
      };

      useSessionStore.getState().setRoundScoreBreakdown(breakdown);
      useSessionStore.getState().setRoundScoreBreakdown(null);

      const { leaderboardAnimation } = useSessionStore.getState();
      expect(leaderboardAnimation.roundScoreBreakdown).toBeNull();
    });

    it('should set breakdown for incorrect answer', () => {
      const breakdown = {
        baseComponent: 0,
        speedBonus: 0,
        streakMultiplier: 1,
        totalScore: 0,
        speedPercentage: 0,
        isCorrect: false,
      };

      useSessionStore.getState().setRoundScoreBreakdown(breakdown);

      const { leaderboardAnimation } = useSessionStore.getState();
      expect(leaderboardAnimation.roundScoreBreakdown).toEqual(breakdown);
      expect(leaderboardAnimation.roundScoreBreakdown!.isCorrect).toBe(false);
    });
  });

  describe('resetLeaderboardAnimation', () => {
    it('should reset all leaderboard animation state to initial values', () => {
      // Set up some state
      useSessionStore.getState().updateLeaderboardEntries([createMockEntry()], 5);
      useSessionStore.getState().setAnimationPhase('delta');
      useSessionStore.getState().setRoundScoreBreakdown({
        baseComponent: 300,
        speedBonus: 620,
        streakMultiplier: 1,
        totalScore: 920,
        speedPercentage: 92,
        isCorrect: true,
      });

      // Reset
      useSessionStore.getState().resetLeaderboardAnimation();

      const { leaderboardAnimation } = useSessionStore.getState();
      expect(leaderboardAnimation.previousEntries).toEqual([]);
      expect(leaderboardAnimation.currentEntries).toEqual([]);
      expect(leaderboardAnimation.lastSequenceNumber).toBe(0);
      expect(leaderboardAnimation.animationPhase).toBe('idle');
      expect(leaderboardAnimation.roundScoreBreakdown).toBeNull();
    });
  });

  describe('resetSession', () => {
    it('should reset leaderboard animation state along with other session state', () => {
      useSessionStore.getState().updateLeaderboardEntries([createMockEntry()], 3);
      useSessionStore.getState().setAnimationPhase('score');

      useSessionStore.getState().resetSession();

      const { leaderboardAnimation } = useSessionStore.getState();
      expect(leaderboardAnimation.previousEntries).toEqual([]);
      expect(leaderboardAnimation.currentEntries).toEqual([]);
      expect(leaderboardAnimation.lastSequenceNumber).toBe(0);
      expect(leaderboardAnimation.animationPhase).toBe('idle');
      expect(leaderboardAnimation.roundScoreBreakdown).toBeNull();
    });
  });
});
