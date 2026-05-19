'use client';

import React, { useCallback, useEffect, useState } from 'react';

import { api } from '@/lib/api';
import { cn } from '@/lib/utils';
import type { PagedHistoryResponse, SessionHistoryEntry } from '@/types';

const PAGE_SIZE = 20;
const MAX_RETRY_ATTEMPTS = 3;
const SEARCH_MIN_LENGTH = 1;
const SEARCH_MAX_LENGTH = 100;

type LoadingState = 'idle' | 'loading' | 'success' | 'error';

interface QuizHistoryFilters {
  startDate: string;
  endDate: string;
  search: string;
}

export interface QuizHistoryPageProps {
  onSessionSelect?: (sessionId: string) => void;
}

export function QuizHistoryPage({ onSessionSelect }: QuizHistoryPageProps) {
  const [data, setData] = useState<PagedHistoryResponse | null>(null);
  const [loadingState, setLoadingState] = useState<LoadingState>('idle');
  const [error, setError] = useState<string | null>(null);
  const [retryCount, setRetryCount] = useState(0);
  const [currentPage, setCurrentPage] = useState(0);

  // Filter state
  const [filters, setFilters] = useState<QuizHistoryFilters>({
    startDate: '',
    endDate: '',
    search: '',
  });

  // Applied filters (only update on explicit action)
  const [appliedFilters, setAppliedFilters] = useState<QuizHistoryFilters>({
    startDate: '',
    endDate: '',
    search: '',
  });

  const fetchHistory = useCallback(
    async (page: number, currentFilters: QuizHistoryFilters) => {
      setLoadingState('loading');
      setError(null);

      try {
        const params = new URLSearchParams();
        params.set('page', String(page));
        params.set('size', String(PAGE_SIZE));

        if (currentFilters.startDate) {
          params.set('startDate', currentFilters.startDate);
        }
        if (currentFilters.endDate) {
          params.set('endDate', currentFilters.endDate);
        }
        if (
          currentFilters.search &&
          currentFilters.search.length >= SEARCH_MIN_LENGTH &&
          currentFilters.search.length <= SEARCH_MAX_LENGTH
        ) {
          params.set('search', currentFilters.search);
        }

        const response = await api.get<PagedHistoryResponse>(
          `/api/history?${params.toString()}`
        );
        setData(response);
        setLoadingState('success');
        setCurrentPage(page);
        setRetryCount(0);
      } catch (err: unknown) {
        const message =
          (err as { message?: string })?.message ||
          'Failed to load quiz history';
        setError(message);
        setLoadingState('error');
      }
    },
    []
  );

  // Initial fetch
  useEffect(() => {
    fetchHistory(0, appliedFilters);
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const handleRetry = useCallback(() => {
    if (retryCount >= MAX_RETRY_ATTEMPTS) return;
    setRetryCount((prev) => prev + 1);
    fetchHistory(currentPage, appliedFilters);
  }, [retryCount, fetchHistory, currentPage, appliedFilters]);

  const handlePageChange = useCallback(
    (page: number) => {
      fetchHistory(page, appliedFilters);
    },
    [fetchHistory, appliedFilters]
  );

  const handleApplyFilters = useCallback(() => {
    setAppliedFilters({ ...filters });
    setCurrentPage(0);
    fetchHistory(0, filters);
  }, [filters, fetchHistory]);

  const handleClearFilters = useCallback(() => {
    const cleared: QuizHistoryFilters = { startDate: '', endDate: '', search: '' };
    setFilters(cleared);
    setAppliedFilters(cleared);
    setCurrentPage(0);
    fetchHistory(0, cleared);
  }, [fetchHistory]);

  const handleSearchChange = (value: string) => {
    if (value.length <= SEARCH_MAX_LENGTH) {
      setFilters((prev) => ({ ...prev, search: value }));
    }
  };

  const handleSearchKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      handleApplyFilters();
    }
  };

  return (
    <div className="mx-auto w-full max-w-4xl px-4 py-6">
      {/* Header */}
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-slate-900 dark:text-white">
          Quiz History
        </h1>
        <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
          Browse your past quiz sessions
        </p>
      </div>

      {/* Filters */}
      <div className="mb-6 rounded-lg border border-slate-200 bg-white p-4 dark:border-slate-700 dark:bg-slate-800">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-end">
          {/* Search input */}
          <div className="flex-1">
            <label
              htmlFor="history-search"
              className="mb-1 block text-xs font-medium text-slate-600 dark:text-slate-400"
            >
              Search by quiz title
            </label>
            <input
              id="history-search"
              type="text"
              value={filters.search}
              onChange={(e) => handleSearchChange(e.target.value)}
              onKeyDown={handleSearchKeyDown}
              placeholder="Search quiz title..."
              maxLength={SEARCH_MAX_LENGTH}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 placeholder-slate-400 focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 dark:border-slate-600 dark:bg-slate-700 dark:text-white dark:placeholder-slate-500"
              aria-label="Search quiz title"
            />
          </div>

          {/* Date range */}
          <div className="flex gap-2">
            <div>
              <label
                htmlFor="history-start-date"
                className="mb-1 block text-xs font-medium text-slate-600 dark:text-slate-400"
              >
                From
              </label>
              <input
                id="history-start-date"
                type="date"
                value={filters.startDate}
                onChange={(e) =>
                  setFilters((prev) => ({ ...prev, startDate: e.target.value }))
                }
                className="rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 dark:border-slate-600 dark:bg-slate-700 dark:text-white"
                aria-label="Start date filter"
              />
            </div>
            <div>
              <label
                htmlFor="history-end-date"
                className="mb-1 block text-xs font-medium text-slate-600 dark:text-slate-400"
              >
                To
              </label>
              <input
                id="history-end-date"
                type="date"
                value={filters.endDate}
                onChange={(e) =>
                  setFilters((prev) => ({ ...prev, endDate: e.target.value }))
                }
                className="rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 dark:border-slate-600 dark:bg-slate-700 dark:text-white"
                aria-label="End date filter"
              />
            </div>
          </div>

          {/* Action buttons */}
          <div className="flex gap-2">
            <button
              onClick={handleApplyFilters}
              className="rounded-md bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2"
            >
              Apply
            </button>
            <button
              onClick={handleClearFilters}
              className="rounded-md border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 dark:border-slate-600 dark:text-slate-300 dark:hover:bg-slate-700"
            >
              Clear
            </button>
          </div>
        </div>
      </div>

      {/* Content */}
      {renderContent({
        loadingState,
        data,
        error,
        retryCount,
        currentPage,
        onRetry: handleRetry,
        onPageChange: handlePageChange,
        onSessionSelect,
      })}
    </div>
  );
}

// Helper to render content based on loading state
function renderContent({
  loadingState,
  data,
  error,
  retryCount,
  currentPage,
  onRetry,
  onPageChange,
  onSessionSelect,
}: {
  loadingState: LoadingState;
  data: PagedHistoryResponse | null;
  error: string | null;
  retryCount: number;
  currentPage: number;
  onRetry: () => void;
  onPageChange: (page: number) => void;
  onSessionSelect?: (sessionId: string) => void;
}) {
  // Loading state with no existing data
  if (loadingState === 'loading' && !data) {
    return (
      <div className="flex items-center justify-center py-12">
        <div className="text-center">
          <div className="mx-auto h-8 w-8 animate-spin rounded-full border-4 border-slate-200 border-t-indigo-600" />
          <p className="mt-3 text-sm text-slate-500 dark:text-slate-400">
            Loading quiz history...
          </p>
        </div>
      </div>
    );
  }

  // Error state with no existing data
  if (loadingState === 'error' && !data) {
    return (
      <div
        className="rounded-lg border border-red-200 bg-red-50 p-6 text-center dark:border-red-800 dark:bg-red-900/20"
        role="alert"
      >
        <p className="text-sm font-medium text-red-800 dark:text-red-300">
          {error || 'Failed to load quiz history'}
        </p>
        {retryCount < MAX_RETRY_ATTEMPTS ? (
          <button
            onClick={onRetry}
            className="mt-3 rounded-md bg-red-100 px-4 py-2 text-sm font-medium text-red-800 hover:bg-red-200 dark:bg-red-900/40 dark:text-red-300 dark:hover:bg-red-900/60"
          >
            Retry ({MAX_RETRY_ATTEMPTS - retryCount} attempts remaining)
          </button>
        ) : (
          <p className="mt-3 text-xs text-red-600 dark:text-red-400">
            Unable to load quiz history. Please try again later.
          </p>
        )}
      </div>
    );
  }

  // Success with data
  if (data) {
    // Empty state
    if (data.sessions.length === 0 && data.totalSessions === 0) {
      return (
        <div className="rounded-lg border border-slate-200 bg-white p-12 text-center dark:border-slate-700 dark:bg-slate-800">
          <p className="text-lg font-medium text-slate-600 dark:text-slate-400">
            No quiz sessions found
          </p>
          <p className="mt-2 text-sm text-slate-500 dark:text-slate-500">
            Your past quiz sessions will appear here once completed.
          </p>
        </div>
      );
    }

    return (
      <div>
        {/* Summary */}
        <div className="mb-4 flex items-center justify-between text-sm text-slate-600 dark:text-slate-400">
          <span>{data.totalSessions} total sessions</span>
          {data.totalPages > 1 && (
            <span>
              Page {data.currentPage + 1} of {data.totalPages}
            </span>
          )}
        </div>

        {/* Session list */}
        <div className="space-y-3">
          {data.sessions.map((session) => (
            <SessionCard
              key={session.sessionId}
              session={session}
              onClick={() => onSessionSelect?.(session.sessionId)}
            />
          ))}
        </div>

        {/* Pagination */}
        {data.totalPages > 1 && (
          <HistoryPagination
            currentPage={currentPage}
            totalPages={data.totalPages}
            onPageChange={onPageChange}
          />
        )}

        {/* Inline error if page load failed but we have previous data */}
        {loadingState === 'error' && (
          <div
            className="mt-4 rounded-lg border border-yellow-200 bg-yellow-50 p-3 text-center dark:border-yellow-800 dark:bg-yellow-900/20"
            role="alert"
          >
            <p className="text-xs text-yellow-800 dark:text-yellow-300">
              Failed to load the requested page. Showing previous data.
            </p>
            {retryCount < MAX_RETRY_ATTEMPTS && (
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

// Session card component
function SessionCard({
  session,
  onClick,
}: {
  session: SessionHistoryEntry;
  onClick?: () => void;
}) {
  const endDate = new Date(session.endedAt);
  const formattedDate = endDate.toLocaleDateString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  });
  const formattedTime = endDate.toLocaleTimeString(undefined, {
    hour: '2-digit',
    minute: '2-digit',
  });

  const durationMinutes = Math.floor(session.durationSeconds / 60);
  const durationSecs = session.durationSeconds % 60;
  const durationDisplay =
    durationMinutes > 0
      ? `${durationMinutes}m ${durationSecs}s`
      : `${durationSecs}s`;

  return (
    <button
      onClick={onClick}
      className="w-full rounded-lg border border-slate-200 bg-white p-4 text-left transition-colors hover:border-indigo-300 hover:bg-indigo-50/50 dark:border-slate-700 dark:bg-slate-800 dark:hover:border-indigo-700 dark:hover:bg-indigo-900/20"
    >
      <div className="flex items-start justify-between">
        <div className="min-w-0 flex-1">
          <h3 className="truncate text-sm font-semibold text-slate-900 dark:text-white">
            {session.quizTitle}
          </h3>
          <div className="mt-1 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-slate-500 dark:text-slate-400">
            <span>
              {formattedDate} at {formattedTime}
            </span>
            <span>{session.participantCount} participants</span>
            <span>{durationDisplay}</span>
          </div>
        </div>
        <span className="ml-2 text-slate-400 dark:text-slate-500">→</span>
      </div>
    </button>
  );
}

// Pagination component for history
function HistoryPagination({
  currentPage,
  totalPages,
  onPageChange,
}: {
  currentPage: number;
  totalPages: number;
  onPageChange: (page: number) => void;
}) {
  const isPrevDisabled = currentPage === 0;
  const isNextDisabled = currentPage >= totalPages - 1;

  return (
    <nav
      className="mt-6 flex items-center justify-center gap-1"
      aria-label="Quiz history pagination"
    >
      <button
        onClick={() => onPageChange(currentPage - 1)}
        disabled={isPrevDisabled}
        className={cn(
          'rounded-md px-3 py-2 text-sm font-medium',
          isPrevDisabled
            ? 'cursor-not-allowed text-slate-400 dark:text-slate-600'
            : 'text-slate-700 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800'
        )}
        aria-label="Previous page"
      >
        ← Prev
      </button>

      {getHistoryPageNumbers(currentPage, totalPages).map((pageNum, idx) =>
        pageNum === -1 ? (
          <span
            key={`ellipsis-${idx}`}
            className="px-2 text-slate-400 dark:text-slate-600"
          >
            …
          </span>
        ) : (
          <button
            key={pageNum}
            onClick={() => onPageChange(pageNum)}
            className={cn(
              'rounded-md px-3 py-2 text-sm font-medium',
              pageNum === currentPage
                ? 'bg-indigo-600 text-white'
                : 'text-slate-700 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800'
            )}
            aria-label={`Page ${pageNum + 1}`}
            aria-current={pageNum === currentPage ? 'page' : undefined}
          >
            {pageNum + 1}
          </button>
        )
      )}

      <button
        onClick={() => onPageChange(currentPage + 1)}
        disabled={isNextDisabled}
        className={cn(
          'rounded-md px-3 py-2 text-sm font-medium',
          isNextDisabled
            ? 'cursor-not-allowed text-slate-400 dark:text-slate-600'
            : 'text-slate-700 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800'
        )}
        aria-label="Next page"
      >
        Next →
      </button>
    </nav>
  );
}

/**
 * Generates page numbers for pagination display.
 * Uses -1 as a sentinel for ellipsis.
 */
export function getHistoryPageNumbers(
  currentPage: number,
  totalPages: number
): number[] {
  if (totalPages <= 7) {
    return Array.from({ length: totalPages }, (_, i) => i);
  }

  const pages: number[] = [];

  // Always show first page
  pages.push(0);

  if (currentPage > 2) {
    pages.push(-1); // ellipsis
  }

  // Pages around current
  const start = Math.max(1, currentPage - 1);
  const end = Math.min(totalPages - 2, currentPage + 1);

  for (let i = start; i <= end; i++) {
    pages.push(i);
  }

  if (currentPage < totalPages - 3) {
    pages.push(-1); // ellipsis
  }

  // Always show last page
  pages.push(totalPages - 1);

  return pages;
}
