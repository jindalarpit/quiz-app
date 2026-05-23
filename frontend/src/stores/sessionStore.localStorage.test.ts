import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import { useSessionStore } from './sessionStore';

// Mock localStorage for node test environment
const localStorageMock = (() => {
  let store: Record<string, string> = {};
  return {
    getItem: vi.fn((key: string) => store[key] ?? null),
    setItem: vi.fn((key: string, value: string) => { store[key] = value; }),
    removeItem: vi.fn((key: string) => { delete store[key]; }),
    clear: vi.fn(() => { store = {}; }),
    get length() { return Object.keys(store).length; },
    key: vi.fn((index: number) => Object.keys(store)[index] ?? null),
  };
})();

Object.defineProperty(globalThis, 'localStorage', { value: localStorageMock });

describe('sessionStore - localStorage persistence (Task 2.2)', () => {
  beforeEach(() => {
    useSessionStore.getState().resetSession();
    localStorageMock.clear();
    vi.clearAllMocks();
  });

  describe('persistAutoModeToLocalStorage', () => {
    it('should write auto mode settings to localStorage with correct key format', () => {
      useSessionStore.getState().setPin('ABC123');
      useSessionStore.getState().setAutoModeEnabled(true);

      const stored = JSON.parse(localStorageMock.getItem('quiz-auto-mode-ABC123')!);
      expect(stored.enabled).toBe(true);
      expect(stored.leaderboardDelay).toBe(3);
      expect(typeof stored.timestamp).toBe('number');
    });

    it('should persist leaderboardDelay changes', () => {
      useSessionStore.getState().setPin('XYZ789');
      useSessionStore.getState().setLeaderboardDelay(10);

      const stored = JSON.parse(localStorageMock.getItem('quiz-auto-mode-XYZ789')!);
      expect(stored.leaderboardDelay).toBe(10);
      expect(stored.enabled).toBe(false);
    });

    it('should not persist if pin is null', () => {
      // pin is null by default after reset
      useSessionStore.getState().persistAutoModeToLocalStorage();
      expect(localStorageMock.setItem).not.toHaveBeenCalled();
    });

    it('should include a timestamp for staleness detection', () => {
      const before = Date.now();
      useSessionStore.getState().setPin('PIN001');
      useSessionStore.getState().setAutoModeEnabled(true);
      const after = Date.now();

      const stored = JSON.parse(localStorageMock.getItem('quiz-auto-mode-PIN001')!);
      expect(stored.timestamp).toBeGreaterThanOrEqual(before);
      expect(stored.timestamp).toBeLessThanOrEqual(after);
    });

    it('should handle localStorage errors gracefully', () => {
      const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
      localStorageMock.setItem.mockImplementationOnce(() => {
        throw new Error('QuotaExceededError');
      });

      useSessionStore.getState().setPin('ERR001');
      // Should not throw
      useSessionStore.getState().persistAutoModeToLocalStorage();

      expect(warnSpy).toHaveBeenCalledWith(
        'Failed to persist auto-mode settings to localStorage:',
        expect.any(Error)
      );
      warnSpy.mockRestore();
    });
  });

  describe('restoreAutoModeFromLocalStorage', () => {
    it('should restore auto mode settings from localStorage', () => {
      const data = { enabled: true, leaderboardDelay: 15, timestamp: Date.now() };
      localStorageMock.setItem('quiz-auto-mode-RESTORE1', JSON.stringify(data));
      localStorageMock.setItem.mockClear();

      useSessionStore.getState().setPin('RESTORE1');
      useSessionStore.getState().restoreAutoModeFromLocalStorage();

      expect(useSessionStore.getState().autoModeEnabled).toBe(true);
      expect(useSessionStore.getState().leaderboardDelay).toBe(15);
    });

    it('should not change state if no data in localStorage', () => {
      useSessionStore.getState().setPin('EMPTY1');
      useSessionStore.getState().restoreAutoModeFromLocalStorage();

      expect(useSessionStore.getState().autoModeEnabled).toBe(false);
      expect(useSessionStore.getState().leaderboardDelay).toBe(3);
    });

    it('should not restore if pin is null', () => {
      const data = { enabled: true, leaderboardDelay: 20, timestamp: Date.now() };
      localStorageMock.setItem('quiz-auto-mode-null', JSON.stringify(data));

      useSessionStore.getState().restoreAutoModeFromLocalStorage();

      expect(useSessionStore.getState().autoModeEnabled).toBe(false);
    });

    it('should fall back to default delay if stored delay is out of range', () => {
      const data = { enabled: true, leaderboardDelay: 50, timestamp: Date.now() };
      localStorageMock.setItem('quiz-auto-mode-RANGE1', JSON.stringify(data));
      localStorageMock.setItem.mockClear();

      useSessionStore.getState().setPin('RANGE1');
      useSessionStore.getState().restoreAutoModeFromLocalStorage();

      expect(useSessionStore.getState().autoModeEnabled).toBe(true);
      expect(useSessionStore.getState().leaderboardDelay).toBe(3); // default
    });

    it('should fall back to default delay if stored delay is not an integer', () => {
      const data = { enabled: false, leaderboardDelay: 5.5, timestamp: Date.now() };
      localStorageMock.setItem('quiz-auto-mode-FLOAT1', JSON.stringify(data));
      localStorageMock.setItem.mockClear();

      useSessionStore.getState().setPin('FLOAT1');
      useSessionStore.getState().restoreAutoModeFromLocalStorage();

      expect(useSessionStore.getState().leaderboardDelay).toBe(3); // default
    });

    it('should handle malformed JSON gracefully', () => {
      const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
      localStorageMock.setItem('quiz-auto-mode-BAD1', 'not-valid-json');
      localStorageMock.setItem.mockClear();

      useSessionStore.getState().setPin('BAD1');
      useSessionStore.getState().restoreAutoModeFromLocalStorage();

      expect(warnSpy).toHaveBeenCalledWith(
        'Failed to restore auto-mode settings from localStorage:',
        expect.any(Error)
      );
      expect(useSessionStore.getState().autoModeEnabled).toBe(false);
      expect(useSessionStore.getState().leaderboardDelay).toBe(3);
      warnSpy.mockRestore();
    });

    it('should handle localStorage getItem errors gracefully', () => {
      const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
      localStorageMock.getItem.mockImplementationOnce(() => {
        throw new Error('SecurityError');
      });

      useSessionStore.getState().setPin('SEC1');
      useSessionStore.getState().restoreAutoModeFromLocalStorage();

      expect(warnSpy).toHaveBeenCalledWith(
        'Failed to restore auto-mode settings from localStorage:',
        expect.any(Error)
      );
      warnSpy.mockRestore();
    });

    it('should ignore data with missing required fields', () => {
      const data = { timestamp: Date.now() }; // missing enabled and leaderboardDelay
      localStorageMock.setItem('quiz-auto-mode-MISS1', JSON.stringify(data));
      localStorageMock.setItem.mockClear();

      useSessionStore.getState().setPin('MISS1');
      useSessionStore.getState().restoreAutoModeFromLocalStorage();

      expect(useSessionStore.getState().autoModeEnabled).toBe(false);
      expect(useSessionStore.getState().leaderboardDelay).toBe(3);
    });
  });

  describe('auto-persist on toggle/delay change', () => {
    it('should persist to localStorage when setAutoModeEnabled is called', () => {
      useSessionStore.getState().setPin('AUTO1');
      localStorageMock.setItem.mockClear();

      useSessionStore.getState().setAutoModeEnabled(true);

      expect(localStorageMock.setItem).toHaveBeenCalledWith(
        'quiz-auto-mode-AUTO1',
        expect.any(String)
      );
    });

    it('should persist to localStorage when setLeaderboardDelay is called with valid value', () => {
      useSessionStore.getState().setPin('AUTO2');
      localStorageMock.setItem.mockClear();

      useSessionStore.getState().setLeaderboardDelay(7);

      expect(localStorageMock.setItem).toHaveBeenCalledWith(
        'quiz-auto-mode-AUTO2',
        expect.any(String)
      );
    });

    it('should not persist when setLeaderboardDelay is called with invalid value', () => {
      useSessionStore.getState().setPin('AUTO3');
      localStorageMock.setItem.mockClear();

      useSessionStore.getState().setLeaderboardDelay(0);

      expect(localStorageMock.setItem).not.toHaveBeenCalled();
    });
  });
});
