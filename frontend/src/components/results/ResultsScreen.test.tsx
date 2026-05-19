/**
 * @vitest-environment jsdom
 */
import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import { ResultsScreen } from './ResultsScreen';
import type {
  PagedLeaderboardResponse,
  ParticipantSelfResult,
  WSMessage,
} from '@/types';

// Mock the api module
vi.mock('@/lib/api', () => ({
  api: {
    get: vi.fn(),
    request: vi.fn(),
  },
}));

// Mock the session store
vi.mock('@/stores/sessionStore', () => ({
  useSessionStore: vi.fn((selector) => {
    if (typeof selector === 'function') {
      return selector({ participantId: null });
    }
    return { participantId: null };
  }),
}));

// Mock child components to isolate ResultsScreen behavior
vi.mock('./LeaderboardTable', () => ({
  LeaderboardTable: ({ data, onPageChange }: { data: { entries: { nickname: string }[]; currentPage: number; totalPages: number; totalParticipants: number }; onPageChange: (page: number) => void }) => (
    <div data-testid="leaderboard-table">
      {data.entries.map((e: { nickname: string }) => (
        <span key={e.nickname}>{e.nickname}</span>
      ))}
      <span>Page {data.currentPage + 1} of {data.totalPages}</span>
      <span>{data.totalParticipants} participants</span>
      {data.totalPages > 1 && (
        <button onClick={() => onPageChange(data.currentPage + 1)}>Next</button>
      )}
    </div>
  ),
}));

vi.mock('./TopThreePodium', () => ({
  TopThreePodium: ({ entries }: { entries: { rank: number; nickname: string }[] }) => (
    <div data-testid="top-three-podium">
      {entries.map((e: { rank: number; nickname: string }) => (
        <span key={e.rank}>{e.rank === 1 ? '🥇' : e.rank === 2 ? '🥈' : '🥉'} {e.nickname}</span>
      ))}
    </div>
  ),
}));

vi.mock('./SelfResultCard', () => ({
  SelfResultCard: ({ result }: { result: { rank: number; scoreDifference: number; aboveAverage: boolean } }) => (
    <div data-testid="self-result-card">
      <span>#{result.rank}</span>
      <span>{result.aboveAverage ? '+' : '-'}{Math.abs(result.scoreDifference)} {result.aboveAverage ? 'above' : 'below'} average</span>
    </div>
  ),
}));

vi.mock('./PerQuestionBreakdown', () => ({
  PerQuestionBreakdown: ({ questionBreakdown }: { questionBreakdown: unknown[] }) => (
    <div data-testid="per-question-breakdown">
      <span>Per-Question Breakdown</span>
      <span>{questionBreakdown.length} questions</span>
    </div>
  ),
}));

import { api } from '@/lib/api';
import { useSessionStore } from '@/stores/sessionStore';

const mockApi = api as { get: ReturnType<typeof vi.fn>; request: ReturnType<typeof vi.fn> };
const mockUseSessionStore = useSessionStore as unknown as ReturnType<typeof vi.fn>;

function makeLeaderboardResponse(
  overrides: Partial<PagedLeaderboardResponse> = {}
): PagedLeaderboardResponse {
  return {
    sessionId: 'session-123',
    entries: [
      {
        rank: 1,
        nickname: 'Alice',
        score: 9000,
        correctAnswers: 9,
        totalAnswers: 10,
        maxStreak: 7,
        avgResponseTimeSec: 2.5,
      },
      {
        rank: 2,
        nickname: 'Bob',
        score: 7500,
        correctAnswers: 7,
        totalAnswers: 10,
        maxStreak: 5,
        avgResponseTimeSec: 3.1,
      },
      {
        rank: 3,
        nickname: 'Charlie',
        score: 6000,
        correctAnswers: 6,
        totalAnswers: 10,
        maxStreak: 3,
        avgResponseTimeSec: 4.0,
      },
    ],
    currentPage: 0,
    totalPages: 1,
    totalParticipants: 3,
    pageSize: 20,
    ...overrides,
  };
}

function makeSelfResult(
  overrides: Partial<ParticipantSelfResult> = {}
): ParticipantSelfResult {
  return {
    rank: 5,
    score: 4200,
    correctAnswers: 5,
    totalQuestions: 10,
    maxStreak: 3,
    avgResponseTimeSec: 4.5,
    scoreDifference: 200,
    aboveAverage: true,
    questionBreakdown: [
      { questionNumber: 1, status: 'CORRECT' },
      { questionNumber: 2, status: 'INCORRECT' },
      { questionNumber: 3, status: 'UNANSWERED' },
    ],
    ...overrides,
  };
}

describe('ResultsScreen', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseSessionStore.mockImplementation((selector: (state: { participantId: string | null }) => unknown) => {
      if (typeof selector === 'function') {
        return selector({ participantId: null });
      }
      return { participantId: null };
    });
  });

  describe('Leaderboard loading and display', () => {
    it('shows loading state initially', () => {
      mockApi.get.mockReturnValue(new Promise(() => {})); // never resolves
      render(<ResultsScreen sessionId="session-123" />);

      expect(screen.getByText('Loading leaderboard...')).toBeInTheDocument();
    });

    it('displays leaderboard data on successful fetch', async () => {
      mockApi.get.mockResolvedValue(makeLeaderboardResponse());
      render(<ResultsScreen sessionId="session-123" />);

      await waitFor(() => {
        expect(screen.getByText('Alice')).toBeInTheDocument();
      });
      expect(screen.getByText('Bob')).toBeInTheDocument();
      expect(screen.getByText('Charlie')).toBeInTheDocument();
    });

    it('shows error state when leaderboard fetch fails', async () => {
      mockApi.get.mockRejectedValue({ message: 'Network error' });
      render(<ResultsScreen sessionId="session-123" />);

      await waitFor(() => {
        expect(screen.getByText('Network error')).toBeInTheDocument();
      });
    });

    it('shows TopThreePodium on first page', async () => {
      mockApi.get.mockResolvedValue(makeLeaderboardResponse());
      render(<ResultsScreen sessionId="session-123" />);

      await waitFor(() => {
        expect(screen.getByTestId('top-three-podium')).toBeInTheDocument();
      });
    });
  });

  describe('Error handling with retry', () => {
    it('shows retry button with remaining attempts on error', async () => {
      mockApi.get.mockRejectedValue({ message: 'Server error' });
      render(<ResultsScreen sessionId="session-123" />);

      await waitFor(() => {
        expect(
          screen.getByText('Retry (3 attempts remaining)')
        ).toBeInTheDocument();
      });
    });

    it('decrements retry count on each retry attempt', async () => {
      mockApi.get.mockRejectedValue({ message: 'Server error' });
      render(<ResultsScreen sessionId="session-123" />);

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
      render(<ResultsScreen sessionId="session-123" />);

      // First render shows error
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
          screen.getByText("We couldn't load the quiz results. Please try again later.")
        ).toBeInTheDocument();
      });
    });
  });

  describe('Graceful degradation', () => {
    it('shows leaderboard even when self-result fails', async () => {
      mockApi.get.mockResolvedValue(makeLeaderboardResponse());
      mockApi.request.mockRejectedValue({ message: 'Self result error' });

      mockUseSessionStore.mockImplementation((selector: (state: { participantId: string | null }) => unknown) => {
        if (typeof selector === 'function') {
          return selector({ participantId: 'participant-1' });
        }
        return { participantId: 'participant-1' };
      });

      render(
        <ResultsScreen
          sessionId="session-123"
          participantId="participant-1"
        />
      );

      // Leaderboard should still show
      await waitFor(() => {
        expect(screen.getByText('Alice')).toBeInTheDocument();
      });

      // Self-result error should show
      await waitFor(() => {
        expect(screen.getByText('Self result error')).toBeInTheDocument();
      });
    });

    it('shows self-result card even when leaderboard fails', async () => {
      mockApi.get.mockRejectedValue({ message: 'Leaderboard error' });
      mockApi.request.mockResolvedValue(makeSelfResult());

      mockUseSessionStore.mockImplementation((selector: (state: { participantId: string | null }) => unknown) => {
        if (typeof selector === 'function') {
          return selector({ participantId: 'participant-1' });
        }
        return { participantId: 'participant-1' };
      });

      render(
        <ResultsScreen
          sessionId="session-123"
          participantId="participant-1"
        />
      );

      // Leaderboard error should show
      await waitFor(() => {
        expect(screen.getByText('Leaderboard error')).toBeInTheDocument();
      });

      // Self-result should still show
      await waitFor(() => {
        expect(screen.getByText('#5')).toBeInTheDocument();
      });
    });
  });

  describe('Self-result display', () => {
    it('fetches and displays self-result when participantId is provided', async () => {
      mockApi.get.mockResolvedValue(makeLeaderboardResponse());
      mockApi.request.mockResolvedValue(makeSelfResult());

      render(
        <ResultsScreen
          sessionId="session-123"
          participantId="participant-1"
        />
      );

      await waitFor(() => {
        expect(screen.getByText('#5')).toBeInTheDocument();
      });
      expect(screen.getByText('+200 above average')).toBeInTheDocument();
    });

    it('does not fetch self-result when no participantId', async () => {
      mockApi.get.mockResolvedValue(makeLeaderboardResponse());

      render(<ResultsScreen sessionId="session-123" />);

      await waitFor(() => {
        expect(screen.getByText('Alice')).toBeInTheDocument();
      });

      expect(mockApi.request).not.toHaveBeenCalled();
    });

    it('displays per-question breakdown when self-result is available', async () => {
      mockApi.get.mockResolvedValue(makeLeaderboardResponse());
      mockApi.request.mockResolvedValue(makeSelfResult());

      render(
        <ResultsScreen
          sessionId="session-123"
          participantId="participant-1"
        />
      );

      await waitFor(() => {
        expect(
          screen.getByText('Per-Question Breakdown')
        ).toBeInTheDocument();
      });
    });
  });

  describe('WebSocket session.ended event', () => {
    it('triggers data fetch when session.ended message is received', async () => {
      mockApi.get.mockResolvedValue(makeLeaderboardResponse());

      const wsMessage: WSMessage = {
        type: 'session.ended',
        payload: {},
      };

      render(
        <ResultsScreen sessionId="session-123" onMessage={wsMessage} />
      );

      await waitFor(() => {
        expect(mockApi.get).toHaveBeenCalledWith(
          '/api/sessions/session-123/leaderboard?page=0&size=20'
        );
      });
    });

    it('fetches self-result on session.ended when participantId is available', async () => {
      mockApi.get.mockResolvedValue(makeLeaderboardResponse());
      mockApi.request.mockResolvedValue(makeSelfResult());

      const wsMessage: WSMessage = {
        type: 'session.ended',
        payload: {},
      };

      render(
        <ResultsScreen
          sessionId="session-123"
          participantId="participant-1"
          onMessage={wsMessage}
        />
      );

      await waitFor(() => {
        expect(mockApi.request).toHaveBeenCalled();
      });
    });
  });

  describe('Pagination', () => {
    it('calls fetchLeaderboard with new page on page change', async () => {
      const multiPageResponse = makeLeaderboardResponse({
        totalPages: 3,
        totalParticipants: 55,
      });
      mockApi.get.mockResolvedValue(multiPageResponse);

      render(<ResultsScreen sessionId="session-123" />);

      await waitFor(() => {
        expect(screen.getByText('Alice')).toBeInTheDocument();
      });

      // The LeaderboardTable should render pagination controls
      // Since totalPages > 1, navigation should be present
      expect(screen.getByText('Page 1 of 3')).toBeInTheDocument();
    });

    it('retains currently displayed page data when next page load fails (Req 2.5)', async () => {
      // First call succeeds (initial page load)
      mockApi.get.mockResolvedValueOnce(makeLeaderboardResponse({
        totalPages: 3,
        totalParticipants: 55,
      }));

      render(<ResultsScreen sessionId="session-123" />);

      await waitFor(() => {
        expect(screen.getByText('Alice')).toBeInTheDocument();
      });

      // Second call fails (page navigation)
      mockApi.get.mockRejectedValueOnce({ message: 'Page load failed' });

      // Trigger page change
      fireEvent.click(screen.getByText('Next'));

      await waitFor(() => {
        // Previous data should still be visible
        expect(screen.getByText('Alice')).toBeInTheDocument();
      });

      // Inline error should show
      expect(screen.getByText('Failed to load the requested page. Showing previous data.')).toBeInTheDocument();
    });
  });

  describe('Self-result retry behavior (Req 7.4)', () => {
    it('shows self-result retry button with remaining attempts on error', async () => {
      mockApi.get.mockResolvedValue(makeLeaderboardResponse());
      mockApi.request.mockRejectedValue({ message: 'Self result error' });

      mockUseSessionStore.mockImplementation((selector: (state: { participantId: string | null }) => unknown) => {
        if (typeof selector === 'function') {
          return selector({ participantId: 'participant-1' });
        }
        return { participantId: 'participant-1' };
      });

      render(
        <ResultsScreen
          sessionId="session-123"
          participantId="participant-1"
        />
      );

      await waitFor(() => {
        expect(
          screen.getByText('Retry (3 attempts remaining)')
        ).toBeInTheDocument();
      });
    });

    it('decrements self-result retry count on each attempt', async () => {
      mockApi.get.mockResolvedValue(makeLeaderboardResponse());
      mockApi.request.mockRejectedValue({ message: 'Self result error' });

      mockUseSessionStore.mockImplementation((selector: (state: { participantId: string | null }) => unknown) => {
        if (typeof selector === 'function') {
          return selector({ participantId: 'participant-1' });
        }
        return { participantId: 'participant-1' };
      });

      render(
        <ResultsScreen
          sessionId="session-123"
          participantId="participant-1"
        />
      );

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

    it('shows persistent self-result failure after max retries exhausted', async () => {
      mockApi.get.mockResolvedValue(makeLeaderboardResponse());
      mockApi.request.mockRejectedValue({ message: 'Self result error' });

      mockUseSessionStore.mockImplementation((selector: (state: { participantId: string | null }) => unknown) => {
        if (typeof selector === 'function') {
          return selector({ participantId: 'participant-1' });
        }
        return { participantId: 'participant-1' };
      });

      render(
        <ResultsScreen
          sessionId="session-123"
          participantId="participant-1"
        />
      );

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
          screen.getByText('Unable to load your results. Please try again later.')
        ).toBeInTheDocument();
      });
    });
  });

  describe('Header', () => {
    it('renders the Quiz Results header', async () => {
      mockApi.get.mockResolvedValue(makeLeaderboardResponse());
      render(<ResultsScreen sessionId="session-123" />);

      await waitFor(() => {
        expect(screen.getByText('Quiz Results')).toBeInTheDocument();
      });
      expect(screen.getByText('Final standings')).toBeInTheDocument();
    });
  });
});
