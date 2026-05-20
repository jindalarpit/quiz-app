import { beforeEach, describe, expect, it } from 'vitest';

import { computeScoreBreakdown } from '@/lib/computeScoreBreakdown';
import { useSessionStore } from '@/stores/sessionStore';
import type { LeaderboardUpdateEntry } from '@/types';

/**
 * Unit tests for the leaderboard.updated WebSocket event handling.
 *
 * Tests:
 * - computeScoreBreakdown utility function
 * - Integration of leaderboard.updated event with SessionStore
 *
 * _Requirements: 3.1, 5.1, 5.2_
 */

function createMockEntry(overrides: Partial<LeaderboardUpdateEntry> = {}): LeaderboardUpdateEntry {
  return {
    participantId: 'participant-1',
    nickname: 'Player1',
    cumulativeScore: 1000,
    roundScore: 920,
    rank: 1,
    rankDelta: 2,
    streakCount: 0,
    streakMultiplier: 1,
    ...overrides,
  };
}

describe('computeScoreBreakdown', () => {
  it('should return zero breakdown for undefined entry', () => {
    const breakdown = computeScoreBreakdown(undefined);

    expect(breakdown.baseComponent).toBe(0);
    expect(breakdown.speedBonus).toBe(0);
    expect(breakdown.streakMultiplier).toBe(1);
    expect(breakdown.totalScore).toBe(0);
    expect(breakdown.speedPercentage).toBe(0);
    expect(breakdown.isCorrect).toBe(false);
  });

  it('should return zero breakdown for entry with roundScore = 0 (incorrect answer)', () => {
    const entry = createMockEntry({ roundScore: 0 });
    const breakdown = computeScoreBreakdown(entry);

    expect(breakdown.baseComponent).toBe(0);
    expect(breakdown.speedBonus).toBe(0);
    expect(breakdown.streakMultiplier).toBe(1);
    expect(breakdown.totalScore).toBe(0);
    expect(breakdown.speedPercentage).toBe(0);
    expect(breakdown.isCorrect).toBe(false);
  });

  it('should return zero breakdown for entry with negative roundScore', () => {
    const entry = createMockEntry({ roundScore: -100 });
    const breakdown = computeScoreBreakdown(entry);

    expect(breakdown.isCorrect).toBe(false);
    expect(breakdown.totalScore).toBe(0);
  });

  it('should compute correct breakdown for a correct answer without streak', () => {
    const entry = createMockEntry({ roundScore: 920, streakMultiplier: 1 });
    const breakdown = computeScoreBreakdown(entry);

    expect(breakdown.isCorrect).toBe(true);
    expect(breakdown.totalScore).toBe(920);
    expect(breakdown.streakMultiplier).toBe(1);
    // baseComponent = 1000 * (1 - 0.7) = 300
    expect(breakdown.baseComponent).toBe(300);
    // speedBonus = 920 - 300 = 620
    expect(breakdown.speedBonus).toBe(620);
    // speedPercentage = round((920 / 1000) * 100) = 92
    expect(breakdown.speedPercentage).toBe(92);
  });

  it('should compute correct breakdown for a correct answer with 2x streak', () => {
    // roundScore = 1840 (920 * 2)
    const entry = createMockEntry({ roundScore: 1840, streakMultiplier: 2, streakCount: 3 });
    const breakdown = computeScoreBreakdown(entry);

    expect(breakdown.isCorrect).toBe(true);
    expect(breakdown.totalScore).toBe(1840);
    expect(breakdown.streakMultiplier).toBe(2);
    // scoreBeforeMultiplier = round(1840 / 2) = 920
    // baseComponent = 300
    expect(breakdown.baseComponent).toBe(300);
    // speedBonus = 920 - 300 = 620
    expect(breakdown.speedBonus).toBe(620);
    // speedPercentage = round((920 / 1000) * 100) = 92
    expect(breakdown.speedPercentage).toBe(92);
  });

  it('should compute correct breakdown for a correct answer with 3x streak', () => {
    // roundScore = 2760 (920 * 3)
    const entry = createMockEntry({ roundScore: 2760, streakMultiplier: 3, streakCount: 5 });
    const breakdown = computeScoreBreakdown(entry);

    expect(breakdown.isCorrect).toBe(true);
    expect(breakdown.totalScore).toBe(2760);
    expect(breakdown.streakMultiplier).toBe(3);
    // scoreBeforeMultiplier = round(2760 / 3) = 920
    expect(breakdown.baseComponent).toBe(300);
    expect(breakdown.speedBonus).toBe(620);
    expect(breakdown.speedPercentage).toBe(92);
  });

  it('should compute breakdown for minimum score (answered at time limit)', () => {
    // Minimum score for Speed Matters mode = 300 (30% of 1000)
    const entry = createMockEntry({ roundScore: 300, streakMultiplier: 1 });
    const breakdown = computeScoreBreakdown(entry);

    expect(breakdown.isCorrect).toBe(true);
    expect(breakdown.totalScore).toBe(300);
    expect(breakdown.baseComponent).toBe(300);
    expect(breakdown.speedBonus).toBe(0);
    expect(breakdown.speedPercentage).toBe(30);
  });

  it('should compute breakdown for maximum score (answered instantly)', () => {
    const entry = createMockEntry({ roundScore: 1000, streakMultiplier: 1 });
    const breakdown = computeScoreBreakdown(entry);

    expect(breakdown.isCorrect).toBe(true);
    expect(breakdown.totalScore).toBe(1000);
    expect(breakdown.baseComponent).toBe(300);
    expect(breakdown.speedBonus).toBe(700);
    expect(breakdown.speedPercentage).toBe(100);
  });

  it('should handle streakMultiplier of 0 gracefully (treat as 1)', () => {
    const entry = createMockEntry({ roundScore: 500, streakMultiplier: 0 });
    const breakdown = computeScoreBreakdown(entry);

    expect(breakdown.isCorrect).toBe(true);
    expect(breakdown.streakMultiplier).toBe(1);
    expect(breakdown.totalScore).toBe(500);
  });
});

describe('leaderboard.updated event integration with SessionStore', () => {
  beforeEach(() => {
    useSessionStore.getState().resetSession();
  });

  it('should update store entries and compute breakdown for participant', () => {
    const store = useSessionStore.getState();
    store.setParticipantId('participant-1');

    const entries: LeaderboardUpdateEntry[] = [
      createMockEntry({ participantId: 'participant-1', roundScore: 920, rank: 1, streakMultiplier: 1 }),
      createMockEntry({ participantId: 'participant-2', roundScore: 800, rank: 2 }),
    ];

    // Simulate the handler logic
    const accepted = store.updateLeaderboardEntries(entries, 1);
    expect(accepted).toBe(true);

    const myEntry = entries.find((e) => e.participantId === 'participant-1');
    const breakdown = computeScoreBreakdown(myEntry);
    store.setRoundScoreBreakdown(breakdown);

    const { leaderboardAnimation } = useSessionStore.getState();
    expect(leaderboardAnimation.currentEntries).toEqual(entries);
    expect(leaderboardAnimation.lastSequenceNumber).toBe(1);
    expect(leaderboardAnimation.animationPhase).toBe('position');
    expect(leaderboardAnimation.roundScoreBreakdown).not.toBeNull();
    expect(leaderboardAnimation.roundScoreBreakdown!.isCorrect).toBe(true);
    expect(leaderboardAnimation.roundScoreBreakdown!.totalScore).toBe(920);
  });

  it('should store previous entries for animation diffing on subsequent updates', () => {
    const store = useSessionStore.getState();

    const entries1: LeaderboardUpdateEntry[] = [
      createMockEntry({ participantId: 'p1', rank: 1, cumulativeScore: 1000 }),
      createMockEntry({ participantId: 'p2', rank: 2, cumulativeScore: 800 }),
    ];
    const entries2: LeaderboardUpdateEntry[] = [
      createMockEntry({ participantId: 'p2', rank: 1, cumulativeScore: 1800 }),
      createMockEntry({ participantId: 'p1', rank: 2, cumulativeScore: 1500 }),
    ];

    store.updateLeaderboardEntries(entries1, 1);
    store.updateLeaderboardEntries(entries2, 2);

    const { leaderboardAnimation } = useSessionStore.getState();
    expect(leaderboardAnimation.previousEntries).toEqual(entries1);
    expect(leaderboardAnimation.currentEntries).toEqual(entries2);
  });

  it('should discard out-of-order events and not update breakdown', () => {
    const store = useSessionStore.getState();
    store.setParticipantId('participant-1');

    const entries1: LeaderboardUpdateEntry[] = [
      createMockEntry({ participantId: 'participant-1', roundScore: 920 }),
    ];
    const entries2: LeaderboardUpdateEntry[] = [
      createMockEntry({ participantId: 'participant-1', roundScore: 500 }),
    ];

    store.updateLeaderboardEntries(entries1, 5);
    store.setRoundScoreBreakdown(computeScoreBreakdown(entries1[0]));

    // Stale event with lower sequence number
    const accepted = store.updateLeaderboardEntries(entries2, 3);
    expect(accepted).toBe(false);

    // Breakdown should remain from the first update
    const { leaderboardAnimation } = useSessionStore.getState();
    expect(leaderboardAnimation.roundScoreBreakdown!.totalScore).toBe(920);
    expect(leaderboardAnimation.currentEntries).toEqual(entries1);
  });

  it('should compute zero breakdown when participant is not in entries', () => {
    const store = useSessionStore.getState();
    store.setParticipantId('participant-99');

    const entries: LeaderboardUpdateEntry[] = [
      createMockEntry({ participantId: 'participant-1', roundScore: 920 }),
    ];

    store.updateLeaderboardEntries(entries, 1);
    const myEntry = entries.find((e) => e.participantId === 'participant-99');
    const breakdown = computeScoreBreakdown(myEntry);
    store.setRoundScoreBreakdown(breakdown);

    const { leaderboardAnimation } = useSessionStore.getState();
    expect(leaderboardAnimation.roundScoreBreakdown!.isCorrect).toBe(false);
    expect(leaderboardAnimation.roundScoreBreakdown!.totalScore).toBe(0);
  });
});
