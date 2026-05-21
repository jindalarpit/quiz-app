import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';
import { formatDateBadge } from '../formatDateBadge';

// Feature: quiz-enhancements-ui, Property 12: Date badge formatting matches expected pattern

/**
 * Property-based tests for date badge formatting.
 *
 * **Validates: Requirements 8.3**
 *
 * For any valid ISO date string, the formatted date badge SHALL produce output
 * matching the pattern "MMM DD, YYYY" (e.g., "Jan 05, 2024", "Dec 31, 2023")
 * where MMM is a 3-letter month abbreviation, DD is a zero-padded day, and YYYY
 * is a 4-digit year.
 */

const VALID_MONTHS = [
  'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
  'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec',
];

// Regex pattern for "MMM DD, YYYY" format
const DATE_BADGE_PATTERN = /^(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec) \d{2}, \d{4}$/;

// Generator for dates within the specified range
const dateArb = fc.date({
  min: new Date(2020, 0, 1),
  max: new Date(2030, 11, 31),
});

describe('Date Badge Formatting Properties (Property 12)', () => {
  it('Property 12.1: formatted output matches "MMM DD, YYYY" regex pattern', () => {
    fc.assert(
      fc.property(dateArb, (date) => {
        const isoDate = date.toISOString();
        const result = formatDateBadge(isoDate);

        expect(result).toMatch(DATE_BADGE_PATTERN);
      }),
      { numRuns: 100 }
    );
  });

  it('Property 12.2: month abbreviation is a valid 3-letter month', () => {
    fc.assert(
      fc.property(dateArb, (date) => {
        const isoDate = date.toISOString();
        const result = formatDateBadge(isoDate);
        const month = result.split(' ')[0];

        expect(VALID_MONTHS).toContain(month);
      }),
      { numRuns: 100 }
    );
  });

  it('Property 12.3: day is zero-padded and between 01 and 31', () => {
    fc.assert(
      fc.property(dateArb, (date) => {
        const isoDate = date.toISOString();
        const result = formatDateBadge(isoDate);
        // Extract day from "MMM DD, YYYY"
        const day = result.split(' ')[1].replace(',', '');

        expect(day).toHaveLength(2);
        const dayNum = parseInt(day, 10);
        expect(dayNum).toBeGreaterThanOrEqual(1);
        expect(dayNum).toBeLessThanOrEqual(31);
      }),
      { numRuns: 100 }
    );
  });

  it('Property 12.4: year is a 4-digit number matching the UTC year of the input date', () => {
    fc.assert(
      fc.property(dateArb, (date) => {
        const isoDate = date.toISOString();
        const result = formatDateBadge(isoDate);
        // Extract year from "MMM DD, YYYY"
        const year = result.split(' ')[2];

        expect(year).toHaveLength(4);
        const yearNum = parseInt(year, 10);
        expect(yearNum).toBe(date.getUTCFullYear());
      }),
      { numRuns: 100 }
    );
  });

  it('Property 12.5: formatted components correspond to the input date UTC values', () => {
    fc.assert(
      fc.property(dateArb, (date) => {
        const isoDate = date.toISOString();
        const result = formatDateBadge(isoDate);

        const expectedMonth = VALID_MONTHS[date.getUTCMonth()];
        const expectedDay = String(date.getUTCDate()).padStart(2, '0');
        const expectedYear = String(date.getUTCFullYear());

        const expected = `${expectedMonth} ${expectedDay}, ${expectedYear}`;
        expect(result).toBe(expected);
      }),
      { numRuns: 100 }
    );
  });
});
