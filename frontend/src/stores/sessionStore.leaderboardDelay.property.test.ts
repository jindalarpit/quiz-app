// Feature: auto-mode-quiz-flow, Property 5: Leaderboard delay validation
import { beforeEach, describe, expect, it } from 'vitest';
import * as fc from 'fast-check';

import { useSessionStore } from '@/stores/sessionStore';

/**
 * Property-based tests for leaderboard delay validation.
 *
 * Property 5: Leaderboard delay validation
 * For any numeric value, setting the leaderboard delay SHALL succeed if and only if
 * the value is an integer in the range [1, 30]. Values outside this range SHALL be rejected.
 *
 * **Validates: Requirements 4.2, 4.3**
 */

describe('Property 5: Leaderboard delay validation', () => {
  beforeEach(() => {
    useSessionStore.getState().resetSession();
  });

  it('setLeaderboardDelay accepts if and only if value is an integer in [1, 30]', () => {
    fc.assert(
      fc.property(
        fc.oneof(
          // Valid integers in range [1, 30]
          fc.integer({ min: 1, max: 30 }),
          // Integers outside range (negatives, zero, large values)
          fc.integer({ min: -1000, max: 0 }),
          fc.integer({ min: 31, max: 10000 }),
          // Floats (non-integer values)
          fc.double({ min: -1000, max: 1000, noNaN: true, noDefaultInfinity: true }).filter(
            (n) => !Number.isInteger(n)
          ),
          // Edge cases: zero, negative
          fc.constant(0),
          fc.constant(-1),
          fc.constant(31),
          fc.constant(0.5),
          fc.constant(1.5),
          fc.constant(29.9)
        ),
        (value) => {
          // Reset store to known state before each check
          useSessionStore.getState().resetSession();
          const previousDelay = useSessionStore.getState().leaderboardDelay;

          const result = useSessionStore.getState().setLeaderboardDelay(value);

          const isValidInput = Number.isInteger(value) && value >= 1 && value <= 30;

          // Result should be true if and only if value is a valid integer in [1, 30]
          expect(result).toBe(isValidInput);

          if (isValidInput) {
            // When valid, the store should be updated
            expect(useSessionStore.getState().leaderboardDelay).toBe(value);
          } else {
            // When invalid, the store should remain unchanged
            expect(useSessionStore.getState().leaderboardDelay).toBe(previousDelay);
          }
        }
      ),
      { numRuns: 100 }
    );
  });

  it('all valid integers in [1, 30] are accepted', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 1, max: 30 }),
        (value) => {
          useSessionStore.getState().resetSession();

          const result = useSessionStore.getState().setLeaderboardDelay(value);

          expect(result).toBe(true);
          expect(useSessionStore.getState().leaderboardDelay).toBe(value);
        }
      ),
      { numRuns: 100 }
    );
  });

  it('non-integer values are always rejected and state is unchanged', () => {
    fc.assert(
      fc.property(
        fc.double({ min: -1000, max: 1000, noNaN: true, noDefaultInfinity: true }).filter(
          (n) => !Number.isInteger(n)
        ),
        (value) => {
          useSessionStore.getState().resetSession();
          const previousDelay = useSessionStore.getState().leaderboardDelay;

          const result = useSessionStore.getState().setLeaderboardDelay(value);

          expect(result).toBe(false);
          expect(useSessionStore.getState().leaderboardDelay).toBe(previousDelay);
        }
      ),
      { numRuns: 100 }
    );
  });

  it('integers outside [1, 30] are always rejected and state is unchanged', () => {
    fc.assert(
      fc.property(
        fc.oneof(
          fc.integer({ min: -10000, max: 0 }),
          fc.integer({ min: 31, max: 10000 })
        ),
        (value) => {
          useSessionStore.getState().resetSession();
          const previousDelay = useSessionStore.getState().leaderboardDelay;

          const result = useSessionStore.getState().setLeaderboardDelay(value);

          expect(result).toBe(false);
          expect(useSessionStore.getState().leaderboardDelay).toBe(previousDelay);
        }
      ),
      { numRuns: 100 }
    );
  });
});
