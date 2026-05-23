// @vitest-environment jsdom
import React from 'react';
import { render, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

import { TimerDisplay } from './TimerDisplay';

describe('TimerDisplay guards', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  describe('sessionState guard', () => {
    it('does NOT fire onExpire when sessionState is REVEAL (not QUESTION_OPEN)', () => {
      const onExpire = vi.fn();
      const now = Date.now();

      render(
        <TimerDisplay
          timeLimit={5}
          serverTimestamp={now}
          questionId="q1"
          sessionState="REVEAL"
          getServerTime={() => now}
          onExpire={onExpire}
        />
      );

      // Advance time past the timer expiry (5 seconds + buffer)
      act(() => {
        vi.advanceTimersByTime(6000);
      });

      expect(onExpire).not.toHaveBeenCalled();
    });

    it('does NOT fire onExpire when sessionState is ENDED', () => {
      const onExpire = vi.fn();
      const now = Date.now();

      render(
        <TimerDisplay
          timeLimit={5}
          serverTimestamp={now}
          questionId="q1"
          sessionState="ENDED"
          getServerTime={() => now}
          onExpire={onExpire}
        />
      );

      act(() => {
        vi.advanceTimersByTime(6000);
      });

      expect(onExpire).not.toHaveBeenCalled();
    });

    it('fires onExpire when sessionState is QUESTION_OPEN and timer expires', () => {
      const onExpire = vi.fn();
      const now = Date.now();
      let currentTime = now;

      render(
        <TimerDisplay
          timeLimit={5}
          serverTimestamp={now}
          questionId="q1"
          sessionState="QUESTION_OPEN"
          getServerTime={() => currentTime}
          onExpire={onExpire}
        />
      );

      // Advance simulated server time past expiry
      currentTime = now + 6000;
      act(() => {
        vi.advanceTimersByTime(6000);
      });

      expect(onExpire).toHaveBeenCalledTimes(1);
    });
  });

  describe('questionId reset guard', () => {
    it('resets the expiry guard when questionId changes, allowing onExpire to fire again', () => {
      const onExpire = vi.fn();
      const now = Date.now();
      let currentTime = now;

      const { rerender } = render(
        <TimerDisplay
          timeLimit={5}
          serverTimestamp={now}
          questionId="q1"
          sessionState="QUESTION_OPEN"
          getServerTime={() => currentTime}
          onExpire={onExpire}
        />
      );

      // Expire the first question
      currentTime = now + 6000;
      act(() => {
        vi.advanceTimersByTime(6000);
      });

      expect(onExpire).toHaveBeenCalledTimes(1);

      // Re-render with a new questionId and fresh timestamp
      const newNow = Date.now();
      currentTime = newNow;

      rerender(
        <TimerDisplay
          timeLimit={5}
          serverTimestamp={newNow}
          questionId="q2"
          sessionState="QUESTION_OPEN"
          getServerTime={() => currentTime}
          onExpire={onExpire}
        />
      );

      // Expire the second question
      currentTime = newNow + 6000;
      act(() => {
        vi.advanceTimersByTime(6000);
      });

      expect(onExpire).toHaveBeenCalledTimes(2);
    });
  });

  describe('duplicate expiry prevention', () => {
    it('fires onExpire only once even when multiple ticks occur after expiry', () => {
      const onExpire = vi.fn();
      const now = Date.now();
      let currentTime = now;

      render(
        <TimerDisplay
          timeLimit={5}
          serverTimestamp={now}
          questionId="q1"
          sessionState="QUESTION_OPEN"
          getServerTime={() => currentTime}
          onExpire={onExpire}
        />
      );

      // Advance past expiry
      currentTime = now + 6000;
      act(() => {
        vi.advanceTimersByTime(6000);
      });

      expect(onExpire).toHaveBeenCalledTimes(1);

      // Simulate additional ticks well past expiry
      currentTime = now + 10000;
      act(() => {
        vi.advanceTimersByTime(5000);
      });

      currentTime = now + 15000;
      act(() => {
        vi.advanceTimersByTime(5000);
      });

      // Still only called once
      expect(onExpire).toHaveBeenCalledTimes(1);
    });
  });
});
