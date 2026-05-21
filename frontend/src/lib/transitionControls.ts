/**
 * Utility functions for managing interactive control states during animations.
 * Encapsulates the logic used in SessionHostPage for disabling controls
 * during cross-fade and staggered animations.
 */

import { ANIMATION_TIMING } from './constants';

/** Possible session view states */
export type SessionViewState = 'LOBBY' | 'QUESTION_OPEN' | 'QUESTION_CLOSED' | 'REVEAL' | 'PAUSED' | 'ENDED';

/** Represents the transition state of the UI */
export interface TransitionState {
  isTransitioning: boolean;
  prefersReducedMotion: boolean;
}

/** Represents an interactive control element */
export interface InteractiveControl {
  type: 'button' | 'link' | 'input';
  id: string;
  disabled: boolean;
  ariaDisabled: boolean;
  pointerEvents: 'auto' | 'none';
}

/**
 * Determines whether controls should be disabled based on transition state.
 * When isTransitioning is true, all interactive controls must be disabled.
 * When prefersReducedMotion is true, isTransitioning should never be true.
 */
export function shouldDisableControls(state: TransitionState): boolean {
  return state.isTransitioning;
}

/**
 * Determines whether a transition should start based on reduced motion preference.
 * When prefersReducedMotion is true, transitions are skipped (never set isTransitioning to true).
 */
export function shouldStartTransition(prefersReducedMotion: boolean): boolean {
  return !prefersReducedMotion;
}

/**
 * Gets the cross-fade duration in milliseconds based on reduced motion preference.
 * Returns 0 when reduced motion is preferred (instant transition).
 */
export function getCrossFadeDurationMs(prefersReducedMotion: boolean): number {
  return prefersReducedMotion ? 0 : ANIMATION_TIMING.crossFade;
}

/**
 * Computes the disabled state for all interactive controls in a transitioning view.
 * Returns an array of control states reflecting whether each control is disabled.
 */
export function getControlStates(
  controls: Array<{ type: 'button' | 'link' | 'input'; id: string }>,
  transitionState: TransitionState
): InteractiveControl[] {
  const disabled = shouldDisableControls(transitionState);
  return controls.map((control) => ({
    ...control,
    disabled,
    ariaDisabled: disabled,
    pointerEvents: disabled ? 'none' as const : 'auto' as const,
  }));
}

/**
 * Simulates the transition lifecycle for a view state change.
 * Returns the sequence of isTransitioning values during the lifecycle.
 *
 * Lifecycle:
 * 1. onAnimationStart → isTransitioning = true (if !prefersReducedMotion)
 * 2. Animation plays for crossFade duration
 * 3. onAnimationComplete → isTransitioning = false
 */
export function simulateTransitionLifecycle(prefersReducedMotion: boolean): {
  duringAnimation: boolean;
  afterAnimation: boolean;
} {
  const duringAnimation = shouldStartTransition(prefersReducedMotion);
  const afterAnimation = false; // Always false after animation completes
  return { duringAnimation, afterAnimation };
}
