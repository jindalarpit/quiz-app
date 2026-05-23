'use client';

import { useCallback, useEffect, useRef, useState } from 'react';

import { api } from '@/lib/api';

export interface UseAutoAdvanceOptions {
  enabled: boolean;
  delaySeconds: number;
  isLastQuestion: boolean;
  sessionState: string;
  isPaused: boolean;
  pin: string;
  onAdvance?: () => void;
}

export interface UseAutoAdvanceReturn {
  countdown: number | null;
  cancel: () => void;
  isActive: boolean;
}

/**
 * Hook that manages auto-advance countdown during the REVEAL state.
 *
 * Starts a countdown when auto mode is enabled, the session is in REVEAL state,
 * and the session is not paused. When the countdown reaches zero, it calls the
 * appropriate endpoint (/next or /end) depending on whether it's the last question.
 *
 * The countdown is cancelled when:
 * - The cancel function is called manually
 * - Auto mode is disabled
 * - The session is paused
 * - The session state transitions away from REVEAL
 */
export function useAutoAdvance({
  enabled,
  delaySeconds,
  isLastQuestion,
  sessionState,
  isPaused,
  pin,
  onAdvance,
}: UseAutoAdvanceOptions): UseAutoAdvanceReturn {
  const [countdown, setCountdown] = useState<number | null>(null);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const cancelledRef = useRef(false);
  // Use refs for values needed at advance time to avoid stale closures
  const isLastQuestionRef = useRef(isLastQuestion);
  const pinRef = useRef(pin);
  const onAdvanceRef = useRef(onAdvance);

  // Keep refs in sync
  useEffect(() => {
    isLastQuestionRef.current = isLastQuestion;
  }, [isLastQuestion]);

  useEffect(() => {
    pinRef.current = pin;
  }, [pin]);

  useEffect(() => {
    onAdvanceRef.current = onAdvance;
  }, [onAdvance]);

  const clearCountdown = useCallback(() => {
    if (intervalRef.current !== null) {
      clearInterval(intervalRef.current);
      intervalRef.current = null;
    }
    setCountdown(null);
  }, []);

  const cancel = useCallback(() => {
    cancelledRef.current = true;
    clearCountdown();
  }, [clearCountdown]);

  useEffect(() => {
    // Determine if conditions are met to start the countdown
    const shouldRun = enabled && sessionState === 'REVEAL' && !isPaused;

    if (!shouldRun) {
      clearCountdown();
      return;
    }

    // Reset cancelled flag when conditions become valid again
    cancelledRef.current = false;

    // Start countdown at delaySeconds
    setCountdown(delaySeconds);

    intervalRef.current = setInterval(() => {
      setCountdown((prev) => {
        if (prev === null || prev <= 1) {
          // Countdown reached zero — clear interval
          if (intervalRef.current !== null) {
            clearInterval(intervalRef.current);
            intervalRef.current = null;
          }
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    return () => {
      if (intervalRef.current !== null) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
    };
  }, [enabled, sessionState, isPaused, delaySeconds, clearCountdown]);

  // Handle the advance API call when countdown reaches 0
  useEffect(() => {
    if (countdown !== 0) return;
    if (cancelledRef.current) return;

    const advance = async () => {
      try {
        if (isLastQuestionRef.current) {
          await api.post(`/api/sessions/${pinRef.current}/end`);
        } else {
          await api.post(`/api/sessions/${pinRef.current}/next`);
        }
        onAdvanceRef.current?.();
      } catch (error) {
        // Log error, cancel countdown, let host fall back to manual controls
        console.error('[useAutoAdvance] Failed to auto-advance:', error);
      }
    };

    advance();
    // After triggering, reset countdown to null so we don't re-trigger
    setCountdown(null);
  }, [countdown]);

  const isActive = countdown !== null && countdown > 0;

  return { countdown, cancel, isActive };
}
