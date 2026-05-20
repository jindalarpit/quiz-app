'use client';

import React, { useEffect, useRef, useState } from 'react';

import { useReducedMotion } from '@/hooks/useReducedMotion';
import { cn } from '@/lib/utils';

export interface SpeedBonusIndicatorProps {
  baseComponent: number;
  speedBonus: number;
  streakMultiplier: number;
  totalScore: number;
  speedPercentage: number;
  isCorrect: boolean;
}

const ANIMATION_DURATION_MS = 800;
const MIN_VISIBLE_DURATION_MS = 3000;

/**
 * Easing function: ease-out cubic
 * Starts fast, decelerates toward the end.
 */
function easeOutCubic(t: number): number {
  return 1 - Math.pow(1 - t, 3);
}

/**
 * Custom hook that animates a number from 0 to a target value over a given duration
 * using requestAnimationFrame with ease-out timing.
 * If reduced motion is preferred, returns the target value immediately.
 */
function useCountingAnimation(target: number, duration: number, reducedMotion: boolean): number {
  const [displayValue, setDisplayValue] = useState(reducedMotion ? target : 0);
  const animationRef = useRef<number | null>(null);
  const startTimeRef = useRef<number | null>(null);

  useEffect(() => {
    if (reducedMotion) {
      setDisplayValue(target);
      return;
    }

    setDisplayValue(0);
    startTimeRef.current = null;

    const animate = (timestamp: number) => {
      if (startTimeRef.current === null) {
        startTimeRef.current = timestamp;
      }

      const elapsed = timestamp - startTimeRef.current;
      const progress = Math.min(elapsed / duration, 1);
      const easedProgress = easeOutCubic(progress);
      const currentValue = Math.round(easedProgress * target);

      setDisplayValue(currentValue);

      if (progress < 1) {
        animationRef.current = requestAnimationFrame(animate);
      }
    };

    animationRef.current = requestAnimationFrame(animate);

    return () => {
      if (animationRef.current !== null) {
        cancelAnimationFrame(animationRef.current);
      }
    };
  }, [target, duration, reducedMotion]);

  return displayValue;
}

/**
 * SpeedBonusIndicator displays the score breakdown after each question round.
 *
 * - For incorrect/unanswered: shows "0 points" with no breakdown
 * - For correct answers: shows base component, speed bonus, and speed percentage
 * - For correct answers with streak: shows base, speed bonus, streak multiplier, and total
 * - Animates the score counting up from 0 over 800ms with ease-out timing
 * - Respects prefers-reduced-motion by displaying final score immediately
 * - Remains visible for minimum 3 seconds or until host advances
 */
export function SpeedBonusIndicator({
  baseComponent,
  speedBonus,
  streakMultiplier,
  totalScore,
  speedPercentage,
  isCorrect,
}: SpeedBonusIndicatorProps) {
  const reducedMotion = useReducedMotion();
  const [isVisible, setIsVisible] = useState(true);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const animatedScore = useCountingAnimation(
    isCorrect ? totalScore : 0,
    ANIMATION_DURATION_MS,
    reducedMotion
  );

  // Ensure component remains visible for minimum 3 seconds
  useEffect(() => {
    timerRef.current = setTimeout(() => {
      // After 3 seconds, the component can be hidden by parent (host advances)
      // We keep it visible — parent controls unmounting
    }, MIN_VISIBLE_DURATION_MS);

    return () => {
      if (timerRef.current !== null) {
        clearTimeout(timerRef.current);
      }
    };
  }, []);

  const hasStreak = streakMultiplier > 1;
  const scoreBeforeMultiplier = baseComponent + speedBonus;

  if (!isCorrect) {
    return (
      <div
        className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-900"
        role="region"
        aria-label="Score breakdown"
        aria-live="polite"
      >
        <div className="text-center">
          <span className="text-2xl font-bold text-slate-400 dark:text-slate-500">
            0 points
          </span>
        </div>
      </div>
    );
  }

  return (
    <div
      className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-900"
      role="region"
      aria-label="Score breakdown"
      aria-live="polite"
    >
      {/* Animated total score */}
      <div className="mb-3 text-center">
        <span
          className="text-3xl font-bold text-slate-900 dark:text-white"
          aria-label={`Total score: ${totalScore} points`}
        >
          {animatedScore.toLocaleString()}
        </span>
        <span className="ml-1 text-sm text-slate-500 dark:text-slate-400">
          points
        </span>
      </div>

      {/* Speed percentage */}
      <div className="mb-3 text-center">
        <span className="text-sm font-medium text-emerald-600 dark:text-emerald-400">
          {speedPercentage}% speed bonus
        </span>
      </div>

      {/* Score breakdown */}
      <div className="space-y-1.5">
        <BreakdownRow label="Base" value={baseComponent} />
        <BreakdownRow label="Speed bonus" value={speedBonus} />

        {hasStreak && (
          <>
            <div className="my-1.5 border-t border-slate-100 dark:border-slate-800" />
            <BreakdownRow
              label={`Subtotal`}
              value={scoreBeforeMultiplier}
            />
            <BreakdownRow
              label={`Streak multiplier`}
              value={`×${streakMultiplier}`}
              isMultiplier
            />
            <div className="my-1.5 border-t border-slate-100 dark:border-slate-800" />
            <BreakdownRow label="Total" value={totalScore} isBold />
          </>
        )}
      </div>
    </div>
  );
}

function BreakdownRow({
  label,
  value,
  isBold = false,
  isMultiplier = false,
}: {
  label: string;
  value: number | string;
  isBold?: boolean;
  isMultiplier?: boolean;
}) {
  return (
    <div className="flex items-center justify-between">
      <span
        className={cn(
          'text-sm text-slate-600 dark:text-slate-400',
          isBold && 'font-semibold text-slate-900 dark:text-white'
        )}
      >
        {label}
      </span>
      <span
        className={cn(
          'text-sm text-slate-900 dark:text-white',
          isBold && 'font-semibold',
          isMultiplier && 'font-semibold text-amber-600 dark:text-amber-400'
        )}
      >
        {isMultiplier ? value : typeof value === 'number' ? value.toLocaleString() : value}
      </span>
    </div>
  );
}
