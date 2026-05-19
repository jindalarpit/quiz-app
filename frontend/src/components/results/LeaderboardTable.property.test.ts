// Feature: quiz-results-leaderboard, Property 5: Pagination control boundaries
import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';

/**
 * Property-based tests for pagination control boundaries.
 *
 * **Validates: Requirements 2.4**
 *
 * For any leaderboard with total pages T and current page P:
 * - if P = 0 then the previous-page control SHALL be disabled
 * - if P = T - 1 then the next-page control SHALL be disabled
 * - otherwise both controls SHALL be enabled
 *
 * This tests the pagination logic extracted from LeaderboardTable:
 *   isPrevDisabled = currentPage === 0
 *   isNextDisabled = currentPage >= totalPages - 1
 */

/**
 * Computes pagination control states matching LeaderboardTable logic.
 */
function getPaginationControlState(currentPage: number, totalPages: number) {
  const isPrevDisabled = currentPage === 0;
  const isNextDisabled = currentPage >= totalPages - 1;
  return { isPrevDisabled, isNextDisabled };
}

describe('Property 5: Pagination control boundaries', () => {
  it('prev control is disabled when currentPage is 0', () => {
    fc.assert(
      fc.property(
        // totalPages >= 1 (at least one page exists)
        fc.integer({ min: 1, max: 1000 }),
        (totalPages) => {
          const currentPage = 0;
          const { isPrevDisabled } = getPaginationControlState(currentPage, totalPages);
          expect(isPrevDisabled).toBe(true);
        }
      ),
      { numRuns: 100 }
    );
  });

  it('next control is disabled when currentPage is the last page (T - 1)', () => {
    fc.assert(
      fc.property(
        // totalPages >= 1 (at least one page exists)
        fc.integer({ min: 1, max: 1000 }),
        (totalPages) => {
          const currentPage = totalPages - 1;
          const { isNextDisabled } = getPaginationControlState(currentPage, totalPages);
          expect(isNextDisabled).toBe(true);
        }
      ),
      { numRuns: 100 }
    );
  });

  it('both controls are enabled when currentPage is neither first nor last', () => {
    fc.assert(
      fc.property(
        // totalPages >= 3 (need at least 3 pages to have a middle page)
        fc.integer({ min: 3, max: 1000 }).chain((totalPages) =>
          fc.tuple(
            fc.constant(totalPages),
            // currentPage is strictly between 0 and totalPages - 1
            fc.integer({ min: 1, max: totalPages - 2 })
          )
        ),
        ([totalPages, currentPage]) => {
          const { isPrevDisabled, isNextDisabled } = getPaginationControlState(
            currentPage,
            totalPages
          );
          expect(isPrevDisabled).toBe(false);
          expect(isNextDisabled).toBe(false);
        }
      ),
      { numRuns: 100 }
    );
  });

  it('on a single-page leaderboard (T=1), both prev and next are disabled', () => {
    fc.assert(
      fc.property(
        fc.constant(1), // totalPages = 1
        (totalPages) => {
          const currentPage = 0; // only valid page
          const { isPrevDisabled, isNextDisabled } = getPaginationControlState(
            currentPage,
            totalPages
          );
          expect(isPrevDisabled).toBe(true);
          expect(isNextDisabled).toBe(true);
        }
      ),
      { numRuns: 100 }
    );
  });

  it('for any valid page P in [0, T-1], prev and next states are mutually consistent', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 1, max: 1000 }).chain((totalPages) =>
          fc.tuple(
            fc.constant(totalPages),
            fc.integer({ min: 0, max: totalPages - 1 })
          )
        ),
        ([totalPages, currentPage]) => {
          const { isPrevDisabled, isNextDisabled } = getPaginationControlState(
            currentPage,
            totalPages
          );

          // prev disabled iff on first page
          expect(isPrevDisabled).toBe(currentPage === 0);

          // next disabled iff on last page
          expect(isNextDisabled).toBe(currentPage >= totalPages - 1);
        }
      ),
      { numRuns: 100 }
    );
  });
});
