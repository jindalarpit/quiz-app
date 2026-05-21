import { describe, expect, it } from 'vitest';
import * as fc from 'fast-check';

import type { SessionHistoryEntry } from '@/types';

/**
 * Pure function that filters sessions by quiz title using case-insensitive matching.
 * This mirrors the backend's case-insensitive search behavior for client-side testing.
 */
export function filterSessionsBySearch(
  sessions: SessionHistoryEntry[],
  search: string
): SessionHistoryEntry[] {
  if (!search) return sessions;
  const lowerSearch = search.toLowerCase();
  return sessions.filter((session) =>
    session.quizTitle.toLowerCase().includes(lowerSearch)
  );
}

/**
 * **Validates: Requirements 1.3**
 *
 * Property 2: Search filtering is case-insensitive
 * For any list of SessionHistoryEntry objects and any search string (1–100 characters),
 * the filtered results SHALL contain only entries whose quizTitle contains the search
 * string when both are compared case-insensitively, and SHALL contain all such matching entries.
 */
describe('searchFilter - Property 2: Search filtering is case-insensitive', () => {
  const sessionArb = fc.record({
    sessionId: fc.uuid(),
    quizTitle: fc.string({ minLength: 0, maxLength: 200 }),
    endedAt: fc.date().map((d) => d.toISOString()),
    participantCount: fc.integer({ min: 1, max: 100 }),
    durationSeconds: fc.integer({ min: 0, max: 86400 }),
  });

  it('filtered results contain only and all case-insensitive matches', () => {
    fc.assert(
      fc.property(
        fc.array(sessionArb, { minLength: 0, maxLength: 30 }),
        fc.string({ minLength: 1, maxLength: 100 }),
        (sessions, search) => {
          const result = filterSessionsBySearch(sessions, search);
          const lowerSearch = search.toLowerCase();

          // Every result must contain the search term (case-insensitive)
          for (const session of result) {
            expect(session.quizTitle.toLowerCase()).toContain(lowerSearch);
          }

          // Every session that matches must be in the result
          const expectedMatches = sessions.filter((s) =>
            s.quizTitle.toLowerCase().includes(lowerSearch)
          );
          expect(result).toHaveLength(expectedMatches.length);

          // Results preserve original order
          const resultIds = result.map((s) => s.sessionId);
          const expectedIds = expectedMatches.map((s) => s.sessionId);
          expect(resultIds).toEqual(expectedIds);
        }
      ),
      { numRuns: 100 }
    );
  });

  it('search is truly case-insensitive: same results regardless of search case', () => {
    fc.assert(
      fc.property(
        fc.array(sessionArb, { minLength: 0, maxLength: 20 }),
        fc.string({ minLength: 1, maxLength: 50 }),
        (sessions, search) => {
          const lowerResult = filterSessionsBySearch(sessions, search.toLowerCase());
          const upperResult = filterSessionsBySearch(sessions, search.toUpperCase());
          const mixedResult = filterSessionsBySearch(sessions, search);

          // All case variants produce the same set of results
          expect(lowerResult).toEqual(upperResult);
          expect(lowerResult).toEqual(mixedResult);
        }
      ),
      { numRuns: 100 }
    );
  });
});
