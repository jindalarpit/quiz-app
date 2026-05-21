/**
 * Converts a duration in seconds to a human-readable format.
 *
 * Examples:
 * - 4980 → "1h 23m"
 * - 300 → "5m"
 * - 45 → "0m 45s"
 * - 3600 → "1h 0m"
 * - 0 → "0m 0s"
 *
 * @param seconds - Duration in seconds (0 to 86400)
 * @returns Formatted duration string
 */
export function formatDuration(seconds: number): string {
  const totalSeconds = Math.max(0, Math.floor(seconds));

  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const secs = totalSeconds % 60;

  if (hours > 0) {
    return `${hours}h ${minutes}m`;
  }

  if (minutes > 0 && secs === 0) {
    return `${minutes}m`;
  }

  return `${minutes}m ${secs}s`;
}
