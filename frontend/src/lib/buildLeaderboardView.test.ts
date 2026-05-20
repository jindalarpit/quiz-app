import { describe, expect, it } from 'vitest';

import type { LeaderboardUpdateEntry } from '@/types';

import { buildHostView, buildParticipantDisplayView } from './buildLeaderboardView';

function makeEntry(overrides: Partial<LeaderboardUpdateEntry> & { rank: number; participantId: string }): LeaderboardUpdateEntry {
  return {
    nickname: `Player${overrides.rank}`,
    cumulativeScore: 1000 - overrides.rank * 100,
    roundScore: 100,
    rankDelta: 0,
    streakCount: 0,
    streakMultiplier: 1,
    ...overrides,
  };
}

function makeEntries(count: number): LeaderboardUpdateEntry[] {
  return Array.from({ length: count }, (_, i) =>
    makeEntry({ rank: i + 1, participantId: `p${i + 1}` })
  );
}

describe('buildHostView', () => {
  it('returns top 5 entries when more than 5 participants', () => {
    const entries = makeEntries(10);
    const result = buildHostView(entries);
    expect(result).toHaveLength(5);
    expect(result.map((e) => e.rank)).toEqual([1, 2, 3, 4, 5]);
  });

  it('returns all entries when fewer than 5 participants', () => {
    const entries = makeEntries(3);
    const result = buildHostView(entries);
    expect(result).toHaveLength(3);
    expect(result.map((e) => e.rank)).toEqual([1, 2, 3]);
  });

  it('returns entries sorted by rank ascending regardless of input order', () => {
    const entries = makeEntries(5).reverse();
    const result = buildHostView(entries);
    expect(result.map((e) => e.rank)).toEqual([1, 2, 3, 4, 5]);
  });

  it('returns empty array for empty input', () => {
    const result = buildHostView([]);
    expect(result).toHaveLength(0);
  });

  it('returns exactly 5 entries for exactly 5 participants', () => {
    const entries = makeEntries(5);
    const result = buildHostView(entries);
    expect(result).toHaveLength(5);
  });
});

describe('buildParticipantDisplayView', () => {
  it('returns own rank + 2 above + 2 below for middle-ranked participant', () => {
    const entries = makeEntries(10);
    const result = buildParticipantDisplayView(entries, 'p5');
    // Should show ranks 3, 4, 5, 6, 7
    expect(result.map((e) => e.rank)).toEqual([3, 4, 5, 6, 7]);
    expect(result.find((e) => e.participantId === 'p5')).toBeDefined();
  });

  it('shows fewer above when near top boundary', () => {
    const entries = makeEntries(10);
    // Participant at rank 2: only 1 above (rank 1), 2 below (ranks 3, 4)
    const result = buildParticipantDisplayView(entries, 'p2');
    expect(result.map((e) => e.rank)).toEqual([1, 2, 3, 4]);
  });

  it('shows fewer below when near bottom boundary', () => {
    const entries = makeEntries(10);
    // Participant at rank 9: 2 above (ranks 7, 8), 1 below (rank 10)
    const result = buildParticipantDisplayView(entries, 'p9');
    expect(result.map((e) => e.rank)).toEqual([7, 8, 9, 10]);
  });

  it('shows no entries above for rank 1', () => {
    const entries = makeEntries(10);
    const result = buildParticipantDisplayView(entries, 'p1');
    // Rank 1: 0 above, 2 below (ranks 2, 3)
    expect(result.map((e) => e.rank)).toEqual([1, 2, 3]);
  });

  it('shows no entries below for last rank', () => {
    const entries = makeEntries(10);
    const result = buildParticipantDisplayView(entries, 'p10');
    // Rank 10: 2 above (ranks 8, 9), 0 below
    expect(result.map((e) => e.rank)).toEqual([8, 9, 10]);
  });

  it('returns all entries when only 1 participant', () => {
    const entries = makeEntries(1);
    const result = buildParticipantDisplayView(entries, 'p1');
    expect(result).toHaveLength(1);
    expect(result[0].participantId).toBe('p1');
  });

  it('returns all entries when 3 or fewer participants', () => {
    const entries = makeEntries(3);
    const result = buildParticipantDisplayView(entries, 'p2');
    // Rank 2: 1 above (rank 1), 1 below (rank 3) = all 3
    expect(result.map((e) => e.rank)).toEqual([1, 2, 3]);
  });

  it('returns all entries for participant not found (fallback)', () => {
    const entries = makeEntries(5);
    const result = buildParticipantDisplayView(entries, 'unknown');
    expect(result).toHaveLength(5);
  });

  it('returns empty array for empty input', () => {
    const result = buildParticipantDisplayView([], 'p1');
    expect(result).toHaveLength(0);
  });

  it('results are sorted by rank ascending', () => {
    const entries = makeEntries(10).reverse(); // shuffled input
    const result = buildParticipantDisplayView(entries, 'p5');
    for (let i = 0; i < result.length - 1; i++) {
      expect(result[i].rank).toBeLessThan(result[i + 1].rank);
    }
  });

  it('always includes the participant own entry', () => {
    const entries = makeEntries(20);
    const result = buildParticipantDisplayView(entries, 'p15');
    expect(result.find((e) => e.participantId === 'p15')).toBeDefined();
  });

  it('maximum entries is 5 (own + 2 above + 2 below)', () => {
    const entries = makeEntries(100);
    const result = buildParticipantDisplayView(entries, 'p50');
    expect(result).toHaveLength(5);
  });
});
