'use client';

import React, { type KeyboardEvent, type ReactNode, useCallback } from 'react';
import { motion } from 'framer-motion';
import Link from 'next/link';
import { useRouter } from 'next/navigation';

import { useReducedMotion } from '@/hooks/useReducedMotion';
import { useAuthStore } from '@/stores/authStore';

export interface FeatureTileProps {
  icon: ReactNode;
  title: string;
  description: string;
  href: string;
  authRequired?: boolean;
  gradientFrom: string;
  gradientTo: string;
}

/**
 * Resolves the navigation target based on auth state.
 * If authRequired and user is unauthenticated, redirects to /login.
 */
export function resolveHref(
  href: string,
  authRequired: boolean,
  isAuthenticated: boolean
): string {
  if (authRequired && !isAuthenticated) {
    return '/login';
  }
  return href;
}

/**
 * Interactive feature tile component for the home page.
 * Supports auth-aware navigation, hover animations, keyboard accessibility,
 * and respects reduced motion preferences.
 */
export function FeatureTile({
  icon,
  title,
  description,
  href,
  authRequired = false,
  gradientFrom,
  gradientTo,
}: FeatureTileProps) {
  const { user } = useAuthStore();
  const router = useRouter();
  const prefersReducedMotion = useReducedMotion();

  const isAuthenticated = !!user;
  const resolvedHref = resolveHref(href, authRequired, isAuthenticated);

  const handleKeyDown = useCallback(
    (e: KeyboardEvent<HTMLAnchorElement>) => {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        router.push(resolvedHref);
      }
    },
    [router, resolvedHref]
  );

  const hoverAnimation = prefersReducedMotion
    ? {}
    : { scale: 1.02, borderColor: '#38bdf8' };

  const transition = prefersReducedMotion
    ? { duration: 0 }
    : { duration: 0.2, ease: 'easeOut' };

  return (
    <motion.div
      whileHover={hoverAnimation}
      transition={transition}
      className="rounded-2xl border border-slate-200 bg-white shadow-sm dark:border-slate-700 dark:bg-slate-800"
    >
      <Link
        href={resolvedHref}
        onKeyDown={handleKeyDown}
        className="block h-full p-6 outline-none focus-visible:ring-2 focus-visible:ring-primary-500 focus-visible:ring-offset-2 rounded-2xl"
        aria-label={`${title} - ${description}`}
      >
        <div
          className={`mb-4 flex h-12 w-12 items-center justify-center rounded-xl bg-gradient-to-br text-white shadow-lg`}
          style={{
            backgroundImage: `linear-gradient(to bottom right, ${gradientFrom}, ${gradientTo})`,
          }}
        >
          {icon}
        </div>
        <h3 className="mb-2 text-lg font-semibold text-slate-900 dark:text-white">
          {title}
        </h3>
        <p className="text-sm leading-relaxed text-slate-600 dark:text-slate-400">
          {description}
        </p>
      </Link>
    </motion.div>
  );
}
