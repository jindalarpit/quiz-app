import { describe, it, expect } from 'vitest';
import { formatDateBadge } from './formatDateBadge';

describe('formatDateBadge', () => {
  it('formats a date in January with zero-padded day', () => {
    expect(formatDateBadge('2024-01-05T10:30:00Z')).toBe('Jan 05, 2024');
  });

  it('formats a date at end of year', () => {
    expect(formatDateBadge('2023-12-31T23:59:59Z')).toBe('Dec 31, 2023');
  });

  it('formats a date in the middle of the year', () => {
    expect(formatDateBadge('2024-06-15T00:00:00Z')).toBe('Jun 15, 2024');
  });

  it('formats the first day of the year', () => {
    expect(formatDateBadge('2024-01-01T00:00:00Z')).toBe('Jan 01, 2024');
  });

  it('formats a date with single-digit day zero-padded', () => {
    expect(formatDateBadge('2024-03-09T12:00:00Z')).toBe('Mar 09, 2024');
  });

  it('formats a date with double-digit day', () => {
    expect(formatDateBadge('2024-11-25T08:00:00Z')).toBe('Nov 25, 2024');
  });

  it('handles leap year date', () => {
    expect(formatDateBadge('2024-02-29T00:00:00Z')).toBe('Feb 29, 2024');
  });
});
