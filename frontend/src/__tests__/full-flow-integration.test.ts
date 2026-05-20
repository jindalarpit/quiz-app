import { beforeEach, describe, expect, it } from 'vitest';

import { buildHostView, buildParticipantView } from '@/lib/buildLeaderboardView';
import { computeScoreBreakdown } from '@/lib/computeScoreBreakdown';
import { useSessionStore } from '@/stores/sessionStore';
import type { LeaderboardUpdateEntry, ScoreBreakdown } from '@/types';

/**
 * Integration tests for the full frontend flow:
 * WebSocket event received → store updated → components render correctly.
 *
 * Tests the data flow from receiving a leaderboard.updated WebSocket event
 * through the session store to the component rendering logic.
 *
 * Requirements: 5.5, 5.6, 7.7
 */

function createEntry(overrides: Partial<LeaderboardUpdateEntry>): LeaderboardUpdateEntry {
  return {
    participantId: 'p1',
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

/**
 * Simulates the full WebSocket event handling flow:
 * 1. Parse the leaderboard.updated event payload
 * 2. Validate sequence number
 * 3. Update session store
 * 4. Compute score breakdown for current participant
 * 5. Build appropriate view (host or participant)
 */
function simulateLeaderboardUpdateEvent(
  payload: {
    sessionId: string;
    roundNumber: number;
    sequenceNumber: number;
    timestamp: number;
    entries: LeaderboardUpdateEntry[];
  },
  currentParticipantId: string,
  isHost: boolean
): {
  accepted: boolean;
  breakdown: ScoreBreakdown | null;
  viewEntries: LeaderboardUpdateEntry[];
} {
  const store = useSessionStore.getState();

  // Step 1: Validate sequence number and update store
  const accepted = store.updateLeaderboardEntries(payload.entries, payload.sequenceNumber);

  if (!accepted) {
    return { accepted: false, breakdown: null, viewEntries: [] };
  }

  // Step 2: Compute score breakdown for current participant
  const myEntry = payload.entries.find((e) => e.participantId === currentParticipantId);
  const breakdown = computeScoreBreakdown(myEntry);
  store.setRoundScoreBreakdown(breakdown);

  // Step 3: Build appropriate view
  let viewEntries: LeaderboardUpdateEntry[];
  if (isHost) {
    viewEntries = buildHostView(payload.entries);
  } else {
    viewEntries = buildParticipantView(payload.entries, currentParticipantId);
  }

  return { accepted, breakdown, viewEntries };
}

describe('Full Flow Integration: WebSocket → Store → Render', () => {
  beforeEach(() => {
    useSessionStore.getState().resetSession();
  });

  describe('End-to-end: quiz scoring mode → session → answer reveal → WebSocket → render', () => {
    it('should process a Speed Matters mode leaderboard update correctly', () => {
      const store = useSessionStore.getState();
      store.setParticipantId('p1');

      // Simulate receiving a leaderboard.updated event after answer reveal
      // Score computed with Speed Matters mode (time_factor = 0.7)
      // p1 answered in 2s out of 20s: 1000 × (1 - (2000/20000) × 0.7) = 930
      const payload = {
        sessionId: 'session-123',
        roundNumber: 0,
        sequenceNumber: 1,
        timestamp: Date.now(),
        entries: [
          createEntry({ participantId: 'p1', nickname: 'Alice', roundScore: 930, cumulativeScore: 930, rank: 1, rankDelta: 0 }),
          createEntry({ participantId: 'p2', nickname: 'Bob', roundScore: 650, cumulativeScore: 650, rank: 2, rankDelta: 0 }),
          createEntry({ participantId: 'p3', nickname: 'Charlie', roundScore: 0, cumulativeScore: 0, rank: 3, rankDelta: 0 }),
        ],
      };

      const result = simulateLeaderboardUpdateEvent(payload, 'p1', false);

      expect(result.accepted).toBe(true);
      expect(result.breakdown).not.toBeNull();
      expect(result.breakdown!.isCorrect).toBe(true);
      expect(result.breakdown!.totalScore).toBe(930);
      expect(result.breakdown!.baseComponent).toBe(300); // 1000 × 0.3
      expect(result.breakdown!.speedBonus).toBe(630); // 930 - 300
      expect(result.breakdown!.speedPercentage).toBe(93); // round((930/1000) × 100)
    });

    it('should process incorrect answer with zero breakdown', () => {
      const store = useSessionStore.getState();
      store.setParticipantId('p3');

      const payload = {
        sessionId: 'session-123',
        roundNumber: 0,
        sequenceNumber: 1,
        timestamp: Date.now(),
        entries: [
          createEntry({ participantId: 'p1', roundScore: 930, rank: 1 }),
          createEntry({ participantId: 'p3', roundScore: 0, rank: 2, rankDelta: 0 }),
        ],
      };

      const result = simulateLeaderboardUpdateEvent(payload, 'p3', false);

      expect(result.accepted).toBe(true);
      expect(result.breakdown!.isCorrect).toBe(false);
      expect(result.breakdown!.totalScore).toBe(0);
      expect(result.breakdown!.baseComponent).toBe(0);
      expect(result.breakdown!.speedBonus).toBe(0);
    });

    it('should handle streak multiplier in score breakdown', () => {
      const store = useSessionStore.getState();
      store.setParticipantId('p1');

      // p1 has 2x streak: base score 825, multiplied = 1650
      const payload = {
        sessionId: 'session-123',
        roundNumber: 2,
        sequenceNumber: 3,
        timestamp: Date.now(),
        entries: [
          createEntry({
            participantId: 'p1',
            roundScore: 1650,
            cumulativeScore: 3000,
            rank: 1,
            rankDelta: 1,
            streakCount: 3,
            streakMultiplier: 2,
          }),
        ],
      };

      const result = simulateLeaderboardUpdateEvent(payload, 'p1', false);

      expect(result.breakdown!.isCorrect).toBe(true);
      expect(result.breakdown!.totalScore).toBe(1650);
      expect(result.breakdown!.streakMultiplier).toBe(2);
      // scoreBeforeMultiplier = round(1650 / 2) = 825
      expect(result.breakdown!.baseComponent).toBe(300);
      expect(result.breakdown!.speedBonus).toBe(525); // 825 - 300
      expect(result.breakdown!.speedPercentage).toBe(83); // round((825/1000) × 100)
    });

    it('should build host view with top 5 entries', () => {
      const store = useSessionStore.getState();
      store.setParticipantId('host-id');

      const entries: LeaderboardUpdateEntry[] = [];
      for (let i = 1; i <= 8; i++) {
        entries.push(createEntry({
          participantId: `p${i}`,
          nickname: `Player${i}`,
          rank: i,
          cumulativeScore: 1000 * (9 - i),
          roundScore: 500 - i * 50,
        }));
      }

      const payload = {
        sessionId: 'session-123',
        roundNumber: 1,
        sequenceNumber: 2,
        timestamp: Date.now(),
        entries,
      };

      const result = simulateLeaderboardUpdateEvent(payload, 'host-id', true);

      expect(result.accepted).toBe(true);
      expect(result.viewEntries).toHaveLength(5);
      expect(result.viewEntries[0].rank).toBe(1);
      expect(result.viewEntries[4].rank).toBe(5);
    });

    it('should build participant view with context rows when ranked below 5th', () => {
      const store = useSessionStore.getState();
      store.setParticipantId('p7');

      const entries: LeaderboardUpdateEntry[] = [];
      for (let i = 1; i <= 10; i++) {
        entries.push(createEntry({
          participantId: `p${i}`,
          nickname: `Player${i}`,
          rank: i,
          cumulativeScore: 1000 * (11 - i),
        }));
      }

      const payload = {
        sessionId: 'session-123',
        roundNumber: 1,
        sequenceNumber: 1,
        timestamp: Date.now(),
        entries,
      };

      const result = simulateLeaderboardUpdateEvent(payload, 'p7', false);

      expect(result.accepted).toBe(true);
      // Should include top 5 + rank 6 (above) + rank 7 (self) + rank 8 (below)
      expect(result.viewEntries.length).toBeLessThanOrEqual(8);
      // Must include the participant's own entry
      expect(result.viewEntries.some((e) => e.participantId === 'p7')).toBe(true);
      // Must include top entries
      expect(result.viewEntries.some((e) => e.rank === 1)).toBe(true);
    });
  });

  describe('Sequence number validation and out-of-order event handling', () => {
    it('should accept events with increasing sequence numbers', () => {
      const store = useSessionStore.getState();
      store.setParticipantId('p1');

      const entries = [createEntry({ participantId: 'p1', roundScore: 500 })];

      const r1 = simulateLeaderboardUpdateEvent(
        { sessionId: 's1', roundNumber: 0, sequenceNumber: 1, timestamp: Date.now(), entries },
        'p1', false
      );
      const r2 = simulateLeaderboardUpdateEvent(
        { sessionId: 's1', roundNumber: 1, sequenceNumber: 2, timestamp: Date.now(), entries },
        'p1', false
      );
      const r3 = simulateLeaderboardUpdateEvent(
        { sessionId: 's1', roundNumber: 2, sequenceNumber: 3, timestamp: Date.now(), entries },
        'p1', false
      );

      expect(r1.accepted).toBe(true);
      expect(r2.accepted).toBe(true);
      expect(r3.accepted).toBe(true);
    });

    it('should discard out-of-order events (stale sequence number)', () => {
      const store = useSessionStore.getState();
      store.setParticipantId('p1');

      const freshEntries = [createEntry({ participantId: 'p1', roundScore: 900, cumulativeScore: 900 })];
      const staleEntries = [createEntry({ participantId: 'p1', roundScore: 500, cumulativeScore: 500 })];

      // Accept fresh event
      simulateLeaderboardUpdateEvent(
        { sessionId: 's1', roundNumber: 1, sequenceNumber: 5, timestamp: Date.now(), entries: freshEntries },
        'p1', false
      );

      // Stale event should be rejected
      const staleResult = simulateLeaderboardUpdateEvent(
        { sessionId: 's1', roundNumber: 0, sequenceNumber: 3, timestamp: Date.now(), entries: staleEntries },
        'p1', false
      );

      expect(staleResult.accepted).toBe(false);

      // Store should still have the fresh data
      const { leaderboardAnimation } = useSessionStore.getState();
      expect(leaderboardAnimation.currentEntries[0].cumulativeScore).toBe(900);
      expect(leaderboardAnimation.lastSequenceNumber).toBe(5);
    });

    it('should discard duplicate events (same sequence number)', () => {
      const store = useSessionStore.getState();
      store.setParticipantId('p1');

      const entries = [createEntry({ participantId: 'p1', roundScore: 700 })];

      simulateLeaderboardUpdateEvent(
        { sessionId: 's1', roundNumber: 0, sequenceNumber: 4, timestamp: Date.now(), entries },
        'p1', false
      );

      const duplicateEntries = [createEntry({ participantId: 'p1', roundScore: 999 })];
      const dupResult = simulateLeaderboardUpdateEvent(
        { sessionId: 's1', roundNumber: 0, sequenceNumber: 4, timestamp: Date.now(), entries: duplicateEntries },
        'p1', false
      );

      expect(dupResult.accepted).toBe(false);
      const { leaderboardAnimation } = useSessionStore.getState();
      expect(leaderboardAnimation.currentEntries[0].roundScore).toBe(700);
    });
  });

  describe('WebSocket reconnection with queued event delivery', () => {
    it('should process queued events in order after reconnection', () => {
      const store = useSessionStore.getState();
      store.setParticipantId('p1');

      // Simulate receiving queued events in order after reconnection
      const queuedEvents = [
        {
          sessionId: 's1', roundNumber: 0, sequenceNumber: 1, timestamp: Date.now(),
          entries: [createEntry({ participantId: 'p1', roundScore: 800, cumulativeScore: 800, rank: 1 })],
        },
        {
          sessionId: 's1', roundNumber: 1, sequenceNumber: 2, timestamp: Date.now(),
          entries: [createEntry({ participantId: 'p1', roundScore: 700, cumulativeScore: 1500, rank: 1 })],
        },
        {
          sessionId: 's1', roundNumber: 2, sequenceNumber: 3, timestamp: Date.now(),
          entries: [createEntry({ participantId: 'p1', roundScore: 900, cumulativeScore: 2400, rank: 1 })],
        },
      ];

      // Process each queued event in order
      for (const event of queuedEvents) {
        const result = simulateLeaderboardUpdateEvent(event, 'p1', false);
        expect(result.accepted).toBe(true);
      }

      // Final state should reflect the last event
      const { leaderboardAnimation } = useSessionStore.getState();
      expect(leaderboardAnimation.lastSequenceNumber).toBe(3);
      expect(leaderboardAnimation.currentEntries[0].cumulativeScore).toBe(2400);
      expect(leaderboardAnimation.currentEntries[0].roundScore).toBe(900);
    });

    it('should maintain previous entries for animation diffing after queued delivery', () => {
      const store = useSessionStore.getState();
      store.setParticipantId('p1');

      // First event
      simulateLeaderboardUpdateEvent(
        {
          sessionId: 's1', roundNumber: 0, sequenceNumber: 1, timestamp: Date.now(),
          entries: [createEntry({ participantId: 'p1', roundScore: 800, cumulativeScore: 800 })],
        },
        'p1', false
      );

      // Second event (simulating queued delivery)
      simulateLeaderboardUpdateEvent(
        {
          sessionId: 's1', roundNumber: 1, sequenceNumber: 2, timestamp: Date.now(),
          entries: [createEntry({ participantId: 'p1', roundScore: 700, cumulativeScore: 1500 })],
        },
        'p1', false
      );

      const { leaderboardAnimation } = useSessionStore.getState();
      // Previous entries should be from the first event
      expect(leaderboardAnimation.previousEntries[0].cumulativeScore).toBe(800);
      // Current entries should be from the second event
      expect(leaderboardAnimation.currentEntries[0].cumulativeScore).toBe(1500);
    });

    it('should set animation phase to position on each accepted event', () => {
      const store = useSessionStore.getState();
      store.setParticipantId('p1');

      const entries = [createEntry({ participantId: 'p1', roundScore: 500 })];

      simulateLeaderboardUpdateEvent(
        { sessionId: 's1', roundNumber: 0, sequenceNumber: 1, timestamp: Date.now(), entries },
        'p1', false
      );

      const { leaderboardAnimation } = useSessionStore.getState();
      expect(leaderboardAnimation.animationPhase).toBe('position');
    });

    it('should handle reconnection with no queued events gracefully', () => {
      const store = useSessionStore.getState();
      store.setParticipantId('p1');

      // No queued events - first event after reconnection
      const result = simulateLeaderboardUpdateEvent(
        {
          sessionId: 's1', roundNumber: 5, sequenceNumber: 6, timestamp: Date.now(),
          entries: [createEntry({ participantId: 'p1', roundScore: 600, cumulativeScore: 4000 })],
        },
        'p1', false
      );

      expect(result.accepted).toBe(true);
      expect(result.breakdown!.totalScore).toBe(600);
    });
  });

  describe('Rank delta and animation state transitions', () => {
    it('should track rank changes across multiple rounds', () => {
      const store = useSessionStore.getState();
      store.setParticipantId('p2');

      // Round 1: p1 is rank 1, p2 is rank 2
      simulateLeaderboardUpdateEvent(
        {
          sessionId: 's1', roundNumber: 0, sequenceNumber: 1, timestamp: Date.now(),
          entries: [
            createEntry({ participantId: 'p1', rank: 1, rankDelta: 0, cumulativeScore: 900 }),
            createEntry({ participantId: 'p2', rank: 2, rankDelta: 0, cumulativeScore: 700 }),
          ],
        },
        'p2', false
      );

      // Round 2: p2 overtakes p1
      simulateLeaderboardUpdateEvent(
        {
          sessionId: 's1', roundNumber: 1, sequenceNumber: 2, timestamp: Date.now(),
          entries: [
            createEntry({ participantId: 'p2', rank: 1, rankDelta: 1, cumulativeScore: 1700 }),
            createEntry({ participantId: 'p1', rank: 2, rankDelta: -1, cumulativeScore: 1400 }),
          ],
        },
        'p2', false
      );

      const { leaderboardAnimation } = useSessionStore.getState();
      // Current entries should show p2 at rank 1 with positive delta
      const p2Entry = leaderboardAnimation.currentEntries.find((e) => e.participantId === 'p2');
      expect(p2Entry).toBeDefined();
      expect(p2Entry!.rank).toBe(1);
      expect(p2Entry!.rankDelta).toBe(1);
    });

    it('should update score breakdown when participant rank changes', () => {
      const store = useSessionStore.getState();
      store.setParticipantId('p1');

      simulateLeaderboardUpdateEvent(
        {
          sessionId: 's1', roundNumber: 1, sequenceNumber: 2, timestamp: Date.now(),
          entries: [
            createEntry({ participantId: 'p1', rank: 3, rankDelta: -2, roundScore: 400, cumulativeScore: 1400 }),
          ],
        },
        'p1', false
      );

      const { leaderboardAnimation } = useSessionStore.getState();
      expect(leaderboardAnimation.roundScoreBreakdown!.totalScore).toBe(400);
      expect(leaderboardAnimation.roundScoreBreakdown!.isCorrect).toBe(true);
    });
  });
});
