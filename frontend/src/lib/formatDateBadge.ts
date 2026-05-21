/**
 * Formats an ISO date string to "MMM DD, YYYY" pattern.
 * Examples: "Jan 05, 2024", "Dec 31, 2023"
 *
 * @param isoDate - An ISO 8601 date string (e.g., "2024-01-05T10:30:00Z")
 * @returns Formatted date string in "MMM DD, YYYY" format
 */
export function formatDateBadge(isoDate: string): string {
  const date = new Date(isoDate);

  const months = [
    'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
    'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec',
  ];

  const month = months[date.getUTCMonth()];
  const day = String(date.getUTCDate()).padStart(2, '0');
  const year = date.getUTCFullYear();

  return `${month} ${day}, ${year}`;
}
