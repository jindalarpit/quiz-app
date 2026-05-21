// Feature: quiz-enhancements-ui, Property 13: Interactive controls disabled during animations
import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';

import {
  shouldDisableControls,
  shouldStartTransition,
  getCrossFadeDurationMs,
  getControlStates,
  simulateTransitionLifecycle,
  type TransitionState,
  type SessionViewState,
} from '@/lib/transitionControls';
import { ANIMATION_TIMING } from '@/lib/constants';

/**
 * **Validates: Requirements 8.6**
 *
 * Property 13: Interactive controls disabled during animations
 * For any view undergoing a cross-fade or staggered animation, all interactive elements
 * (buttons, links, inputs) within the transitioning container SHALL have their `disabled`
 * attribute set to `true` or `pointer-events: none` applied until the animation completes.
 */

// Generators for transition states
const transitionStateArb: fc.Arbitrary<TransitionState> = fc.record({
  isTransitioning: fc.boolean(),
  prefersReducedMotion: fc.boolean(),
});

// Generator for session view states (used to simulate state changes)
const sessionViewStateArb: fc.Arbitrary<SessionViewState> = fc.constantFrom(
  'LOBBY',
  'QUESTION_OPEN',
  'QUESTION_CLOSED',
  'REVEAL',
  'PAUSED',
  'ENDED'
);

// Generator for interactive control types
const controlTypeArb = fc.constantFrom('button' as const, 'link' as const, 'input' as const);

// Generator for a list of interactive controls (simulating buttons/links/inputs in a view)
const controlListArb = fc.array(
  fc.record({
    type: controlTypeArb,
    id: fc.string({ minLength: 1, maxLength: 20 }),
  }),
  { minLength: 1, maxLength: 10 }
);

describe('Animation Disable Properties (Property 13)', () => {
  it('P13.1: When isTransitioning is true, all interactive controls are disabled', () => {
    fc.assert(
      fc.property(controlListArb, (controls) => {
        const transitionState: TransitionState = {
          isTransitioning: true,
          prefersReducedMotion: false,
        };

        const controlStates = getControlStates(controls, transitionState);

        // Every control must be disabled during transition
        for (const control of controlStates) {
          expect(control.disabled).toBe(true);
          expect(control.ariaDisabled).toBe(true);
          expect(control.pointerEvents).toBe('none');
        }
      }),
      { numRuns: 100 }
    );
  });

  it('P13.2: When isTransitioning is false, no controls are disabled by transition logic', () => {
    fc.assert(
      fc.property(controlListArb, (controls) => {
        const transitionState: TransitionState = {
          isTransitioning: false,
          prefersReducedMotion: false,
        };

        const controlStates = getControlStates(controls, transitionState);

        // No control should be disabled when not transitioning
        for (const control of controlStates) {
          expect(control.disabled).toBe(false);
          expect(control.ariaDisabled).toBe(false);
          expect(control.pointerEvents).toBe('auto');
        }
      }),
      { numRuns: 100 }
    );
  });

  it('P13.3: When prefersReducedMotion is true, transitions never start (controls never disabled by animation)', () => {
    fc.assert(
      fc.property(sessionViewStateArb, sessionViewStateArb, (fromState, toState) => {
        const prefersReducedMotion = true;

        // Simulate what happens when a state change occurs with reduced motion
        const lifecycle = simulateTransitionLifecycle(prefersReducedMotion);

        // During animation phase, isTransitioning should be false (transition never starts)
        expect(lifecycle.duringAnimation).toBe(false);
        // After animation, isTransitioning should also be false
        expect(lifecycle.afterAnimation).toBe(false);
      }),
      { numRuns: 100 }
    );
  });

  it('P13.4: When prefersReducedMotion is false, transitions DO start on state change', () => {
    fc.assert(
      fc.property(sessionViewStateArb, sessionViewStateArb, (fromState, toState) => {
        const prefersReducedMotion = false;

        const lifecycle = simulateTransitionLifecycle(prefersReducedMotion);

        // During animation phase, isTransitioning should be true
        expect(lifecycle.duringAnimation).toBe(true);
        // After animation completes, isTransitioning should be false
        expect(lifecycle.afterAnimation).toBe(false);
      }),
      { numRuns: 100 }
    );
  });

  it('P13.5: Cross-fade duration is 0 when reduced motion is preferred, non-zero otherwise', () => {
    fc.assert(
      fc.property(fc.boolean(), (prefersReducedMotion) => {
        const duration = getCrossFadeDurationMs(prefersReducedMotion);

        if (prefersReducedMotion) {
          expect(duration).toBe(0);
        } else {
          expect(duration).toBe(ANIMATION_TIMING.crossFade);
          expect(duration).toBeGreaterThan(0);
        }
      }),
      { numRuns: 100 }
    );
  });

  it('P13.6: All control types (button, link, input) are uniformly disabled during transitions', () => {
    fc.assert(
      fc.property(transitionStateArb, (transitionState) => {
        // Create one of each control type
        const controls = [
          { type: 'button' as const, id: 'start-quiz' },
          { type: 'link' as const, id: 'nav-link' },
          { type: 'input' as const, id: 'text-input' },
        ];

        const controlStates = getControlStates(controls, transitionState);
        const expectedDisabled = shouldDisableControls(transitionState);

        // All controls must have the same disabled state regardless of type
        for (const control of controlStates) {
          expect(control.disabled).toBe(expectedDisabled);
          expect(control.ariaDisabled).toBe(expectedDisabled);
          expect(control.pointerEvents).toBe(expectedDisabled ? 'none' : 'auto');
        }
      }),
      { numRuns: 100 }
    );
  });

  it('P13.7: shouldDisableControls is consistent with isTransitioning flag', () => {
    fc.assert(
      fc.property(transitionStateArb, (state) => {
        const result = shouldDisableControls(state);
        // The disabled state must exactly match the isTransitioning flag
        expect(result).toBe(state.isTransitioning);
      }),
      { numRuns: 100 }
    );
  });

  it('P13.8: Controls are re-enabled after animation completes (lifecycle invariant)', () => {
    fc.assert(
      fc.property(fc.boolean(), (prefersReducedMotion) => {
        const lifecycle = simulateTransitionLifecycle(prefersReducedMotion);

        // After animation completes, controls must always be re-enabled
        const postAnimationState: TransitionState = {
          isTransitioning: lifecycle.afterAnimation,
          prefersReducedMotion,
        };

        expect(shouldDisableControls(postAnimationState)).toBe(false);
      }),
      { numRuns: 100 }
    );
  });
});
