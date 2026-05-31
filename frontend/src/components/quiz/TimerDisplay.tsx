'use client';

import React, { useCallback, useEffect, useRef, useState } from 'react';

import { cn } from '@/lib/utils';

interface TimerDisplayProps {
  /** Total time limit in seconds */
  timeLimit: number;
  /** Server timestamp (epoch ms) when the question started */
  serverTimestamp: number;
  /** Unique ID for the current question — resets the expiry guard when it changes */
  questionId: string;
  /** Current session state — onExpire only fires when this equals 'QUESTION_OPEN' */
  sessionState: string;
  /** Function to get estimated server time (from TimerSync). Falls back to Date.now() if not provided. */
  getServerTime?: () => number;
  /** Callback fired once when the timer reaches zero */
  onExpire?: () => void;
}

/** Threshold in seconds for switching to high-frequency updates */
const URGENT_THRESHOLD_SECONDS = 3;
/** Normal update interval in ms */
const NORMAL_INTERVAL_MS = 1000;
/** High-frequency update interval in ms (final 3 seconds) */
const URGENT_INTERVAL_MS = 100;

export function TimerDisplay({ timeLimit, serverTimestamp, questionId, sessionState, getServerTime, onExpire }: TimerDisplayProps) {
  const [remaining, setRemaining] = useState(timeLimit);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const currentIntervalMs = useRef<number>(NORMAL_INTERVAL_MS);
  const expiredRef = useRef(false);
  const prevQuestionIdRef = useRef(questionId);

  // Reset expiredRef when questionId changes so each question gets a fresh guard
  if (prevQuestionIdRef.current !== questionId) {
    prevQuestionIdRef.current = questionId;
    expiredRef.current = false;
  }

  // Keep a ref to sessionState so the interval callback always sees the latest value
  const sessionStateRef = useRef(sessionState);
  sessionStateRef.current = sessionState;

  // Keep a stable ref to onExpire so the interval callback always sees the latest
  const onExpireRef = useRef(onExpire);
  onExpireRef.current = onExpire;

  const getCurrentServerTime = useCallback((): number => {
    return getServerTime ? getServerTime() : Date.now();
  }, [getServerTime]);

  const updateTimer = useCallback(() => {
    const currentTime = getCurrentServerTime();
    const elapsed = (currentTime - serverTimestamp) / 1000;
    const left = Math.max(0, timeLimit - elapsed);
    setRemaining(left);
    return left;
  }, [getCurrentServerTime, serverTimestamp, timeLimit]);

  useEffect(() => {
    // Initial update
    const left = updateTimer();

    const checkExpiry = (currentLeft: number) => {
      if (currentLeft <= 0 && !expiredRef.current && onExpireRef.current && sessionStateRef.current === 'QUESTION_OPEN') {
        expiredRef.current = true;
        onExpireRef.current();
      }
    };

    // Check immediately if already expired
    checkExpiry(left);
    if (left <= 0) return;

    const tick = () => {
      const currentLeft = updateTimer();

      // Switch to high-frequency updates when entering final 3 seconds
      if (currentLeft <= URGENT_THRESHOLD_SECONDS && currentIntervalMs.current !== URGENT_INTERVAL_MS) {
        currentIntervalMs.current = URGENT_INTERVAL_MS;
        if (intervalRef.current) {
          clearInterval(intervalRef.current);
        }
        intervalRef.current = setInterval(tick, URGENT_INTERVAL_MS);
      }

      // Stop when timer reaches 0
      if (currentLeft <= 0) {
        if (intervalRef.current) {
          clearInterval(intervalRef.current);
          intervalRef.current = null;
        }
        checkExpiry(currentLeft);
      }
    };

    // Determine initial interval based on remaining time
    const initialInterval = left <= URGENT_THRESHOLD_SECONDS ? URGENT_INTERVAL_MS : NORMAL_INTERVAL_MS;
    currentIntervalMs.current = initialInterval;
    intervalRef.current = setInterval(tick, initialInterval);

    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
    };
  }, [timeLimit, serverTimestamp, updateTimer]);

  const percentage = (remaining / timeLimit) * 100;
  const isUrgent = remaining <= URGENT_THRESHOLD_SECONDS;
  const displaySeconds = Math.ceil(remaining);

  return (
    <div className="mb-6 w-full">
      <div className="flex items-center justify-between">
        <span
          className={cn(
            'text-3xl font-bold tabular-nums',
            isUrgent ? 'text-red-600 animate-pulse' : 'text-slate-900 dark:text-white'
          )}
        >
          {displaySeconds}
        </span>
        <span className="text-sm text-slate-500">{timeLimit}s</span>
      </div>
      <div className="mt-2 h-3 w-full overflow-hidden rounded-full bg-slate-200 dark:bg-slate-700">
        <div
          className={cn(
            'h-full rounded-full transition-all duration-200',
            isUrgent ? 'bg-red-500' : percentage > 50 ? 'bg-green-500' : 'bg-yellow-500'
          )}
          style={{ width: `${percentage}%` }}
        />
      </div>
    </div>
  );
}
