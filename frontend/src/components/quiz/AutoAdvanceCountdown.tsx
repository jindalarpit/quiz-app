'use client';

import React from 'react';

import { cn } from '@/lib/utils';

interface AutoAdvanceCountdownProps {
  totalSeconds: number;
  remainingSeconds: number;
  onCancel: () => void;
}

/**
 * Visual countdown indicator shown during REVEAL state when auto mode is active.
 * Displays remaining seconds with a circular progress indicator and a cancel button.
 * Respects `prefers-reduced-motion` by disabling smooth transitions.
 */
export function AutoAdvanceCountdown({
  totalSeconds,
  remainingSeconds,
  onCancel,
}: AutoAdvanceCountdownProps) {
  const progress = totalSeconds > 0 ? (totalSeconds - remainingSeconds) / totalSeconds : 0;
  const percentage = Math.min(Math.max(progress * 100, 0), 100);

  // SVG circular progress values
  const radius = 40;
  const circumference = 2 * Math.PI * radius;
  const strokeDashoffset = circumference * (1 - progress);

  return (
    <div className="flex flex-col items-center gap-3">
      {/* Circular progress indicator with remaining seconds */}
      <div
        className="relative flex items-center justify-center"
        role="progressbar"
        aria-valuenow={remainingSeconds}
        aria-valuemin={0}
        aria-valuemax={totalSeconds}
        aria-label={`Auto-advancing in ${remainingSeconds} seconds`}
      >
        <svg
          width="96"
          height="96"
          viewBox="0 0 96 96"
          className="rotate-[-90deg]"
        >
          {/* Background circle */}
          <circle
            cx="48"
            cy="48"
            r={radius}
            fill="none"
            stroke="currentColor"
            strokeWidth="6"
            className="text-slate-200 dark:text-slate-700"
          />
          {/* Progress circle */}
          <circle
            cx="48"
            cy="48"
            r={radius}
            fill="none"
            stroke="currentColor"
            strokeWidth="6"
            strokeLinecap="round"
            strokeDasharray={circumference}
            strokeDashoffset={strokeDashoffset}
            className={cn(
              'text-primary-600 dark:text-primary-400',
              'motion-safe:transition-[stroke-dashoffset] motion-safe:duration-1000 motion-safe:ease-linear'
            )}
          />
        </svg>
        {/* Remaining seconds display */}
        <span className="absolute text-2xl font-bold tabular-nums text-slate-900 dark:text-white">
          {remainingSeconds}
        </span>
      </div>

      {/* Progress bar (linear) */}
      <div className="w-full max-w-xs">
        <div className="h-2 w-full overflow-hidden rounded-full bg-slate-200 dark:bg-slate-700">
          <div
            className={cn(
              'h-full rounded-full bg-primary-600 dark:bg-primary-400',
              'motion-safe:transition-[width] motion-safe:duration-1000 motion-safe:ease-linear'
            )}
            style={{ width: `${percentage}%` }}
          />
        </div>
        <p className="mt-1 text-center text-xs text-slate-500 dark:text-slate-400">
          Auto-advancing in {remainingSeconds}s
        </p>
      </div>

      {/* Cancel button */}
      <button
        type="button"
        onClick={onCancel}
        aria-label="Cancel auto-advance"
        className={cn(
          'rounded-md px-4 py-2 text-sm font-medium',
          'border border-slate-300 bg-white text-slate-700',
          'hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2',
          'dark:border-slate-600 dark:bg-slate-800 dark:text-slate-200 dark:hover:bg-slate-700',
          'motion-safe:transition-colors motion-safe:duration-150'
        )}
      >
        Cancel
      </button>
    </div>
  );
}
