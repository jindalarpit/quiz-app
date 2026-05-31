'use client';

import { useParams, useRouter } from 'next/navigation';
import { useCallback, useEffect, useState } from 'react';

import { ExportButtons } from '@/components/history/ExportButtons';
import { api } from '@/lib/api';

interface LeaderboardEntry {
  rank: number;
  nickname: string;
  score: number;
  correctAnswers: number;
  totalAnswers: number;
  maxStreak: number;
  avgResponseTimeSec: number;
}

export default function SessionDetailPage() {
  const params = useParams();
  const router = useRouter();
  const sessionId = params.sessionId as string;

  const [leaderboard, setLeaderboard] = useState<LeaderboardEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchLeaderboard = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await api.get<LeaderboardEntry[]>(
        `/api/history/${sessionId}/leaderboard`
      );
      setLeaderboard(data);
    } catch (err: unknown) {
      const message =
        (err as { message?: string })?.message || 'Failed to load leaderboard';
      setError(message);
    } finally {
      setLoading(false);
    }
  }, [sessionId]);

  useEffect(() => {
    fetchLeaderboard();
  }, [fetchLeaderboard]);

  return (
    <div className="mx-auto w-full max-w-3xl px-4 py-6">
      <button
        onClick={() => router.push('/history')}
        className="mb-4 inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-white"
      >
        ← Back to History
      </button>

      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-bold text-slate-900 dark:text-white">
          Session Results
        </h1>
        <ExportButtons sessionId={sessionId} />
      </div>

      {loading && (
        <div className="flex items-center justify-center py-12">
          <div className="mx-auto h-8 w-8 animate-spin rounded-full border-4 border-slate-200 border-t-indigo-600" />
        </div>
      )}

      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-6 text-center dark:border-red-800 dark:bg-red-900/20">
          <p className="text-sm text-red-800 dark:text-red-300">{error}</p>
          <button
            onClick={fetchLeaderboard}
            className="mt-3 rounded-md bg-red-100 px-4 py-2 text-sm font-medium text-red-800 hover:bg-red-200"
          >
            Retry
          </button>
        </div>
      )}

      {!loading && !error && leaderboard.length === 0 && (
        <div className="rounded-lg border border-slate-200 bg-white p-8 text-center dark:border-slate-700 dark:bg-slate-800">
          <p className="text-slate-600 dark:text-slate-400">
            No leaderboard data available for this session.
          </p>
        </div>
      )}

      {!loading && !error && leaderboard.length > 0 && (
        <div className="overflow-hidden rounded-lg border border-slate-200 bg-white dark:border-slate-700 dark:bg-slate-800">
          <table className="w-full text-sm">
            <thead className="border-b border-slate-200 bg-slate-50 dark:border-slate-700 dark:bg-slate-900">
              <tr>
                <th className="px-4 py-3 text-left font-medium text-slate-600 dark:text-slate-400">Rank</th>
                <th className="px-4 py-3 text-left font-medium text-slate-600 dark:text-slate-400">Player</th>
                <th className="px-4 py-3 text-right font-medium text-slate-600 dark:text-slate-400">Score</th>
                <th className="px-4 py-3 text-right font-medium text-slate-600 dark:text-slate-400">Correct</th>
                <th className="px-4 py-3 text-right font-medium text-slate-600 dark:text-slate-400">Streak</th>
                <th className="px-4 py-3 text-right font-medium text-slate-600 dark:text-slate-400">Avg Time</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-slate-700">
              {leaderboard.map((entry) => (
                <tr key={entry.rank} className="hover:bg-slate-50 dark:hover:bg-slate-700/50">
                  <td className="px-4 py-3 font-bold text-slate-900 dark:text-white">
                    {entry.rank === 1 ? '🥇' : entry.rank === 2 ? '🥈' : entry.rank === 3 ? '🥉' : `#${entry.rank}`}
                  </td>
                  <td className="px-4 py-3 font-medium text-slate-900 dark:text-white">
                    {entry.nickname}
                  </td>
                  <td className="px-4 py-3 text-right font-semibold text-indigo-600 dark:text-indigo-400">
                    {entry.score.toLocaleString()}
                  </td>
                  <td className="px-4 py-3 text-right text-slate-600 dark:text-slate-400">
                    {entry.correctAnswers}/{entry.totalAnswers}
                  </td>
                  <td className="px-4 py-3 text-right text-slate-600 dark:text-slate-400">
                    {entry.maxStreak}🔥
                  </td>
                  <td className="px-4 py-3 text-right text-slate-600 dark:text-slate-400">
                    {entry.avgResponseTimeSec}s
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
