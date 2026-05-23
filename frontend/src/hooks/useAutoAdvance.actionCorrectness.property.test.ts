// Feature: auto-mode-quiz-flow, Property 4: Auto-advance action correctness
/**
 * @vitest-environment jsdom
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import * as fc from 'fast-check';
import { renderHook, act } from '@testing-library/react';
import { useAutoAdvance } from './useAutoAdvance';

vi.mock('@/lib/api', () => ({
  api: {
    post: vi.fn().mockResolvedValue(undefined),
  },
}));

import { api } from '@/lib/api';

/**
 * Property-based tests for useAutoAdvance action correctness (Property 4).
 *
 * **Validates: Requirements 3.2, 5.1**
 *
 * Property 4: Auto-advance action correctness — For any auto-advance completion
 * (countdown reaching zero with auto mode still enabled), if the current question
 * is the last question then the end-session endpoint SHALL be called, otherwise
 * the next-question endpoint SHALL be called.
 */

beforeEach(() => {
  vi.useFakeTimers();
  vi.mocked(api.post).mockClear();
  vi.mocked(api.post).mockResolvedValue(undefined);
});

afterEach(() => {
  vi.useRealTimers();
});

describe('Property 4: Auto-advance action correctness', () => {
  it('calls /end when isLastQuestion is true, /next otherwise', () => {
    fc.assert(
      fc.property(
        // Generate random quiz lengths (1–100 questions)
        fc.integer({ min: 1, max: 100 }),
        // Generate a random current question position index
        fc.integer({ min: 0, max: 99 }),
        (quizLength, rawPosition) => {
          // Ensure position is within valid range for this quiz length
          const currentPosition = rawPosition % quizLength;
          const isLastQuestion = currentPosition === quizLength - 1;
          const pin = 'TEST-PIN';

          vi.mocked(api.post).mockClear();
          vi.mocked(api.post).mockResolvedValue(undefined);

          const { result, unmount } = renderHook(() =>
            useAutoAdvance({
              enabled: true,
              delaySeconds: 1,
              isLastQuestion,
              sessionState: 'REVEAL',
              isPaused: false,
              pin,
              onAdvance: vi.fn(),
            })
          );

          // Countdown should start at 1
          expect(result.current.countdown).toBe(1);

          // Advance timers by 1 second to trigger the countdown reaching 0
          act(() => {
            vi.advanceTimersByTime(1000);
          });

          // After countdown reaches 0, the hook should call the appropriate endpoint
          if (isLastQuestion) {
            expect(api.post).toHaveBeenCalledWith(`/api/sessions/${pin}/end`);
            expect(api.post).not.toHaveBeenCalledWith(`/api/sessions/${pin}/next`);
          } else {
            expect(api.post).toHaveBeenCalledWith(`/api/sessions/${pin}/next`);
            expect(api.post).not.toHaveBeenCalledWith(`/api/sessions/${pin}/end`);
          }

          unmount();
        }
      ),
      { numRuns: 100 }
    );
  });
});
