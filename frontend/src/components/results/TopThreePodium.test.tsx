/**
 * @vitest-environment jsdom
 */
import React from 'react';
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import { TopThreePodium, type PodiumEntry } from './TopThreePodium';

function makePodiumEntry(overrides: Partial<PodiumEntry> = {}): PodiumEntry {
  return {
    rank: 1,
    nickname: 'Player1',
    score: 5000,
    correctAnswers: 8,
    totalAnswers: 10,
    maxStreak: 5,
    avgResponseTimeSec: 3.2,
    ...overrides,
  };
}

describe('TopThreePodium', () => {
  it('renders nothing when entries array is empty', () => {
    const { container } = render(<TopThreePodium entries={[]} />);
    expect(container.firstChild).toBeNull();
  });

  it('renders a single participant with gold indicator', () => {
    const entries = [makePodiumEntry({ rank: 1, nickname: 'Alice', score: 9000 })];
    render(<TopThreePodium entries={entries} />);

    expect(screen.getByText('Alice')).toBeInTheDocument();
    expect(screen.getByText('9,000')).toBeInTheDocument();
    expect(screen.getByText('🥇')).toBeInTheDocument();
    expect(screen.getByText('1st')).toBeInTheDocument();
    expect(screen.queryByText('🥈')).not.toBeInTheDocument();
    expect(screen.queryByText('🥉')).not.toBeInTheDocument();
  });

  it('renders two participants with gold and silver indicators', () => {
    const entries = [
      makePodiumEntry({ rank: 1, nickname: 'Alice', score: 9000 }),
      makePodiumEntry({ rank: 2, nickname: 'Bob', score: 7500 }),
    ];
    render(<TopThreePodium entries={entries} />);

    expect(screen.getByText('Alice')).toBeInTheDocument();
    expect(screen.getByText('Bob')).toBeInTheDocument();
    expect(screen.getByText('🥇')).toBeInTheDocument();
    expect(screen.getByText('🥈')).toBeInTheDocument();
    expect(screen.getByText('1st')).toBeInTheDocument();
    expect(screen.getByText('2nd')).toBeInTheDocument();
    expect(screen.queryByText('🥉')).not.toBeInTheDocument();
  });

  it('renders three participants with gold, silver, and bronze indicators', () => {
    const entries = [
      makePodiumEntry({ rank: 1, nickname: 'Alice', score: 9000 }),
      makePodiumEntry({ rank: 2, nickname: 'Bob', score: 7500 }),
      makePodiumEntry({ rank: 3, nickname: 'Charlie', score: 6000 }),
    ];
    render(<TopThreePodium entries={entries} />);

    expect(screen.getByText('Alice')).toBeInTheDocument();
    expect(screen.getByText('Bob')).toBeInTheDocument();
    expect(screen.getByText('Charlie')).toBeInTheDocument();
    expect(screen.getByText('🥇')).toBeInTheDocument();
    expect(screen.getByText('🥈')).toBeInTheDocument();
    expect(screen.getByText('🥉')).toBeInTheDocument();
    expect(screen.getByText('1st')).toBeInTheDocument();
    expect(screen.getByText('2nd')).toBeInTheDocument();
    expect(screen.getByText('3rd')).toBeInTheDocument();
  });

  it('only renders up to 3 entries even if more are provided', () => {
    const entries = [
      makePodiumEntry({ rank: 1, nickname: 'Alice', score: 9000 }),
      makePodiumEntry({ rank: 2, nickname: 'Bob', score: 7500 }),
      makePodiumEntry({ rank: 3, nickname: 'Charlie', score: 6000 }),
      makePodiumEntry({ rank: 4, nickname: 'Dave', score: 5000 }),
    ];
    render(<TopThreePodium entries={entries} />);

    expect(screen.getByText('Alice')).toBeInTheDocument();
    expect(screen.getByText('Bob')).toBeInTheDocument();
    expect(screen.getByText('Charlie')).toBeInTheDocument();
    expect(screen.queryByText('Dave')).not.toBeInTheDocument();
  });

  it('displays correct answers and streak for each entry', () => {
    const entries = [
      makePodiumEntry({ rank: 1, nickname: 'Alice', correctAnswers: 9, totalAnswers: 10, maxStreak: 7 }),
    ];
    render(<TopThreePodium entries={entries} />);

    expect(screen.getByText('9/10 correct')).toBeInTheDocument();
    expect(screen.getByText('🔥 7 streak')).toBeInTheDocument();
  });

  it('has accessible region label', () => {
    const entries = [makePodiumEntry({ rank: 1, nickname: 'Alice' })];
    render(<TopThreePodium entries={entries} />);

    expect(screen.getByRole('region', { name: 'Top participants podium' })).toBeInTheDocument();
  });

  it('has accessible aria-label for each podium card', () => {
    const entries = [
      makePodiumEntry({ rank: 1, nickname: 'Alice' }),
      makePodiumEntry({ rank: 2, nickname: 'Bob' }),
    ];
    render(<TopThreePodium entries={entries} />);

    expect(screen.getByLabelText('1st place: Alice')).toBeInTheDocument();
    expect(screen.getByLabelText('2nd place: Bob')).toBeInTheDocument();
  });
});
