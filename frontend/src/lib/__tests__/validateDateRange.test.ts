import { describe, it, expect } from 'vitest';
import { validateDateRange } from '../validateDateRange';

describe('validateDateRange', () => {
  it('returns valid when start date is before end date', () => {
    const result = validateDateRange('2024-01-01', '2024-01-31');
    expect(result).toEqual({ valid: true });
  });

  it('returns valid when start date equals end date', () => {
    const result = validateDateRange('2024-03-15', '2024-03-15');
    expect(result).toEqual({ valid: true });
  });

  it('returns error when start date is after end date', () => {
    const result = validateDateRange('2024-06-15', '2024-01-01');
    expect(result.valid).toBe(false);
    expect(result.error).toBeDefined();
  });

  it('handles ISO datetime strings correctly', () => {
    const result = validateDateRange(
      '2024-01-15T10:00:00Z',
      '2024-01-15T09:00:00Z'
    );
    expect(result.valid).toBe(false);
    expect(result.error).toBeDefined();
  });

  it('returns valid for same datetime', () => {
    const result = validateDateRange(
      '2024-05-20T12:00:00Z',
      '2024-05-20T12:00:00Z'
    );
    expect(result).toEqual({ valid: true });
  });
});
