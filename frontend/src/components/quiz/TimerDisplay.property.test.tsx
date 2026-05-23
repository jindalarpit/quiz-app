// Feature: auto-mode-quiz-flow, Property 1: Timer expiry idempotence
/**
 * @vitest-environment jsdom
 */
import React from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import * as fc from 'fast-check';
import { render, act, cleanup } from '@testing-library/react';
import { TimerDisplay } from './TimerDisplay';

/**
 * Property-based tests for TimerDisplay timer expiry idempotence (Property 1).
 *
 * **Validates: Requirements 1.1, 1.2, 1.3**
 *
 * Property 1: Timer expiry idempotence — For any question with any time limit and
 * server timestamp, regardless of how many interval ticks occur after the timer has
 * expired (simulating browser throttling with delayed or batched ticks), the `onExpire`
 * callback SHALL be invoked at most once per question.
 */

beforeEach(() => {
  vi.useFakeTimers();
});

afterEach(() => {
  cleanup();
  vi.useRealTimers();
});

describe('Property 1: Timer expiry idempotence', () => {
  it('onExpire is called at most once regardless of tick count after expiry', () => {
    fc.assert(
      fc.property(
        // Generate random time limit between 1 and 300 seconds
        fc.integer({ min: 1, max: 300 }),
        // Generate a number of extra ticks after expiry (simulating batched/delayed ticks)
        fc.integer({ min: 1, max: 20 }),
        // Generate tick interval in ms (simulating browser throttling with variable intervals)
        fc.integer({ min: 100, max: 5000 }),
        (timeLimit, extraTicks, tickInterval) => {
          const onExpire = vi.fn();
          const baseTime = 1000000;

          // serverTimestamp is the time the question started
          const serverTimestamp = baseTime;

          // Set system time to the start
          vi.setSystemTime(baseTime);

          render(
            <TimerDisplay
              timeLimit={timeLimit}
              serverTimestamp={serverTimestamp}
              questionId="test-question-1"
              sessionState="QUESTION_OPEN"
              onExpire={onExpire}
            />
          );

          // Advance time past the expiry point
          // Move to exactly when the timer should expire
          const expiryMs = timeLimit * 1000;

          act(() => {
            vi.setSystemTime(baseTime + expiryMs + 1);
            vi.advanceTimersByTime(expiryMs + 1);
          });

          // Simulate multiple additional ticks after expiry (browser throttling / batched ticks)
          for (let i = 0; i < extraTicks; i++) {
            act(() => {
              vi.advanceTimersByTime(tickInterval);
            });
          }

          // onExpire should be called at most once
          expect(onExpire).toHaveBeenCalledTimes(1);

          cleanup();
        }
      ),
      { numRuns: 100 }
    );
  });

  it('onExpire is called at most once even with rapid successive ticks', () => {
    fc.assert(
      fc.property(
        // Generate random time limit between 1 and 300 seconds
        fc.integer({ min: 1, max: 300 }),
        // Generate number of rapid ticks (simulating many batched callbacks)
        fc.integer({ min: 5, max: 50 }),
        (timeLimit, rapidTicks) => {
          const onExpire = vi.fn();
          const baseTime = 1000000;
          const serverTimestamp = baseTime;

          vi.setSystemTime(baseTime);

          render(
            <TimerDisplay
              timeLimit={timeLimit}
              serverTimestamp={serverTimestamp}
              questionId="test-question-rapid"
              sessionState="QUESTION_OPEN"
              onExpire={onExpire}
            />
          );

          // Jump time well past expiry in one go, then fire many rapid ticks
          const wellPastExpiry = timeLimit * 1000 + 5000;
          vi.setSystemTime(baseTime + wellPastExpiry);

          // Fire many rapid ticks in quick succession (simulating browser releasing throttled callbacks)
          for (let i = 0; i < rapidTicks; i++) {
            act(() => {
              vi.advanceTimersByTime(100);
            });
          }

          // onExpire should be called at most once
          expect(onExpire.mock.calls.length).toBeLessThanOrEqual(1);

          cleanup();
        }
      ),
      { numRuns: 100 }
    );
  });

  it('onExpire is called at most once per question across varying server timestamps', () => {
    fc.assert(
      fc.property(
        // Generate random time limit between 1 and 300 seconds
        fc.integer({ min: 1, max: 300 }),
        // Generate a random server timestamp offset (simulating different start times)
        fc.integer({ min: 0, max: 100000 }),
        // Generate number of ticks after expiry
        fc.integer({ min: 2, max: 15 }),
        (timeLimit, timestampOffset, ticksAfterExpiry) => {
          const onExpire = vi.fn();
          const baseTime = 1000000 + timestampOffset;
          const serverTimestamp = baseTime;

          vi.setSystemTime(baseTime);

          render(
            <TimerDisplay
              timeLimit={timeLimit}
              serverTimestamp={serverTimestamp}
              questionId={`question-${timestampOffset}`}
              sessionState="QUESTION_OPEN"
              onExpire={onExpire}
            />
          );

          // Advance to just past expiry
          const expiryMs = timeLimit * 1000;
          act(() => {
            vi.setSystemTime(baseTime + expiryMs + 500);
            vi.advanceTimersByTime(expiryMs + 500);
          });

          // Continue ticking after expiry
          for (let i = 0; i < ticksAfterExpiry; i++) {
            act(() => {
              vi.setSystemTime(baseTime + expiryMs + 500 + (i + 1) * 1000);
              vi.advanceTimersByTime(1000);
            });
          }

          // onExpire should be called at most once
          expect(onExpire.mock.calls.length).toBeLessThanOrEqual(1);

          cleanup();
        }
      ),
      { numRuns: 100 }
    );
  });
});
