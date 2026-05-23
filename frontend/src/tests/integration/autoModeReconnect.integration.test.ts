/**
 * @vitest-environment jsdom
 */
/**
 * Integration tests for auto-mode reconnection during REVEAL state.
 *
 * Tests that:
 * 1. Disconnecting during REVEAL cancels the countdown
 * 2. Reconnecting restarts the countdown from the beginning (full delaySeconds)
 * 3. localStorage persistence survives reconnection and restores settings correctly
 *
 * Requirements: 7.1, 7.2
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, renderHook } from '@testing-library/react';

import { useAutoAdvance, UseAutoAdvanceOptions } from '@/hooks/useAutoAdvance';
import { useSessionStore } from '@/stores/sessionStore';

vi.mock('@/lib/api', () => ({
  api: {
    post: vi.fn(),
  },
}));

import { api } from '@/lib/api';

const mockedApiPost = vi.mocked(api.post);

// Mock localStorage for node test environment
const localStorageMock = (() => {
  let store: Record<string, string> = {};
  return {
    getItem: (key: string) => store[key] ?? null,
    setItem: (key: string, value: string) => {
      store[key] = value;
    },
    removeItem: (key: string) => {
      delete store[key];
    },
    clear: () => {
      store = {};
    },
  };
})();

function defaultOptions(overrides: Partial<UseAutoAdvanceOptions> = {}): UseAutoAdvanceOptions {
  return {
    enabled: true,
    delaySeconds: 5,
    isLastQuestion: false,
    sessionState: 'REVEAL',
    isPaused: false,
    pin: 'TEST01',
    onAdvance: vi.fn(),
    ...overrides,
  };
}

beforeEach(() => {
  vi.useFakeTimers();
  mockedApiPost.mockResolvedValue(undefined);
  // Set up localStorage mock
  Object.defineProperty(globalThis, 'localStorage', {
    value: localStorageMock,
    writable: true,
    configurable: true,
  });
  localStorageMock.clear();
  // Reset session store
  useSessionStore.getState().resetSession();
});

afterEach(() => {
  vi.useRealTimers();
  vi.restoreAllMocks();
  localStorageMock.clear();
});

describe('Integration: Auto Mode Reconnection during REVEAL (Req 7.1, 7.2)', () => {
  describe('Disconnect during REVEAL → reconnect → countdown restarts from beginning', () => {
    it('should restart countdown from beginning (full delaySeconds) after reconnection during REVEAL', () => {
      // Step 1: Set up session store with autoModeEnabled=true, leaderboardDelay=5, pin set
      const store = useSessionStore.getState();
      store.setPin('TEST01');
      store.setAutoModeEnabled(true);
      store.setLeaderboardDelay(5);

      // Step 2: Persist auto mode settings to localStorage
      store.persistAutoModeToLocalStorage();

      // Step 3: Render the useAutoAdvance hook with enabled=true, sessionState='REVEAL', isPaused=false, delaySeconds=5
      const { result, rerender } = renderHook(
        (props: UseAutoAdvanceOptions) => useAutoAdvance(props),
        { initialProps: defaultOptions({ enabled: true, sessionState: 'REVEAL', isPaused: false, delaySeconds: 5 }) }
      );

      // Countdown should start at 5
      expect(result.current.countdown).toBe(5);
      expect(result.current.isActive).toBe(true);

      // Step 4: Advance timers by 2 seconds (countdown should be at 3)
      act(() => {
        vi.advanceTimersByTime(2000);
      });
      expect(result.current.countdown).toBe(3);

      // Step 5: Simulate "disconnect" by re-rendering with sessionState='LOBBY' — countdown should cancel
      rerender(defaultOptions({ enabled: true, sessionState: 'LOBBY', isPaused: false, delaySeconds: 5 }));

      expect(result.current.countdown).toBeNull();
      expect(result.current.isActive).toBe(false);

      // Step 6: Simulate "reconnect" by re-rendering with sessionState='REVEAL' again — countdown should restart from 5
      rerender(defaultOptions({ enabled: true, sessionState: 'REVEAL', isPaused: false, delaySeconds: 5 }));

      // Step 7: Verify countdown is back at 5 (restarted from beginning, not resumed from 3)
      expect(result.current.countdown).toBe(5);
      expect(result.current.isActive).toBe(true);
    });

    it('should restart countdown from beginning when enabled toggles off and back on during REVEAL', () => {
      const store = useSessionStore.getState();
      store.setPin('TEST01');
      store.setAutoModeEnabled(true);
      store.setLeaderboardDelay(5);
      store.persistAutoModeToLocalStorage();

      const { result, rerender } = renderHook(
        (props: UseAutoAdvanceOptions) => useAutoAdvance(props),
        { initialProps: defaultOptions({ enabled: true, sessionState: 'REVEAL', delaySeconds: 5 }) }
      );

      // Countdown starts at 5
      expect(result.current.countdown).toBe(5);

      // Advance 2 seconds
      act(() => {
        vi.advanceTimersByTime(2000);
      });
      expect(result.current.countdown).toBe(3);

      // Simulate disconnect by disabling (enabled=false)
      rerender(defaultOptions({ enabled: false, sessionState: 'REVEAL', delaySeconds: 5 }));
      expect(result.current.countdown).toBeNull();
      expect(result.current.isActive).toBe(false);

      // Simulate reconnect by re-enabling
      rerender(defaultOptions({ enabled: true, sessionState: 'REVEAL', delaySeconds: 5 }));

      // Countdown should restart from 5, not resume from 3
      expect(result.current.countdown).toBe(5);
      expect(result.current.isActive).toBe(true);
    });

    it('should not call API during disconnect period', () => {
      const store = useSessionStore.getState();
      store.setPin('TEST01');
      store.setAutoModeEnabled(true);
      store.setLeaderboardDelay(3);
      store.persistAutoModeToLocalStorage();

      const { result, rerender } = renderHook(
        (props: UseAutoAdvanceOptions) => useAutoAdvance(props),
        { initialProps: defaultOptions({ enabled: true, sessionState: 'REVEAL', delaySeconds: 3 }) }
      );

      // Advance 1 second
      act(() => {
        vi.advanceTimersByTime(1000);
      });
      expect(result.current.countdown).toBe(2);

      // Disconnect
      rerender(defaultOptions({ enabled: true, sessionState: 'LOBBY', delaySeconds: 3 }));

      // Advance past what would have been the original countdown end
      act(() => {
        vi.advanceTimersByTime(5000);
      });

      // No API call should have been made during disconnect
      expect(mockedApiPost).not.toHaveBeenCalled();
    });

    it('should complete countdown and call API after reconnection', async () => {
      const store = useSessionStore.getState();
      store.setPin('TEST01');
      store.setAutoModeEnabled(true);
      store.setLeaderboardDelay(5);
      store.persistAutoModeToLocalStorage();

      const onAdvance = vi.fn();
      const { result, rerender } = renderHook(
        (props: UseAutoAdvanceOptions) => useAutoAdvance(props),
        { initialProps: defaultOptions({ enabled: true, sessionState: 'REVEAL', delaySeconds: 5, onAdvance }) }
      );

      // Advance 2 seconds
      act(() => {
        vi.advanceTimersByTime(2000);
      });
      expect(result.current.countdown).toBe(3);

      // Disconnect
      rerender(defaultOptions({ enabled: true, sessionState: 'LOBBY', delaySeconds: 5, onAdvance }));
      expect(result.current.countdown).toBeNull();

      // Reconnect
      rerender(defaultOptions({ enabled: true, sessionState: 'REVEAL', delaySeconds: 5, onAdvance }));
      expect(result.current.countdown).toBe(5);

      // Advance full 5 seconds to complete the restarted countdown
      act(() => {
        vi.advanceTimersByTime(5000);
      });

      // Flush async advance call
      await act(async () => {
        await vi.runAllTimersAsync();
      });

      // API should have been called with /next (not last question)
      expect(mockedApiPost).toHaveBeenCalledWith('/api/sessions/TEST01/next');
      expect(onAdvance).toHaveBeenCalled();
    });
  });

  describe('localStorage persistence and restoration (Req 7.1)', () => {
    it('should persist auto mode settings to localStorage and restore them correctly', () => {
      const store = useSessionStore.getState();

      // Set auto mode settings
      store.setPin('PERSIST01');
      store.setAutoModeEnabled(true);
      store.setLeaderboardDelay(7);

      // Persist to localStorage
      store.persistAutoModeToLocalStorage();

      // Verify localStorage has the data
      const raw = localStorageMock.getItem('quiz-auto-mode-PERSIST01');
      expect(raw).not.toBeNull();
      const parsed = JSON.parse(raw!);
      expect(parsed.enabled).toBe(true);
      expect(parsed.leaderboardDelay).toBe(7);
      expect(typeof parsed.timestamp).toBe('number');

      // Reset store state (simulating a fresh page load / reconnection)
      store.resetSession();

      // Verify store is reset
      expect(useSessionStore.getState().autoModeEnabled).toBe(false);
      expect(useSessionStore.getState().leaderboardDelay).toBe(3);

      // Set pin again (needed for restore)
      useSessionStore.getState().setPin('PERSIST01');

      // Restore from localStorage
      useSessionStore.getState().restoreAutoModeFromLocalStorage();

      // Verify settings are restored correctly
      const restored = useSessionStore.getState();
      expect(restored.autoModeEnabled).toBe(true);
      expect(restored.leaderboardDelay).toBe(7);
    });

    it('should handle missing localStorage data gracefully on restore', () => {
      const store = useSessionStore.getState();
      store.setPin('NODATA01');

      // No data persisted — restore should not crash or change defaults
      store.restoreAutoModeFromLocalStorage();

      expect(useSessionStore.getState().autoModeEnabled).toBe(false);
      expect(useSessionStore.getState().leaderboardDelay).toBe(3);
    });

    it('should restore auto mode and restart countdown from beginning on reconnection', () => {
      // Simulate initial session with auto mode
      const store = useSessionStore.getState();
      store.setPin('RECON01');
      store.setAutoModeEnabled(true);
      store.setLeaderboardDelay(5);
      store.persistAutoModeToLocalStorage();

      // Render hook — countdown starts
      const { result, rerender } = renderHook(
        (props: UseAutoAdvanceOptions) => useAutoAdvance(props),
        { initialProps: defaultOptions({ enabled: true, sessionState: 'REVEAL', delaySeconds: 5, pin: 'RECON01' }) }
      );

      expect(result.current.countdown).toBe(5);

      // Advance 3 seconds
      act(() => {
        vi.advanceTimersByTime(3000);
      });
      expect(result.current.countdown).toBe(2);

      // Simulate disconnect — state goes to LOBBY
      rerender(defaultOptions({ enabled: true, sessionState: 'LOBBY', delaySeconds: 5, pin: 'RECON01' }));
      expect(result.current.countdown).toBeNull();

      // Simulate reconnection: reset store, restore from localStorage
      useSessionStore.getState().resetSession();
      useSessionStore.getState().setPin('RECON01');
      useSessionStore.getState().restoreAutoModeFromLocalStorage();

      // Verify auto mode was restored
      const restoredState = useSessionStore.getState();
      expect(restoredState.autoModeEnabled).toBe(true);
      expect(restoredState.leaderboardDelay).toBe(5);

      // Simulate reconnect — state goes back to REVEAL with restored settings
      rerender(defaultOptions({
        enabled: restoredState.autoModeEnabled,
        sessionState: 'REVEAL',
        delaySeconds: restoredState.leaderboardDelay,
        pin: 'RECON01',
      }));

      // Countdown should restart from beginning (5), not from where it left off (2)
      expect(result.current.countdown).toBe(5);
      expect(result.current.isActive).toBe(true);
    });
  });
});
