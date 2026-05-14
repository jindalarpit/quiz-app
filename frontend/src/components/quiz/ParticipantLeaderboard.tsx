'use client';

import { cn } from '@/lib/utils';
import type { LeaderboardEntry } from '@/types';

export interface ParticipantLeaderboardViewData {
  ownRank: number;
  ownScore: number;
  rankChange: number;
  above: LeaderboardEntry | null;
  below: LeaderboardEntry | null;
}

interface ParticipantLeaderboardProps {
  view: ParticipantLeaderboardViewData;
  nickname: string;
}

export function ParticipantLeaderboard({ view, nickname }: ParticipantLeaderboardProps) {
  const { ownRank, ownScore, rankChange, above, below } = view;

  return (
    <div className="mt-4 w-full animate-slide-in">
      {/* Neighbor above */}
      {above && (
        <NeighborRow entry={above} position="above" />
      )}

      {/* Own rank - prominent display */}
      <div className="my-2 rounded-xl border-2 border-indigo-500 bg-indigo-50 p-4 dark:border-indigo-400 dark:bg-indigo-950/40">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <span className="text-3xl font-black text-indigo-600 dark:text-indigo-400">
              #{ownRank}
            </span>
            <div className="flex flex-col">
              <span className="text-sm font-medium text-slate-500 dark:text-slate-400">You</span>
              <span className="font-semibold text-slate-900 dark:text-white">{nickname}</span>
            </div>
          </div>
          <div className="flex items-center gap-3">
            <RankChangeIndicator rankChange={rankChange} />
            <span className="text-xl font-bold text-slate-900 dark:text-white">
              {ownScore.toLocaleString()}
            </span>
          </div>
        </div>
      </div>

      {/* Neighbor below */}
      {below && (
        <NeighborRow entry={below} position="below" />
      )}
    </div>
  );
}

function NeighborRow({ entry }: { entry: LeaderboardEntry; position: 'above' | 'below' }) {
  return (
    <div
      className={cn(
        'flex items-center justify-between rounded-lg p-3',
        'bg-slate-50 dark:bg-slate-800'
      )}
    >
      <div className="flex items-center gap-3">
        <span className="text-sm font-bold text-slate-400 dark:text-slate-500">
          #{entry.rank}
        </span>
        <span className="text-sm font-medium text-slate-700 dark:text-slate-300">
          {entry.nickname}
        </span>
      </div>
      <div className="flex items-center gap-2">
        {entry.rankChange !== 0 && (
          <RankChangeIndicator rankChange={entry.rankChange} size="sm" />
        )}
        <span className="text-sm font-semibold text-slate-700 dark:text-slate-300">
          {entry.score.toLocaleString()}
        </span>
      </div>
    </div>
  );
}

function RankChangeIndicator({ rankChange, size = 'md' }: { rankChange: number; size?: 'sm' | 'md' }) {
  if (rankChange === 0) return null;

  const isUp = rankChange > 0;
  const textSize = size === 'sm' ? 'text-xs' : 'text-sm';

  return (
    <span
      className={cn(
        'font-semibold',
        textSize,
        isUp ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'
      )}
      aria-label={isUp ? `Moved up ${rankChange} positions` : `Moved down ${Math.abs(rankChange)} positions`}
    >
      {isUp ? `↑${rankChange}` : `↓${Math.abs(rankChange)}`}
    </span>
  );
}
