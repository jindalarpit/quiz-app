'use client';

import { clsx } from 'clsx';

interface SkeletonProps {
  className?: string;
  /** Explicit width to prevent CLS */
  width?: string;
  /** Explicit height to prevent CLS */
  height?: string;
}

/**
 * Generic skeleton loading component with pulse animation.
 * Always set explicit dimensions to prevent Cumulative Layout Shift.
 */
export function Skeleton({ className, width, height }: SkeletonProps) {
  return (
    <div
      className={clsx('animate-pulse rounded-md bg-slate-200 dark:bg-slate-700', className)}
      style={{ width, height }}
      role="status"
      aria-label="Loading..."
    />
  );
}

/**
 * Skeleton for the quiz list on the dashboard page.
 * Renders 3 quiz card placeholders with explicit heights.
 */
export function QuizListSkeleton() {
  return (
    <div className="space-y-4" role="status" aria-label="Loading quizzes...">
      {[1, 2, 3].map((i) => (
        <div
          key={i}
          className="rounded-lg border border-slate-200 p-4 dark:border-slate-700"
          style={{ height: '120px' }}
        >
          <Skeleton className="mb-3 h-5 w-3/4" height="20px" />
          <Skeleton className="mb-2 h-4 w-1/2" height="16px" />
          <div className="flex gap-2">
            <Skeleton className="h-6 w-16" height="24px" />
            <Skeleton className="h-6 w-20" height="24px" />
          </div>
        </div>
      ))}
    </div>
  );
}

/**
 * Skeleton for the quiz editor page.
 * Shows placeholders for title, description, and question list.
 */
export function QuizEditorSkeleton() {
  return (
    <div className="space-y-6" role="status" aria-label="Loading quiz editor...">
      {/* Title */}
      <Skeleton className="h-10 w-full max-w-md" height="40px" />
      {/* Description */}
      <Skeleton className="h-20 w-full" height="80px" />
      {/* Questions */}
      <div className="space-y-3">
        {[1, 2, 3].map((i) => (
          <div
            key={i}
            className="rounded-lg border border-slate-200 p-4 dark:border-slate-700"
            style={{ height: '80px' }}
          >
            <Skeleton className="mb-2 h-4 w-2/3" height="16px" />
            <Skeleton className="h-4 w-1/3" height="16px" />
          </div>
        ))}
      </div>
    </div>
  );
}

/**
 * Skeleton for the live session view.
 * Shows placeholder for question area and answer options.
 */
export function SessionSkeleton() {
  return (
    <div className="space-y-6" role="status" aria-label="Loading session...">
      {/* Timer bar */}
      <Skeleton className="h-2 w-full" height="8px" />
      {/* Question text */}
      <Skeleton className="mx-auto h-8 w-3/4" height="32px" />
      {/* Answer options grid */}
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        {[1, 2, 3, 4].map((i) => (
          <Skeleton key={i} className="h-[60px] w-full rounded-lg" height="60px" />
        ))}
      </div>
    </div>
  );
}
