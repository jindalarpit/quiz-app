import { describe, expect, it } from 'vitest';
import * as fc from 'fast-check';

import { formatDuration } from '../formatDuration';

/**
 * **Validates: Requirements 1.2**
 *
 * Property 1: Session duration formatting
 * For any durationSeconds value (0 to 86400), the formatted duration string
 * SHALL be mathematically equivalent to the input seconds.
 */
describe('formatDuration - Property 1: Session duration formatting', () => {
  it('formatted output is mathematically equivalent to input seconds', () => {
    fc.assert(
      fc.property(fc.integer({ min: 0, max: 86400 }), (seconds) => {
        const formatted = formatDuration(seconds);

        // Parse the formatted string back to seconds
        const parsedSeconds = parseFormattedDuration(formatted);

        // When hours > 0, the format is "Xh Ym" which drops seconds,
        // so we compare against the floored-to-minutes value
        const hours = Math.floor(seconds / 3600);
        if (hours > 0) {
          // Format drops remaining seconds when hours are present
          const expectedWithoutSeconds =
            Math.floor(seconds / 3600) * 3600 +
            Math.floor((seconds % 3600) / 60) * 60;
          expect(parsedSeconds).toBe(expectedWithoutSeconds);
        } else {
          // No hours: format preserves full seconds precision
          expect(parsedSeconds).toBe(seconds);
        }
      }),
      { numRuns: 100 }
    );
  });

  it('formatted output always contains valid time components', () => {
    fc.assert(
      fc.property(fc.integer({ min: 0, max: 86400 }), (seconds) => {
        const formatted = formatDuration(seconds);

        // Must match one of the valid patterns:
        // "Xh Ym" (hours present) or "Xm" (exact minutes) or "Xm Ys" (minutes and seconds)
        const hoursMinutesPattern = /^\d+h \d+m$/;
        const minutesOnlyPattern = /^\d+m$/;
        const minutesSecondsPattern = /^\d+m \d+s$/;

        const matchesPattern =
          hoursMinutesPattern.test(formatted) ||
          minutesOnlyPattern.test(formatted) ||
          minutesSecondsPattern.test(formatted);

        expect(matchesPattern).toBe(true);
      }),
      { numRuns: 100 }
    );
  });
});

/**
 * Parses a formatted duration string back to total seconds.
 * Supports formats: "Xh Ym", "Xm", "Xm Ys"
 */
function parseFormattedDuration(formatted: string): number {
  let total = 0;

  const hoursMatch = formatted.match(/(\d+)h/);
  const minutesMatch = formatted.match(/(\d+)m/);
  const secondsMatch = formatted.match(/(\d+)s/);

  if (hoursMatch) {
    total += parseInt(hoursMatch[1], 10) * 3600;
  }
  if (minutesMatch) {
    total += parseInt(minutesMatch[1], 10) * 60;
  }
  if (secondsMatch) {
    total += parseInt(secondsMatch[1], 10);
  }

  return total;
}
