'use client';

interface ErrorStateProps {
  /** Error message to display */
  message?: string;
  /** Optional detailed description */
  description?: string;
  /** Retry callback. If provided, a retry button is shown. */
  onRetry?: () => void;
  /** Custom retry button label */
  retryLabel?: string;
}

/**
 * Error state component with icon, message, and optional retry button.
 * Use when data fetching fails or an unexpected error occurs.
 */
export function ErrorState({
  message = 'Something went wrong',
  description,
  onRetry,
  retryLabel = 'Try again',
}: ErrorStateProps) {
  return (
    <div className="flex flex-col items-center justify-center px-4 py-12 text-center" role="alert">
      <div className="mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-red-100 dark:bg-red-900/30">
        <ErrorIcon className="h-8 w-8 text-red-600 dark:text-red-400" />
      </div>
      <h3 className="mb-1 text-lg font-semibold text-slate-900 dark:text-white">{message}</h3>
      {description && <p className="mb-4 max-w-sm text-sm text-slate-600 dark:text-slate-400">{description}</p>}
      {onRetry && (
        <button
          onClick={onRetry}
          className="mt-2 min-h-[44px] min-w-[44px] rounded-lg bg-primary-600 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-primary-700 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2"
        >
          {retryLabel}
        </button>
      )}
    </div>
  );
}

function ErrorIcon({ className }: { className?: string }) {
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
      <circle cx="12" cy="12" r="10" />
      <line x1="12" y1="8" x2="12" y2="12" />
      <line x1="12" y1="16" x2="12.01" y2="16" />
    </svg>
  );
}
