import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

// Use vi.hoisted to declare mocks that are used inside vi.mock factory
const { mockPlay, mockStop, mockVolume, mockUnload, mockPlaying, mockResume } = vi.hoisted(() => ({
  mockPlay: vi.fn().mockReturnValue(1),
  mockStop: vi.fn(),
  mockVolume: vi.fn(),
  mockUnload: vi.fn(),
  mockPlaying: vi.fn().mockReturnValue(false),
  mockResume: vi.fn().mockResolvedValue(undefined),
}));

vi.mock('howler', () => {
  return {
    Howl: vi.fn().mockImplementation(() => ({
      play: mockPlay,
      stop: mockStop,
      volume: mockVolume,
      unload: mockUnload,
      playing: mockPlaying,
    })),
    Howler: {
      ctx: { state: 'suspended', resume: mockResume },
    },
  };
});

// Mock window.matchMedia
const mockAddEventListener = vi.fn();
const mockRemoveEventListener = vi.fn();
let mockReducedMotionMatches = false;

Object.defineProperty(globalThis, 'window', {
  value: globalThis,
  writable: true,
});

Object.defineProperty(globalThis, 'matchMedia', {
  value: vi.fn().mockImplementation(() => ({
    matches: mockReducedMotionMatches,
    addEventListener: mockAddEventListener,
    removeEventListener: mockRemoveEventListener,
  })),
  writable: true,
});

// Mock sessionStorage
let mockStorage: Record<string, string> = {};
Object.defineProperty(globalThis, 'sessionStorage', {
  value: {
    getItem: (key: string) => mockStorage[key] ?? null,
    setItem: (key: string, value: string) => {
      mockStorage[key] = value;
    },
    removeItem: (key: string) => {
      delete mockStorage[key];
    },
  },
  writable: true,
});

import { Howl } from 'howler';
import { AudioManager } from '@/lib/audio-manager';

describe('AudioManager', () => {
  beforeEach(() => {
    AudioManager.resetInstance();
    vi.clearAllMocks();
    mockStorage = {};
    mockPlaying.mockReturnValue(false);
    mockReducedMotionMatches = false;
  });

  afterEach(() => {
    AudioManager.resetInstance();
  });

  describe('Singleton pattern', () => {
    it('returns the same instance on multiple calls', () => {
      const instance1 = AudioManager.getInstance();
      const instance2 = AudioManager.getInstance();
      expect(instance1).toBe(instance2);
    });

    it('creates a new instance after reset', () => {
      const instance1 = AudioManager.getInstance();
      AudioManager.resetInstance();
      const instance2 = AudioManager.getInstance();
      expect(instance1).not.toBe(instance2);
    });
  });

  describe('preloadAll', () => {
    it('creates Howl instances for all tracks', () => {
      const manager = AudioManager.getInstance();
      manager.preloadAll();
      // 5 tracks: lobby-music, countdown-tick, correct-answer, incorrect-answer, leaderboard-music
      expect(Howl).toHaveBeenCalledTimes(5);
    });

    it('only preloads once', () => {
      const manager = AudioManager.getInstance();
      manager.preloadAll();
      manager.preloadAll();
      expect(Howl).toHaveBeenCalledTimes(5);
    });
  });

  describe('Mute state', () => {
    it('starts unmuted by default', () => {
      const manager = AudioManager.getInstance();
      expect(manager.isMuted()).toBe(false);
    });

    it('can be muted', () => {
      const manager = AudioManager.getInstance();
      manager.setMuted(true);
      expect(manager.isMuted()).toBe(true);
    });

    it('persists mute state to sessionStorage', () => {
      const manager = AudioManager.getInstance();
      manager.setMuted(true);
      expect(mockStorage['quiz-audio-muted']).toBe('true');
    });

    it('loads mute state from sessionStorage', () => {
      mockStorage['quiz-audio-muted'] = 'true';
      AudioManager.resetInstance();
      const manager = AudioManager.getInstance();
      expect(manager.isMuted()).toBe(true);
    });

    it('sets volume to 0 on all howls when muted', () => {
      const manager = AudioManager.getInstance();
      manager.preloadAll();
      manager.setMuted(true);
      expect(mockVolume).toHaveBeenCalledWith(0);
    });

    it('restores volume when unmuted', () => {
      const manager = AudioManager.getInstance();
      manager.preloadAll();
      manager.setMuted(true);
      vi.clearAllMocks();
      manager.setMuted(false);
      expect(mockVolume).toHaveBeenCalled();
      const calls = mockVolume.mock.calls;
      calls.forEach((call: number[]) => {
        expect(call[0]).toBeGreaterThan(0);
      });
    });
  });

  describe('Music enabled setting', () => {
    it('defaults to music enabled', () => {
      const manager = AudioManager.getInstance();
      expect(manager.isMusicEnabled()).toBe(true);
    });

    it('can disable music', () => {
      const manager = AudioManager.getInstance();
      manager.setMusicEnabled(false);
      expect(manager.isMusicEnabled()).toBe(false);
    });

    it('stops background music when disabled', () => {
      const manager = AudioManager.getInstance();
      manager.preloadAll();
      manager.setMusicEnabled(false);
      expect(mockStop).toHaveBeenCalled();
    });

    it('does not play lobby music when music is disabled', () => {
      const manager = AudioManager.getInstance();
      manager.preloadAll();
      manager.setMusicEnabled(false);
      vi.clearAllMocks();
      manager.playLobbyMusic();
      expect(mockPlay).not.toHaveBeenCalled();
    });

    it('does not play leaderboard music when music is disabled', () => {
      const manager = AudioManager.getInstance();
      manager.preloadAll();
      manager.setMusicEnabled(false);
      vi.clearAllMocks();
      manager.playLeaderboardMusic();
      expect(mockPlay).not.toHaveBeenCalled();
    });
  });

  describe('Playback', () => {
    it('plays lobby music', () => {
      const manager = AudioManager.getInstance();
      manager.preloadAll();
      manager.playLobbyMusic();
      expect(mockPlay).toHaveBeenCalled();
    });

    it('stops lobby music', () => {
      const manager = AudioManager.getInstance();
      manager.preloadAll();
      manager.stopLobbyMusic();
      expect(mockStop).toHaveBeenCalled();
    });

    it('plays countdown tick', () => {
      const manager = AudioManager.getInstance();
      manager.preloadAll();
      manager.playCountdownTick();
      expect(mockPlay).toHaveBeenCalled();
    });

    it('plays correct answer sound', () => {
      const manager = AudioManager.getInstance();
      manager.preloadAll();
      manager.playCorrectAnswer();
      expect(mockPlay).toHaveBeenCalled();
    });

    it('plays incorrect answer sound', () => {
      const manager = AudioManager.getInstance();
      manager.preloadAll();
      manager.playIncorrectAnswer();
      expect(mockPlay).toHaveBeenCalled();
    });

    it('plays leaderboard music', () => {
      const manager = AudioManager.getInstance();
      manager.preloadAll();
      manager.playLeaderboardMusic();
      expect(mockPlay).toHaveBeenCalled();
    });

    it('does not play when muted', () => {
      const manager = AudioManager.getInstance();
      manager.preloadAll();
      manager.setMuted(true);
      vi.clearAllMocks();
      manager.playCountdownTick();
      expect(mockPlay).not.toHaveBeenCalled();
    });

    it('does not play looping track if already playing', () => {
      mockPlaying.mockReturnValue(true);
      const manager = AudioManager.getInstance();
      manager.preloadAll();
      manager.playLobbyMusic();
      expect(mockPlay).not.toHaveBeenCalled();
    });

    it('stops leaderboard music when playing lobby music', () => {
      const manager = AudioManager.getInstance();
      manager.preloadAll();
      manager.playLobbyMusic();
      expect(mockStop).toHaveBeenCalled();
    });
  });

  describe('Autoplay handling', () => {
    it('starts with autoplay not blocked', () => {
      const manager = AudioManager.getInstance();
      expect(manager.isAutoplayBlocked()).toBe(false);
    });

    it('unlockAudio resumes audio context', () => {
      const manager = AudioManager.getInstance();
      manager.unlockAudio();
      expect(mockResume).toHaveBeenCalled();
    });
  });

  describe('prefers-reduced-motion', () => {
    it('detects reduced motion preference', () => {
      mockReducedMotionMatches = true;
      AudioManager.resetInstance();
      const manager = AudioManager.getInstance();
      expect(manager.getPrefersReducedMotion()).toBe(true);
    });
  });

  describe('destroy', () => {
    it('unloads all howls', () => {
      const manager = AudioManager.getInstance();
      manager.preloadAll();
      manager.destroy();
      expect(mockUnload).toHaveBeenCalledTimes(5);
    });
  });
});
