/**
 * Validates that a date range is logically valid (start date is not after end date).
 *
 * @param startDate - ISO date string for the range start
 * @param endDate - ISO date string for the range end
 * @returns An object with `valid: true` if the range is acceptable,
 *          or `valid: false` with an `error` message if start is strictly after end.
 */
export function validateDateRange(
  startDate: string,
  endDate: string
): { valid: boolean; error?: string } {
  const start = new Date(startDate);
  const end = new Date(endDate);

  if (start.getTime() > end.getTime()) {
    return {
      valid: false,
      error: 'Start date must not be after end date',
    };
  }

  return { valid: true };
}
