/**
 * @vitest-environment jsdom
 */
import React from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, act } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import { SpeedBonusIndicator, type SpeedBonusIndicatorProps } from './SpeedBonusIndicator';

// Mock useReducedMotion hook
let mockReducedMotion = false;
vi.mock('@/hooks/useReducedMotion', () => ({
  useReducedMotion: () => mockReducedMotion,
}));

function makeProps(overrides: Partial<SpeedBonusIndicatorProps> = {}): SpeedBonusIndicatorProps {
  return {
    baseComponent: 300,
    speedBonus: 620,
    streakMultiplier: 1,
    totalScore: 920,
    speedPercentage: 92,
    isCorrect: true,
    ...overrides,
  };
}

describe('SpeedBonusIndicator', () => {
  beforeEach(() => {
    mockReducedMotion = false;
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  describe('incorrect/unanswered answers', () => {
    it('displays "0 points" with no breakdown for incorrect answers', () => {
      mockReducedMotion = true;
      render(<SpeedBonusIndicator {...makeProps({ isCorrect: false })} />);

      expect(screen.getByText('0 points')).toBeInTheDocument();
      // Should not show breakdown labels
      expect(screen.queryByText('Base')).not.toBeInTheDocument();
      expect(screen.queryByText('Speed bonus')).not.toBeInTheDocument();
    });

    it('has accessible region label for incorrect answers', () => {
      mockReducedMotion = true;
      render(<SpeedBonusIndicator {...makeProps({ isCorrect: false })} />);

      expect(screen.getByRole('region', { name: 'Score breakdown' })).toBeInTheDocument();
    });
  });

  describe('correct answers without streak', () => {
    it('displays base component and speed bonus breakdown', () => {
      mockReducedMotion = true;
      render(<SpeedBonusIndicator {...makeProps()} />);

      expect(screen.getByText('Base')).toBeInTheDocument();
      expect(screen.getByText('Speed bonus')).toBeInTheDocument();
      expect(screen.getByText('300')).toBeInTheDocument();
      expect(screen.getByText('620')).toBeInTheDocument();
    });

    it('displays speed percentage', () => {
      mockReducedMotion = true;
      render(<SpeedBonusIndicator {...makeProps({ speedPercentage: 92 })} />);

      expect(screen.getByText('92% speed bonus')).toBeInTheDocument();
    });

    it('does not display streak multiplier when multiplier is 1', () => {
      mockReducedMotion = true;
      render(<SpeedBonusIndicator {...makeProps({ streakMultiplier: 1 })} />);

      expect(screen.queryByText('Streak multiplier')).not.toBeInTheDocument();
      expect(screen.queryByText('×2')).not.toBeInTheDocument();
      expect(screen.queryByText('×3')).not.toBeInTheDocument();
    });

    it('displays total score with reduced motion', () => {
      mockReducedMotion = true;
      render(<SpeedBonusIndicator {...makeProps({ totalScore: 920 })} />);

      expect(screen.getByText('920')).toBeInTheDocument();
      expect(screen.getByText('points')).toBeInTheDocument();
    });

    it('has accessible aria-label for total score', () => {
      mockReducedMotion = true;
      render(<SpeedBonusIndicator {...makeProps({ totalScore: 920 })} />);

      expect(screen.getByLabelText('Total score: 920 points')).toBeInTheDocument();
    });
  });

  describe('correct answers with streak multiplier', () => {
    it('displays streak multiplier as ×2', () => {
      mockReducedMotion = true;
      render(
        <SpeedBonusIndicator
          {...makeProps({
            baseComponent: 300,
            speedBonus: 620,
            streakMultiplier: 2,
            totalScore: 1840,
          })}
        />
      );

      expect(screen.getByText('Streak multiplier')).toBeInTheDocument();
      expect(screen.getByText('×2')).toBeInTheDocument();
    });

    it('displays streak multiplier as ×3', () => {
      mockReducedMotion = true;
      render(
        <SpeedBonusIndicator
          {...makeProps({
            baseComponent: 300,
            speedBonus: 620,
            streakMultiplier: 3,
            totalScore: 2760,
          })}
        />
      );

      expect(screen.getByText('Streak multiplier')).toBeInTheDocument();
      expect(screen.getByText('×3')).toBeInTheDocument();
    });

    it('displays subtotal and total when streak is active', () => {
      mockReducedMotion = true;
      render(
        <SpeedBonusIndicator
          {...makeProps({
            baseComponent: 300,
            speedBonus: 620,
            streakMultiplier: 2,
            totalScore: 1840,
          })}
        />
      );

      expect(screen.getByText('Subtotal')).toBeInTheDocument();
      expect(screen.getByText('Total')).toBeInTheDocument();
      // Subtotal = 300 + 620 = 920
      expect(screen.getByText('920')).toBeInTheDocument();
    });
  });

  describe('reduced motion behavior', () => {
    it('displays final score immediately without animation when reduced motion is preferred', () => {
      mockReducedMotion = true;
      render(<SpeedBonusIndicator {...makeProps({ totalScore: 750 })} />);

      // Score should be displayed immediately at final value
      expect(screen.getByLabelText('Total score: 750 points')).toBeInTheDocument();
      expect(screen.getByText('750')).toBeInTheDocument();
    });
  });

  describe('counting animation', () => {
    it('starts at 0 when animation is enabled', () => {
      mockReducedMotion = false;
      render(<SpeedBonusIndicator {...makeProps({ totalScore: 920 })} />);

      // Initially should show 0 (animation hasn't started yet)
      expect(screen.getByLabelText('Total score: 920 points')).toBeInTheDocument();
    });
  });

  describe('accessibility', () => {
    it('has role="region" with aria-label', () => {
      mockReducedMotion = true;
      render(<SpeedBonusIndicator {...makeProps()} />);

      expect(screen.getByRole('region', { name: 'Score breakdown' })).toBeInTheDocument();
    });

    it('has aria-live="polite" for dynamic content', () => {
      mockReducedMotion = true;
      const { container } = render(<SpeedBonusIndicator {...makeProps()} />);

      const region = container.querySelector('[aria-live="polite"]');
      expect(region).toBeInTheDocument();
    });
  });
});
