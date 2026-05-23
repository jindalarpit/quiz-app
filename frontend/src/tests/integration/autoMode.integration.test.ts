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

describe('Auto Mode Integration: auto-advance happy path', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    mockedApiPost.mockResolvedValue(undefined);
    // Reset the session store
    useSessionStore.getState().resetSession();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('auto mode enabled → enters REVEAL → countdown → auto-advances to next question', async () => {
    // Step 1: Set up the session store with autoModeEnabled=true, leaderboardDelay=3, pin set
    const store = useSessionStore.getState();
    store.setPin('TEST123');
    store.setAutoModeEnabled(true);
    store.setLeaderboardDelay(3);

    // Verify store state is correct
    const storeState = useSessionStore.getState();
    expect(storeState.autoModeEnabled).toBe(true);
    expect(storeState.leaderboardDelay).toBe(3);
    expect(storeState.pin).toBe('TEST123');

    // Step 2: Render the useAutoAdvance hook with enabled=true, sessionState='REVEAL',
    // isPaused=false, delaySeconds=3, isLastQuestion=false
    const { result } = renderHook(() =>
      useAutoAdvance({
        enabled: useSessionStore.getState().autoModeEnabled,
        delaySeconds: useSessionStore.getState().leaderboardDelay,
        isLastQuestion: false,
        sessionState: 'REVEAL',
        isPaused: false,
        pin: useSessionStore.getState().pin!,
      })
    );

    // Step 3: Verify countdown starts at 3
    expect(result.current.countdown).toBe(3);
    expect(result.current.isActive).toBe(true);

    // Step 4: Advance timers by 3 seconds
    act(() => {
      vi.advanceTimersByTime(3000);
    });

    // Flush the async advance call (api.post is async)
    await act(async () => {
      await vi.runAllTimersAsync();
    });

    // Step 5: Verify api.post was called with `/api/sessions/TEST123/next`
    expect(mockedApiPost).toHaveBeenCalledTimes(1);
    expect(mockedApiPost).toHaveBeenCalledWith('/api/sessions/TEST123/next');

    // Step 6: Verify countdown is now null (completed)
    expect(result.current.countdown).toBeNull();
  });
});
