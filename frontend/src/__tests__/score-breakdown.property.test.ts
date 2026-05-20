// Feature: dynamic-scoring-leaderboard, Property 6: Score Breakdown Consistency
import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';
import { computeScoreBreakdown } from '@/lib/computeScoreBreakdown';
import type { LeaderboardUpdateEntry } from '@/types';

/**
 * Property-based tests for score breakdown consistency (Property 6).
 *
 * **Validates: Requirements 2.1, 2.2, 2.6**
 *
 * Property 6: Score Breakdown Consistency — For any correctly answered question
 * with earned_score, base_points, and time_factor, the score breakdown SHALL satisfy:
 * - base_component + speed_bonus = earned_score_before_multiplier
 * - total = (base_component + speed_bonus) × streak_multiplier = earned_score
 * - speed_percentage = round((earned_score_before_multiplier / base_points) × 100)
 */

const DEFAULT_BASE_POINTS = 1000;
const DEFAULT_TIME_FACTOR = 0.7;

// Generator for valid streak multipliers (1, 2, or 3)
const streakMultiplierArb = fc.constantFrom(1, 2, 3);

// Generator for a valid score before multiplier (between min and max for default config)
// Min score = base_points × (1 - time_factor) = 1000 × 0.3 = 300
// Max score = base_points = 1000
const scoreBeforeMultiplierArb = fc.integer({
  min: Math.round(DEFAULT_BASE_POINTS * (1 - DEFAULT_TIME_FACTOR)),
  max: DEFAULT_BASE_POINTS,
});

// Generator for a valid LeaderboardUpdateEntry representing a correct answer
const correctAnswerEntryArb = fc
  .tuple(scoreBeforeMultiplierArb, streakMultiplierArb)
  .map(([scoreBeforeMultiplier, streakMultiplier]) => {
    const roundScore = scoreBeforeMultiplier * streakMultiplier;
    return {
      entry: {
        participantId: 'test-participant',
        nickname: 'TestPlayer',
        cumulativeScore: roundScore + 1000,
        roundScore,
        rank: 1,
        rankDelta: 0,
        streakCount: streakMultiplier === 1 ? 0 : streakMultiplier === 2 ? 3 : 5,
        streakMultiplier,
      } as LeaderboardUpdateEntry,
      expectedScoreBeforeMultiplier: scoreBeforeMultiplier,
    };
  });

describe('Score Breakdown Consistency Properties (Property 6)', () => {
  it('P6.1: base_component + speed_bonus = earned_score_before_multiplier', () => {
    fc.assert(
      fc.property(correctAnswerEntryArb, ({ entry, expectedScoreBeforeMultiplier }) => {
        const breakdown = computeScoreBreakdown(entry);

        // The score before multiplier should equal base_component + speed_bonus
        const scoreBeforeMultiplier = breakdown.baseComponent + breakdown.speedBonus;
        expect(scoreBeforeMultiplier).toBe(expectedScoreBeforeMultiplier);
      }),
      { numRuns: 500 }
    );
  });

  it('P6.2: total = (base_component + speed_bonus) × streak_multiplier', () => {
    fc.assert(
      fc.property(correctAnswerEntryArb, ({ entry }) => {
        const breakdown = computeScoreBreakdown(entry);

        // Total score should equal (base + speed) × multiplier
        const expectedTotal =
          (breakdown.baseComponent + breakdown.speedBonus) * breakdown.streakMultiplier;
        expect(breakdown.totalScore).toBe(expectedTotal);
      }),
      { numRuns: 500 }
    );
  });

  it('P6.3: speed_percentage = round((scoreBeforeMultiplier / base_points) × 100)', () => {
    fc.assert(
      fc.property(correctAnswerEntryArb, ({ entry, expectedScoreBeforeMultiplier }) => {
        const breakdown = computeScoreBreakdown(entry);

        // Speed percentage should be the percentage of max possible points earned
        const expectedPercentage = Math.round(
          (expectedScoreBeforeMultiplier / DEFAULT_BASE_POINTS) * 100
        );
        expect(breakdown.speedPercentage).toBe(expectedPercentage);
      }),
      { numRuns: 500 }
    );
  });

  it('P6.4: isCorrect is true for any entry with roundScore > 0', () => {
    fc.assert(
      fc.property(correctAnswerEntryArb, ({ entry }) => {
        const breakdown = computeScoreBreakdown(entry);
        expect(breakdown.isCorrect).toBe(true);
      }),
      { numRuns: 500 }
    );
  });

  it('P6.5: isCorrect is false and all components are 0 for incorrect answers', () => {
    const incorrectEntryArb = fc.record({
      participantId: fc.uuid(),
      nickname: fc.string({ minLength: 1, maxLength: 20 }),
      cumulativeScore: fc.nat({ max: 50000 }),
      roundScore: fc.constantFrom(0, -1, -100),
      rank: fc.integer({ min: 1, max: 100 }),
      rankDelta: fc.integer({ min: -50, max: 50 }),
      streakCount: fc.nat({ max: 10 }),
      streakMultiplier: fc.constantFrom(1, 2, 3),
    });

    fc.assert(
      fc.property(incorrectEntryArb, (entry) => {
        const breakdown = computeScoreBreakdown(entry as LeaderboardUpdateEntry);

        expect(breakdown.isCorrect).toBe(false);
        expect(breakdown.baseComponent).toBe(0);
        expect(breakdown.speedBonus).toBe(0);
        expect(breakdown.totalScore).toBe(0);
        expect(breakdown.speedPercentage).toBe(0);
        expect(breakdown.streakMultiplier).toBe(1);
      }),
      { numRuns: 500 }
    );
  });

  it('P6.6: speed_bonus is always non-negative for correct answers', () => {
    fc.assert(
      fc.property(correctAnswerEntryArb, ({ entry }) => {
        const breakdown = computeScoreBreakdown(entry);
        expect(breakdown.speedBonus).toBeGreaterThanOrEqual(0);
      }),
      { numRuns: 500 }
    );
  });

  it('P6.7: base_component is consistent with default configuration', () => {
    fc.assert(
      fc.property(correctAnswerEntryArb, ({ entry }) => {
        const breakdown = computeScoreBreakdown(entry);

        // Base component should be base_points × (1 - time_factor) = 1000 × 0.3 = 300
        const expectedBase = Math.round(DEFAULT_BASE_POINTS * (1 - DEFAULT_TIME_FACTOR));
        expect(breakdown.baseComponent).toBe(expectedBase);
      }),
      { numRuns: 500 }
    );
  });
});
