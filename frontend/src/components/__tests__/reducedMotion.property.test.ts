// Feature: quiz-enhancements-ui, Property 8: Reduced motion disables all entrance animations
import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';

import {
  getAllAnimationConfigs,
  getContainerVariants,
  getCardVariants,
  getModalBackdropConfig,
  getModalContentConfig,
  getFadeInUpConfig,
  getErrorSlideInConfig,
  getCrossFadeConfig,
} from '@/lib/animationVariants';

/**
 * **Validates: Requirements 5.7, 7.6**
 *
 * Property 8: Reduced motion disables all entrance animations
 * For any page component that uses animations, when `useReducedMotion()` returns `true`,
 * all framer-motion animation props SHALL resolve to their final state immediately
 * (duration: 0 or animation: "none"), and no CSS transition classes SHALL be applied.
 */

// Generator for page component animation configs
const animationConfigNameArb = fc.constantFrom(
  'container',
  'card',
  'modalBackdrop',
  'modalContent',
  'fadeInUp',
  'errorSlideIn',
  'crossFade'
);

describe('Reduced Motion Properties (Property 8)', () => {
  it('P8.1: When reduced motion is preferred, all animation durations are 0', () => {
    fc.assert(
      fc.property(animationConfigNameArb, (configName) => {
        const configs = getAllAnimationConfigs(true);
        const config = configs[configName as keyof typeof configs];

        // Check transition duration is 0
        if ('visible' in config && 'transition' in (config as { visible: { transition?: { duration?: number } } }).visible) {
          const visible = (config as { visible: { transition: { duration: number } } }).visible;
          expect(visible.transition.duration).toBe(0);
        }
        if ('transition' in config) {
          const motionConfig = config as { transition: { duration: number } };
          expect(motionConfig.transition.duration).toBe(0);
        }
      }),
      { numRuns: 100 }
    );
  });

  it('P8.2: When reduced motion is preferred, initial state matches animate state for all configs', () => {
    fc.assert(
      fc.property(animationConfigNameArb, (configName) => {
        const configs = getAllAnimationConfigs(true);
        const config = configs[configName as keyof typeof configs];

        if ('initial' in config && 'animate' in config) {
          const motionConfig = config as { initial: Record<string, unknown>; animate: Record<string, unknown> };
          // For reduced motion, initial should match animate (no visible change)
          for (const key of Object.keys(motionConfig.animate)) {
            if (key !== 'transition') {
              expect(motionConfig.initial[key]).toBe(motionConfig.animate[key]);
            }
          }
        }

        if ('hidden' in config && 'visible' in config) {
          const variantConfig = config as { hidden: Record<string, unknown>; visible: Record<string, unknown> };
          // For card variants, hidden should match visible (no visible change)
          for (const key of Object.keys(variantConfig.visible)) {
            if (key !== 'transition') {
              expect(variantConfig.hidden[key]).toBe(variantConfig.visible[key]);
            }
          }
        }
      }),
      { numRuns: 100 }
    );
  });

  it('P8.3: When reduced motion is preferred, stagger delay is 0 for container variants', () => {
    fc.assert(
      fc.property(fc.constant(true), (reducedMotion) => {
        const container = getContainerVariants(reducedMotion);
        expect(container.visible.transition!.staggerChildren).toBe(0);
      }),
      { numRuns: 100 }
    );
  });

  it('P8.4: When reduced motion is NOT preferred, animations have non-zero durations', () => {
    fc.assert(
      fc.property(animationConfigNameArb, (configName) => {
        const configs = getAllAnimationConfigs(false);
        const config = configs[configName as keyof typeof configs];

        if ('visible' in config && 'transition' in (config as { visible: { transition?: { duration?: number } } }).visible) {
          const visible = (config as { visible: { transition: { duration: number } } }).visible;
          expect(visible.transition.duration).toBeGreaterThan(0);
        }
        if ('initial' in config && 'transition' in config) {
          const motionConfig = config as { transition: { duration: number } };
          expect(motionConfig.transition.duration).toBeGreaterThan(0);
        }
      }),
      { numRuns: 100 }
    );
  });

  it('P8.5: When reduced motion is NOT preferred, initial state differs from animate state', () => {
    fc.assert(
      fc.property(animationConfigNameArb, (configName) => {
        const configs = getAllAnimationConfigs(false);
        const config = configs[configName as keyof typeof configs];

        if ('initial' in config && 'animate' in config) {
          const motionConfig = config as { initial: Record<string, unknown>; animate: Record<string, unknown> };
          // At least one property should differ between initial and animate
          const keys = Object.keys(motionConfig.initial);
          const hasDifference = keys.some(
            (key) => motionConfig.initial[key] !== motionConfig.animate[key]
          );
          expect(hasDifference).toBe(true);
        }

        if ('hidden' in config && 'visible' in config) {
          const variantConfig = config as { hidden: Record<string, unknown>; visible: Record<string, unknown> };
          const keys = Object.keys(variantConfig.hidden);
          if (keys.length > 0) {
            const hasDifference = keys.some(
              (key) => variantConfig.hidden[key] !== variantConfig.visible[key]
            );
            expect(hasDifference).toBe(true);
          }
        }
      }),
      { numRuns: 100 }
    );
  });

  it('P8.6: Card variants with reduced motion have opacity=1 and y=0 in hidden state', () => {
    fc.assert(
      fc.property(fc.constant(true), (reducedMotion) => {
        const card = getCardVariants(reducedMotion);
        expect(card.hidden.opacity).toBe(1);
        expect(card.hidden.y).toBe(0);
        expect(card.visible.opacity).toBe(1);
        expect(card.visible.y).toBe(0);
        expect(card.visible.transition.duration).toBe(0);
      }),
      { numRuns: 100 }
    );
  });

  it('P8.7: Modal configs with reduced motion show final state immediately', () => {
    fc.assert(
      fc.property(fc.constant(true), (reducedMotion) => {
        const backdrop = getModalBackdropConfig(reducedMotion);
        const content = getModalContentConfig(reducedMotion);

        // Backdrop: opacity should be 1 in both initial and animate
        expect(backdrop.initial.opacity).toBe(1);
        expect(backdrop.animate.opacity).toBe(1);
        expect(backdrop.transition.duration).toBe(0);

        // Content: scale should be 1 and opacity 1 in both initial and animate
        expect(content.initial.scale).toBe(1);
        expect(content.initial.opacity).toBe(1);
        expect(content.animate.scale).toBe(1);
        expect(content.animate.opacity).toBe(1);
        expect(content.transition.duration).toBe(0);
      }),
      { numRuns: 100 }
    );
  });

  it('P8.8: FadeInUp config with reduced motion shows final state immediately', () => {
    fc.assert(
      fc.property(fc.constant(true), (reducedMotion) => {
        const fadeInUp = getFadeInUpConfig(reducedMotion);

        expect(fadeInUp.initial.opacity).toBe(1);
        expect(fadeInUp.initial.y).toBe(0);
        expect(fadeInUp.animate.opacity).toBe(1);
        expect(fadeInUp.animate.y).toBe(0);
        expect(fadeInUp.transition.duration).toBe(0);
      }),
      { numRuns: 100 }
    );
  });

  it('P8.9: Error slide-in config with reduced motion shows final state immediately', () => {
    fc.assert(
      fc.property(fc.constant(true), (reducedMotion) => {
        const errorSlideIn = getErrorSlideInConfig(reducedMotion);

        expect(errorSlideIn.initial.opacity).toBe(1);
        expect(errorSlideIn.initial.x).toBe(0);
        expect(errorSlideIn.animate.opacity).toBe(1);
        expect(errorSlideIn.animate.x).toBe(0);
        expect(errorSlideIn.transition.duration).toBe(0);
      }),
      { numRuns: 100 }
    );
  });

  it('P8.10: Cross-fade config with reduced motion shows final state immediately', () => {
    fc.assert(
      fc.property(fc.constant(true), (reducedMotion) => {
        const crossFade = getCrossFadeConfig(reducedMotion);

        expect(crossFade.initial.opacity).toBe(1);
        expect(crossFade.animate.opacity).toBe(1);
        expect(crossFade.transition.duration).toBe(0);
      }),
      { numRuns: 100 }
    );
  });
});
