'use client';

import { cn } from '@/lib/utils';
import { AnimatePresence, motion } from 'framer-motion';
import type { LeaderboardEntry } from '@/types';

interface LeaderboardProps {
  entries: LeaderboardEntry[];
  title?: string;
}

const RANK_COLORS = [
  'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-300', // 1st
  'bg-slate-100 text-slate-700 dark:bg-slate-700 dark:text-slate-300', // 2nd
  'bg-orange-100 text-orange-800 dark:bg-orange-900/30 dark:text-orange-300', // 3rd
];

const RANK_ICONS = ['🥇', '🥈', '🥉'];

export function Leaderboard({ entries, title = 'Leaderboard' }: LeaderboardProps) {
  if (entries.length === 0) return null;

  return (
    <div className="mt-6 w-full">
      <h3 className="mb-4 text-center text-lg font-semibold text-slate-900 dark:text-white">
        {title}
      </h3>

      <div className="space-y-2">
        <AnimatePresence mode="popLayout">
          {entries.slice(0, 5).map((entry) => (
            <motion.div
              key={entry.participantId}
              layout
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -20 }}
              transition={{
                layout: { type: 'spring', stiffness: 300, damping: 30, duration: 0.5 },
                opacity: { duration: 0.3 },
                y: { duration: 0.3 },
              }}
              className={cn(
                'flex items-center justify-between rounded-lg p-3',
                entry.rank <= 3 ? RANK_COLORS[entry.rank - 1] : 'bg-slate-50 dark:bg-slate-800'
              )}
            >
              <div className="flex items-center gap-3">
                <span className="text-lg">
                  {entry.rank <= 3 ? (
                    RANK_ICONS[entry.rank - 1]
                  ) : (
                    <span className="ml-1 text-sm font-bold text-slate-500">#{entry.rank}</span>
                  )}
                </span>
                <span className="font-medium text-slate-900 dark:text-white">
                  {entry.nickname}
                </span>
              </div>
              <div className="flex items-center gap-2">
                <RankChangeAnimation rankChange={entry.rankChange} />
                <span className="font-bold text-slate-900 dark:text-white">
                  {entry.score.toLocaleString()}
                </span>
              </div>
            </motion.div>
          ))}
        </AnimatePresence>
      </div>
    </div>
  );
}

function RankChangeAnimation({ rankChange }: { rankChange: number }) {
  if (rankChange === 0) return null;

  const isUp = rankChange > 0;

  return (
    <motion.span
      initial={{ opacity: 0, scale: 0.5 }}
      animate={{ opacity: 1, scale: 1 }}
      transition={{ duration: 0.4, delay: 0.2 }}
      className={cn(
        'text-xs font-semibold',
        isUp ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'
      )}
      aria-label={isUp ? `Moved up ${rankChange} positions` : `Moved down ${Math.abs(rankChange)} positions`}
    >
      {isUp ? `↑${rankChange}` : `↓${Math.abs(rankChange)}`}
    </motion.span>
  );
}
