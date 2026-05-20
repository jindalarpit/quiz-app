// Feature: dynamic-scoring-leaderboard, Property 8: Personalized Leaderboard View Construction
import { describe, expect, it } from 'vitest';
import * as fc from 'fast-check';

import { buildHostView, buildParticipantView } from '@/lib/buildLeaderboardView';
import type { LeaderboardUpdateEntry } from '@/types';

/**
 * Property-based tests for personalized leaderboard view construction.
 *
 * Property 8: Personalized Leaderboard View Construction
 * For any session with P participants (P ≥ 1) and a participant at rank R:
 * - The host view SHALL contain min(5, P) entries ordered by rank ascending.
 * - A participant's view SHALL contain the top-5 entries plus, if R > 5,
 *   the participant's own entry with one entry above (if R > 6) and one entry below (if R < P).
 * - The total entries in a participant view SHALL never exceed 8.
 *
 * **Validates: Requirements 3.6, 5.3**
 */

// Generator for a set of unique participants with contiguous ranks
function generateRankedEntries(count: number): fc.Arbitrary<LeaderboardUpdateEntry[]> {
  return fc
    .array(
      fc.record({
        participantId: fc.uuid(),
        nickname: fc.string({ minLength: 1, maxLength: 15 }),
        cumulativeScore: fc.nat({ max: 100000 }),
        roundScore: fc.nat({ max: 5000 }),
        rankDelta: fc.integer({ min: -20, max: 20 }),
        streakCount: fc.nat({ max: 10 }),
        streakMultiplier: fc.constantFrom(1, 2, 3),
      }),
      { minLength: count, maxLength: count }
    )
    .map((entries) => {
      // Ensure unique participant IDs and assign contiguous ranks
      const seen = new Set<string>();
      const unique = entries.filter((e) => {
        if (seen.has(e.participantId)) return false;
        seen.add(e.participantId);
        return true;
      });
      // Sort by cumulative score descending to assign ranks
      unique.sort((a, b) => b.cumulativeScore - a.cumulativeScore);
      return unique.map((e, i) => ({
        ...e,
        rank: i + 1,
      }));
    });
}

// Generator for a session with P participants (1 to 30)
const sessionArb = fc.integer({ min: 1, max: 30 }).chain((count) => generateRankedEntries(count));

// Generator for a session with a specific participant selected
const sessionWithParticipantArb = sessionArb
  .filter((entries) => entries.length >= 1)
  .chain((entries) =>
    fc.integer({ min: 0, max: entries.length - 1 }).map((idx) => ({
      entries,
      participantId: entries[idx].participantId,
      participantRank: entries[idx].rank,
    }))
  );

describe('Property 8: Personalized Leaderboard View Construction', () => {
  describe('Host View', () => {
    it('host view contains min(5, P) entries', () => {
      fc.assert(
        fc.property(sessionArb, (entries) => {
          const hostView = buildHostView(entries);
          const expectedCount = Math.min(5, entries.length);
          expect(hostView.length).toBe(expectedCount);
        }),
        { numRuns: 300 }
      );
    });

    it('host view entries are ordered by rank ascending', () => {
      fc.assert(
        fc.property(
          sessionArb.filter((e) => e.length >= 2),
          (entries) => {
            const hostView = buildHostView(entries);
            for (let i = 0; i < hostView.length - 1; i++) {
              expect(hostView[i].rank).toBeLessThan(hostView[i + 1].rank);
            }
          }
        ),
        { numRuns: 300 }
      );
    });

    it('host view contains only the top-ranked entries', () => {
      fc.assert(
        fc.property(sessionArb, (entries) => {
          const hostView = buildHostView(entries);
          const maxRankInView = Math.max(...hostView.map((e) => e.rank));
          expect(maxRankInView).toBeLessThanOrEqual(Math.min(5, entries.length));
        }),
        { numRuns: 300 }
      );
    });
  });

  describe('Participant View', () => {
    it('participant view never exceeds 8 entries', () => {
      fc.assert(
        fc.property(sessionWithParticipantArb, ({ entries, participantId }) => {
          const view = buildParticipantView(entries, participantId);
          expect(view.length).toBeLessThanOrEqual(8);
        }),
        { numRuns: 300 }
      );
    });

    it('participant in top 5 sees exactly min(5, P) entries', () => {
      fc.assert(
        fc.property(
          sessionWithParticipantArb.filter(({ participantRank }) => participantRank <= 5),
          ({ entries, participantId }) => {
            const view = buildParticipantView(entries, participantId);
            expect(view.length).toBe(Math.min(5, entries.length));
          }
        ),
        { numRuns: 300 }
      );
    });

    it('participant ranked below 5th always sees their own entry in the view', () => {
      fc.assert(
        fc.property(
          sessionWithParticipantArb.filter(
            ({ participantRank, entries }) => participantRank > 5 && entries.length > 5
          ),
          ({ entries, participantId }) => {
            const view = buildParticipantView(entries, participantId);
            const ownEntry = view.find((e) => e.participantId === participantId);
            expect(ownEntry).toBeDefined();
          }
        ),
        { numRuns: 300 }
      );
    });

    it('participant view always includes the top 5 entries when P >= 5', () => {
      fc.assert(
        fc.property(
          sessionWithParticipantArb.filter(({ entries }) => entries.length >= 5),
          ({ entries, participantId }) => {
            const view = buildParticipantView(entries, participantId);
            const top5Ranks = [1, 2, 3, 4, 5];
            for (const rank of top5Ranks) {
              const found = view.find((e) => e.rank === rank);
              expect(found).toBeDefined();
            }
          }
        ),
        { numRuns: 300 }
      );
    });

    it('participant ranked > 6 sees one entry above them in the view', () => {
      fc.assert(
        fc.property(
          sessionWithParticipantArb.filter(
            ({ participantRank, entries }) => participantRank > 6 && entries.length > 6
          ),
          ({ entries, participantId, participantRank }) => {
            const view = buildParticipantView(entries, participantId);
            const aboveEntry = view.find((e) => e.rank === participantRank - 1);
            expect(aboveEntry).toBeDefined();
          }
        ),
        { numRuns: 300 }
      );
    });

    it('participant not ranked last sees one entry below them in the view', () => {
      fc.assert(
        fc.property(
          sessionWithParticipantArb.filter(
            ({ participantRank, entries }) => participantRank > 5 && participantRank < entries.length
          ),
          ({ entries, participantId, participantRank }) => {
            const view = buildParticipantView(entries, participantId);
            const belowEntry = view.find((e) => e.rank === participantRank + 1);
            expect(belowEntry).toBeDefined();
          }
        ),
        { numRuns: 300 }
      );
    });

    it('participant ranked last does not have an entry below them', () => {
      fc.assert(
        fc.property(
          sessionWithParticipantArb.filter(
            ({ participantRank, entries }) => participantRank === entries.length && entries.length > 5
          ),
          ({ entries, participantId, participantRank }) => {
            const view = buildParticipantView(entries, participantId);
            const belowEntry = view.find((e) => e.rank === participantRank + 1);
            expect(belowEntry).toBeUndefined();
          }
        ),
        { numRuns: 300 }
      );
    });

    it('participant ranked 6th does not have a separate above entry (adjacent to top 5)', () => {
      fc.assert(
        fc.property(
          sessionWithParticipantArb.filter(
            ({ participantRank, entries }) => participantRank === 6 && entries.length >= 6
          ),
          ({ entries, participantId }) => {
            const view = buildParticipantView(entries, participantId);
            // Top 5 + own entry (rank 6) + possibly one below = max 7
            // The entry at rank 5 is already in top 5, so no separate "above" entry needed
            // View should be: top 5 + participant (rank 6) + one below (if exists)
            const maxExpected = entries.length > 6 ? 7 : 6;
            expect(view.length).toBeLessThanOrEqual(maxExpected);
          }
        ),
        { numRuns: 300 }
      );
    });
  });
});
