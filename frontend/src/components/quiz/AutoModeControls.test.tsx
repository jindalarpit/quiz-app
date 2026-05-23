// @vitest-environment jsdom
import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';

import { AutoModeControls } from './AutoModeControls';

describe('AutoModeControls', () => {
  const defaultProps = {
    enabled: false,
    leaderboardDelay: 3,
    onToggle: vi.fn(),
    onDelayChange: vi.fn(),
  };

  describe('full mode (lobby display)', () => {
    it('renders toggle switch with proper label', () => {
      render(<AutoModeControls {...defaultProps} />);

      const toggle = screen.getByRole('switch', { name: /toggle auto mode/i });
      expect(toggle).toBeDefined();
      expect(toggle.getAttribute('aria-checked')).toBe('false');
    });

    it('renders Auto Mode label text', () => {
      render(<AutoModeControls {...defaultProps} />);

      expect(screen.getByText('Auto Mode')).toBeDefined();
      expect(screen.getByText('Automatically advance through questions')).toBeDefined();
    });

    it('shows status indicator as OFF when disabled', () => {
      render(<AutoModeControls {...defaultProps} enabled={false} />);

      expect(screen.getByText('Auto Mode: OFF')).toBeDefined();
    });

    it('shows status indicator as ON when enabled', () => {
      render(<AutoModeControls {...defaultProps} enabled={true} />);

      expect(screen.getByText('Auto Mode: ON')).toBeDefined();
    });

    it('calls onToggle with true when toggle is clicked while disabled', () => {
      const onToggle = vi.fn();
      render(<AutoModeControls {...defaultProps} onToggle={onToggle} enabled={false} />);

      const toggle = screen.getByRole('switch', { name: /toggle auto mode/i });
      fireEvent.click(toggle);

      expect(onToggle).toHaveBeenCalledWith(true);
    });

    it('calls onToggle with false when toggle is clicked while enabled', () => {
      const onToggle = vi.fn();
      render(<AutoModeControls {...defaultProps} onToggle={onToggle} enabled={true} />);

      const toggle = screen.getByRole('switch', { name: /toggle auto mode/i });
      fireEvent.click(toggle);

      expect(onToggle).toHaveBeenCalledWith(false);
    });

    it('does not show delay input when disabled', () => {
      render(<AutoModeControls {...defaultProps} enabled={false} />);

      expect(screen.queryByLabelText(/leaderboard display time/i)).toBeNull();
    });

    it('shows delay input when enabled', () => {
      render(<AutoModeControls {...defaultProps} enabled={true} />);

      const input = screen.getByLabelText(/leaderboard display time/i);
      expect(input).toBeDefined();
      expect((input as HTMLInputElement).value).toBe('3');
    });

    it('delay input has min=1 and max=30 attributes', () => {
      render(<AutoModeControls {...defaultProps} enabled={true} />);

      const input = screen.getByLabelText(/leaderboard display time/i) as HTMLInputElement;
      expect(input.getAttribute('min')).toBe('1');
      expect(input.getAttribute('max')).toBe('30');
      expect(input.getAttribute('type')).toBe('number');
    });

    it('calls onDelayChange when delay input value changes', () => {
      const onDelayChange = vi.fn();
      render(<AutoModeControls {...defaultProps} enabled={true} onDelayChange={onDelayChange} />);

      const input = screen.getByLabelText(/leaderboard display time/i);
      fireEvent.change(input, { target: { value: '10' } });

      expect(onDelayChange).toHaveBeenCalledWith(10);
    });

    it('has accessible group role with label', () => {
      render(<AutoModeControls {...defaultProps} />);

      const group = screen.getByRole('group', { name: /auto mode controls/i });
      expect(group).toBeDefined();
    });

    it('delay description text is visible', () => {
      render(<AutoModeControls {...defaultProps} enabled={true} />);

      expect(screen.getByText('seconds (1–30)')).toBeDefined();
    });
  });

  describe('compact mode (in-game floating display)', () => {
    it('renders in compact mode with smaller toggle', () => {
      render(<AutoModeControls {...defaultProps} compact={true} />);

      const toggle = screen.getByRole('switch', { name: /toggle auto mode/i });
      expect(toggle).toBeDefined();
    });

    it('shows compact status indicator ON', () => {
      render(<AutoModeControls {...defaultProps} compact={true} enabled={true} />);

      expect(screen.getByText('ON')).toBeDefined();
    });

    it('shows compact status indicator OFF', () => {
      render(<AutoModeControls {...defaultProps} compact={true} enabled={false} />);

      expect(screen.getByText('OFF')).toBeDefined();
    });

    it('shows delay value in seconds when enabled', () => {
      render(<AutoModeControls {...defaultProps} compact={true} enabled={true} leaderboardDelay={5} />);

      expect(screen.getByText('5s')).toBeDefined();
    });

    it('does not show delay value when disabled', () => {
      render(<AutoModeControls {...defaultProps} compact={true} enabled={false} />);

      expect(screen.queryByText('3s')).toBeNull();
    });

    it('has accessible group role with label in compact mode', () => {
      render(<AutoModeControls {...defaultProps} compact={true} />);

      const group = screen.getByRole('group', { name: /auto mode controls/i });
      expect(group).toBeDefined();
    });

    it('toggle is keyboard accessible', () => {
      const onToggle = vi.fn();
      render(<AutoModeControls {...defaultProps} compact={true} onToggle={onToggle} />);

      const toggle = screen.getByRole('switch', { name: /toggle auto mode/i });
      fireEvent.keyDown(toggle, { key: 'Enter' });
      fireEvent.click(toggle);

      expect(onToggle).toHaveBeenCalled();
    });
  });

  describe('accessibility', () => {
    it('toggle has aria-checked reflecting enabled state', () => {
      const { rerender } = render(<AutoModeControls {...defaultProps} enabled={false} />);

      const toggle = screen.getByRole('switch', { name: /toggle auto mode/i });
      expect(toggle.getAttribute('aria-checked')).toBe('false');

      rerender(<AutoModeControls {...defaultProps} enabled={true} />);
      expect(toggle.getAttribute('aria-checked')).toBe('true');
    });

    it('status indicator has aria-live for screen reader announcements', () => {
      render(<AutoModeControls {...defaultProps} />);

      const status = screen.getByText('Auto Mode: OFF');
      expect(status.getAttribute('aria-live')).toBe('polite');
    });

    it('delay input has aria-describedby linking to description', () => {
      render(<AutoModeControls {...defaultProps} enabled={true} />);

      const input = screen.getByLabelText(/leaderboard display time/i);
      expect(input.getAttribute('aria-describedby')).toBe('delay-description');
    });
  });
});
