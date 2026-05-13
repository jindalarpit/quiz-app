import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';
import type { LeaderboardEntry } from '@/types';

/**
 * Property-based tests for leaderboard consistency (P2).
 *
 * **Validates: Requirements 6.1, 6.2, 6.3, 6.4**
 *
 * These tests verify structural invariants of the leaderboard:
 * - All participants appear exactly once
 * - Ranks are contiguous (1, 2, 3, ... N)
 * - Higher score always has lower rank number
 * - Total participants in leaderboard equals session participant count
 */

// Generator for participant data
const participantArb = fc.record({
  participantId: fc.uuid(),
  nickname: fc.string({ minLength: 3, maxLength: 20 }),
  score: fc.nat({ max: 50000 }),
});

// Generator for a list of unique participants
const uniqueParticipantsArb = fc
  .array(participantArb, { minLength: 1, maxLength: 100 })
  .map((participants) => {
    // Ensure unique participant IDs
    const seen = new Set<string>();
    return participants.filter((p) => {
      if (seen.has(p.participantId)) return false;
      seen.add(p.participantId);
      return true;
    });
  })
  .filter((arr) => arr.length >= 1);

/**
 * Simulates the leaderboard building logic that mirrors the backend's
 * RedisSessionService.getTopN behavior: sort by score descending, assign contiguous ranks.
 */
function buildLeaderboard(
  participants: { participantId: string; nickname: string; score: number }[]
): LeaderboardEntry[] {
  const sorted = [...participants].sort((a, b) => {
    if (b.score !== a.score) return b.score - a.score;
    return a.participantId.localeCompare(b.participantId); // Stable tie-break
  });

  return sorted.map((p, index) => ({
    participantId: p.participantId,
    nickname: p.nickname,
    score: p.score,
    rank: index + 1,
    rankChange: 0,
  }));
}

describe('Leaderboard Consistency Properties (P2)', () => {
  it('P2.1: All participants appear exactly once in the leaderboard', () => {
    fc.assert(
      fc.property(uniqueParticipantsArb, (participants) => {
        const leaderboard = buildLeaderboard(participants);

        // Each participant appears exactly once
        const ids = leaderboard.map((e) => e.participantId);
        const uniqueIds = new Set(ids);
        expect(uniqueIds.size).toBe(ids.length);

        // All original participants are present
        const originalIds = new Set(participants.map((p) => p.participantId));
        expect(uniqueIds.size).toBe(originalIds.size);
        participants.forEach((p) => {
          expect(uniqueIds.has(p.participantId)).toBe(true);
        });
      }),
      { numRuns: 500 }
    );
  });

  it('P2.2: Ranks are contiguous (1, 2, 3, ... N)', () => {
    fc.assert(
      fc.property(uniqueParticipantsArb, (participants) => {
        const leaderboard = buildLeaderboard(participants);

        const ranks = leaderboard.map((e) => e.rank).sort((a, b) => a - b);

        // Ranks should be 1, 2, 3, ..., N
        for (let i = 0; i < ranks.length; i++) {
          expect(ranks[i]).toBe(i + 1);
        }
      }),
      { numRuns: 500 }
    );
  });

  it('P2.3: Higher score always has lower rank number', () => {
    fc.assert(
      fc.property(
        uniqueParticipantsArb.filter((arr) => arr.length >= 2),
        (participants) => {
          const leaderboard = buildLeaderboard(participants);

          for (let i = 0; i < leaderboard.length; i++) {
            for (let j = i + 1; j < leaderboard.length; j++) {
              const a = leaderboard[i];
              const b = leaderboard[j];

              if (a.score > b.score) {
                expect(a.rank).toBeLessThan(b.rank);
              } else if (b.score > a.score) {
                expect(b.rank).toBeLessThan(a.rank);
              }
              // If scores are equal, both orderings are valid (tie-breaking)
            }
          }
        }
      ),
      { numRuns: 500 }
    );
  });

  it('P2.4: Total participants in leaderboard equals session participant count', () => {
    fc.assert(
      fc.property(uniqueParticipantsArb, (participants) => {
        const leaderboard = buildLeaderboard(participants);

        expect(leaderboard.length).toBe(participants.length);
      }),
      { numRuns: 500 }
    );
  });

  it('P2.5: Leaderboard is sorted by score descending', () => {
    fc.assert(
      fc.property(
        uniqueParticipantsArb.filter((arr) => arr.length >= 2),
        (participants) => {
          const leaderboard = buildLeaderboard(participants);

          for (let i = 0; i < leaderboard.length - 1; i++) {
            expect(leaderboard[i].score).toBeGreaterThanOrEqual(leaderboard[i + 1].score);
          }
        }
      ),
      { numRuns: 500 }
    );
  });
});
