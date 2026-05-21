'use client';

import React, { useEffect, useRef, useState } from 'react';
import { motion } from 'framer-motion';

import { useReducedMotion } from '@/hooks/useReducedMotion';
import { ANIMATION_TIMING } from '@/lib/constants';
import { cn } from '@/lib/utils';
import type { LeaderboardEntry } from '@/types';

export interface AnimatedPodiumProps {
  entries: LeaderboardEntry[]; // top 3
}

const PODIUM_GRADIENTS = [
  {
    label: '1st',
    gradient: 'from-amber-100 via-yellow-50 to-amber-100 dark:from-amber-900/30 dark:via-yellow-900/20 dark:to-amber-900/30',
    border: 'border-amber-300 dark:border-amber-600',
    textClass: 'text-amber-700 dark:text-amber-300',
  },
  {
    label: '2nd',
    gradient: 'from-slate-100 via-gray-50 to-slate-100 dark:from-slate-800/50 dark:via-slate-700/30 dark:to-slate-800/50',
    border: 'border-slate-300 dark:border-slate-500',
    textClass: 'text-slate-700 dark:text-slate-300',
  },
  {
    label: '3rd',
    gradient: 'from-orange-100 via-amber-50 to-orange-100 dark:from-orange-900/30 dark:via-amber-900/20 dark:to-orange-900/30',
    border: 'border-orange-300 dark:border-orange-600',
    textClass: 'text-orange-700 dark:text-orange-300',
  },
] as const;

/**
 * AnimatedPodium displays the top 3 entries with gradient backgrounds,
 * animated score counters that count up from 0 to the final value,
 * and a trophy icon for 1st place.
 *
 * Respects `useReducedMotion` — shows final values immediately if true.
 */
export function AnimatedPodium({ entries }: AnimatedPodiumProps) {
  const reducedMotion = useReducedMotion();
  const topEntries = entries.slice(0, 3);

  if (topEntries.length === 0) {
    return null;
  }

  return (
    <div
      className="w-full"
      role="region"
      aria-label="Podium - Top participants"
      data-testid="animated-podium"
    >
      <div
        className={cn(
          'grid gap-4',
          topEntries.length === 1 && 'grid-cols-1 max-w-sm mx-auto',
          topEntries.length === 2 && 'grid-cols-2 max-w-lg mx-auto',
          topEntries.length >= 3 && 'grid-cols-1 sm:grid-cols-3'
        )}
      >
        {topEntries.map((entry, index) => (
          <PodiumCard
            key={entry.participantId}
            entry={entry}
            index={index}
            reducedMotion={reducedMotion}
          />
        ))}
      </div>
    </div>
  );
}

interface PodiumCardProps {
  entry: LeaderboardEntry;
  index: number;
  reducedMotion: boolean;
}

function PodiumCard({ entry, index, reducedMotion }: PodiumCardProps) {
  const config = PODIUM_GRADIENTS[index];

  return (
    <motion.div
      initial={reducedMotion ? false : { opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={
        reducedMotion
          ? { duration: 0 }
          : { duration: 0.4, delay: index * 0.1 }
      }
      className={cn(
        'flex flex-col items-center rounded-2xl border-2 p-5 bg-gradient-to-br',
        config.gradient,
        config.border
      )}
      aria-label={`${config.label} place: ${entry.nickname} with ${entry.score} points`}
      data-testid={`podium-card-${index + 1}`}
    >
      {/* Trophy icon for 1st place, medal for others */}
      {index === 0 ? (
        <TrophyIcon className="h-10 w-10 text-amber-500" />
      ) : index === 1 ? (
        <MedalIcon className="h-8 w-8 text-slate-400" />
      ) : (
        <MedalIcon className="h-8 w-8 text-orange-500" />
      )}

      {/* Rank label */}
      <span className={cn('mt-1 text-sm font-semibold', config.textClass)}>
        {config.label}
      </span>

      {/* Nickname */}
      <span className={cn('mt-2 text-lg font-bold truncate max-w-full', config.textClass)}>
        {entry.nickname}
      </span>

      {/* Animated score counter */}
      <ScoreCounter
        targetScore={entry.score}
        reducedMotion={reducedMotion}
      />
    </motion.div>
  );
}

interface ScoreCounterProps {
  targetScore: number;
  reducedMotion: boolean;
}

/**
 * Animates a score counter from 0 to the target value over ANIMATION_TIMING.scoreCountUp ms.
 * If reduced motion is enabled, shows the final value immediately.
 */
function ScoreCounter({ targetScore, reducedMotion }: ScoreCounterProps) {
  const [displayScore, setDisplayScore] = useState(reducedMotion ? targetScore : 0);
  const animationRef = useRef<number | null>(null);
  const startTimeRef = useRef<number | null>(null);

  useEffect(() => {
    if (reducedMotion) {
      setDisplayScore(targetScore);
      return;
    }

    // Reset for new target
    setDisplayScore(0);
    startTimeRef.current = null;

    const duration = ANIMATION_TIMING.scoreCountUp;

    const animate = (timestamp: number) => {
      if (startTimeRef.current === null) {
        startTimeRef.current = timestamp;
      }

      const elapsed = timestamp - startTimeRef.current;
      const progress = Math.min(elapsed / duration, 1);
      // Ease-out cubic for smooth deceleration
      const easedProgress = 1 - Math.pow(1 - progress, 3);
      const value = Math.round(targetScore * easedProgress);

      setDisplayScore(value);

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
  }, [targetScore, reducedMotion]);

  return (
    <span
      className="mt-1 text-2xl font-black text-slate-900 dark:text-white"
      aria-label={`Score: ${targetScore}`}
      data-testid="score-counter"
    >
      {displayScore.toLocaleString()}
    </span>
  );
}

// ============ SVG Icons ============

function TrophyIcon({ className }: { className?: string }) {
  return (
    <svg
      className={className}
      viewBox="0 0 24 24"
      fill="currentColor"
      aria-hidden="true"
      data-testid="trophy-icon"
    >
      <path d="M5 3h14v2h-1v1a7 7 0 01-4 6.32V15h2a3 3 0 013 3v1H5v-1a3 3 0 013-3h2v-2.68A7 7 0 016 6V5H5V3zm3 2v1a5 5 0 005 5 5 5 0 005-5V5H8zM3 5h2v2a5 5 0 001.5 3.57A8.96 8.96 0 013 5zm16 0h2a8.96 8.96 0 01-3.5 5.57A5 5 0 0019 7V5z" />
    </svg>
  );
}

function MedalIcon({ className }: { className?: string }) {
  return (
    <svg
      className={className}
      viewBox="0 0 24 24"
      fill="currentColor"
      aria-hidden="true"
    >
      <circle cx="12" cy="9" r="6" />
      <path d="M8 14.5V22l4-2 4 2v-7.5" />
    </svg>
  );
}
