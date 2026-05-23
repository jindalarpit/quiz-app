/**
 * @vitest-environment jsdom
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useAutoAdvance, UseAutoAdvanceOptions } from './useAutoAdvance';

vi.mock('@/lib/api', () => ({
  api: {
    post: vi.fn(),
  },
}));

import { api } from '@/lib/api';

const mockedApiPost = vi.mocked(api.post);

function defaultOptions(overrides: Partial<UseAutoAdvanceOptions> = {}): UseAutoAdvanceOptions {
  return {
    enabled: true,
    delaySeconds: 5,
    isLastQuestion: false,
    sessionState: 'REVEAL',
    isPaused: false,
    pin: 'ABC123',
    onAdvance: vi.fn(),
    ...overrides,
  };
}

beforeEach(() => {
  vi.useFakeTimers();
  mockedApiPost.mockResolvedValue(undefined);
});

afterEach(() => {
  vi.useRealTimers();
  vi.restoreAllMocks();
});

describe('useAutoAdvance - unit tests', () => {
  describe('Countdown pauses when session is paused (Req 6.4)', () => {
    it('countdown stops and returns null when isPaused changes to true', () => {
      const { result, rerender } = renderHook(
        (props: UseAutoAdvanceOptions) => useAutoAdvance(props),
        { initialProps: defaultOptions() }
      );

      // Countdown should be active
      expect(result.current.countdown).toBe(5);
      expect(result.current.isActive).toBe(true);

      // Advance 2 seconds
      act(() => {
        vi.advanceTimersByTime(2000);
      });
      expect(result.current.countdown).toBe(3);

      // Pause the session
      rerender(defaultOptions({ isPaused: true }));

      // Countdown should be cancelled (null)
      expect(result.current.countdown).toBeNull();
      expect(result.current.isActive).toBe(false);
    });

    it('countdown does not start when isPaused is true from the beginning', () => {
      const { result } = renderHook(() =>
        useAutoAdvance(defaultOptions({ isPaused: true }))
      );

      expect(result.current.countdown).toBeNull();
      expect(result.current.isActive).toBe(false);
    });
  });

  describe('Countdown cancels when auto mode is disabled mid-countdown (Req 2.3, 6.3)', () => {
    it('countdown cancels immediately when enabled changes to false', () => {
      const { result, rerender } = renderHook(
        (props: UseAutoAdvanceOptions) => useAutoAdvance(props),
        { initialProps: defaultOptions() }
      );

      // Countdown should be active
      expect(result.current.countdown).toBe(5);
      expect(result.current.isActive).toBe(true);

      // Advance 1 second
      act(() => {
        vi.advanceTimersByTime(1000);
      });
      expect(result.current.countdown).toBe(4);

      // Disable auto mode
      rerender(defaultOptions({ enabled: false }));

      // Countdown should be cancelled
      expect(result.current.countdown).toBeNull();
      expect(result.current.isActive).toBe(false);
    });

    it('no API call is made after disabling auto mode mid-countdown', () => {
      const { rerender } = renderHook(
        (props: UseAutoAdvanceOptions) => useAutoAdvance(props),
        { initialProps: defaultOptions({ delaySeconds: 3 }) }
      );

      // Advance 1 second
      act(() => {
        vi.advanceTimersByTime(1000);
      });

      // Disable auto mode
      rerender(defaultOptions({ enabled: false, delaySeconds: 3 }));

      // Advance past what would have been the countdown end
      act(() => {
        vi.advanceTimersByTime(5000);
      });

      expect(mockedApiPost).not.toHaveBeenCalled();
    });
  });

  describe('Countdown cancels when state transitions away from REVEAL', () => {
    it('countdown cancels when sessionState changes from REVEAL to QUESTION_OPEN', () => {
      const { result, rerender } = renderHook(
        (props: UseAutoAdvanceOptions) => useAutoAdvance(props),
        { initialProps: defaultOptions() }
      );

      // Countdown should be active
      expect(result.current.countdown).toBe(5);
      expect(result.current.isActive).toBe(true);

      // Transition state away from REVEAL
      rerender(defaultOptions({ sessionState: 'QUESTION_OPEN' }));

      // Countdown should be cancelled
      expect(result.current.countdown).toBeNull();
      expect(result.current.isActive).toBe(false);
    });

    it('countdown cancels when sessionState changes from REVEAL to ENDED', () => {
      const { result, rerender } = renderHook(
        (props: UseAutoAdvanceOptions) => useAutoAdvance(props),
        { initialProps: defaultOptions() }
      );

      expect(result.current.isActive).toBe(true);

      // Transition to ENDED
      rerender(defaultOptions({ sessionState: 'ENDED' }));

      expect(result.current.countdown).toBeNull();
      expect(result.current.isActive).toBe(false);
    });
  });

  describe('API error handling falls back gracefully', () => {
    it('logs error and resets countdown to null when api.post rejects', async () => {
      const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
      const onAdvance = vi.fn();

      mockedApiPost.mockRejectedValueOnce(new Error('Network error'));

      const { result } = renderHook(() =>
        useAutoAdvance(defaultOptions({ delaySeconds: 2, onAdvance }))
      );

      expect(result.current.countdown).toBe(2);

      // Advance to countdown reaching 0
      act(() => {
        vi.advanceTimersByTime(2000);
      });

      // Flush the async advance call
      await act(async () => {
        await vi.runAllTimersAsync();
      });

      // Countdown should be reset to null (not stuck at 0)
      expect(result.current.countdown).toBeNull();
      // onAdvance should NOT have been called
      expect(onAdvance).not.toHaveBeenCalled();
      // Error should have been logged
      expect(consoleErrorSpy).toHaveBeenCalledWith(
        '[useAutoAdvance] Failed to auto-advance:',
        expect.any(Error)
      );

      consoleErrorSpy.mockRestore();
    });

    it('does not crash the hook when api.post throws', async () => {
      vi.spyOn(console, 'error').mockImplementation(() => {});
      mockedApiPost.mockRejectedValueOnce(new Error('Server error'));

      const { result } = renderHook(() =>
        useAutoAdvance(defaultOptions({ delaySeconds: 1 }))
      );

      // Advance to countdown reaching 0
      act(() => {
        vi.advanceTimersByTime(1000);
      });

      // Flush async
      await act(async () => {
        await vi.runAllTimersAsync();
      });

      // Hook should still be usable — countdown is null, not crashed
      expect(result.current.countdown).toBeNull();
      expect(result.current.isActive).toBe(false);
      expect(result.current.cancel).toBeInstanceOf(Function);

      vi.restoreAllMocks();
    });
  });
});
