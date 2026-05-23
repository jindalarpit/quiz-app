// Feature: auto-mode-quiz-flow, Property 6: Auto mode persistence round-trip
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import * as fc from 'fast-check';
import { useSessionStore } from './sessionStore';

/**
 * Property-based test for auto mode persistence round-trip (Property 6).
 *
 * **Validates: Requirements 7.1**
 *
 * Property 6: Auto mode persistence round-trip — For any auto mode configuration
 * (enabled state and leaderboard delay), persisting to localStorage and then
 * restoring SHALL produce an identical configuration.
 */

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

describe('Auto Mode Persistence Round-Trip (Property 6)', () => {
  beforeEach(() => {
    // Set up localStorage mock
    Object.defineProperty(globalThis, 'localStorage', {
      value: localStorageMock,
      writable: true,
      configurable: true,
    });
    localStorageMock.clear();
    useSessionStore.getState().resetSession();
  });

  afterEach(() => {
    localStorageMock.clear();
    vi.restoreAllMocks();
  });

  it('P6: Persisting and restoring auto mode config produces identical configuration', () => {
    fc.assert(
      fc.property(
        fc.boolean(),
        fc.integer({ min: 1, max: 30 }),
        (enabled, leaderboardDelay) => {
          const store = useSessionStore.getState();

          // 1. Set pin (required for persistence to work)
          store.setPin('TEST-PIN');

          // 2. Set auto mode configuration
          store.setAutoModeEnabled(enabled);
          store.setLeaderboardDelay(leaderboardDelay);

          // 3. Persist to localStorage
          store.persistAutoModeToLocalStorage();

          // 4. Reset store state (but keep pin and localStorage intact)
          useSessionStore.setState({
            autoModeEnabled: false,
            leaderboardDelay: 3,
          });

          // Verify state was actually reset
          const resetState = useSessionStore.getState();
          expect(resetState.autoModeEnabled).toBe(false);
          expect(resetState.leaderboardDelay).toBe(3);

          // 5. Restore from localStorage
          resetState.restoreAutoModeFromLocalStorage();

          // 6. Assert equality
          const restoredState = useSessionStore.getState();
          expect(restoredState.autoModeEnabled).toBe(enabled);
          expect(restoredState.leaderboardDelay).toBe(leaderboardDelay);
        }
      ),
      { numRuns: 100 }
    );
  });
});
