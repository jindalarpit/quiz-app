'use client';

import { Leaderboard } from '@/components/quiz/Leaderboard';
import type { LeaderboardEntry, SessionSummary } from '@/types';

interface SessionEndProps {
  leaderboard: LeaderboardEntry[];
  summary: SessionSummary | null;
  onPlayAgain: () => void;
}

export function SessionEnd({ leaderboard, summary, onPlayAgain }: SessionEndProps) {
  return (
    <div className="flex min-h-[calc(100vh-4rem)] flex-col items-center justify-center p-4">
      <div className="w-full max-w-lg text-center">
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

        <Leaderboard entries={leaderboard} title="Final Standings" />

        <button onClick={onPlayAgain} className="btn-primary mt-8 px-8 py-3 text-lg">
          Play Again
        </button>
      </div>
    </div>
  );
}
