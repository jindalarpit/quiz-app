/**
 * @vitest-environment jsdom
 */
import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import { describe, it, expect, vi } from 'vitest';

import { AutoAdvanceCountdown } from './AutoAdvanceCountdown';

describe('AutoAdvanceCountdown', () => {
  describe('remaining seconds display', () => {
    it('displays the remainingSeconds value correctly', () => {
      render(
        <AutoAdvanceCountdown
          totalSeconds={5}
          remainingSeconds={3}
          onCancel={vi.fn()}
        />
      );

      // The remaining seconds should be displayed as a large number
      expect(screen.getByText('3')).toBeInTheDocument();
    });

    it('displays zero when remainingSeconds is 0', () => {
      render(
        <AutoAdvanceCountdown
          totalSeconds={5}
          remainingSeconds={0}
          onCancel={vi.fn()}
        />
      );

      expect(screen.getByText('0')).toBeInTheDocument();
    });

    it('displays the full totalSeconds value when countdown just started', () => {
      render(
        <AutoAdvanceCountdown
          totalSeconds={10}
          remainingSeconds={10}
          onCancel={vi.fn()}
        />
      );

      expect(screen.getByText('10')).toBeInTheDocument();
    });
  });

  describe('cancel button', () => {
    it('calls onCancel when the cancel button is clicked', () => {
      const onCancel = vi.fn();

      render(
        <AutoAdvanceCountdown
          totalSeconds={5}
          remainingSeconds={3}
          onCancel={onCancel}
        />
      );

      const cancelButton = screen.getByRole('button', { name: /cancel auto-advance/i });
      fireEvent.click(cancelButton);

      expect(onCancel).toHaveBeenCalledTimes(1);
    });

    it('has accessible aria-label on the cancel button', () => {
      render(
        <AutoAdvanceCountdown
          totalSeconds={5}
          remainingSeconds={3}
          onCancel={vi.fn()}
        />
      );

      const cancelButton = screen.getByRole('button', { name: 'Cancel auto-advance' });
      expect(cancelButton).toBeInTheDocument();
    });
  });

  describe('progress indicator', () => {
    it('has correct aria-valuenow set to remainingSeconds', () => {
      render(
        <AutoAdvanceCountdown
          totalSeconds={10}
          remainingSeconds={7}
          onCancel={vi.fn()}
        />
      );

      const progressbar = screen.getByRole('progressbar');
      expect(progressbar).toHaveAttribute('aria-valuenow', '7');
    });

    it('has correct aria-valuemin set to 0', () => {
      render(
        <AutoAdvanceCountdown
          totalSeconds={10}
          remainingSeconds={7}
          onCancel={vi.fn()}
        />
      );

      const progressbar = screen.getByRole('progressbar');
      expect(progressbar).toHaveAttribute('aria-valuemin', '0');
    });

    it('has correct aria-valuemax set to totalSeconds', () => {
      render(
        <AutoAdvanceCountdown
          totalSeconds={10}
          remainingSeconds={7}
          onCancel={vi.fn()}
        />
      );

      const progressbar = screen.getByRole('progressbar');
      expect(progressbar).toHaveAttribute('aria-valuemax', '10');
    });

    it('reflects the ratio of elapsed time correctly in the linear progress bar', () => {
      // totalSeconds=10, remainingSeconds=4 → elapsed=6, progress=60%
      const { container } = render(
        <AutoAdvanceCountdown
          totalSeconds={10}
          remainingSeconds={4}
          onCancel={vi.fn()}
        />
      );

      // The linear progress bar inner div should have width reflecting the elapsed percentage
      const progressFill = container.querySelector('[style*="width"]');
      expect(progressFill).not.toBeNull();
      expect(progressFill!.getAttribute('style')).toContain('width: 60%');
    });

    it('shows 0% progress when countdown just started (remaining equals total)', () => {
      const { container } = render(
        <AutoAdvanceCountdown
          totalSeconds={5}
          remainingSeconds={5}
          onCancel={vi.fn()}
        />
      );

      const progressFill = container.querySelector('[style*="width"]');
      expect(progressFill).not.toBeNull();
      expect(progressFill!.getAttribute('style')).toContain('width: 0%');
    });

    it('shows 100% progress when countdown is complete (remaining is 0)', () => {
      const { container } = render(
        <AutoAdvanceCountdown
          totalSeconds={5}
          remainingSeconds={0}
          onCancel={vi.fn()}
        />
      );

      const progressFill = container.querySelector('[style*="width"]');
      expect(progressFill).not.toBeNull();
      expect(progressFill!.getAttribute('style')).toContain('width: 100%');
    });
  });
});
