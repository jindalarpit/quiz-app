'use client';

import React, { useCallback, useEffect, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';

import { useReducedMotion } from '@/hooks/useReducedMotion';
import { cn } from '@/lib/utils';
import type { AnimationPhase, LeaderboardUpdateEntry } from '@/types';

export interface AnimatedLeaderboardProps {
  entries: LeaderboardUpdateEntry[];
  previousEntries: LeaderboardUpdateEntry[];
  isHost: boolean;
  roundNumber: number;
  animationPhase: AnimationPhase;
}

/** Duration constants (ms) */
const POSITION_DURATION_MS = 500;
const SCORE_DURATION_MS = 600;
const DELTA_FADE_DURATION_MS = 200;
const MAX_ANIMATION_BUDGET_MS = 1500;
const PARTICLE_DURATION_MS = 1000;
const PULSE_DURATION_MS = 300;

/** Framer Motion duration in seconds */
const POSITION_DURATION_S = POSITION_DURATION_MS / 1000;
const DELTA_FADE_DURATION_S = DELTA_FADE_DURATION_MS / 1000;

/**
 * AnimatedLeaderboard displays the live leaderboard with animated rank transitions,
 * score increments, and position changes after each question round.
 *
 * Animation sequence (total 1300ms):
 * 1. Rank position changes via vertical slide (500ms)
 * 2. Score counting up from previous to new score (600ms)
 * 3. Rank delta indicator fade-in (200ms)
 *
 * Visual treatments:
 * - Top 3: gold/silver/bronze backgrounds with trophy/medal icons
 * - Particle animation when entering top 3 for first time in session
 * - Streak flame icons (none < 3, single 3-4, double ≥ 5)
 * - Pulse animation on multiplier badge when streak multiplier is active
 *
 * Handles animation interruption by cancelling in-progress animations,
 * applying final state immediately, and beginning the new sequence.
 *
 * Respects prefers-reduced-motion by skipping all animations.
 */
export function AnimatedLeaderboard({
  entries,
  previousEntries,
  isHost,
  roundNumber,
  animationPhase,
}: AnimatedLeaderboardProps) {
  const reducedMotion = useReducedMotion();
  const [phase, setPhase] = useState<'position' | 'score' | 'delta' | 'complete'>(
    reducedMotion ? 'complete' : 'position'
  );
  const phaseTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const budgetTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const sequenceIdRef = useRef(0);

  // Track which participants have been in top 3 during this session
  const top3SeenRef = useRef<Set<string>>(new Set());

  const isFirstRound = roundNumber === 1;

  // Build a map of previous scores by participantId for score counting animation
  const previousScoreMap = useRef<Map<string, number>>(new Map());

  useEffect(() => {
    const map = new Map<string, number>();
    for (const entry of previousEntries) {
      map.set(entry.participantId, entry.cumulativeScore);
    }
    previousScoreMap.current = map;
  }, [previousEntries]);

  // Determine which participants are entering top 3 for the first time
  const newTop3Participants = new Set<string>();
  for (const entry of entries) {
    if (entry.rank <= 3 && !top3SeenRef.current.has(entry.participantId)) {
      newTop3Participants.add(entry.participantId);
    }
  }

  // After rendering, mark current top 3 as seen
  useEffect(() => {
    for (const entry of entries) {
      if (entry.rank <= 3) {
        top3SeenRef.current.add(entry.participantId);
      }
    }
  }, [entries]);

  const cancelTimers = useCallback(() => {
    if (phaseTimerRef.current !== null) {
      clearTimeout(phaseTimerRef.current);
      phaseTimerRef.current = null;
    }
    if (budgetTimerRef.current !== null) {
      clearTimeout(budgetTimerRef.current);
      budgetTimerRef.current = null;
    }
  }, []);

  // Start animation sequence when entries change
  useEffect(() => {
    if (reducedMotion) {
      setPhase('complete');
      return;
    }

    // Cancel any in-progress animation and apply final state
    cancelTimers();
    sequenceIdRef.current += 1;
    const currentSequenceId = sequenceIdRef.current;

    // Begin new animation sequence
    setPhase('position');

    // Phase 1 → Phase 2: after position animation completes
    phaseTimerRef.current = setTimeout(() => {
      if (sequenceIdRef.current !== currentSequenceId) return;
      setPhase('score');

      // Phase 2 → Phase 3: after score counting completes
      phaseTimerRef.current = setTimeout(() => {
        if (sequenceIdRef.current !== currentSequenceId) return;
        setPhase('delta');

        // Phase 3 → Complete: after delta fade-in completes
        phaseTimerRef.current = setTimeout(() => {
          if (sequenceIdRef.current !== currentSequenceId) return;
          setPhase('complete');
        }, DELTA_FADE_DURATION_MS);
      }, SCORE_DURATION_MS);
    }, POSITION_DURATION_MS);

    // Safety budget: force complete within MAX_ANIMATION_BUDGET_MS
    budgetTimerRef.current = setTimeout(() => {
      if (sequenceIdRef.current !== currentSequenceId) return;
      setPhase('complete');
    }, MAX_ANIMATION_BUDGET_MS);

    return cancelTimers;
  }, [entries, reducedMotion, cancelTimers]);

  return (
    <div
      className="w-full rounded-lg border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-900"
      role="region"
      aria-label="Leaderboard"
      aria-live="polite"
    >
      <h2 className="mb-3 text-lg font-semibold text-slate-900 dark:text-white">
        Leaderboard
      </h2>

      <div className="space-y-1">
        <AnimatePresence mode="popLayout">
          {entries.map((entry) => (
            <LeaderboardRow
              key={entry.participantId}
              entry={entry}
              previousScore={previousScoreMap.current.get(entry.participantId) ?? entry.cumulativeScore}
              phase={phase}
              isFirstRound={isFirstRound}
              reducedMotion={reducedMotion}
              isNewTop3={newTop3Participants.has(entry.participantId)}
            />
          ))}
        </AnimatePresence>
      </div>
    </div>
  );
}

// ============ Leaderboard Row ============

interface LeaderboardRowProps {
  entry: LeaderboardUpdateEntry;
  previousScore: number;
  phase: 'position' | 'score' | 'delta' | 'complete';
  isFirstRound: boolean;
  reducedMotion: boolean;
  isNewTop3: boolean;
}

/**
 * Returns the background class for top-3 visual treatments.
 */
function getTopRankStyles(rank: number): string {
  switch (rank) {
    case 1:
      return 'bg-amber-50 border border-amber-300 dark:bg-amber-900/20 dark:border-amber-700';
    case 2:
      return 'bg-slate-100 border border-slate-300 dark:bg-slate-700/30 dark:border-slate-500';
    case 3:
      return 'bg-orange-50 border border-orange-300 dark:bg-orange-900/20 dark:border-orange-700';
    default:
      return 'bg-slate-50 dark:bg-slate-800';
  }
}

function LeaderboardRow({
  entry,
  previousScore,
  phase,
  isFirstRound,
  reducedMotion,
  isNewTop3,
}: LeaderboardRowProps) {
  const showDelta = phase === 'delta' || phase === 'complete';
  const [showParticles, setShowParticles] = useState(false);

  // Trigger particle animation for new top 3 entries
  useEffect(() => {
    if (isNewTop3 && !reducedMotion) {
      setShowParticles(true);
      const timer = setTimeout(() => {
        setShowParticles(false);
      }, PARTICLE_DURATION_MS);
      return () => clearTimeout(timer);
    }
  }, [isNewTop3, reducedMotion]);

  return (
    <motion.div
      layout={!reducedMotion}
      initial={reducedMotion ? false : { opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      exit={reducedMotion ? undefined : { opacity: 0, y: -20 }}
      transition={
        reducedMotion
          ? { duration: 0 }
          : {
              layout: { duration: POSITION_DURATION_S, type: 'spring', stiffness: 300, damping: 30 },
              opacity: { duration: 0.2 },
              y: { duration: POSITION_DURATION_S },
            }
      }
      className={cn(
        'relative flex items-center justify-between rounded-md px-3 py-2',
        getTopRankStyles(entry.rank)
      )}
      data-testid={`leaderboard-row-${entry.participantId}`}
    >
      {/* Particle animation overlay */}
      {showParticles && <ParticleEffect />}

      {/* Rank with top-3 icon */}
      <div className="flex items-center gap-3">
        <span
          className="flex w-8 items-center justify-center text-sm font-bold text-slate-700 dark:text-slate-300"
          aria-label={`Rank ${entry.rank}`}
        >
          {entry.rank <= 3 ? (
            <TopRankIcon rank={entry.rank} />
          ) : (
            <span>#{entry.rank}</span>
          )}
        </span>

        {/* Nickname */}
        <span className="text-sm font-medium text-slate-900 dark:text-white">
          {entry.nickname}
        </span>

        {/* Streak flame icon */}
        <StreakIcon streakCount={entry.streakCount} />
      </div>

      {/* Score, multiplier badge, and rank delta */}
      <div className="flex items-center gap-3">
        {/* Streak multiplier badge */}
        {entry.streakMultiplier > 1 && (
          <MultiplierBadge
            multiplier={entry.streakMultiplier}
            reducedMotion={reducedMotion}
          />
        )}

        {/* Score with counting animation */}
        <ScoreDisplay
          currentScore={entry.cumulativeScore}
          previousScore={previousScore}
          phase={phase}
          reducedMotion={reducedMotion}
        />

        {/* Rank delta indicator */}
        <RankDeltaIndicator
          rankDelta={entry.rankDelta}
          isVisible={showDelta}
          isFirstRound={isFirstRound}
          reducedMotion={reducedMotion}
        />
      </div>
    </motion.div>
  );
}

// ============ Top 3 Visual Treatments ============

function TopRankIcon({ rank }: { rank: number }) {
  switch (rank) {
    case 1:
      return (
        <span aria-label="1st place" data-testid="rank-icon-trophy">
          <TrophyIcon className="h-5 w-5 text-amber-500" />
        </span>
      );
    case 2:
      return (
        <span aria-label="2nd place" data-testid="rank-icon-medal-silver">
          <MedalIcon className="h-5 w-5 text-slate-400" />
        </span>
      );
    case 3:
      return (
        <span aria-label="3rd place" data-testid="rank-icon-medal-bronze">
          <MedalIcon className="h-5 w-5 text-orange-500" />
        </span>
      );
    default:
      return null;
  }
}

// ============ Particle Animation ============

function ParticleEffect() {
  return (
    <div
      className="pointer-events-none absolute inset-0 overflow-hidden rounded-md"
      aria-hidden="true"
      data-testid="particle-effect"
    >
      {Array.from({ length: 8 }).map((_, i) => (
        <span
          key={i}
          className="absolute left-1/2 top-1/2 h-2 w-2 rounded-full animate-particle-burst"
          style={{
            backgroundColor: i % 2 === 0 ? '#f59e0b' : '#fbbf24',
            animationDelay: `${i * 50}ms`,
            transform: `rotate(${i * 45}deg) translateX(10px)`,
          }}
        />
      ))}
    </div>
  );
}

// ============ Streak Icons ============

interface StreakIconProps {
  streakCount: number;
}

function StreakIcon({ streakCount }: StreakIconProps) {
  if (streakCount < 3) {
    return null;
  }

  if (streakCount >= 5) {
    return (
      <span
        className="flex items-center"
        aria-label={`Streak of ${streakCount}, double flame`}
        data-testid="streak-double-flame"
      >
        <FlameIcon className="h-4 w-4 text-orange-500" />
        <FlameIcon className="-ml-1 h-4 w-4 text-red-500" />
      </span>
    );
  }

  // streakCount 3-4: single flame
  return (
    <span
      className="flex items-center"
      aria-label={`Streak of ${streakCount}, single flame`}
      data-testid="streak-single-flame"
    >
      <FlameIcon className="h-4 w-4 text-orange-500" />
    </span>
  );
}

// ============ Multiplier Badge ============

interface MultiplierBadgeProps {
  multiplier: number;
  reducedMotion: boolean;
}

function MultiplierBadge({ multiplier, reducedMotion }: MultiplierBadgeProps) {
  const [shouldPulse, setShouldPulse] = useState(false);

  useEffect(() => {
    if (!reducedMotion && multiplier > 1) {
      setShouldPulse(true);
      const timer = setTimeout(() => {
        setShouldPulse(false);
      }, PULSE_DURATION_MS);
      return () => clearTimeout(timer);
    }
  }, [multiplier, reducedMotion]);

  return (
    <span
      className={cn(
        'inline-flex items-center rounded-full bg-purple-100 px-2 py-0.5 text-xs font-bold text-purple-700 dark:bg-purple-900/30 dark:text-purple-300',
        shouldPulse && !reducedMotion && 'animate-pulse-multiplier'
      )}
      data-testid="multiplier-badge"
      aria-label={`${multiplier}x streak multiplier`}
    >
      {multiplier}x
    </span>
  );
}

// ============ Score Display ============

interface ScoreDisplayProps {
  currentScore: number;
  previousScore: number;
  phase: 'position' | 'score' | 'delta' | 'complete';
  reducedMotion: boolean;
}

function ScoreDisplay({ currentScore, previousScore, phase, reducedMotion }: ScoreDisplayProps) {
  const [displayScore, setDisplayScore] = useState(reducedMotion ? currentScore : previousScore);
  const animationRef = useRef<number | null>(null);
  const startTimeRef = useRef<number | null>(null);

  useEffect(() => {
    // If reduced motion, show final score immediately
    if (reducedMotion) {
      setDisplayScore(currentScore);
      return;
    }

    // During position phase, show previous score
    if (phase === 'position') {
      setDisplayScore(previousScore);
      return;
    }

    // During score phase, animate from previous to current
    if (phase === 'score') {
      startTimeRef.current = null;

      const animate = (timestamp: number) => {
        if (startTimeRef.current === null) {
          startTimeRef.current = timestamp;
        }

        const elapsed = timestamp - startTimeRef.current;
        const progress = Math.min(elapsed / SCORE_DURATION_MS, 1);
        // Ease-out cubic
        const easedProgress = 1 - Math.pow(1 - progress, 3);
        const value = Math.round(previousScore + (currentScore - previousScore) * easedProgress);

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
    }

    // During delta or complete phase, show final score
    setDisplayScore(currentScore);
  }, [phase, currentScore, previousScore, reducedMotion]);

  // When entries change (interruption), immediately show current score if in complete phase
  useEffect(() => {
    if (reducedMotion || phase === 'complete' || phase === 'delta') {
      setDisplayScore(currentScore);
    }
  }, [currentScore, reducedMotion, phase]);

  return (
    <span
      className="min-w-[60px] text-right text-sm font-semibold text-slate-900 dark:text-white"
      aria-label={`Score: ${currentScore}`}
    >
      {displayScore.toLocaleString()}
    </span>
  );
}

// ============ Rank Delta Indicator ============

interface RankDeltaIndicatorProps {
  rankDelta: number;
  isVisible: boolean;
  isFirstRound: boolean;
  reducedMotion: boolean;
}

function RankDeltaIndicator({
  rankDelta,
  isVisible,
  isFirstRound,
  reducedMotion,
}: RankDeltaIndicatorProps) {
  // On first round, always show neutral indicator
  const effectiveDelta = isFirstRound ? 0 : rankDelta;

  const content = (() => {
    if (effectiveDelta > 0) {
      return (
        <span
          className="flex items-center text-xs font-medium text-emerald-600 dark:text-emerald-400"
          aria-label={`Moved up ${effectiveDelta} positions`}
          data-testid="rank-delta-up"
        >
          <ArrowUp className="mr-0.5 h-3 w-3" />
          +{effectiveDelta}
        </span>
      );
    }
    if (effectiveDelta < 0) {
      return (
        <span
          className="flex items-center text-xs font-medium text-red-600 dark:text-red-400"
          aria-label={`Moved down ${Math.abs(effectiveDelta)} positions`}
          data-testid="rank-delta-down"
        >
          <ArrowDown className="mr-0.5 h-3 w-3" />
          {effectiveDelta}
        </span>
      );
    }
    return (
      <span
        className="flex items-center text-xs font-medium text-slate-400 dark:text-slate-500"
        aria-label="Rank unchanged"
        data-testid="rank-delta-neutral"
      >
        <Dash className="mr-0.5 h-3 w-3" />
        0
      </span>
    );
  })();

  if (reducedMotion) {
    return <div className="w-12">{content}</div>;
  }

  return (
    <motion.div
      className="w-12"
      initial={{ opacity: 0 }}
      animate={{ opacity: isVisible ? 1 : 0 }}
      transition={{ duration: DELTA_FADE_DURATION_S }}
    >
      {content}
    </motion.div>
  );
}

// ============ SVG Icons ============

function ArrowUp({ className }: { className?: string }) {
  return (
    <svg
      className={className}
      viewBox="0 0 12 12"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M6 10V2M6 2L2 6M6 2L10 6" />
    </svg>
  );
}

function ArrowDown({ className }: { className?: string }) {
  return (
    <svg
      className={className}
      viewBox="0 0 12 12"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M6 2V10M6 10L2 6M6 10L10 6" />
    </svg>
  );
}

function Dash({ className }: { className?: string }) {
  return (
    <svg
      className={className}
      viewBox="0 0 12 12"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      aria-hidden="true"
    >
      <path d="M2 6H10" />
    </svg>
  );
}

function TrophyIcon({ className }: { className?: string }) {
  return (
    <svg
      className={className}
      viewBox="0 0 24 24"
      fill="currentColor"
      aria-hidden="true"
    >
      <path d="M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z" />
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

function FlameIcon({ className }: { className?: string }) {
  return (
    <svg
      className={className}
      viewBox="0 0 24 24"
      fill="currentColor"
      aria-hidden="true"
    >
      <path d="M13.5 0.67s0.74 2.65 0.74 4.8c0 2.06-1.35 3.73-3.41 3.73-2.07 0-3.63-1.67-3.63-3.73l0.03-0.36C5.21 7.51 4 10.62 4 14c0 4.42 3.58 8 8 8s8-3.58 8-8C20 8.61 17.41 3.8 13.5 0.67zM11.71 19c-1.78 0-3.22-1.4-3.22-3.14 0-1.62 1.05-2.76 2.81-3.12 1.77-0.36 3.6-1.21 4.62-2.58 0.39 1.29 0.59 2.65 0.59 4.04 0 2.65-2.15 4.8-4.8 4.8z" />
    </svg>
  );
}
