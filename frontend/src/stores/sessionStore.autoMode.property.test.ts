// Feature: auto-mode-quiz-flow, Property 2: Auto mode state toggle consistency
import { describe, it, expect, beforeEach } from 'vitest';
import * as fc from 'fast-check';
import { useSessionStore } from './sessionStore';

/**
 * Property-based tests for auto mode state toggle consistency (Property 2).
 *
 * **Validates: Requirements 2.2**
 *
 * Property 2: Auto mode state toggle consistency — For any sequence of enable/disable
 * toggle actions, the stored `autoModeEnabled` state SHALL always equal the value of
 * the most recent toggle action.
 */

beforeEach(() => {
  useSessionStore.getState().resetSession();
});

describe('Property 2: Auto mode state toggle consistency', () => {
  it('final autoModeEnabled state equals the last toggle value in any sequence', () => {
    fc.assert(
      fc.property(
        // Generate random non-empty arrays of boolean values representing toggle sequences
        fc.array(fc.boolean(), { minLength: 1, maxLength: 50 }),
        (toggleSequence) => {
          // Reset store before each property run
          useSessionStore.getState().resetSession();

          // Apply each toggle in sequence
          for (const value of toggleSequence) {
            useSessionStore.getState().setAutoModeEnabled(value);
          }

          // The final state should equal the last toggle value
          const finalState = useSessionStore.getState().autoModeEnabled;
          const lastValue = toggleSequence[toggleSequence.length - 1];

          expect(finalState).toBe(lastValue);
        }
      ),
      { numRuns: 100 }
    );
  });
});
