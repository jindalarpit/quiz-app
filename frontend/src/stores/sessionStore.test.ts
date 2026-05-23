import { describe, it, expect, beforeEach } from 'vitest';
import { useSessionStore } from './sessionStore';

describe('sessionStore - auto mode state and actions', () => {
  beforeEach(() => {
    // Reset the store before each test
    useSessionStore.getState().resetSession();
  });

  describe('initial state', () => {
    it('should have autoModeEnabled defaulting to false', () => {
      expect(useSessionStore.getState().autoModeEnabled).toBe(false);
    });

    it('should have leaderboardDelay defaulting to 3', () => {
      expect(useSessionStore.getState().leaderboardDelay).toBe(3);
    });

    it('should have autoAdvanceCountdown defaulting to null', () => {
      expect(useSessionStore.getState().autoAdvanceCountdown).toBe(null);
    });
  });

  describe('setAutoModeEnabled', () => {
    it('should enable auto mode', () => {
      useSessionStore.getState().setAutoModeEnabled(true);
      expect(useSessionStore.getState().autoModeEnabled).toBe(true);
    });

    it('should disable auto mode', () => {
      useSessionStore.getState().setAutoModeEnabled(true);
      useSessionStore.getState().setAutoModeEnabled(false);
      expect(useSessionStore.getState().autoModeEnabled).toBe(false);
    });
  });

  describe('setLeaderboardDelay', () => {
    it('should accept valid integer in range [1, 30]', () => {
      const result = useSessionStore.getState().setLeaderboardDelay(5);
      expect(result).toBe(true);
      expect(useSessionStore.getState().leaderboardDelay).toBe(5);
    });

    it('should accept minimum value 1', () => {
      const result = useSessionStore.getState().setLeaderboardDelay(1);
      expect(result).toBe(true);
      expect(useSessionStore.getState().leaderboardDelay).toBe(1);
    });

    it('should accept maximum value 30', () => {
      const result = useSessionStore.getState().setLeaderboardDelay(30);
      expect(result).toBe(true);
      expect(useSessionStore.getState().leaderboardDelay).toBe(30);
    });

    it('should reject value below 1', () => {
      useSessionStore.getState().setLeaderboardDelay(5); // set a valid value first
      const result = useSessionStore.getState().setLeaderboardDelay(0);
      expect(result).toBe(false);
      expect(useSessionStore.getState().leaderboardDelay).toBe(5); // unchanged
    });

    it('should reject value above 30', () => {
      useSessionStore.getState().setLeaderboardDelay(5);
      const result = useSessionStore.getState().setLeaderboardDelay(31);
      expect(result).toBe(false);
      expect(useSessionStore.getState().leaderboardDelay).toBe(5); // unchanged
    });

    it('should reject non-integer values', () => {
      useSessionStore.getState().setLeaderboardDelay(5);
      const result = useSessionStore.getState().setLeaderboardDelay(3.5);
      expect(result).toBe(false);
      expect(useSessionStore.getState().leaderboardDelay).toBe(5); // unchanged
    });

    it('should reject negative values', () => {
      useSessionStore.getState().setLeaderboardDelay(5);
      const result = useSessionStore.getState().setLeaderboardDelay(-1);
      expect(result).toBe(false);
      expect(useSessionStore.getState().leaderboardDelay).toBe(5); // unchanged
    });
  });

  describe('setAutoAdvanceCountdown', () => {
    it('should set countdown to a number', () => {
      useSessionStore.getState().setAutoAdvanceCountdown(5);
      expect(useSessionStore.getState().autoAdvanceCountdown).toBe(5);
    });

    it('should set countdown to null (inactive)', () => {
      useSessionStore.getState().setAutoAdvanceCountdown(5);
      useSessionStore.getState().setAutoAdvanceCountdown(null);
      expect(useSessionStore.getState().autoAdvanceCountdown).toBe(null);
    });
  });

  describe('resetSession', () => {
    it('should reset auto mode state to defaults', () => {
      useSessionStore.getState().setAutoModeEnabled(true);
      useSessionStore.getState().setLeaderboardDelay(10);
      useSessionStore.getState().setAutoAdvanceCountdown(7);

      useSessionStore.getState().resetSession();

      expect(useSessionStore.getState().autoModeEnabled).toBe(false);
      expect(useSessionStore.getState().leaderboardDelay).toBe(3);
      expect(useSessionStore.getState().autoAdvanceCountdown).toBe(null);
    });
  });
});
