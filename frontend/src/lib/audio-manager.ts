import { Howl, Howler } from 'howler';

/**
 * Audio track identifiers used by the AudioManager.
 */
export type AudioTrack =
  | 'lobby-music'
  | 'countdown-tick'
  | 'correct-answer'
  | 'incorrect-answer'
  | 'leaderboard-music';

/**
 * Configuration for each audio track.
 */
interface TrackConfig {
  src: string;
  loop: boolean;
  volume: number;
}

const TRACK_CONFIGS: Record<AudioTrack, TrackConfig> = {
  'lobby-music': { src: '/audio/lobby-music.mp3', loop: true, volume: 0.4 },
  'countdown-tick': { src: '/audio/countdown-tick.mp3', loop: false, volume: 0.6 },
  'correct-answer': { src: '/audio/correct-answer.mp3', loop: false, volume: 0.7 },
  'incorrect-answer': { src: '/audio/incorrect-answer.mp3', loop: false, volume: 0.7 },
  'leaderboard-music': { src: '/audio/leaderboard-music.mp3', loop: true, volume: 0.4 },
};

const MUTE_STORAGE_KEY = 'quiz-audio-muted';

/**
 * Singleton AudioManager class that manages all audio playback for the quiz platform.
 * Uses Howler.js for cross-browser audio support and handles:
 * - Audio preloading during lobby phase
 * - Mute/unmute state (persisted in sessionStorage)
 * - Browser autoplay policy detection
 * - Host music-enabled setting
 * - prefers-reduced-motion media query
 */
export class AudioManager {
  private static instance: AudioManager | null = null;

  private howls: Map<AudioTrack, Howl> = new Map();
  private muted: boolean = false;
  private musicEnabled: boolean = true;
  private preloaded: boolean = false;
  private autoplayBlocked: boolean = false;
  private prefersReducedMotion: boolean = false;
  private reducedMotionQuery: MediaQueryList | null = null;

  private constructor() {
    this.loadMuteState();
    this.detectReducedMotion();
  }

  /**
   * Get the singleton AudioManager instance.
   */
  static getInstance(): AudioManager {
    if (!AudioManager.instance) {
      AudioManager.instance = new AudioManager();
    }
    return AudioManager.instance;
  }

  /**
   * Reset the singleton instance (useful for testing).
   */
  static resetInstance(): void {
    if (AudioManager.instance) {
      AudioManager.instance.destroy();
      AudioManager.instance = null;
    }
  }

  /**
   * Preload all audio tracks. Call during lobby phase to prevent playback delays.
   */
  preloadAll(): void {
    if (this.preloaded) return;

    for (const [track, config] of Object.entries(TRACK_CONFIGS)) {
      const howl = new Howl({
        src: [config.src],
        loop: config.loop,
        volume: this.muted ? 0 : config.volume,
        preload: true,
        html5: false, // Use Web Audio API for better control
        onplayerror: () => {
          this.autoplayBlocked = true;
        },
        onunlock: () => {
          this.autoplayBlocked = false;
        },
      });
      this.howls.set(track as AudioTrack, howl);
    }

    this.preloaded = true;
  }

  /**
   * Play lobby background music (loops).
   */
  playLobbyMusic(): void {
    if (!this.canPlayMusic()) return;
    this.stopTrack('leaderboard-music');
    this.playTrack('lobby-music');
  }

  /**
   * Stop lobby background music.
   */
  stopLobbyMusic(): void {
    this.stopTrack('lobby-music');
  }

  /**
   * Play countdown tick sound effect.
   */
  playCountdownTick(): void {
    if (!this.canPlaySound()) return;
    this.playTrack('countdown-tick');
  }

  /**
   * Play correct answer sound effect.
   */
  playCorrectAnswer(): void {
    if (!this.canPlaySound()) return;
    this.playTrack('correct-answer');
  }

  /**
   * Play incorrect answer sound effect.
   */
  playIncorrectAnswer(): void {
    if (!this.canPlaySound()) return;
    this.playTrack('incorrect-answer');
  }

  /**
   * Play leaderboard background music (loops).
   */
  playLeaderboardMusic(): void {
    if (!this.canPlayMusic()) return;
    this.stopTrack('lobby-music');
    this.playTrack('leaderboard-music');
  }

  /**
   * Stop leaderboard background music.
   */
  stopLeaderboardMusic(): void {
    this.stopTrack('leaderboard-music');
  }

  /**
   * Set the muted state. Persists to sessionStorage.
   */
  setMuted(muted: boolean): void {
    this.muted = muted;
    this.persistMuteState();

    // Apply mute state to all active howls
    const tracks: AudioTrack[] = Array.from(this.howls.keys());
    for (const track of tracks) {
      const howl = this.howls.get(track);
      if (!howl) continue;
      const config = TRACK_CONFIGS[track];
      howl.volume(muted ? 0 : config.volume);
    }
  }

  /**
   * Check if audio is currently muted.
   */
  isMuted(): boolean {
    return this.muted;
  }

  /**
   * Set whether the host has enabled music for this session.
   */
  setMusicEnabled(enabled: boolean): void {
    this.musicEnabled = enabled;
    if (!enabled) {
      this.stopTrack('lobby-music');
      this.stopTrack('leaderboard-music');
    }
  }

  /**
   * Check if the host has enabled music.
   */
  isMusicEnabled(): boolean {
    return this.musicEnabled;
  }

  /**
   * Check if browser autoplay is blocked.
   */
  isAutoplayBlocked(): boolean {
    return this.autoplayBlocked;
  }

  /**
   * Attempt to unlock audio context after user interaction.
   * Call this from a click/tap event handler.
   */
  unlockAudio(): void {
    // Howler.js provides a global unlock mechanism
    const ctx = Howler.ctx;
    if (ctx && ctx.state === 'suspended') {
      ctx.resume().then(() => {
        this.autoplayBlocked = false;
      });
    }
    this.autoplayBlocked = false;
  }

  /**
   * Check if the user prefers reduced motion.
   */
  getPrefersReducedMotion(): boolean {
    return this.prefersReducedMotion;
  }

  /**
   * Clean up all audio resources.
   */
  destroy(): void {
    const tracks: AudioTrack[] = Array.from(this.howls.keys());
    for (const track of tracks) {
      const howl = this.howls.get(track);
      if (howl) howl.unload();
    }
    this.howls.clear();
    this.preloaded = false;

    if (this.reducedMotionQuery) {
      this.reducedMotionQuery.removeEventListener('change', this.handleReducedMotionChange);
      this.reducedMotionQuery = null;
    }
  }

  // ─── Private Methods ───────────────────────────────────────────────

  private playTrack(track: AudioTrack): void {
    const howl = this.howls.get(track);
    if (!howl) return;

    // For looping tracks, only play if not already playing
    const config = TRACK_CONFIGS[track];
    if (config.loop && howl.playing()) return;

    howl.play();
  }

  private stopTrack(track: AudioTrack): void {
    const howl = this.howls.get(track);
    if (!howl) return;
    howl.stop();
  }

  /**
   * Whether music (looping background tracks) can play.
   * Requires: not muted, music enabled by host, not autoplay-blocked.
   */
  private canPlayMusic(): boolean {
    return !this.muted && this.musicEnabled && !this.autoplayBlocked;
  }

  /**
   * Whether sound effects can play.
   * Requires: not muted, not autoplay-blocked.
   * Sound effects play regardless of musicEnabled setting.
   */
  private canPlaySound(): boolean {
    return !this.muted && !this.autoplayBlocked;
  }

  private loadMuteState(): void {
    if (typeof window === 'undefined') return;
    try {
      const stored = sessionStorage.getItem(MUTE_STORAGE_KEY);
      this.muted = stored === 'true';
    } catch {
      // sessionStorage not available (e.g., private browsing in some browsers)
      this.muted = false;
    }
  }

  private persistMuteState(): void {
    if (typeof window === 'undefined') return;
    try {
      sessionStorage.setItem(MUTE_STORAGE_KEY, String(this.muted));
    } catch {
      // Ignore storage errors
    }
  }

  private detectReducedMotion(): void {
    if (typeof window === 'undefined') return;
    this.reducedMotionQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
    this.prefersReducedMotion = this.reducedMotionQuery.matches;
    this.reducedMotionQuery.addEventListener('change', this.handleReducedMotionChange);
  }

  private handleReducedMotionChange = (event: MediaQueryListEvent): void => {
    this.prefersReducedMotion = event.matches;
  };
}
