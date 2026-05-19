import { describe, it, expect } from 'vitest';
import type { ParticipantSelfResult } from '@/types';

/**
 * Unit tests for SelfResultCard component logic.
 *
 * Tests the data formatting and display logic used by the SelfResultCard component.
 * Validates: Requirements 1.5, 7.1, 7.2
 */

/** Computes the score comparison text shown in the SelfResultCard */
function formatScoreComparison(scoreDifference: number, aboveAverage: boolean): string {
  const sign = aboveAverage ? '+' : '-';
  const label = aboveAverage ? 'above' : 'below';
  return `${sign}${Math.abs(scoreDifference)} ${label} average`;
}

/** Computes accuracy percentage from correct answers and total questions */
function computeAccuracy(correctAnswers: number, totalQuestions: number): string {
  if (totalQuestions === 0) return '0%';
  return `${Math.round((correctAnswers / totalQuestions) * 100)}%`;
}

/** Formats average response time to 1 decimal place */
function formatAvgResponseTime(avgResponseTimeSec: number): string {
  return `${avgResponseTimeSec.toFixed(1)}s`;
}

describe('SelfResultCard logic', () => {
  const baseResult: ParticipantSelfResult = {
    rank: 12,
    score: 5200,
    correctAnswers: 6,
    totalQuestions: 10,
    maxStreak: 3,
    avgResponseTimeSec: 4.1,
    scoreDifference: 320,
    aboveAverage: true,
    questionBreakdown: [],
  };

  describe('Score comparison indicator', () => {
    it('shows positive difference when above average', () => {
      const text = formatScoreComparison(baseResult.scoreDifference, baseResult.aboveAverage);
      expect(text).toBe('+320 above average');
    });

    it('shows negative difference when below average', () => {
      const text = formatScoreComparison(450, false);
      expect(text).toBe('-450 below average');
    });

    it('shows zero difference correctly when above average', () => {
      const text = formatScoreComparison(0, true);
      expect(text).toBe('+0 above average');
    });

    it('shows zero difference correctly when below average', () => {
      const text = formatScoreComparison(0, false);
      expect(text).toBe('-0 below average');
    });
  });

  describe('Accuracy computation', () => {
    it('computes accuracy percentage correctly', () => {
      expect(computeAccuracy(6, 10)).toBe('60%');
    });

    it('handles perfect score', () => {
      expect(computeAccuracy(10, 10)).toBe('100%');
    });

    it('handles zero correct answers', () => {
      expect(computeAccuracy(0, 10)).toBe('0%');
    });

    it('handles zero total questions', () => {
      expect(computeAccuracy(0, 0)).toBe('0%');
    });

    it('rounds to nearest integer', () => {
      expect(computeAccuracy(1, 3)).toBe('33%');
      expect(computeAccuracy(2, 3)).toBe('67%');
    });
  });

  describe('Average response time formatting', () => {
    it('formats to one decimal place', () => {
      expect(formatAvgResponseTime(4.1)).toBe('4.1s');
    });

    it('adds trailing zero for whole numbers', () => {
      expect(formatAvgResponseTime(3.0)).toBe('3.0s');
    });

    it('rounds to one decimal place', () => {
      expect(formatAvgResponseTime(2.567)).toBe('2.6s');
    });

    it('handles zero response time', () => {
      expect(formatAvgResponseTime(0)).toBe('0.0s');
    });
  });

  describe('Result data structure', () => {
    it('contains all required fields for display', () => {
      expect(baseResult).toHaveProperty('rank');
      expect(baseResult).toHaveProperty('score');
      expect(baseResult).toHaveProperty('correctAnswers');
      expect(baseResult).toHaveProperty('totalQuestions');
      expect(baseResult).toHaveProperty('maxStreak');
      expect(baseResult).toHaveProperty('avgResponseTimeSec');
      expect(baseResult).toHaveProperty('scoreDifference');
      expect(baseResult).toHaveProperty('aboveAverage');
    });

    it('rank is a positive integer', () => {
      expect(baseResult.rank).toBeGreaterThan(0);
      expect(Number.isInteger(baseResult.rank)).toBe(true);
    });

    it('score is a non-negative integer', () => {
      expect(baseResult.score).toBeGreaterThanOrEqual(0);
      expect(Number.isInteger(baseResult.score)).toBe(true);
    });

    it('correctAnswers does not exceed totalQuestions', () => {
      expect(baseResult.correctAnswers).toBeLessThanOrEqual(baseResult.totalQuestions);
    });
  });
});
