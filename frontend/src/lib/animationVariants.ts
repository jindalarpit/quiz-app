import { ANIMATION_TIMING } from './constants';

/**
 * Animation variant types used across the application.
 */
export interface AnimationVariant {
  opacity?: number;
  y?: number;
  x?: number;
  scale?: number;
  height?: number | string;
}

export interface AnimationTransition {
  duration: number;
  ease?: string;
  staggerChildren?: number;
}

export interface MotionConfig {
  initial: AnimationVariant;
  animate: AnimationVariant;
  exit?: AnimationVariant;
  transition: AnimationTransition;
}

export interface ContainerVariants {
  hidden: AnimationVariant & { transition?: AnimationTransition };
  visible: AnimationVariant & { transition?: AnimationTransition };
}

export interface CardVariants {
  hidden: AnimationVariant;
  visible: AnimationVariant & { transition: AnimationTransition };
}

/**
 * Creates staggered container animation variants.
 * When prefersReducedMotion is true, stagger delay is 0.
 */
export function getContainerVariants(prefersReducedMotion: boolean): ContainerVariants {
  return {
    hidden: {},
    visible: {
      transition: {
        staggerChildren: prefersReducedMotion ? 0 : ANIMATION_TIMING.staggerDelay / 1000,
        duration: prefersReducedMotion ? 0 : ANIMATION_TIMING.cardEntrance / 1000,
      },
    },
  };
}

/**
 * Creates card entrance animation variants.
 * When prefersReducedMotion is true, initial state matches final state and duration is 0.
 */
export function getCardVariants(prefersReducedMotion: boolean): CardVariants {
  return {
    hidden: prefersReducedMotion
      ? { opacity: 1, y: 0 }
      : { opacity: 0, y: 20 },
    visible: {
      opacity: 1,
      y: 0,
      transition: prefersReducedMotion
        ? { duration: 0 }
        : { duration: ANIMATION_TIMING.cardEntrance / 1000, ease: 'easeOut' },
    },
  };
}

/**
 * Creates modal backdrop animation config.
 * When prefersReducedMotion is true, no animation is applied (duration: 0).
 */
export function getModalBackdropConfig(prefersReducedMotion: boolean): MotionConfig {
  return prefersReducedMotion
    ? {
        initial: { opacity: 1 },
        animate: { opacity: 1 },
        exit: { opacity: 1 },
        transition: { duration: 0 },
      }
    : {
        initial: { opacity: 0 },
        animate: { opacity: 1 },
        exit: { opacity: 0 },
        transition: { duration: ANIMATION_TIMING.modalBackdrop / 1000 },
      };
}

/**
 * Creates modal content animation config.
 * When prefersReducedMotion is true, no animation is applied (duration: 0).
 */
export function getModalContentConfig(prefersReducedMotion: boolean): MotionConfig {
  return prefersReducedMotion
    ? {
        initial: { scale: 1, opacity: 1 },
        animate: { scale: 1, opacity: 1 },
        exit: { scale: 1, opacity: 1 },
        transition: { duration: 0 },
      }
    : {
        initial: { scale: 0.95, opacity: 0 },
        animate: { scale: 1, opacity: 1 },
        exit: { scale: 0.95, opacity: 0 },
        transition: { duration: ANIMATION_TIMING.modalContent / 1000, ease: 'easeOut' },
      };
}

/**
 * Creates fade-in-up entrance animation config (used on JoinPage, etc.).
 * When prefersReducedMotion is true, no animation is applied (duration: 0).
 */
export function getFadeInUpConfig(prefersReducedMotion: boolean): MotionConfig {
  return prefersReducedMotion
    ? {
        initial: { opacity: 1, y: 0 },
        animate: { opacity: 1, y: 0 },
        transition: { duration: 0 },
      }
    : {
        initial: { opacity: 0, y: 20 },
        animate: { opacity: 1, y: 0 },
        transition: { duration: 0.4, ease: 'easeOut' },
      };
}

/**
 * Creates error slide-in animation config.
 * When prefersReducedMotion is true, no animation is applied (duration: 0).
 */
export function getErrorSlideInConfig(prefersReducedMotion: boolean): MotionConfig {
  return prefersReducedMotion
    ? {
        initial: { opacity: 1, x: 0 },
        animate: { opacity: 1, x: 0 },
        exit: { opacity: 1, x: 0 },
        transition: { duration: 0 },
      }
    : {
        initial: { opacity: 0, x: -20, height: 0 },
        animate: { opacity: 1, x: 0, height: 'auto' as unknown as number },
        exit: { opacity: 0, x: -20, height: 0 },
        transition: { duration: 0.3, ease: 'easeOut' },
      };
}

/**
 * Creates cross-fade transition config (used on SessionHostPage).
 * When prefersReducedMotion is true, no animation is applied (duration: 0).
 */
export function getCrossFadeConfig(prefersReducedMotion: boolean): MotionConfig {
  return prefersReducedMotion
    ? {
        initial: { opacity: 1 },
        animate: { opacity: 1 },
        exit: { opacity: 1 },
        transition: { duration: 0 },
      }
    : {
        initial: { opacity: 0 },
        animate: { opacity: 1 },
        exit: { opacity: 0 },
        transition: { duration: ANIMATION_TIMING.crossFade / 1000 },
      };
}

/**
 * Returns all animation configs for a given reduced motion preference.
 * Useful for testing that all animations respect the preference.
 */
export function getAllAnimationConfigs(prefersReducedMotion: boolean) {
  return {
    container: getContainerVariants(prefersReducedMotion),
    card: getCardVariants(prefersReducedMotion),
    modalBackdrop: getModalBackdropConfig(prefersReducedMotion),
    modalContent: getModalContentConfig(prefersReducedMotion),
    fadeInUp: getFadeInUpConfig(prefersReducedMotion),
    errorSlideIn: getErrorSlideInConfig(prefersReducedMotion),
    crossFade: getCrossFadeConfig(prefersReducedMotion),
  };
}
