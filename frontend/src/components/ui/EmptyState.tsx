'use client';

import { type ReactNode } from 'react';

interface EmptyStateProps {
  /** Icon or illustration to display */
  icon?: ReactNode;
  /** Main message */
  message: string;
  /** Optional description */
  description?: string;
  /** Action button label */
  actionLabel?: string;
  /** Action button callback */
  onAction?: () => void;
}

/**
 * Empty state component for when there's no data to display.
 * Use in dashboard (no quizzes), quiz editor (no questions), etc.
 */
export function EmptyState({ icon, message, description, actionLabel, onAction }: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center justify-center px-4 py-12 text-center">
      <div className="mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-slate-100 dark:bg-slate-800">
        {icon || <DefaultEmptyIcon className="h-8 w-8 text-slate-400 dark:text-slate-500" />}
      </div>
      <h3 className="mb-1 text-lg font-semibold text-slate-900 dark:text-white">{message}</h3>
      {description && <p className="mb-4 max-w-sm text-sm text-slate-600 dark:text-slate-400">{description}</p>}
      {actionLabel && onAction && (
        <button
          onClick={onAction}
          className="mt-2 min-h-[44px] min-w-[44px] rounded-lg bg-primary-600 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-primary-700 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2"
        >
          {actionLabel}
        </button>
      )}
    </div>
  );
}

function DefaultEmptyIcon({ className }: { className?: string }) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      aria-hidden="true"
    >
      <path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z" />
      <polyline points="13 2 13 9 20 9" />
    </svg>
  );
}
