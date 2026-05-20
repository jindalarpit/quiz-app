import type { LeaderboardUpdateEntry } from '@/types';

/**
 * Builds the host leaderboard view: top min(5, P) entries ordered by rank ascending.
 */
export function buildHostView(entries: LeaderboardUpdateEntry[]): LeaderboardUpdateEntry[] {
  const sorted = [...entries].sort((a, b) => a.rank - b.rank);
  return sorted.slice(0, Math.min(5, sorted.length));
}

/**
 * Builds a personalized participant leaderboard view for WebSocket payload filtering.
 *
 * Rules (from design Property 8):
 * - Always includes the top-5 entries (or all if fewer than 5 participants)
 * - If the participant is ranked below 5th, also includes:
 *   - The participant's own entry
 *   - One entry above (if rank > 6, i.e., there's a gap between top-5 and participant)
 *   - One entry below (if participant is not last)
 * - Total entries never exceed 8
 */
export function buildParticipantView(
  allEntries: LeaderboardUpdateEntry[],
  participantId: string
): LeaderboardUpdateEntry[] {
  if (allEntries.length === 0) return [];

  const sorted = [...allEntries].sort((a, b) => a.rank - b.rank);
  const totalParticipants = sorted.length;

  // Top 5 (or all if fewer than 5)
  const top5 = sorted.slice(0, Math.min(5, totalParticipants));

  // Find the participant's entry
  const participantEntry = sorted.find((e) => e.participantId === participantId);

  // If participant not found or already in top 5, just return top 5
  if (!participantEntry || participantEntry.rank <= 5) {
    return top5;
  }

  // Participant is ranked below 5th — add context entries
  const result = [...top5];
  const participantRank = participantEntry.rank;

  // Add one entry above if there's a gap (rank > 6 means there's someone between top-5 and participant)
  if (participantRank > 6) {
    const aboveEntry = sorted.find((e) => e.rank === participantRank - 1);
    if (aboveEntry) {
      result.push(aboveEntry);
    }
  }

  // Add the participant's own entry
  result.push(participantEntry);

  // Add one entry below if participant is not last
  if (participantRank < totalParticipants) {
    const belowEntry = sorted.find((e) => e.rank === participantRank + 1);
    if (belowEntry) {
      result.push(belowEntry);
    }
  }

  return result;
}

/**
 * Builds the frontend participant leaderboard display view (Requirement 3.6).
 *
 * Shows the participant's own rank plus up to 2 participants immediately above
 * and up to 2 participants immediately below, displaying fewer context rows
 * when the participant's rank is within 2 positions of the first or last rank.
 *
 * The entries are returned sorted by rank ascending.
 */
export function buildParticipantDisplayView(
  allEntries: LeaderboardUpdateEntry[],
  participantId: string
): LeaderboardUpdateEntry[] {
  if (allEntries.length === 0) return [];

  const sorted = [...allEntries].sort((a, b) => a.rank - b.rank);
  const totalParticipants = sorted.length;

  // Find the participant's entry
  const participantEntry = sorted.find((e) => e.participantId === participantId);

  // If participant not found, return all entries (fallback)
  if (!participantEntry) {
    return sorted;
  }

  const participantRank = participantEntry.rank;

  // Collect entries: own rank + up to 2 above + up to 2 below
  const resultSet = new Set<string>();
  const result: LeaderboardUpdateEntry[] = [];

  // Add up to 2 entries above (fewer when near top boundary)
  for (let r = Math.max(1, participantRank - 2); r < participantRank; r++) {
    const entry = sorted.find((e) => e.rank === r);
    if (entry && !resultSet.has(entry.participantId)) {
      resultSet.add(entry.participantId);
      result.push(entry);
    }
  }

  // Add the participant's own entry
  if (!resultSet.has(participantEntry.participantId)) {
    resultSet.add(participantEntry.participantId);
    result.push(participantEntry);
  }

  // Add up to 2 entries below (fewer when near bottom boundary)
  for (let r = participantRank + 1; r <= Math.min(totalParticipants, participantRank + 2); r++) {
    const entry = sorted.find((e) => e.rank === r);
    if (entry && !resultSet.has(entry.participantId)) {
      resultSet.add(entry.participantId);
      result.push(entry);
    }
  }

  // Sort by rank ascending
  result.sort((a, b) => a.rank - b.rank);

  return result;
}
