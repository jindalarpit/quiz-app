'use client';

import React from 'react';

import { cn } from '@/lib/utils';

export interface PodiumEntry {
  rank: number;
  nickname: string;
  score: number;
  correctAnswers: number;
  totalAnswers: number;
  maxStreak: number;
  avgResponseTimeSec: number;
}

interface TopThreePodiumProps {
  entries: PodiumEntry[];
}

const PODIUM_CONFIG = [
  {
    label: '1st',
    icon: '🥇',
    bgClass: 'bg-yellow-50 border-yellow-300 dark:bg-yellow-900/20 dark:border-yellow-600',
    textClass: 'text-yellow-700 dark:text-yellow-300',
    rankClass: 'text-yellow-600 dark:text-yellow-400',
  },
  {
    label: '2nd',
    icon: '🥈',
    bgClass: 'bg-slate-50 border-slate-300 dark:bg-slate-800/50 dark:border-slate-600',
    textClass: 'text-slate-700 dark:text-slate-300',
    rankClass: 'text-slate-500 dark:text-slate-400',
  },
  {
    label: '3rd',
    icon: '🥉',
    bgClass: 'bg-orange-50 border-orange-300 dark:bg-orange-900/20 dark:border-orange-600',
    textClass: 'text-orange-700 dark:text-orange-300',
    rankClass: 'text-orange-600 dark:text-orange-400',
  },
] as const;

export function TopThreePodium({ entries }: TopThreePodiumProps) {
  if (entries.length === 0) {
    return null;
  }

  const topEntries = entries.slice(0, 3);

  return (
    <div className="w-full" role="region" aria-label="Top participants podium">
      <div
        className={cn(
          'grid gap-3',
          topEntries.length === 1 && 'grid-cols-1 max-w-sm mx-auto',
          topEntries.length === 2 && 'grid-cols-2 max-w-lg mx-auto',
          topEntries.length >= 3 && 'grid-cols-1 sm:grid-cols-3'
        )}
      >
        {topEntries.map((entry, index) => {
          const config = PODIUM_CONFIG[index];
          return (
            <PodiumCard key={entry.rank} entry={entry} config={config} />
          );
        })}
      </div>
    </div>
  );
}

interface PodiumCardProps {
  entry: PodiumEntry;
  config: (typeof PODIUM_CONFIG)[number];
}

function PodiumCard({ entry, config }: PodiumCardProps) {
  return (
    <div
      className={cn(
        'flex flex-col items-center rounded-xl border-2 p-4',
        config.bgClass
      )}
      aria-label={`${config.label} place: ${entry.nickname}`}
    >
      <span className="text-3xl" aria-hidden="true">
        {config.icon}
      </span>
      <span className={cn('mt-1 text-sm font-semibold', config.rankClass)}>
        {config.label}
      </span>
      <span className={cn('mt-2 text-lg font-bold truncate max-w-full', config.textClass)}>
        {entry.nickname}
      </span>
      <span className="mt-1 text-2xl font-black text-slate-900 dark:text-white">
        {entry.score.toLocaleString()}
      </span>
      <div className="mt-2 flex flex-wrap justify-center gap-x-3 gap-y-1 text-xs text-slate-500 dark:text-slate-400">
        <span>{entry.correctAnswers}/{entry.totalAnswers} correct</span>
        <span>🔥 {entry.maxStreak} streak</span>
      </div>
    </div>
  );
}
