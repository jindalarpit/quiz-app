/**
 * Pure mapping functions for leaderboard visual indicators.
 * These functions encapsulate the logic used by the AnimatedLeaderboard component
 * for rank delta indicators and streak icons.
 */

export type RankDeltaDirection = 'up' | 'down' | 'neutral';
export type RankDeltaColor = 'green' | 'red' | 'gray';

export interface RankDeltaMapping {
  direction: RankDeltaDirection;
  color: RankDeltaColor;
  testId: 'rank-delta-up' | 'rank-delta-down' | 'rank-delta-neutral';
}

/**
 * Maps a rank_delta value to its visual indicator properties.
 *
 * - rank_delta > 0 → green upward arrow (moved up in rank)
 * - rank_delta < 0 → red downward arrow (moved down in rank)
 * - rank_delta = 0 → gray horizontal dash (unchanged)
 */
export function getRankDeltaMapping(rankDelta: number): RankDeltaMapping {
  if (rankDelta > 0) {
    return { direction: 'up', color: 'green', testId: 'rank-delta-up' };
  }
  if (rankDelta < 0) {
    return { direction: 'down', color: 'red', testId: 'rank-delta-down' };
  }
  return { direction: 'neutral', color: 'gray', testId: 'rank-delta-neutral' };
}

export type StreakIconType = 'none' | 'single-flame' | 'double-flame';

export interface StreakIconMapping {
  iconType: StreakIconType;
  testId: 'streak-single-flame' | 'streak-double-flame' | null;
  hasFlame: boolean;
}

/**
 * Maps a streak_count value to its visual icon properties.
 *
 * - streak_count < 3 → no flame icon displayed
 * - streak_count 3-4 → single flame icon
 * - streak_count ≥ 5 → double flame icon
 */
export function getStreakIconMapping(streakCount: number): StreakIconMapping {
  if (streakCount >= 5) {
    return { iconType: 'double-flame', testId: 'streak-double-flame', hasFlame: true };
  }
  if (streakCount >= 3) {
    return { iconType: 'single-flame', testId: 'streak-single-flame', hasFlame: true };
  }
  return { iconType: 'none', testId: null, hasFlame: false };
}
