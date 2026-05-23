// Feature: auto-mode-quiz-flow, Property 3: Auto-advance countdown initiation
/**
 * @vitest-environment jsdom
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import * as fc from 'fast-check';
import { renderHook } from '@testing-library/react';
import { useAutoAdvance } from './useAutoAdvance';

/**
 * Property-based tests for useAutoAdvance countdown initiation (Property 3).
 *
 * **Validates: Requirements 3.1**
 *
 * Property 3: Auto-advance countdown initiation — For any entry into REVEAL state
 * with autoModeEnabled = true and a valid leaderboardDelay value in [1, 30], the
 * auto-advance countdown SHALL start with the configured delay value.
 */

beforeEach(() => {
  vi.useFakeTimers();
});

afterEach(() => {
  vi.useRealTimers();
});

describe('Property 3: Auto-advance countdown initiation', () => {
  it('countdown starts at the configured delaySeconds when enabled in REVEAL state', () => {
    fc.assert(
      fc.property(
        // Generate random valid delay values (integers 1-30)
        fc.integer({ min: 1, max: 30 }),
        (delaySeconds) => {
          const { result, unmount } = renderHook(() =>
            useAutoAdvance({
              enabled: true,
              delaySeconds,
              isLastQuestion: false,
              sessionState: 'REVEAL',
              isPaused: false,
              pin: 'TEST123',
              onAdvance: vi.fn(),
            })
          );

          // Assert that the initial countdown value equals delaySeconds
          expect(result.current.countdown).toBe(delaySeconds);
          // Assert isActive is true
          expect(result.current.isActive).toBe(true);

          unmount();
        }
      ),
      { numRuns: 100 }
    );
  });
});
