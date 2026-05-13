'use client';

import { type Transition, type Variants, AnimatePresence, motion } from 'framer-motion';
import { type ReactNode } from 'react';

import { useReducedMotion } from '@/hooks/useReducedMotion';

type AnimationVariant = 'slideInRight' | 'slideInUp' | 'fadeIn' | 'pulse' | 'scaleIn';

interface AnimatedContainerProps {
  children: ReactNode;
  variant?: AnimationVariant;
  /** Duration in seconds. Default: 0.3 */
  duration?: number;
  /** Delay in seconds before animation starts */
  delay?: number;
  className?: string;
}

const animationVariants: Record<AnimationVariant, Variants> = {
  slideInRight: {
    initial: { opacity: 0, x: 40 },
    animate: { opacity: 1, x: 0 },
    exit: { opacity: 0, x: -40 },
  },
  slideInUp: {
    initial: { opacity: 0, y: 20 },
    animate: { opacity: 1, y: 0 },
    exit: { opacity: 0, y: -20 },
  },
  fadeIn: {
    initial: { opacity: 0 },
    animate: { opacity: 1 },
    exit: { opacity: 0 },
  },
  pulse: {
    initial: { scale: 1 },
    animate: { scale: [1, 1.05, 1] },
    exit: { scale: 1 },
  },
  scaleIn: {
    initial: { opacity: 0, scale: 0.9 },
    animate: { opacity: 1, scale: 1 },
    exit: { opacity: 0, scale: 0.9 },
  },
};

/**
 * Animated wrapper component using Framer Motion.
 * Automatically disables animations when prefers-reduced-motion is active.
 *
 * Variants:
 * - slideInRight: Question reveal animation (300ms)
 * - slideInUp: General content reveal
 * - fadeIn: Subtle appearance
 * - pulse: Answer feedback animation on selection
 * - scaleIn: Scale-based entrance
 */
export function AnimatedContainer({
  children,
  variant = 'fadeIn',
  duration = 0.3,
  delay = 0,
  className,
}: AnimatedContainerProps) {
  const prefersReducedMotion = useReducedMotion();

  const transitionDuration = prefersReducedMotion ? 0 : duration;

  const transition: Transition = {
    duration: transitionDuration,
    delay: prefersReducedMotion ? 0 : delay,
    ease: 'easeOut',
  };

  return (
    <motion.div
      variants={animationVariants[variant]}
      initial={prefersReducedMotion ? false : 'initial'}
      animate="animate"
      exit={prefersReducedMotion ? undefined : 'exit'}
      transition={transition}
      className={className}
    >
      {children}
    </motion.div>
  );
}

/**
 * Wrapper for AnimatePresence to handle exit animations.
 * Use this to wrap content that conditionally renders AnimatedContainers.
 */
export function AnimatedPresence({ children }: { children: ReactNode }) {
  return <AnimatePresence mode="wait">{children}</AnimatePresence>;
}
