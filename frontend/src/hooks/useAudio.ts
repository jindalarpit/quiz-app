'use client';

import { useCallback, useEffect, useRef, useState } from 'react';

import { AudioManager } from '@/lib/audio-manager';

export interface UseAudioOptions {
  /** Whether the host has enabled music for this session */
  musicEnabled?: boolean;
}

export interface UseAudioReturn {
  /** Whether audio is currently muted */
  isMuted: boolean;
  /** Toggle mute state */
  toggleMute: () => void;
  /** Set mute state explicitly */
  setMuted: (muted: boolean) => void;
  /** Whether browser autoplay is blocked */
  isAutoplayBlocked: boolean;
  /** Unlock audio after user interaction */
  unlockAudio: () => void;
  /** Preload all audio assets */
  preloadAll: () => void;
  /** Play lobby background music */
  playLobbyMusic: () => void;
  /** Stop lobby background music */
  stopLobbyMusic: () => void;
  /** Play countdown tick sound */
  playCountdownTick: () => void;
  /** Play correct answer sound */
  playCorrectAnswer: () => void;
  /** Play incorrect answer sound */
  playIncorrectAnswer: () => void;
  /** Play leaderboard music */
  playLeaderboardMusic: () => void;
  /** Stop leaderboard music */
  stopLeaderboardMusic: () => void;
  /** Whether user prefers reduced motion */
  prefersReducedMotion: boolean;
}

/**
 * React hook for managing audio playback in the quiz session.
 *
 * Integrates with the AudioManager singleton and provides reactive state
 * for mute status and autoplay blocking.
 *
 * Usage:
 * ```tsx
 * const { isMuted, toggleMute, preloadAll, playLobbyMusic } = useAudio({ musicEnabled: true });
 *
 * useEffect(() => {
 *   preloadAll();
 *   playLobbyMusic();
 * }, [preloadAll, playLobbyMusic]);
 * ```
 */
export function useAudio(options: UseAudioOptions = {}): UseAudioReturn {
  const { musicEnabled = true } = options;
  const audioManagerRef = useRef<AudioManager | null>(null);
  const [isMuted, setIsMuted] = useState(false);
  const [isAutoplayBlocked, setIsAutoplayBlocked] = useState(false);
  const [prefersReducedMotion, setPrefersReducedMotion] = useState(false);

  // Initialize audio manager
  useEffect(() => {
    const manager = AudioManager.getInstance();
    audioManagerRef.current = manager;
    setIsMuted(manager.isMuted());
    setIsAutoplayBlocked(manager.isAutoplayBlocked());
    setPrefersReducedMotion(manager.getPrefersReducedMotion());
  }, []);

  // Sync musicEnabled setting
  useEffect(() => {
    const manager = audioManagerRef.current;
    if (manager) {
      manager.setMusicEnabled(musicEnabled);
    }
  }, [musicEnabled]);

  // Poll for autoplay blocked state changes (Howler resolves this asynchronously)
  useEffect(() => {
    const interval = setInterval(() => {
      const manager = audioManagerRef.current;
      if (manager) {
        const blocked = manager.isAutoplayBlocked();
        setIsAutoplayBlocked((prev) => (prev !== blocked ? blocked : prev));
      }
    }, 1000);
    return () => clearInterval(interval);
  }, []);

  // Listen for prefers-reduced-motion changes
  useEffect(() => {
    if (typeof window === 'undefined') return;
    const query = window.matchMedia('(prefers-reduced-motion: reduce)');
    const handler = (e: MediaQueryListEvent) => setPrefersReducedMotion(e.matches);
    setPrefersReducedMotion(query.matches);
    query.addEventListener('change', handler);
    return () => query.removeEventListener('change', handler);
  }, []);

  const toggleMute = useCallback(() => {
    const manager = audioManagerRef.current;
    if (!manager) return;
    const newMuted = !manager.isMuted();
    manager.setMuted(newMuted);
    setIsMuted(newMuted);
  }, []);

  const setMutedState = useCallback((muted: boolean) => {
    const manager = audioManagerRef.current;
    if (!manager) return;
    manager.setMuted(muted);
    setIsMuted(muted);
  }, []);

  const unlockAudio = useCallback(() => {
    const manager = audioManagerRef.current;
    if (!manager) return;
    manager.unlockAudio();
    setIsAutoplayBlocked(false);
  }, []);

  const preloadAll = useCallback(() => {
    const manager = audioManagerRef.current;
    if (!manager) return;
    manager.preloadAll();
  }, []);

  const playLobbyMusic = useCallback(() => {
    const manager = audioManagerRef.current;
    if (!manager) return;
    manager.playLobbyMusic();
  }, []);

  const stopLobbyMusic = useCallback(() => {
    const manager = audioManagerRef.current;
    if (!manager) return;
    manager.stopLobbyMusic();
  }, []);

  const playCountdownTick = useCallback(() => {
    const manager = audioManagerRef.current;
    if (!manager) return;
    manager.playCountdownTick();
  }, []);

  const playCorrectAnswer = useCallback(() => {
    const manager = audioManagerRef.current;
    if (!manager) return;
    manager.playCorrectAnswer();
  }, []);

  const playIncorrectAnswer = useCallback(() => {
    const manager = audioManagerRef.current;
    if (!manager) return;
    manager.playIncorrectAnswer();
  }, []);

  const playLeaderboardMusic = useCallback(() => {
    const manager = audioManagerRef.current;
    if (!manager) return;
    manager.playLeaderboardMusic();
  }, []);

  const stopLeaderboardMusic = useCallback(() => {
    const manager = audioManagerRef.current;
    if (!manager) return;
    manager.stopLeaderboardMusic();
  }, []);

  return {
    isMuted,
    toggleMute,
    setMuted: setMutedState,
    isAutoplayBlocked,
    unlockAudio,
    preloadAll,
    playLobbyMusic,
    stopLobbyMusic,
    playCountdownTick,
    playCorrectAnswer,
    playIncorrectAnswer,
    playLeaderboardMusic,
    stopLeaderboardMusic,
    prefersReducedMotion,
  };
}
