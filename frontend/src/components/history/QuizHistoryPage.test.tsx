/**
 * @vitest-environment jsdom
 */
import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import { QuizHistoryPage } from './QuizHistoryPage';
import type { PagedHistoryResponse } from '@/types';

// Mock the api module
vi.mock('@/lib/api', () => ({
  api: {
    get: vi.fn(),
  },
}));

import { api } from '@/lib/api';

const mockApi = api as { get: ReturnType<typeof vi.fn> };

function makeHistoryResponse(
  overrides: Partial<PagedHistoryResponse> = {}
): PagedHistoryResponse {
  return {
    sessions: [
      {
        sessionId: 'session-1',
        quizTitle: 'Geography Quiz',
        endedAt: '2024-03-15T14:30:00Z',
        participantCount: 32,
        durationSeconds: 420,
      },
      {
        sessionId: 'session-2',
        quizTitle: 'Science Quiz',
        endedAt: '2024-03-14T10:00:00Z',
        participantCount: 18,
        durationSeconds: 300,
      },
    ],
    currentPage: 0,
    totalPages: 1,
    totalSessions: 2,
    ...overrides,
  };
}

function makeMultiPageResponse(page: number = 0): PagedHistoryResponse {
  return {
    sessions: Array.from({ length: 20 }, (_, i) => ({
      sessionId: `session-${page * 20 + i + 1}`,
      quizTitle: `Quiz ${page * 20 + i + 1}`,
      endedAt: '2024-03-15T14:30:00Z',
      participantCount: 10 + i,
      durationSeconds: 300 + i * 10,
    })),
    currentPage: page,
    totalPages: 5,
    totalSessions: 98,
  };
}

describe('QuizHistoryPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Initial loading and display', () => {
    it('shows loading state initially', () => {
      mockApi.get.mockReturnValue(new Promise(() => {})); // never resolves
      render(<QuizHistoryPage />);

      expect(screen.getByText('Loading quiz history...')).toBeInTheDocument();
    });

    it('displays session list on successful fetch', async () => {
      mockApi.get.mockResolvedValue(makeHistoryResponse());
      render(<QuizHistoryPage />);

      await waitFor(() => {
        expect(screen.getByText('Geography Quiz')).toBeInTheDocument();
      });
      expect(screen.getByText('Science Quiz')).toBeInTheDocument();
    });

    it('displays total session count', async () => {
      mockApi.get.mockResolvedValue(makeHistoryResponse());
      render(<QuizHistoryPage />);

      await waitFor(() => {
        expect(screen.getByText('2 total sessions')).toBeInTheDocument();
      });
    });

    it('calls api with correct initial parameters', async () => {
      mockApi.get.mockResolvedValue(makeHistoryResponse());
      render(<QuizHistoryPage />);

      await waitFor(() => {
        expect(mockApi.get).toHaveBeenCalledWith('/api/history?page=0&size=20');
      });
    });
  });

  describe('Empty state (Req 3.7)', () => {
    it('displays empty state message when no sessions exist', async () => {
      mockApi.get.mockResolvedValue({
        sessions: [],
        currentPage: 0,
        totalPages: 0,
        totalSessions: 0,
      });
      render(<QuizHistoryPage />);

      await waitFor(() => {
        expect(screen.getByText('No quiz sessions found')).toBeInTheDocument();
      });
      expect(
        screen.getByText(
          'Your past quiz sessions will appear here once completed.'
        )
      ).toBeInTheDocument();
    });
  });

  describe('Error handling with retry (Req 3.8)', () => {
    it('shows error message when fetch fails', async () => {
      mockApi.get.mockRejectedValue({ message: 'Network error' });
      render(<QuizHistoryPage />);

      await waitFor(() => {
        expect(screen.getByText('Network error')).toBeInTheDocument();
      });
    });

    it('shows retry button with remaining attempts', async () => {
      mockApi.get.mockRejectedValue({ message: 'Server error' });
      render(<QuizHistoryPage />);

      await waitFor(() => {
        expect(
          screen.getByText('Retry (3 attempts remaining)')
        ).toBeInTheDocument();
      });
    });

    it('decrements retry count on each retry attempt', async () => {
      mockApi.get.mockRejectedValue({ message: 'Server error' });
      render(<QuizHistoryPage />);

      await waitFor(() => {
        expect(
          screen.getByText('Retry (3 attempts remaining)')
        ).toBeInTheDocument();
      });

      fireEvent.click(screen.getByText('Retry (3 attempts remaining)'));

      await waitFor(() => {
        expect(
          screen.getByText('Retry (2 attempts remaining)')
        ).toBeInTheDocument();
      });
    });

    it('shows persistent failure message after max retries exhausted', async () => {
      mockApi.get.mockRejectedValue({ message: 'Server error' });
      render(<QuizHistoryPage />);

      // Initial error
      await waitFor(() => {
        expect(
          screen.getByText('Retry (3 attempts remaining)')
        ).toBeInTheDocument();
      });

      // Retry 1
      fireEvent.click(screen.getByText('Retry (3 attempts remaining)'));
      await waitFor(() => {
        expect(
          screen.getByText('Retry (2 attempts remaining)')
        ).toBeInTheDocument();
      });

      // Retry 2
      fireEvent.click(screen.getByText('Retry (2 attempts remaining)'));
      await waitFor(() => {
        expect(
          screen.getByText('Retry (1 attempts remaining)')
        ).toBeInTheDocument();
      });

      // Retry 3
      fireEvent.click(screen.getByText('Retry (1 attempts remaining)'));
      await waitFor(() => {
        expect(
          screen.getByText(
            'Unable to load quiz history. Please try again later.'
          )
        ).toBeInTheDocument();
      });
    });

    it('shows default error message when error has no message', async () => {
      mockApi.get.mockRejectedValue({});
      render(<QuizHistoryPage />);

      await waitFor(() => {
        expect(
          screen.getByText('Failed to load quiz history')
        ).toBeInTheDocument();
      });
    });
  });

  describe('Pagination', () => {
    it('displays page info when multiple pages exist', async () => {
      mockApi.get.mockResolvedValue(makeMultiPageResponse(0));
      render(<QuizHistoryPage />);

      await waitFor(() => {
        expect(screen.getByText('Page 1 of 5')).toBeInTheDocument();
      });
      expect(screen.getByText('98 total sessions')).toBeInTheDocument();
    });

    it('disables previous button on first page', async () => {
      mockApi.get.mockResolvedValue(makeMultiPageResponse(0));
      render(<QuizHistoryPage />);

      await waitFor(() => {
        expect(screen.getByLabelText('Previous page')).toBeDisabled();
      });
    });

    it('enables next button when not on last page', async () => {
      mockApi.get.mockResolvedValue(makeMultiPageResponse(0));
      render(<QuizHistoryPage />);

      await waitFor(() => {
        expect(screen.getByLabelText('Next page')).not.toBeDisabled();
      });
    });

    it('navigates to next page on next button click', async () => {
      mockApi.get
        .mockResolvedValueOnce(makeMultiPageResponse(0))
        .mockResolvedValueOnce(makeMultiPageResponse(1));

      render(<QuizHistoryPage />);

      await waitFor(() => {
        expect(screen.getByLabelText('Next page')).toBeInTheDocument();
      });

      fireEvent.click(screen.getByLabelText('Next page'));

      await waitFor(() => {
        expect(mockApi.get).toHaveBeenCalledWith(
          '/api/history?page=1&size=20'
        );
      });
    });

    it('navigates to specific page on numbered button click', async () => {
      mockApi.get
        .mockResolvedValueOnce(makeMultiPageResponse(0))
        .mockResolvedValueOnce(makeMultiPageResponse(2));

      render(<QuizHistoryPage />);

      await waitFor(() => {
        expect(screen.getByLabelText('Page 3')).toBeInTheDocument();
      });

      fireEvent.click(screen.getByLabelText('Page 3'));

      await waitFor(() => {
        expect(mockApi.get).toHaveBeenCalledWith(
          '/api/history?page=2&size=20'
        );
      });
    });

    it('does not show pagination when only one page', async () => {
      mockApi.get.mockResolvedValue(makeHistoryResponse());
      render(<QuizHistoryPage />);

      await waitFor(() => {
        expect(screen.getByText('Geography Quiz')).toBeInTheDocument();
      });

      expect(screen.queryByLabelText('Previous page')).not.toBeInTheDocument();
      expect(screen.queryByLabelText('Next page')).not.toBeInTheDocument();
    });

    it('retains previous data when page load fails', async () => {
      mockApi.get
        .mockResolvedValueOnce(makeMultiPageResponse(0))
        .mockRejectedValueOnce({ message: 'Page load failed' });

      render(<QuizHistoryPage />);

      await waitFor(() => {
        expect(screen.getByText('Quiz 1')).toBeInTheDocument();
      });

      fireEvent.click(screen.getByLabelText('Next page'));

      await waitFor(() => {
        expect(
          screen.getByText(
            'Failed to load the requested page. Showing previous data.'
          )
        ).toBeInTheDocument();
      });

      // Previous data should still be visible
      expect(screen.getByText('Quiz 1')).toBeInTheDocument();
    });
  });

  describe('Search filter', () => {
    it('renders search input', async () => {
      mockApi.get.mockResolvedValue(makeHistoryResponse());
      render(<QuizHistoryPage />);

      await waitFor(() => {
        expect(screen.getByLabelText('Search quiz title')).toBeInTheDocument();
      });
    });

    it('applies search filter on Apply button click', async () => {
      mockApi.get
        .mockResolvedValueOnce(makeHistoryResponse())
        .mockResolvedValueOnce(
          makeHistoryResponse({
            sessions: [
              {
                sessionId: 'session-1',
                quizTitle: 'Geography Quiz',
                endedAt: '2024-03-15T14:30:00Z',
                participantCount: 32,
                durationSeconds: 420,
              },
            ],
            totalSessions: 1,
          })
        );

      render(<QuizHistoryPage />);

      await waitFor(() => {
        expect(screen.getByText('Geography Quiz')).toBeInTheDocument();
      });

      const searchInput = screen.getByLabelText('Search quiz title');
      fireEvent.change(searchInput, { target: { value: 'Geography' } });
      fireEvent.click(screen.getByText('Apply'));

      await waitFor(() => {
        expect(mockApi.get).toHaveBeenCalledWith(
          '/api/history?page=0&size=20&search=Geography'
        );
      });
    });

    it('applies search filter on Enter key press', async () => {
      mockApi.get
        .mockResolvedValueOnce(makeHistoryResponse())
        .mockResolvedValueOnce(makeHistoryResponse());

      render(<QuizHistoryPage />);

      await waitFor(() => {
        expect(screen.getByLabelText('Search quiz title')).toBeInTheDocument();
      });

      const searchInput = screen.getByLabelText('Search quiz title');
      fireEvent.change(searchInput, { target: { value: 'Science' } });
      fireEvent.keyDown(searchInput, { key: 'Enter' });

      await waitFor(() => {
        expect(mockApi.get).toHaveBeenCalledWith(
          '/api/history?page=0&size=20&search=Science'
        );
      });
    });

    it('limits search input to 100 characters', async () => {
      mockApi.get.mockResolvedValue(makeHistoryResponse());
      render(<QuizHistoryPage />);

      await waitFor(() => {
        expect(screen.getByLabelText('Search quiz title')).toBeInTheDocument();
      });

      const searchInput = screen.getByLabelText(
        'Search quiz title'
      ) as HTMLInputElement;
      expect(searchInput.maxLength).toBe(100);
    });

    it('resets to page 0 when applying filters', async () => {
      mockApi.get
        .mockResolvedValueOnce(makeMultiPageResponse(0))
        .mockResolvedValueOnce(makeMultiPageResponse(1))
        .mockResolvedValueOnce(makeHistoryResponse());

      render(<QuizHistoryPage />);

      // Navigate to page 2
      await waitFor(() => {
        expect(screen.getByLabelText('Next page')).toBeInTheDocument();
      });
      fireEvent.click(screen.getByLabelText('Next page'));

      await waitFor(() => {
        expect(mockApi.get).toHaveBeenCalledWith(
          '/api/history?page=1&size=20'
        );
      });

      // Apply search filter
      const searchInput = screen.getByLabelText('Search quiz title');
      fireEvent.change(searchInput, { target: { value: 'test' } });
      fireEvent.click(screen.getByText('Apply'));

      await waitFor(() => {
        expect(mockApi.get).toHaveBeenCalledWith(
          '/api/history?page=0&size=20&search=test'
        );
      });
    });
  });

  describe('Date range filter', () => {
    it('renders date range inputs', async () => {
      mockApi.get.mockResolvedValue(makeHistoryResponse());
      render(<QuizHistoryPage />);

      await waitFor(() => {
        expect(screen.getByLabelText('Start date filter')).toBeInTheDocument();
        expect(screen.getByLabelText('End date filter')).toBeInTheDocument();
      });
    });

    it('applies date range filter on Apply button click', async () => {
      mockApi.get
        .mockResolvedValueOnce(makeHistoryResponse())
        .mockResolvedValueOnce(makeHistoryResponse());

      render(<QuizHistoryPage />);

      await waitFor(() => {
        expect(screen.getByLabelText('Start date filter')).toBeInTheDocument();
      });

      const startDate = screen.getByLabelText('Start date filter');
      const endDate = screen.getByLabelText('End date filter');

      fireEvent.change(startDate, { target: { value: '2024-03-01' } });
      fireEvent.change(endDate, { target: { value: '2024-03-15' } });
      fireEvent.click(screen.getByText('Apply'));

      await waitFor(() => {
        expect(mockApi.get).toHaveBeenCalledWith(
          '/api/history?page=0&size=20&startDate=2024-03-01&endDate=2024-03-15'
        );
      });
    });

    it('clears all filters on Clear button click', async () => {
      mockApi.get
        .mockResolvedValueOnce(makeHistoryResponse())
        .mockResolvedValueOnce(makeHistoryResponse())
        .mockResolvedValueOnce(makeHistoryResponse());

      render(<QuizHistoryPage />);

      await waitFor(() => {
        expect(screen.getByLabelText('Search quiz title')).toBeInTheDocument();
      });

      // Set filters
      const searchInput = screen.getByLabelText('Search quiz title');
      fireEvent.change(searchInput, { target: { value: 'test' } });
      fireEvent.click(screen.getByText('Apply'));

      await waitFor(() => {
        expect(mockApi.get).toHaveBeenCalledWith(
          '/api/history?page=0&size=20&search=test'
        );
      });

      // Clear filters
      fireEvent.click(screen.getByText('Clear'));

      await waitFor(() => {
        expect(mockApi.get).toHaveBeenCalledWith('/api/history?page=0&size=20');
      });
    });
  });

  describe('Session selection', () => {
    it('calls onSessionSelect when a session card is clicked', async () => {
      const onSessionSelect = vi.fn();
      mockApi.get.mockResolvedValue(makeHistoryResponse());

      render(<QuizHistoryPage onSessionSelect={onSessionSelect} />);

      await waitFor(() => {
        expect(screen.getByText('Geography Quiz')).toBeInTheDocument();
      });

      fireEvent.click(screen.getByText('Geography Quiz'));

      expect(onSessionSelect).toHaveBeenCalledWith('session-1');
    });
  });
});

describe('QuizHistoryPage - getHistoryPageNumbers', () => {
  // Import the exported utility function
  let getHistoryPageNumbers: (
    currentPage: number,
    totalPages: number
  ) => number[];

  beforeEach(async () => {
    const mod = await import('./QuizHistoryPage');
    getHistoryPageNumbers = mod.getHistoryPageNumbers;
  });

  it('returns all pages when totalPages <= 7', () => {
    expect(getHistoryPageNumbers(0, 5)).toEqual([0, 1, 2, 3, 4]);
  });

  it('returns all pages for exactly 7 pages', () => {
    expect(getHistoryPageNumbers(3, 7)).toEqual([0, 1, 2, 3, 4, 5, 6]);
  });

  it('includes ellipsis for many pages when on first page', () => {
    const pages = getHistoryPageNumbers(0, 10);
    expect(pages[0]).toBe(0);
    expect(pages).toContain(-1); // ellipsis
    expect(pages[pages.length - 1]).toBe(9); // last page
  });

  it('includes ellipsis on both sides when in the middle', () => {
    const pages = getHistoryPageNumbers(5, 10);
    expect(pages[0]).toBe(0);
    expect(pages[pages.length - 1]).toBe(9);
    // Should have ellipsis markers
    const ellipsisCount = pages.filter((p) => p === -1).length;
    expect(ellipsisCount).toBe(2);
  });

  it('includes ellipsis before when on last page', () => {
    const pages = getHistoryPageNumbers(9, 10);
    expect(pages[0]).toBe(0);
    expect(pages).toContain(-1); // ellipsis
    expect(pages[pages.length - 1]).toBe(9);
  });
});
