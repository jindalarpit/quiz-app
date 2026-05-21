import { describe, expect, it } from 'vitest';

import { formatDuration } from './formatDuration';

describe('formatDuration', () => {
  it('formats 0 seconds as "0m 0s"', () => {
    expect(formatDuration(0)).toBe('0m 0s');
  });

  it('formats seconds-only values with 0m prefix', () => {
    expect(formatDuration(45)).toBe('0m 45s');
  });

  it('formats exact minutes without seconds', () => {
    expect(formatDuration(300)).toBe('5m');
  });

  it('formats minutes with remaining seconds', () => {
    expect(formatDuration(125)).toBe('2m 5s');
  });

  it('formats exactly 1 hour', () => {
    expect(formatDuration(3600)).toBe('1h 0m');
  });

  it('formats hours and minutes', () => {
    expect(formatDuration(4980)).toBe('1h 23m');
  });

  it('formats 24 hours (86400 seconds)', () => {
    expect(formatDuration(86400)).toBe('24h 0m');
  });

  it('formats hours with minutes but drops seconds', () => {
    expect(formatDuration(3661)).toBe('1h 1m');
  });

  it('handles negative values by treating as 0', () => {
    expect(formatDuration(-10)).toBe('0m 0s');
  });

  it('floors fractional seconds', () => {
    expect(formatDuration(45.9)).toBe('0m 45s');
  });
});
