'use client';

import { cn } from '@/lib/utils';
import type { PagedLeaderboardResponse } from '@/types';

interface LeaderboardTableProps {
  data: PagedLeaderboardResponse;
  onPageChange: (page: number) => void;
}

export function LeaderboardTable({ data, onPageChange }: LeaderboardTableProps) {
  const { entries, currentPage, totalPages, totalParticipants } = data;

  const isPrevDisabled = currentPage === 0;
  const isNextDisabled = currentPage >= totalPages - 1;

  return (
    <div className="w-full">
      {/* Summary info */}
      <div className="mb-4 flex items-center justify-between text-sm text-slate-600 dark:text-slate-400">
        <span>{totalParticipants} participants</span>
        <span>
          Page {currentPage + 1} of {totalPages}
        </span>
      </div>

      {/* Table */}
      <div className="overflow-x-auto rounded-lg border border-slate-200 dark:border-slate-700">
        <table className="w-full text-left text-sm">
          <thead className="bg-slate-50 text-xs uppercase text-slate-600 dark:bg-slate-800 dark:text-slate-400">
            <tr>
              <th scope="col" className="px-4 py-3">
                Rank
              </th>
              <th scope="col" className="px-4 py-3">
                Nickname
              </th>
              <th scope="col" className="px-4 py-3 text-right">
                Score
              </th>
              <th scope="col" className="px-4 py-3 text-right">
                Correct
              </th>
              <th scope="col" className="px-4 py-3 text-right">
                Max Streak
              </th>
            </tr>
          </thead>
          <tbody>
            {entries.map((entry) => (
              <tr
                key={entry.rank}
                className="border-t border-slate-200 dark:border-slate-700"
              >
                <td className="px-4 py-3 font-medium text-slate-900 dark:text-white">
                  <span
                    className={cn(
                      'inline-flex h-7 w-7 items-center justify-center rounded-full text-xs font-bold',
                      entry.rank === 1 &&
                        'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-300',
                      entry.rank === 2 &&
                        'bg-slate-100 text-slate-700 dark:bg-slate-700 dark:text-slate-300',
                      entry.rank === 3 &&
                        'bg-orange-100 text-orange-800 dark:bg-orange-900/30 dark:text-orange-300',
                      entry.rank > 3 && 'text-slate-500 dark:text-slate-400'
                    )}
                  >
                    {entry.rank}
                  </span>
                </td>
                <td className="px-4 py-3 font-medium text-slate-900 dark:text-white">
                  {entry.nickname}
                </td>
                <td className="px-4 py-3 text-right font-semibold text-slate-900 dark:text-white">
                  {entry.score.toLocaleString()}
                </td>
                <td className="px-4 py-3 text-right text-slate-700 dark:text-slate-300">
                  {entry.correctAnswers}/{entry.totalAnswers}
                </td>
                <td className="px-4 py-3 text-right text-slate-700 dark:text-slate-300">
                  {entry.maxStreak}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Pagination controls */}
      {totalPages > 1 && (
        <nav
          className="mt-4 flex items-center justify-center gap-1"
          aria-label="Leaderboard pagination"
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

          {getPageNumbers(currentPage, totalPages).map((pageNum, idx) =>
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
      )}
    </div>
  );
}

/**
 * Generates an array of page numbers to display in pagination.
 * Uses -1 as a sentinel for ellipsis.
 * Shows first page, last page, and pages around the current page.
 */
export function getPageNumbers(currentPage: number, totalPages: number): number[] {
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
