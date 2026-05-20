import type { LeaderboardUpdateEntry, ScoreBreakdown } from '@/types';

/**
 * Compute a ScoreBreakdown for the current participant from their leaderboard entry.
 *
 * Since the frontend does not have access to the exact time_factor or time_taken,
 * we derive the breakdown from the available entry data:
 * - If roundScore > 0, the participant answered correctly.
 * - The baseComponent is the minimum guaranteed points (base_points × (1 - time_factor)).
 *   Without knowing the exact time_factor on the frontend, we approximate using the
 *   default base_points of 1000 and time_factor of 0.7, giving a base of 300.
 *   However, since the actual score before multiplier = roundScore / streakMultiplier,
 *   we compute: scoreBeforeMultiplier = roundScore / streakMultiplier
 *   baseComponent = min(300, scoreBeforeMultiplier) — the minimum guaranteed for a correct answer
 *   speedBonus = scoreBeforeMultiplier - baseComponent
 * - speedPercentage = round((scoreBeforeMultiplier / base_points) × 100)
 *
 * For simplicity and correctness with variable base_points and time_factors,
 * we use a heuristic: baseComponent = 30% of the score ceiling (1000 default base_points).
 * The backend should ideally send the breakdown, but until then we compute from available data.
 *
 * _Requirements: 2.1, 2.2, 5.2_
 */

const DEFAULT_BASE_POINTS = 1000;
const DEFAULT_TIME_FACTOR = 0.7;

export function computeScoreBreakdown(
  entry: LeaderboardUpdateEntry | undefined
): ScoreBreakdown {
  if (!entry || entry.roundScore <= 0) {
    return {
      baseComponent: 0,
      speedBonus: 0,
      streakMultiplier: 1,
      totalScore: 0,
      speedPercentage: 0,
      isCorrect: false,
    };
  }

  const streakMultiplier = entry.streakMultiplier > 0 ? entry.streakMultiplier : 1;
  const scoreBeforeMultiplier = Math.round(entry.roundScore / streakMultiplier);

  // The base component is the minimum guaranteed points for a correct answer
  // base_points × (1 - time_factor) = 1000 × 0.3 = 300 for default "Speed Matters" mode
  const baseComponent = Math.round(DEFAULT_BASE_POINTS * (1 - DEFAULT_TIME_FACTOR));

  // Speed bonus is the additional points earned above the base component
  const speedBonus = Math.max(0, scoreBeforeMultiplier - baseComponent);

  // Speed percentage: how much of the maximum possible points were earned
  const speedPercentage = Math.round((scoreBeforeMultiplier / DEFAULT_BASE_POINTS) * 100);

  return {
    baseComponent,
    speedBonus,
    streakMultiplier,
    totalScore: entry.roundScore,
    speedPercentage,
    isCorrect: true,
  };
}
