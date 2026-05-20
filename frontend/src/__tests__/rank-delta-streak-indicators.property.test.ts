// Feature: dynamic-scoring-leaderboard, Property 10: Rank Delta Indicator Mapping
// Feature: dynamic-scoring-leaderboard, Property 11: Streak Icon Mapping
import { describe, expect, it } from 'vitest';
import * as fc from 'fast-check';

import {
  getRankDeltaMapping,
  getStreakIconMapping,
} from '@/lib/leaderboardIndicators';

/**
 * Property-based tests for rank delta indicators and streak icons.
 *
 * **Validates: Requirements 3.3, 3.4, 6.3**
 *
 * Property 10: Rank Delta Indicator Mapping — For any rank_delta value:
 * - rank_delta > 0 → green upward arrow
 * - rank_delta < 0 → red downward arrow
 * - rank_delta = 0 → gray horizontal dash
 *
 * Property 11: Streak Icon Mapping — For any streak_count value:
 * - streak_count < 3 → no flame icon
 * - streak_count 3-4 → single flame icon
 * - streak_count ≥ 5 → double flame icon
 */

describe('Property 10: Rank Delta Indicator Mapping', () => {
  it('P10.1: positive rank_delta maps to green upward arrow', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 1, max: 1000 }),
        (rankDelta) => {
          const mapping = getRankDeltaMapping(rankDelta);

          expect(mapping.direction).toBe('up');
          expect(mapping.color).toBe('green');
          expect(mapping.testId).toBe('rank-delta-up');
        }
      ),
      { numRuns: 500 }
    );
  });

  it('P10.2: negative rank_delta maps to red downward arrow', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: -1000, max: -1 }),
        (rankDelta) => {
          const mapping = getRankDeltaMapping(rankDelta);

          expect(mapping.direction).toBe('down');
          expect(mapping.color).toBe('red');
          expect(mapping.testId).toBe('rank-delta-down');
        }
      ),
      { numRuns: 500 }
    );
  });

  it('P10.3: zero rank_delta maps to gray horizontal dash', () => {
    const mapping = getRankDeltaMapping(0);

    expect(mapping.direction).toBe('neutral');
    expect(mapping.color).toBe('gray');
    expect(mapping.testId).toBe('rank-delta-neutral');
  });

  it('P10.4: for any rank_delta, exactly one of up/down/neutral is returned', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: -1000, max: 1000 }),
        (rankDelta) => {
          const mapping = getRankDeltaMapping(rankDelta);

          // Exactly one direction
          const validDirections = ['up', 'down', 'neutral'] as const;
          expect(validDirections).toContain(mapping.direction);

          // Direction matches the sign of rankDelta
          if (rankDelta > 0) {
            expect(mapping.direction).toBe('up');
          } else if (rankDelta < 0) {
            expect(mapping.direction).toBe('down');
          } else {
            expect(mapping.direction).toBe('neutral');
          }
        }
      ),
      { numRuns: 500 }
    );
  });

  it('P10.5: color is always consistent with direction', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: -1000, max: 1000 }),
        (rankDelta) => {
          const mapping = getRankDeltaMapping(rankDelta);

          // Color must match direction
          if (mapping.direction === 'up') {
            expect(mapping.color).toBe('green');
          } else if (mapping.direction === 'down') {
            expect(mapping.color).toBe('red');
          } else {
            expect(mapping.color).toBe('gray');
          }
        }
      ),
      { numRuns: 500 }
    );
  });

  it('P10.6: testId is always consistent with direction', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: -1000, max: 1000 }),
        (rankDelta) => {
          const mapping = getRankDeltaMapping(rankDelta);

          if (mapping.direction === 'up') {
            expect(mapping.testId).toBe('rank-delta-up');
          } else if (mapping.direction === 'down') {
            expect(mapping.testId).toBe('rank-delta-down');
          } else {
            expect(mapping.testId).toBe('rank-delta-neutral');
          }
        }
      ),
      { numRuns: 500 }
    );
  });
});

describe('Property 11: Streak Icon Mapping', () => {
  it('P11.1: streak_count < 3 displays no flame icon', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 0, max: 2 }),
        (streakCount) => {
          const mapping = getStreakIconMapping(streakCount);

          expect(mapping.iconType).toBe('none');
          expect(mapping.testId).toBeNull();
          expect(mapping.hasFlame).toBe(false);
        }
      ),
      { numRuns: 500 }
    );
  });

  it('P11.2: streak_count 3-4 displays single flame icon', () => {
    fc.assert(
      fc.property(
        fc.constantFrom(3, 4),
        (streakCount) => {
          const mapping = getStreakIconMapping(streakCount);

          expect(mapping.iconType).toBe('single-flame');
          expect(mapping.testId).toBe('streak-single-flame');
          expect(mapping.hasFlame).toBe(true);
        }
      ),
      { numRuns: 500 }
    );
  });

  it('P11.3: streak_count >= 5 displays double flame icon', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 5, max: 1000 }),
        (streakCount) => {
          const mapping = getStreakIconMapping(streakCount);

          expect(mapping.iconType).toBe('double-flame');
          expect(mapping.testId).toBe('streak-double-flame');
          expect(mapping.hasFlame).toBe(true);
        }
      ),
      { numRuns: 500 }
    );
  });

  it('P11.4: for any streak_count >= 0, exactly one icon type is returned', () => {
    fc.assert(
      fc.property(
        fc.nat({ max: 1000 }),
        (streakCount) => {
          const mapping = getStreakIconMapping(streakCount);

          const validTypes = ['none', 'single-flame', 'double-flame'] as const;
          expect(validTypes).toContain(mapping.iconType);

          // Verify the mapping is correct for the given streak count
          if (streakCount < 3) {
            expect(mapping.iconType).toBe('none');
          } else if (streakCount <= 4) {
            expect(mapping.iconType).toBe('single-flame');
          } else {
            expect(mapping.iconType).toBe('double-flame');
          }
        }
      ),
      { numRuns: 500 }
    );
  });

  it('P11.5: hasFlame is true if and only if streak_count >= 3', () => {
    fc.assert(
      fc.property(
        fc.nat({ max: 1000 }),
        (streakCount) => {
          const mapping = getStreakIconMapping(streakCount);

          if (streakCount >= 3) {
            expect(mapping.hasFlame).toBe(true);
          } else {
            expect(mapping.hasFlame).toBe(false);
          }
        }
      ),
      { numRuns: 500 }
    );
  });

  it('P11.6: testId is non-null if and only if a flame is displayed', () => {
    fc.assert(
      fc.property(
        fc.nat({ max: 1000 }),
        (streakCount) => {
          const mapping = getStreakIconMapping(streakCount);

          if (mapping.hasFlame) {
            expect(mapping.testId).not.toBeNull();
            expect(['streak-single-flame', 'streak-double-flame']).toContain(mapping.testId);
          } else {
            expect(mapping.testId).toBeNull();
          }
        }
      ),
      { numRuns: 500 }
    );
  });
});
