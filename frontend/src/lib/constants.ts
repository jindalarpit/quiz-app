/**
 * Shared animation timing constants (in milliseconds).
 * Used across dashboard, editor, join, session host, and results pages
 * to ensure consistent motion design.
 */
export const ANIMATION_TIMING = {
  /** Delay between consecutive staggered items (e.g., quiz cards, leaderboard rows) */
  staggerDelay: 75,
  /** Duration for card fade-in entrance animations */
  cardEntrance: 300,
  /** Duration for modal backdrop fade-in */
  modalBackdrop: 200,
  /** Duration for modal content scale-up entrance */
  modalContent: 250,
  /** Duration for cross-fade state transitions (e.g., session host views) */
  crossFade: 300,
  /** Duration for score counter count-up animation on podium */
  scoreCountUp: 1000,
  /** Delay between consecutive leaderboard row entrances */
  rowStagger: 50,
} as const;
