/**
 * @vitest-environment jsdom
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, renderHook } from '@testing-library/react';

import { useAutoAdvance } from '@/hooks/useAutoAdvance';
import { useSessionStore } from '@/stores/sessionStore';

vi.mock('@/lib/api', () => ({
  api: {
    post: vi.fn(),
  },
}));

import { api } from '@/lib/api';

const mockedApiPost = vi.mocked(api.post);

/**
 * Integration test: Auto mode on last question → REVEAL → countdown → calls /end endpoint.
 *
 * Validates the full auto-end flow when the quiz reaches the last question:
 * 1. Auto mode is enabled with leaderboardDelay configured
 * 2. Session enters REVEAL state on the last question
 * 3. Countdown starts at the configured delay
 * 4. When countdown reaches zero, /end endpoint is called (not /next)
 *
 * Requirements: 5.1, 5.2
 */
describe('Integration: Auto-end on last question', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    mockedApiPost.mockResolvedValue(undefined);

    // Set up the session store with auto mode enabled
    const store = useSessionStore.getState();
    store.resetSession();
    store.setPin('TEST99');
    store.setAutoModeEnabled(true);
    store.setLeaderboardDelay(5);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
    useSessionStore.getState().resetSession();
  });

  it('auto mode on last question → REVEAL → countdown → calls /end endpoint', async () => {
    // Render the useAutoAdvance hook simulating the last question in REVEAL state
    const { result } = renderHook(() =>
      useAutoAdvance({
        enabled: true,
        sessionState: 'REVEAL',
        isPaused: false,
        delaySeconds: 5,
        isLastQuestion: true,
        pin: 'TEST99',
      })
    );

    // Verify countdown starts at 5
    expect(result.current.countdown).toBe(5);
    expect(result.current.isActive).toBe(true);

    // Advance timers by 5 seconds (countdown reaches 0)
    act(() => {
      vi.advanceTimersByTime(5000);
    });

    // Flush the async api.post call
    await act(async () => {
      await vi.runAllTimersAsync();
    });

    // Verify api.post was called with /end (NOT /next)
    expect(mockedApiPost).toHaveBeenCalledTimes(1);
    expect(mockedApiPost).toHaveBeenCalledWith('/api/sessions/TEST99/end');
    expect(mockedApiPost).not.toHaveBeenCalledWith('/api/sessions/TEST99/next');

    // Verify countdown is now null (completed)
    expect(result.current.countdown).toBeNull();
  });
});
