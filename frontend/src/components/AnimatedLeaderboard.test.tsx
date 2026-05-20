/**
 * @vitest-environment jsdom
 */
import React from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, act } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import { AnimatedLeaderboard, type AnimatedLeaderboardProps } from './AnimatedLeaderboard';
import type { LeaderboardUpdateEntry } from '@/types';

// Mock useReducedMotion hook
let mockReducedMotion = false;
vi.mock('@/hooks/useReducedMotion', () => ({
  useReducedMotion: () => mockReducedMotion,
}));

// Mock framer-motion to avoid animation complexity in unit tests
vi.mock('framer-motion', () => ({
  AnimatePresence: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  motion: {
    div: React.forwardRef(
      (
        {
          children,
          initial,
          animate,
          exit,
          transition,
          layout,
          ...props
        }: React.HTMLAttributes<HTMLDivElement> & Record<string, unknown>,
        ref: React.Ref<HTMLDivElement>
      ) => (
        <div ref={ref} {...props}>
          {children}
        </div>
      )
    ),
  },
}));

function makeEntry(overrides: Partial<LeaderboardUpdateEntry> = {}): LeaderboardUpdateEntry {
  return {
    participantId: 'p1',
    nickname: 'Player1',
    cumulativeScore: 1000,
    roundScore: 500,
    rank: 1,
    rankDelta: 0,
    streakCount: 0,
    streakMultiplier: 1,
    ...overrides,
  };
}

function makeEntries(count: number): LeaderboardUpdateEntry[] {
  return Array.from({ length: count }, (_, i) =>
    makeEntry({
      participantId: `p${i + 1}`,
      nickname: `Player${i + 1}`,
      cumulativeScore: (count - i) * 1000,
      roundScore: 500,
      rank: i + 1,
      rankDelta: i === 0 ? 2 : i === 1 ? -1 : 0,
      streakCount: i === 0 ? 5 : i === 1 ? 3 : 1,
      streakMultiplier: i === 0 ? 3 : i === 1 ? 2 : 1,
    })
  );
}

function makeProps(overrides: Partial<AnimatedLeaderboardProps> = {}): AnimatedLeaderboardProps {
  return {
    entries: makeEntries(5),
    previousEntries: makeEntries(5),
    isHost: true,
    roundNumber: 2,
    animationPhase: 'idle',
    ...overrides,
  };
}

describe('AnimatedLeaderboard', () => {
  beforeEach(() => {
    mockReducedMotion = false;
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  describe('render with mock leaderboard data', () => {
    it('renders all entries with correct data-testid attributes', () => {
      mockReducedMotion = true;
      const entries = makeEntries(5);
      render(<AnimatedLeaderboard {...makeProps({ entries })} />);

      for (const entry of entries) {
        expect(
          screen.getByTestId(`leaderboard-row-${entry.participantId}`)
        ).toBeInTheDocument();
      }
    });

    it('displays participant nicknames', () => {
      mockReducedMotion = true;
      const entries = makeEntries(3);
      render(<AnimatedLeaderboard {...makeProps({ entries })} />);

      expect(screen.getByText('Player1')).toBeInTheDocument();
      expect(screen.getByText('Player2')).toBeInTheDocument();
      expect(screen.getByText('Player3')).toBeInTheDocument();
    });

    it('displays leaderboard heading', () => {
      mockReducedMotion = true;
      render(<AnimatedLeaderboard {...makeProps()} />);

      expect(screen.getByText('Leaderboard')).toBeInTheDocument();
    });

    it('has accessible region with aria-label', () => {
      mockReducedMotion = true;
      render(<AnimatedLeaderboard {...makeProps()} />);

      expect(screen.getByRole('region', { name: 'Leaderboard' })).toBeInTheDocument();
    });

    it('displays trophy icon for 1st place', () => {
      mockReducedMotion = true;
      const entries = makeEntries(3);
      render(<AnimatedLeaderboard {...makeProps({ entries })} />);

      expect(screen.getByTestId('rank-icon-trophy')).toBeInTheDocument();
    });

    it('displays silver medal icon for 2nd place', () => {
      mockReducedMotion = true;
      const entries = makeEntries(3);
      render(<AnimatedLeaderboard {...makeProps({ entries })} />);

      expect(screen.getByTestId('rank-icon-medal-silver')).toBeInTheDocument();
    });

    it('displays bronze medal icon for 3rd place', () => {
      mockReducedMotion = true;
      const entries = makeEntries(3);
      render(<AnimatedLeaderboard {...makeProps({ entries })} />);

      expect(screen.getByTestId('rank-icon-medal-bronze')).toBeInTheDocument();
    });

    it('displays streak icons based on streak count', () => {
      mockReducedMotion = true;
      const entries = [
        makeEntry({ participantId: 'p1', rank: 1, streakCount: 5 }),
        makeEntry({ participantId: 'p2', rank: 2, streakCount: 3 }),
        makeEntry({ participantId: 'p3', rank: 3, streakCount: 1 }),
      ];
      render(<AnimatedLeaderboard {...makeProps({ entries })} />);

      expect(screen.getByTestId('streak-double-flame')).toBeInTheDocument();
      expect(screen.getByTestId('streak-single-flame')).toBeInTheDocument();
    });

    it('displays multiplier badge when streak multiplier > 1', () => {
      mockReducedMotion = true;
      const entries = [
        makeEntry({ participantId: 'p1', rank: 1, streakMultiplier: 2 }),
        makeEntry({ participantId: 'p2', rank: 2, streakMultiplier: 1 }),
      ];
      render(<AnimatedLeaderboard {...makeProps({ entries })} />);

      const badges = screen.getAllByTestId('multiplier-badge');
      expect(badges).toHaveLength(1);
      expect(badges[0]).toHaveTextContent('2x');
    });
  });

  describe('first-round behavior (no rank change animations)', () => {
    it('shows neutral indicator for all participants on first round', () => {
      mockReducedMotion = true;
      const entries = [
        makeEntry({ participantId: 'p1', rank: 1, rankDelta: 2 }),
        makeEntry({ participantId: 'p2', rank: 2, rankDelta: -1 }),
        makeEntry({ participantId: 'p3', rank: 3, rankDelta: 0 }),
      ];
      render(
        <AnimatedLeaderboard
          {...makeProps({ entries, roundNumber: 1 })}
        />
      );

      // All should show neutral indicator regardless of rankDelta values
      const neutralIndicators = screen.getAllByTestId('rank-delta-neutral');
      expect(neutralIndicators).toHaveLength(3);

      // Should NOT show up or down indicators
      expect(screen.queryByTestId('rank-delta-up')).not.toBeInTheDocument();
      expect(screen.queryByTestId('rank-delta-down')).not.toBeInTheDocument();
    });

    it('shows rank delta indicators on subsequent rounds', () => {
      mockReducedMotion = true;
      const entries = [
        makeEntry({ participantId: 'p1', rank: 1, rankDelta: 2 }),
        makeEntry({ participantId: 'p2', rank: 2, rankDelta: -1 }),
        makeEntry({ participantId: 'p3', rank: 3, rankDelta: 0 }),
      ];
      render(
        <AnimatedLeaderboard
          {...makeProps({ entries, roundNumber: 2 })}
        />
      );

      expect(screen.getByTestId('rank-delta-up')).toBeInTheDocument();
      expect(screen.getByTestId('rank-delta-down')).toBeInTheDocument();
      expect(screen.getByTestId('rank-delta-neutral')).toBeInTheDocument();
    });
  });

  describe('reduced motion behavior', () => {
    it('displays final scores immediately without animation when reduced motion is preferred', () => {
      mockReducedMotion = true;
      const entries = [
        makeEntry({ participantId: 'p1', rank: 1, cumulativeScore: 2500 }),
      ];
      const previousEntries = [
        makeEntry({ participantId: 'p1', rank: 1, cumulativeScore: 1500 }),
      ];
      render(
        <AnimatedLeaderboard
          {...makeProps({ entries, previousEntries })}
        />
      );

      // Should show final score immediately
      expect(screen.getByText('2,500')).toBeInTheDocument();
    });

    it('shows rank delta indicators immediately without fade-in when reduced motion is preferred', () => {
      mockReducedMotion = true;
      const entries = [
        makeEntry({ participantId: 'p1', rank: 1, rankDelta: 3 }),
      ];
      render(
        <AnimatedLeaderboard
          {...makeProps({ entries, roundNumber: 2 })}
        />
      );

      expect(screen.getByTestId('rank-delta-up')).toBeInTheDocument();
    });

    it('does not show particle effects when reduced motion is preferred', () => {
      mockReducedMotion = true;
      // New entry entering top 3 for first time
      const entries = [
        makeEntry({ participantId: 'new-player', rank: 1, cumulativeScore: 5000 }),
      ];
      render(
        <AnimatedLeaderboard
          {...makeProps({ entries, previousEntries: [] })}
        />
      );

      expect(screen.queryByTestId('particle-effect')).not.toBeInTheDocument();
    });
  });

  describe('animation interruption handling', () => {
    it('cancels in-progress animation and applies final state when entries change', () => {
      mockReducedMotion = false;
      const entries1 = [
        makeEntry({ participantId: 'p1', rank: 1, cumulativeScore: 1000 }),
      ];
      const previousEntries1 = [
        makeEntry({ participantId: 'p1', rank: 1, cumulativeScore: 500 }),
      ];

      const { rerender } = render(
        <AnimatedLeaderboard
          {...makeProps({ entries: entries1, previousEntries: previousEntries1 })}
        />
      );

      // Advance partially through animation (200ms into position phase)
      act(() => {
        vi.advanceTimersByTime(200);
      });

      // New entries arrive (interruption)
      const entries2 = [
        makeEntry({ participantId: 'p1', rank: 1, cumulativeScore: 2000 }),
      ];
      const previousEntries2 = [
        makeEntry({ participantId: 'p1', rank: 1, cumulativeScore: 1000 }),
      ];

      rerender(
        <AnimatedLeaderboard
          {...makeProps({ entries: entries2, previousEntries: previousEntries2 })}
        />
      );

      // After full animation budget, should show final state
      act(() => {
        vi.advanceTimersByTime(1500);
      });

      expect(screen.getByText('2,000')).toBeInTheDocument();
    });

    it('completes all animations within 1500ms budget', () => {
      mockReducedMotion = false;
      const entries = [
        makeEntry({ participantId: 'p1', rank: 1, cumulativeScore: 3000, rankDelta: 2 }),
      ];
      const previousEntries = [
        makeEntry({ participantId: 'p1', rank: 3, cumulativeScore: 2000 }),
      ];

      render(
        <AnimatedLeaderboard
          {...makeProps({ entries, previousEntries, roundNumber: 3 })}
        />
      );

      // After 1500ms (max budget), everything should be in final state
      act(() => {
        vi.advanceTimersByTime(1500);
      });

      // Final score should be displayed
      expect(screen.getByText('3,000')).toBeInTheDocument();
      // Rank delta should be visible
      expect(screen.getByTestId('rank-delta-up')).toBeInTheDocument();
    });
  });

  describe('host vs participant view rendering', () => {
    it('renders top 5 entries for host view', () => {
      mockReducedMotion = true;
      const entries = makeEntries(5);
      render(
        <AnimatedLeaderboard
          {...makeProps({ entries, isHost: true })}
        />
      );

      // All 5 entries should be rendered
      for (let i = 1; i <= 5; i++) {
        expect(screen.getByTestId(`leaderboard-row-p${i}`)).toBeInTheDocument();
      }
    });

    it('renders participant view with context rows', () => {
      mockReducedMotion = true;
      // Simulate a participant view: top 5 + own entry with context
      const entries = [
        makeEntry({ participantId: 'p1', rank: 1, nickname: 'Leader' }),
        makeEntry({ participantId: 'p2', rank: 2, nickname: 'Second' }),
        makeEntry({ participantId: 'p3', rank: 3, nickname: 'Third' }),
        makeEntry({ participantId: 'p4', rank: 4, nickname: 'Fourth' }),
        makeEntry({ participantId: 'p5', rank: 5, nickname: 'Fifth' }),
        makeEntry({ participantId: 'p7', rank: 7, nickname: 'AboveMe' }),
        makeEntry({ participantId: 'p8', rank: 8, nickname: 'Me' }),
        makeEntry({ participantId: 'p9', rank: 9, nickname: 'BelowMe' }),
      ];
      render(
        <AnimatedLeaderboard
          {...makeProps({ entries, isHost: false })}
        />
      );

      // All provided entries should be rendered (the component renders what it receives)
      expect(screen.getByText('Leader')).toBeInTheDocument();
      expect(screen.getByText('Me')).toBeInTheDocument();
      expect(screen.getByText('AboveMe')).toBeInTheDocument();
      expect(screen.getByText('BelowMe')).toBeInTheDocument();
    });

    it('renders fewer entries when session has fewer than 5 participants', () => {
      mockReducedMotion = true;
      const entries = makeEntries(3);
      render(
        <AnimatedLeaderboard
          {...makeProps({ entries, isHost: true })}
        />
      );

      // Only 3 rows should be rendered
      const rows = screen.getAllByTestId(/^leaderboard-row-/);
      expect(rows).toHaveLength(3);
    });

    it('renders all entries passed to it regardless of isHost flag', () => {
      mockReducedMotion = true;
      const entries = makeEntries(7);
      render(
        <AnimatedLeaderboard
          {...makeProps({ entries, isHost: false })}
        />
      );

      // Component renders all entries it receives (filtering is done upstream)
      const rows = screen.getAllByTestId(/^leaderboard-row-/);
      expect(rows).toHaveLength(7);
    });
  });

  describe('rank delta indicators', () => {
    it('shows green upward arrow for positive rank delta', () => {
      mockReducedMotion = true;
      const entries = [
        makeEntry({ participantId: 'p1', rank: 1, rankDelta: 3 }),
      ];
      render(
        <AnimatedLeaderboard
          {...makeProps({ entries, roundNumber: 2 })}
        />
      );

      const upIndicator = screen.getByTestId('rank-delta-up');
      expect(upIndicator).toHaveTextContent('+3');
    });

    it('shows red downward arrow for negative rank delta', () => {
      mockReducedMotion = true;
      const entries = [
        makeEntry({ participantId: 'p1', rank: 4, rankDelta: -2 }),
      ];
      render(
        <AnimatedLeaderboard
          {...makeProps({ entries, roundNumber: 2 })}
        />
      );

      const downIndicator = screen.getByTestId('rank-delta-down');
      expect(downIndicator).toHaveTextContent('-2');
    });

    it('shows gray dash for zero rank delta', () => {
      mockReducedMotion = true;
      const entries = [
        makeEntry({ participantId: 'p1', rank: 2, rankDelta: 0 }),
      ];
      render(
        <AnimatedLeaderboard
          {...makeProps({ entries, roundNumber: 2 })}
        />
      );

      expect(screen.getByTestId('rank-delta-neutral')).toBeInTheDocument();
    });
  });

  describe('particle effects for new top 3', () => {
    it('shows particle effect when participant enters top 3 for first time', () => {
      mockReducedMotion = false;
      const entries = [
        makeEntry({ participantId: 'new-top3', rank: 2, cumulativeScore: 3000 }),
      ];
      render(
        <AnimatedLeaderboard
          {...makeProps({ entries, previousEntries: [] })}
        />
      );

      expect(screen.getByTestId('particle-effect')).toBeInTheDocument();
    });

    it('particle effect disappears after 1000ms', () => {
      mockReducedMotion = false;
      const entries = [
        makeEntry({ participantId: 'new-top3', rank: 1, cumulativeScore: 5000 }),
      ];
      render(
        <AnimatedLeaderboard
          {...makeProps({ entries, previousEntries: [] })}
        />
      );

      expect(screen.getByTestId('particle-effect')).toBeInTheDocument();

      act(() => {
        vi.advanceTimersByTime(1000);
      });

      expect(screen.queryByTestId('particle-effect')).not.toBeInTheDocument();
    });
  });
});
