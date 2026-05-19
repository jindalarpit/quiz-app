'use client';

import React, { useCallback, useEffect, useRef, useState } from 'react';

import { api } from '@/lib/api';
import { useSessionStore } from '@/stores/sessionStore';
import type {
  PagedLeaderboardResponse,
  ParticipantSelfResult,
  WSMessage,
} from '@/types';

import { LeaderboardTable } from './LeaderboardTable';
import { PerQuestionBreakdown } from './PerQuestionBreakdown';
import { SelfResultCard } from './SelfResultCard';
import { TopThreePodium } from './TopThreePodium';

const MAX_RETRY_ATTEMPTS = 3;

type LoadingState = 'idle' | 'loading' | 'success' | 'error';

interface ResultsScreenProps {
  sessionId: string;
  participantId?: string;
  onMessage?: WSMessage;
}

export function ResultsScreen({
  sessionId,
  participantId,
  onMessage,
}: ResultsScreenProps) {
  // Leaderboard state
  const [leaderboardData, setLeaderboardData] =
    useState<PagedLeaderboardResponse | null>(null);
  const [leaderboardState, setLeaderboardState] =
    useState<LoadingState>('idle');
  const [leaderboardError, setLeaderboardError] = useState<string | null>(null);
  const [leaderboardRetryCount, setLeaderboardRetryCount] = useState(0);

  // Self-result state
  const [selfResult, setSelfResult] = useState<ParticipantSelfResult | null>(
    null
  );
  const [selfResultState, setSelfResultState] = useState<LoadingState>('idle');
  const [selfResultError, setSelfResultError] = useState<string | null>(null);
  const [selfResultRetryCount, setSelfResultRetryCount] = useState(0);

  // Current page for pagination
  const [currentPage, setCurrentPage] = useState(0);

  // Track if we received data via WebSocket
  const receivedViaWs = useRef(false);

  // Get participantId from store if not provided via props
  const storeParticipantId = useSessionStore((s) => s.participantId);
  const effectiveParticipantId = participantId || storeParticipantId;

  // Handle WebSocket session.ended event
  useEffect(() => {
    if (
      onMessage &&
      onMessage.type === 'session.ended' &&
      !receivedViaWs.current
    ) {
      receivedViaWs.current = true;
      // The WebSocket event signals session end; fetch fresh data from API
      fetchLeaderboard(0);
      if (effectiveParticipantId) {
        fetchSelfResult();
      }
    }
  }, [onMessage]); // eslint-disable-line react-hooks/exhaustive-deps

  // Initial data fetch on mount
  useEffect(() => {
    if (!receivedViaWs.current) {
      fetchLeaderboard(0);
      if (effectiveParticipantId) {
        fetchSelfResult();
      }
    }
  }, [sessionId, effectiveParticipantId]); // eslint-disable-line react-hooks/exhaustive-deps

  const fetchLeaderboard = useCallback(
    async (page: number) => {
      setLeaderboardState('loading');
      setLeaderboardError(null);

      try {
        const data = await api.get<PagedLeaderboardResponse>(
          `/api/sessions/${sessionId}/leaderboard?page=${page}&size=20`
        );
        setLeaderboardData(data);
        setLeaderboardState('success');
        setCurrentPage(page);
        setLeaderboardRetryCount(0);
      } catch (err: unknown) {
        const message =
          (err as { message?: string })?.message ||
          'Failed to load leaderboard';
        setLeaderboardError(message);
        setLeaderboardState('error');
      }
    },
    [sessionId]
  );

  const fetchSelfResult = useCallback(async () => {
    if (!effectiveParticipantId) return;

    setSelfResultState('loading');
    setSelfResultError(null);

    try {
      const data = await api.request<ParticipantSelfResult>(
        `/api/sessions/${sessionId}/leaderboard/self`,
        {
          method: 'GET',
          headers: {
            'X-Participant-Id': effectiveParticipantId,
          },
        }
      );
      setSelfResult(data);
      setSelfResultState('success');
      setSelfResultRetryCount(0);
    } catch (err: unknown) {
      const message =
        (err as { message?: string })?.message ||
        'Failed to load your results';
      setSelfResultError(message);
      setSelfResultState('error');
    }
  }, [sessionId, effectiveParticipantId]);

  const handleLeaderboardRetry = useCallback(() => {
    if (leaderboardRetryCount >= MAX_RETRY_ATTEMPTS) return;
    setLeaderboardRetryCount((prev) => prev + 1);
    fetchLeaderboard(currentPage);
  }, [leaderboardRetryCount, fetchLeaderboard, currentPage]);

  const handleSelfResultRetry = useCallback(() => {
    if (selfResultRetryCount >= MAX_RETRY_ATTEMPTS) return;
    setSelfResultRetryCount((prev) => prev + 1);
    fetchSelfResult();
  }, [selfResultRetryCount, fetchSelfResult]);

  const handlePageChange = useCallback(
    (page: number) => {
      fetchLeaderboard(page);
    },
    [fetchLeaderboard]
  );

  // Both sections completely failed with all retries exhausted
  const bothFailedPermanently =
    leaderboardState === 'error' &&
    !leaderboardData &&
    leaderboardRetryCount >= MAX_RETRY_ATTEMPTS &&
    (!effectiveParticipantId ||
      (selfResultState === 'error' &&
        !selfResult &&
        selfResultRetryCount >= MAX_RETRY_ATTEMPTS));

  if (bothFailedPermanently) {
    return (
      <div className="flex min-h-[50vh] items-center justify-center p-4">
        <div
          className="max-w-md rounded-lg border border-red-200 bg-red-50 p-8 text-center dark:border-red-800 dark:bg-red-900/20"
          role="alert"
        >
          <p className="text-lg font-semibold text-red-800 dark:text-red-300">
            Unable to Load Results
          </p>
          <p className="mt-2 text-sm text-red-600 dark:text-red-400">
            We couldn&apos;t load the quiz results. Please try again later.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="mx-auto w-full max-w-4xl px-4 py-6 pb-32">
      {/* Header */}
      <div className="mb-6 text-center">
        <h1 className="text-2xl font-bold text-slate-900 dark:text-white">
          Quiz Results
        </h1>
        <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
          Final standings
        </p>
      </div>

      {/* Leaderboard Section */}
      {renderLeaderboardContent({
        leaderboardState,
        leaderboardData,
        leaderboardError,
        leaderboardRetryCount,
        currentPage,
        onRetry: handleLeaderboardRetry,
        onPageChange: handlePageChange,
      })}

      {/* Per-question breakdown */}
      {selfResult && selfResult.questionBreakdown.length > 0 && (
        <div className="mt-6">
          <PerQuestionBreakdown
            questionBreakdown={selfResult.questionBreakdown}
          />
        </div>
      )}

      {/* Self Result Card (fixed position at bottom) - graceful degradation */}
      {effectiveParticipantId && selfResult && (
        <SelfResultCard result={selfResult} />
      )}

      {/* Self Result Error (fixed position at bottom) - graceful degradation */}
      {effectiveParticipantId &&
        selfResultState === 'error' &&
        !selfResult && (
          <div
            className="fixed bottom-4 left-1/2 z-50 w-[calc(100%-2rem)] max-w-md -translate-x-1/2 rounded-xl border border-red-200 bg-white p-4 shadow-lg dark:border-red-800 dark:bg-slate-900"
            role="alert"
          >
            <p className="text-center text-sm font-medium text-red-800 dark:text-red-300">
              {selfResultError || 'Failed to load your results'}
            </p>
            {selfResultRetryCount < MAX_RETRY_ATTEMPTS ? (
              <button
                onClick={handleSelfResultRetry}
                className="mt-2 w-full rounded-md bg-red-100 px-3 py-1.5 text-xs font-medium text-red-800 hover:bg-red-200 dark:bg-red-900/40 dark:text-red-300 dark:hover:bg-red-900/60"
              >
                Retry ({MAX_RETRY_ATTEMPTS - selfResultRetryCount} attempts
                remaining)
              </button>
            ) : (
              <p className="mt-2 text-center text-xs text-red-600 dark:text-red-400">
                Unable to load your results. Please try again later.
              </p>
            )}
          </div>
        )}
    </div>
  );
}

// Helper to render leaderboard content based on state
function renderLeaderboardContent({
  leaderboardState,
  leaderboardData,
  leaderboardError,
  leaderboardRetryCount,
  currentPage,
  onRetry,
  onPageChange,
}: {
  leaderboardState: LoadingState;
  leaderboardData: PagedLeaderboardResponse | null;
  leaderboardError: string | null;
  leaderboardRetryCount: number;
  currentPage: number;
  onRetry: () => void;
  onPageChange: (page: number) => void;
}) {
  // Loading state with no existing data
  if (leaderboardState === 'loading' && !leaderboardData) {
    return (
      <div className="flex items-center justify-center py-12">
        <div className="text-center">
          <div className="mx-auto h-8 w-8 animate-spin rounded-full border-4 border-slate-200 border-t-indigo-600" />
          <p className="mt-3 text-sm text-slate-500 dark:text-slate-400">
            Loading leaderboard...
          </p>
        </div>
      </div>
    );
  }

  // Error state with no existing data
  if (leaderboardState === 'error' && !leaderboardData) {
    return (
      <div
        className="rounded-lg border border-red-200 bg-red-50 p-6 text-center dark:border-red-800 dark:bg-red-900/20"
        role="alert"
      >
        <p className="text-sm font-medium text-red-800 dark:text-red-300">
          {leaderboardError || 'Failed to load leaderboard'}
        </p>
        {leaderboardRetryCount < MAX_RETRY_ATTEMPTS ? (
          <button
            onClick={onRetry}
            className="mt-3 rounded-md bg-red-100 px-4 py-2 text-sm font-medium text-red-800 hover:bg-red-200 dark:bg-red-900/40 dark:text-red-300 dark:hover:bg-red-900/60"
          >
            Retry ({MAX_RETRY_ATTEMPTS - leaderboardRetryCount} attempts
            remaining)
          </button>
        ) : (
          <p className="mt-3 text-xs text-red-600 dark:text-red-400">
            Unable to load results. Please try again later.
          </p>
        )}
      </div>
    );
  }

  // Success or error with existing data (graceful degradation for page load failure)
  if (leaderboardData) {
    const topEntries = leaderboardData.entries
      .filter((e) => e.rank <= 3)
      .map((e) => ({
        rank: e.rank,
        nickname: e.nickname,
        score: e.score,
        correctAnswers: e.correctAnswers,
        totalAnswers: e.totalAnswers,
        maxStreak: e.maxStreak,
        avgResponseTimeSec: e.avgResponseTimeSec,
      }));

    return (
      <div className="space-y-6">
        {/* Top Three Podium - only show on first page */}
        {currentPage === 0 && topEntries.length > 0 && (
          <TopThreePodium entries={topEntries} />
        )}

        {/* Leaderboard Table */}
        <LeaderboardTable data={leaderboardData} onPageChange={onPageChange} />

        {/* Inline error if page load failed but we have previous data */}
        {leaderboardState === 'error' && (
          <div
            className="rounded-lg border border-yellow-200 bg-yellow-50 p-3 text-center dark:border-yellow-800 dark:bg-yellow-900/20"
            role="alert"
          >
            <p className="text-xs text-yellow-800 dark:text-yellow-300">
              Failed to load the requested page. Showing previous data.
            </p>
            {leaderboardRetryCount < MAX_RETRY_ATTEMPTS && (
              <button
                onClick={onRetry}
                className="mt-2 text-xs font-medium text-yellow-800 underline hover:no-underline dark:text-yellow-300"
              >
                Retry
              </button>
            )}
          </div>
        )}
      </div>
    );
  }

  return null;
}
