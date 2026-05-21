import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';
import { validateDateRange } from '../validateDateRange';

/**
 * Property-based tests for date range validation.
 *
 * **Validates: Requirements 1.4**
 *
 * For any pair of dates where the start date is strictly after the end date,
 * the validation function SHALL return an error. For any pair where start <= end,
 * the validation function SHALL return valid.
 */

describe('Date Range Validation Properties (Property 3)', () => {
  it('Property 3.1: rejects date ranges where start is strictly after end', () => {
    fc.assert(
      fc.property(
        fc.date({ min: new Date(2000, 0, 1), max: new Date(2050, 11, 31) }),
        fc.date({ min: new Date(2000, 0, 1), max: new Date(2050, 11, 31) }),
        (dateA, dateB) => {
          // Ensure start is strictly after end
          const start = dateA.getTime() > dateB.getTime() ? dateA : dateB;
          const end = dateA.getTime() > dateB.getTime() ? dateB : dateA;

          // Skip equal dates — we only test strictly invalid ranges
          fc.pre(start.getTime() > end.getTime());

          const result = validateDateRange(start.toISOString(), end.toISOString());

          expect(result.valid).toBe(false);
          expect(result.error).toBeDefined();
          expect(typeof result.error).toBe('string');
          expect(result.error!.length).toBeGreaterThan(0);
        }
      ),
      { numRuns: 100 }
    );
  });

  it('Property 3.2: accepts date ranges where start is before or equal to end', () => {
    fc.assert(
      fc.property(
        fc.date({ min: new Date(2000, 0, 1), max: new Date(2050, 11, 31) }),
        fc.date({ min: new Date(2000, 0, 1), max: new Date(2050, 11, 31) }),
        (dateA, dateB) => {
          // Ensure start <= end
          const start = dateA.getTime() <= dateB.getTime() ? dateA : dateB;
          const end = dateA.getTime() <= dateB.getTime() ? dateB : dateA;

          const result = validateDateRange(start.toISOString(), end.toISOString());

          expect(result.valid).toBe(true);
          expect(result.error).toBeUndefined();
        }
      ),
      { numRuns: 100 }
    );
  });

  it('Property 3.3: equal dates are always valid', () => {
    fc.assert(
      fc.property(
        fc.date({ min: new Date(2000, 0, 1), max: new Date(2050, 11, 31) }),
        (date) => {
          const isoString = date.toISOString();
          const result = validateDateRange(isoString, isoString);

          expect(result.valid).toBe(true);
          expect(result.error).toBeUndefined();
        }
      ),
      { numRuns: 100 }
    );
  });
});
