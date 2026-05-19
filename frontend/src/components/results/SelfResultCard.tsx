'use client';

import { cn } from '@/lib/utils';
import type { ParticipantSelfResult } from '@/types';

interface SelfResultCardProps {
  result: ParticipantSelfResult;
}

export function SelfResultCard({ result }: SelfResultCardProps) {
  const {
    rank,
    score,
    correctAnswers,
    totalQuestions,
    maxStreak,
    avgResponseTimeSec,
    scoreDifference,
    aboveAverage,
  } = result;

  return (
    <div
      className="fixed bottom-4 left-1/2 z-50 w-[calc(100%-2rem)] max-w-md -translate-x-1/2 rounded-xl border border-slate-200 bg-white p-4 shadow-lg dark:border-slate-700 dark:bg-slate-900"
      role="region"
      aria-label="Your results"
    >
      {/* Rank and Score Header */}
      <div className="mb-3 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <span className="text-2xl font-bold text-slate-900 dark:text-white">
            #{rank}
          </span>
          <span className="text-sm text-slate-500 dark:text-slate-400">
            Your Rank
          </span>
        </div>
        <div className="text-right">
          <span className="text-xl font-bold text-slate-900 dark:text-white">
            {score.toLocaleString()}
          </span>
          <span className="ml-1 text-sm text-slate-500 dark:text-slate-400">
            pts
          </span>
        </div>
      </div>

      {/* Score Comparison Indicator */}
      <div
        className={cn(
          'mb-3 rounded-lg px-3 py-1.5 text-center text-sm font-medium',
          aboveAverage
            ? 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300'
            : 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300'
        )}
        aria-label={`Score is ${aboveAverage ? 'above' : 'below'} average by ${Math.abs(scoreDifference)} points`}
      >
        {aboveAverage ? '+' : '-'}
        {Math.abs(scoreDifference)} {aboveAverage ? 'above' : 'below'} average
      </div>

      {/* Stats Grid */}
      <div className="grid grid-cols-4 gap-2 text-center">
        <StatItem
          label="Correct"
          value={`${correctAnswers}/${totalQuestions}`}
        />
        <StatItem label="Streak" value={String(maxStreak)} />
        <StatItem
          label="Avg Time"
          value={`${avgResponseTimeSec.toFixed(1)}s`}
        />
        <StatItem
          label="Accuracy"
          value={
            totalQuestions > 0
              ? `${Math.round((correctAnswers / totalQuestions) * 100)}%`
              : '0%'
          }
        />
      </div>
    </div>
  );
}

function StatItem({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex flex-col">
      <span className="text-sm font-semibold text-slate-900 dark:text-white">
        {value}
      </span>
      <span className="text-xs text-slate-500 dark:text-slate-400">
        {label}
      </span>
    </div>
  );
}
