'use client';

import { motion } from 'framer-motion';

import { AnimatedPodium } from '@/components/results/AnimatedPodium';
import { useReducedMotion } from '@/hooks/useReducedMotion';
import { ANIMATION_TIMING } from '@/lib/constants';
import { cn } from '@/lib/utils';
import type { LeaderboardEntry, SessionSummary } from '@/types';

interface SessionEndProps {
  leaderboard: LeaderboardEntry[];
  summary: SessionSummary | null;
  onPlayAgain: () => void;
}

const RANK_COLORS = [
  'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-300', // 1st
  'bg-slate-100 text-slate-700 dark:bg-slate-700 dark:text-slate-300', // 2nd
  'bg-orange-100 text-orange-800 dark:bg-orange-900/30 dark:text-orange-300', // 3rd
];

const RANK_ICONS = ['🥇', '🥈', '🥉'];

export function SessionEnd({ leaderboard, summary, onPlayAgain }: SessionEndProps) {
  const reducedMotion = useReducedMotion();
  const topThree = leaderboard.slice(0, 3);
  const remainingEntries = leaderboard.slice(3);

  return (
    <div className="flex min-h-[calc(100vh-4rem)] flex-col items-center justify-center p-4">
      <div className="w-full max-w-2xl text-center">
        <h1 className="text-3xl font-bold text-slate-900 dark:text-white">🎉 Quiz Complete!</h1>

        {summary && (
          <div className="mt-6 flex justify-center gap-6">
            <div className="text-center">
              <p className="text-2xl font-bold text-primary-600">{summary.totalQuestions}</p>
              <p className="text-xs text-slate-500">Questions</p>
            </div>
            <div className="text-center">
              <p className="text-2xl font-bold text-primary-600">{summary.totalParticipants}</p>
              <p className="text-xs text-slate-500">Players</p>
            </div>
            <div className="text-center">
              <p className="text-2xl font-bold text-primary-600">
                {Math.floor(summary.durationSeconds / 60)}:{String(summary.durationSeconds % 60).padStart(2, '0')}
              </p>
              <p className="text-xs text-slate-500">Duration</p>
            </div>
          </div>
        )}

        {/* Animated Podium for top 3 */}
        {topThree.length > 0 && (
          <div className="mt-8">
            <AnimatedPodium entries={topThree} />
          </div>
        )}

        {/* Animated Leaderboard rows */}
        {leaderboard.length > 0 && (
          <div className="mt-8 w-full">
            <h3 className="mb-4 text-center text-lg font-semibold text-slate-900 dark:text-white">
              Final Standings
            </h3>

            <div className="space-y-2" data-testid="animated-leaderboard">
              {leaderboard.map((entry, index) => (
                <motion.div
                  key={entry.participantId}
                  initial={reducedMotion ? false : { opacity: 0, y: 20 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={
                    reducedMotion
                      ? { duration: 0 }
                      : {
                          duration: ANIMATION_TIMING.cardEntrance / 1000,
                          delay: (index * ANIMATION_TIMING.rowStagger) / 1000,
                        }
                  }
                  className={cn(
                    'flex items-center justify-between rounded-2xl border border-slate-200 p-4 dark:border-slate-700',
                    entry.rank <= 3
                      ? RANK_COLORS[entry.rank - 1]
                      : 'bg-slate-50 dark:bg-slate-800'
                  )}
                  data-testid={`leaderboard-row-${index}`}
                >
                  <div className="flex items-center gap-3">
                    <span className="text-lg">
                      {entry.rank <= 3 ? (
                        RANK_ICONS[entry.rank - 1]
                      ) : (
                        <span className="ml-1 text-sm font-bold text-slate-500">
                          #{entry.rank}
                        </span>
                      )}
                    </span>
                    <span className="font-medium text-slate-900 dark:text-white">
                      {entry.nickname}
                    </span>
                  </div>
                  <span className="font-bold text-slate-900 dark:text-white">
                    {entry.score.toLocaleString()}
                  </span>
                </motion.div>
              ))}
            </div>
          </div>
        )}

        <button onClick={onPlayAgain} className="btn-primary mt-8 px-8 py-3 text-lg">
          Play Again
        </button>
      </div>
    </div>
  );
}
