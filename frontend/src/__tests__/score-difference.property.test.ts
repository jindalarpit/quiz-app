import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';

// Feature: quiz-results-leaderboard, Property 13: Score difference computation

/**
 * Property-based tests for score difference computation.
 *
 * **Validates: Requirements 7.2**
 *
 * For any set of participant scores where at least one participant submitted an answer:
 * - The score difference for a given participant SHALL equal that participant's score
 *   minus the arithmetic mean of all participant scores (rounded to nearest integer)
 * - The aboveAverage flag SHALL be true if and only if the difference is positive
 */

/**
 * Computes the score difference and aboveAverage flag for a participant.
 * This mirrors the logic used in the SelfResultCard component.
 */
function computeScoreDifference(
  participantScore: number,
  allScores: number[]
): { scoreDifference: number; aboveAverage: boolean } {
  const mean = allScores.reduce((sum, s) => sum + s, 0) / allScores.length;
  const scoreDifference = Math.round(participantScore - mean);
  const aboveAverage = scoreDifference > 0;
  return { scoreDifference, aboveAverage };
}

// Generator for a non-empty array of participant scores (at least 1 participant)
const allScoresArb = fc.array(fc.integer({ min: 0, max: 50000 }), {
  minLength: 1,
  maxLength: 100,
});

describe('Score Difference Computation Properties (Property 13)', () => {
  it('Property 13.1: scoreDifference equals participant score minus mean of all scores (rounded)', () => {
    fc.assert(
      fc.property(allScoresArb, fc.integer({ min: 0, max: 99 }), (scores, indexRaw) => {
        // Pick a participant from the scores array
        const participantIndex = indexRaw % scores.length;
        const participantScore = scores[participantIndex];

        const { scoreDifference } = computeScoreDifference(participantScore, scores);

        // Compute expected value independently
        const mean = scores.reduce((sum, s) => sum + s, 0) / scores.length;
        const expectedDifference = Math.round(participantScore - mean);

        expect(scoreDifference).toBe(expectedDifference);
      }),
      { numRuns: 100 }
    );
  });

  it('Property 13.2: aboveAverage is true if and only if scoreDifference is positive', () => {
    fc.assert(
      fc.property(allScoresArb, fc.integer({ min: 0, max: 99 }), (scores, indexRaw) => {
        // Pick a participant from the scores array
        const participantIndex = indexRaw % scores.length;
        const participantScore = scores[participantIndex];

        const { scoreDifference, aboveAverage } = computeScoreDifference(
          participantScore,
          scores
        );

        if (scoreDifference > 0) {
          expect(aboveAverage).toBe(true);
        } else {
          expect(aboveAverage).toBe(false);
        }
      }),
      { numRuns: 100 }
    );
  });

  it('Property 13.3: when all scores are equal, scoreDifference is 0 and aboveAverage is false', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 0, max: 50000 }),
        fc.integer({ min: 1, max: 50 }),
        (score, count) => {
          const allScores = Array(count).fill(score);
          const { scoreDifference, aboveAverage } = computeScoreDifference(score, allScores);

          expect(scoreDifference).toBe(0);
          expect(aboveAverage).toBe(false);
        }
      ),
      { numRuns: 100 }
    );
  });

  it('Property 13.4: highest scorer always has non-negative scoreDifference', () => {
    fc.assert(
      fc.property(allScoresArb, (scores) => {
        const maxScore = Math.max(...scores);
        const { scoreDifference } = computeScoreDifference(maxScore, scores);

        expect(scoreDifference).toBeGreaterThanOrEqual(0);
      }),
      { numRuns: 100 }
    );
  });

  it('Property 13.5: lowest scorer always has non-positive scoreDifference', () => {
    fc.assert(
      fc.property(allScoresArb, (scores) => {
        const minScore = Math.min(...scores);
        const { scoreDifference } = computeScoreDifference(minScore, scores);

        expect(scoreDifference).toBeLessThanOrEqual(0);
      }),
      { numRuns: 100 }
    );
  });
});
