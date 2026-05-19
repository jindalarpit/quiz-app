'use client';

import React, { useCallback, useState } from 'react';

interface ExportButtonsProps {
  sessionId: string;
}

type ExportFormat = 'csv' | 'pdf';

interface ExportState {
  loading: boolean;
  error: string | null;
}

/**
 * ExportButtons component provides CSV and PDF download triggers for quiz session results.
 * Shows loading state during generation and error messages on failure.
 *
 * Requirements: 4.4, 5.4
 */
export function ExportButtons({ sessionId }: ExportButtonsProps) {
  const [csvState, setCsvState] = useState<ExportState>({ loading: false, error: null });
  const [pdfState, setPdfState] = useState<ExportState>({ loading: false, error: null });

  const handleExport = useCallback(
    async (format: ExportFormat) => {
      const setState = format === 'csv' ? setCsvState : setPdfState;
      setState({ loading: true, error: null });

      try {
        await downloadExport(sessionId, format);
        setState({ loading: false, error: null });
      } catch (err: unknown) {
        const message =
          (err as { message?: string })?.message ||
          `Failed to export ${format.toUpperCase()}`;
        setState({ loading: false, error: message });
      }
    },
    [sessionId]
  );

  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-center gap-3">
        <button
          onClick={() => handleExport('csv')}
          disabled={csvState.loading}
          aria-label="Export CSV"
          className="inline-flex min-h-[44px] items-center gap-2 rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 transition-colors hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-200 dark:hover:bg-slate-700"
        >
          {csvState.loading ? (
            <LoadingSpinner />
          ) : (
            <DownloadIcon />
          )}
          {csvState.loading ? 'Generating...' : 'Export CSV'}
        </button>

        <button
          onClick={() => handleExport('pdf')}
          disabled={pdfState.loading}
          aria-label="Export PDF"
          className="inline-flex min-h-[44px] items-center gap-2 rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 transition-colors hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-200 dark:hover:bg-slate-700"
        >
          {pdfState.loading ? (
            <LoadingSpinner />
          ) : (
            <DownloadIcon />
          )}
          {pdfState.loading ? 'Generating...' : 'Export PDF'}
        </button>
      </div>

      {/* Error messages */}
      {csvState.error && (
        <p className="text-sm text-red-600 dark:text-red-400" role="alert">
          {csvState.error}
        </p>
      )}
      {pdfState.error && (
        <p className="text-sm text-red-600 dark:text-red-400" role="alert">
          {pdfState.error}
        </p>
      )}
    </div>
  );
}

/**
 * Downloads an export file (CSV or PDF) for the given session.
 * Uses fetch directly to handle binary responses and Content-Disposition headers.
 */
async function downloadExport(sessionId: string, format: ExportFormat): Promise<void> {
  const baseUrl = process.env.NEXT_PUBLIC_API_URL || '';
  const url = `${baseUrl}/api/export/${sessionId}/${format}`;

  // Get auth token and user ID from localStorage
  const token = getAccessToken();
  const userId = getUserId();

  const headers: Record<string, string> = {};
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  if (userId) {
    headers['X-User-Id'] = userId;
  }

  const response = await fetch(url, { method: 'GET', headers });

  if (!response.ok) {
    const errorBody = await response.json().catch(() => ({}));
    const message =
      (errorBody as { message?: string })?.message ||
      `Export failed with status ${response.status}`;
    throw new Error(message);
  }

  // Extract filename from Content-Disposition header
  const contentDisposition = response.headers.get('Content-Disposition');
  const filename = extractFilename(contentDisposition, format, sessionId);

  // Download the file
  const blob = await response.blob();
  triggerDownload(blob, filename);
}

/**
 * Extracts filename from Content-Disposition header.
 * Falls back to a default filename if header is missing or malformed.
 */
function extractFilename(
  contentDisposition: string | null,
  format: ExportFormat,
  sessionId: string
): string {
  if (contentDisposition) {
    const match = contentDisposition.match(/filename="?([^";\n]+)"?/);
    if (match && match[1]) {
      return match[1];
    }
  }
  // Fallback filename
  return `quiz-results-${sessionId}.${format}`;
}

/**
 * Triggers a browser file download for the given blob.
 */
function triggerDownload(blob: Blob, filename: string): void {
  const downloadUrl = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = downloadUrl;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(downloadUrl);
}

/**
 * Retrieves the access token from localStorage auth storage.
 */
function getAccessToken(): string | null {
  if (typeof window === 'undefined') return null;
  const stored = localStorage.getItem('auth-storage');
  if (!stored) return null;
  try {
    const parsed = JSON.parse(stored);
    return parsed?.state?.tokens?.accessToken || null;
  } catch {
    return null;
  }
}

/**
 * Retrieves the user ID from localStorage auth storage.
 */
function getUserId(): string | null {
  if (typeof window === 'undefined') return null;
  const stored = localStorage.getItem('auth-storage');
  if (!stored) return null;
  try {
    const parsed = JSON.parse(stored);
    return parsed?.state?.user?.id || null;
  } catch {
    return null;
  }
}

function LoadingSpinner() {
  return (
    <svg
      className="h-4 w-4 animate-spin"
      xmlns="http://www.w3.org/2000/svg"
      fill="none"
      viewBox="0 0 24 24"
      aria-hidden="true"
    >
      <circle
        className="opacity-25"
        cx="12"
        cy="12"
        r="10"
        stroke="currentColor"
        strokeWidth="4"
      />
      <path
        className="opacity-75"
        fill="currentColor"
        d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
      />
    </svg>
  );
}

function DownloadIcon() {
  return (
    <svg
      className="h-4 w-4"
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4" />
      <polyline points="7 10 12 15 17 10" />
      <line x1="12" y1="15" x2="12" y2="3" />
    </svg>
  );
}

// Export for testing
export { downloadExport, extractFilename, triggerDownload };
