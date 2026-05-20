// Feature: dynamic-scoring-leaderboard, Property 12: Monotonic Sequence Numbers
import { beforeEach, describe, expect, it } from 'vitest';
import * as fc from 'fast-check';

import { useSessionStore } from '@/stores/sessionStore';
import type { LeaderboardUpdateEntry } from '@/types';

/**
 * Property-based tests for monotonic sequence number enforcement.
 *
 * Property 12: Monotonic Sequence Numbers
 * For any sequence of leaderboard.updated events within a session, each event's
 * sequence_number SHALL be strictly greater than the previous event's sequence_number.
 * The frontend SHALL discard any event whose sequence_number is less than or equal
 * to the last processed sequence_number.
 *
 * **Validates: Requirements 5.7**
 */

// Generator for a single leaderboard entry
const leaderboardEntryArb: fc.Arbitrary<LeaderboardUpdateEntry> = fc.record({
  participantId: fc.uuid(),
  nickname: fc.string({ minLength: 1, maxLength: 20 }),
  cumulativeScore: fc.nat({ max: 100000 }),
  roundScore: fc.nat({ max: 5000 }),
  rank: fc.integer({ min: 1, max: 100 }),
  rankDelta: fc.integer({ min: -50, max: 50 }),
  streakCount: fc.nat({ max: 10 }),
  streakMultiplier: fc.constantFrom(1, 2, 3),
});

// Generator for a non-empty array of leaderboard entries
const entriesArb = fc.array(leaderboardEntryArb, { minLength: 1, maxLength: 10 });

// Generator for a sequence of events with sequence numbers (some valid, some stale)
const eventSequenceArb = fc.array(
  fc.record({
    entries: entriesArb,
    sequenceNumber: fc.integer({ min: 1, max: 1000 }),
  }),
  { minLength: 2, maxLength: 20 }
);

describe('Property 12: Monotonic Sequence Numbers', () => {
  beforeEach(() => {
    useSessionStore.getState().resetSession();
  });

  it('events with sequence_number ≤ last processed are discarded', () => {
    fc.assert(
      fc.property(eventSequenceArb, (events) => {
        // Reset store for each property run
        useSessionStore.getState().resetSession();

        let lastAcceptedSeqNum = 0;
        let lastAcceptedEntries: LeaderboardUpdateEntry[] = [];

        for (const event of events) {
          const store = useSessionStore.getState();
          const accepted = store.updateLeaderboardEntries(event.entries, event.sequenceNumber);

          if (event.sequenceNumber > lastAcceptedSeqNum) {
            // Event should be accepted
            expect(accepted).toBe(true);
            lastAcceptedSeqNum = event.sequenceNumber;
            lastAcceptedEntries = event.entries;
          } else {
            // Event should be discarded (sequence_number ≤ last processed)
            expect(accepted).toBe(false);
          }

          // After processing, the store should reflect the last accepted state
          const { leaderboardAnimation } = useSessionStore.getState();
          expect(leaderboardAnimation.lastSequenceNumber).toBe(lastAcceptedSeqNum);
          expect(leaderboardAnimation.currentEntries).toEqual(lastAcceptedEntries);
        }
      }),
      { numRuns: 200 }
    );
  });

  it('strictly increasing sequence numbers are always accepted', () => {
    fc.assert(
      fc.property(
        fc.array(entriesArb, { minLength: 2, maxLength: 15 }),
        (entryBatches) => {
          useSessionStore.getState().resetSession();

          // Process events with strictly increasing sequence numbers
          for (let i = 0; i < entryBatches.length; i++) {
            const seqNum = i + 1;
            const store = useSessionStore.getState();
            const accepted = store.updateLeaderboardEntries(entryBatches[i], seqNum);

            expect(accepted).toBe(true);

            const { leaderboardAnimation } = useSessionStore.getState();
            expect(leaderboardAnimation.lastSequenceNumber).toBe(seqNum);
            expect(leaderboardAnimation.currentEntries).toEqual(entryBatches[i]);
          }
        }
      ),
      { numRuns: 200 }
    );
  });

  it('equal sequence numbers are discarded (not just less than)', () => {
    fc.assert(
      fc.property(
        entriesArb,
        entriesArb,
        fc.integer({ min: 1, max: 1000 }),
        (entries1, entries2, seqNum) => {
          useSessionStore.getState().resetSession();

          const store = useSessionStore.getState();

          // First event accepted
          const accepted1 = store.updateLeaderboardEntries(entries1, seqNum);
          expect(accepted1).toBe(true);

          // Same sequence number should be discarded
          const accepted2 = useSessionStore.getState().updateLeaderboardEntries(entries2, seqNum);
          expect(accepted2).toBe(false);

          // Store should still have the first entries
          const { leaderboardAnimation } = useSessionStore.getState();
          expect(leaderboardAnimation.currentEntries).toEqual(entries1);
          expect(leaderboardAnimation.lastSequenceNumber).toBe(seqNum);
        }
      ),
      { numRuns: 200 }
    );
  });

  it('previous entries are preserved when stale events arrive', () => {
    fc.assert(
      fc.property(
        entriesArb,
        entriesArb,
        entriesArb,
        fc.integer({ min: 5, max: 1000 }),
        (entries1, entries2, staleEntries, baseSeqNum) => {
          useSessionStore.getState().resetSession();

          const store = useSessionStore.getState();

          // Accept first event
          store.updateLeaderboardEntries(entries1, baseSeqNum);

          // Accept second event (higher seq)
          useSessionStore.getState().updateLeaderboardEntries(entries2, baseSeqNum + 1);

          // Try stale event (lower seq)
          const staleSeqNum = baseSeqNum - 1;
          const accepted = useSessionStore.getState().updateLeaderboardEntries(staleEntries, staleSeqNum);
          expect(accepted).toBe(false);

          // Previous entries should be entries1 (from before the second update)
          const { leaderboardAnimation } = useSessionStore.getState();
          expect(leaderboardAnimation.previousEntries).toEqual(entries1);
          expect(leaderboardAnimation.currentEntries).toEqual(entries2);
          expect(leaderboardAnimation.lastSequenceNumber).toBe(baseSeqNum + 1);
        }
      ),
      { numRuns: 200 }
    );
  });
});
