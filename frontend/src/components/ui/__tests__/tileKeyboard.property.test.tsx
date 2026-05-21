// Feature: quiz-enhancements-ui, Property 7: Feature tiles are keyboard accessible
/**
 * @vitest-environment jsdom
 */
import React from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, fireEvent, cleanup } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import * as fc from 'fast-check';

/**
 * Property-based tests for keyboard accessibility (Property 7).
 *
 * **Validates: Requirements 4.10**
 *
 * Property 7: Feature tiles are keyboard accessible — For any feature tile,
 * the element SHALL be focusable (tabIndex ≥ 0 or native focusable element)
 * and SHALL trigger navigation when activated via Enter or Space key events.
 */

// Mock next/navigation
const mockPush = vi.fn();
vi.mock('next/navigation', () => ({
  useRouter: () => ({
    push: mockPush,
    prefetch: vi.fn(),
    back: vi.fn(),
    forward: vi.fn(),
    refresh: vi.fn(),
    replace: vi.fn(),
  }),
}));

// Mock next/link to render a real <a> tag that supports keyboard events
vi.mock('next/link', () => ({
  default: React.forwardRef(function MockLink(
    { href, children, onKeyDown, className, ...rest }: any,
    ref: any
  ) {
    return (
      <a
        ref={ref}
        href={href}
        onClick={(e: React.MouseEvent) => {
          e.preventDefault();
          mockPush(href);
        }}
        onKeyDown={onKeyDown}
        className={className}
        {...rest}
      >
        {children}
      </a>
    );
  }),
}));

// Mock framer-motion to render plain divs
vi.mock('framer-motion', () => ({
  motion: {
    div: React.forwardRef(function MockMotionDiv(
      { children, whileHover, transition, ...props }: any,
      ref: any
    ) {
      return (
        <div ref={ref} {...props}>
          {children}
        </div>
      );
    }),
  },
  AnimatePresence: ({ children }: any) => <>{children}</>,
}));

// Mock useReducedMotion hook
vi.mock('@/hooks/useReducedMotion', () => ({
  useReducedMotion: () => false,
}));

// Mock auth store - control via variable
let mockUser: { id: string; username: string } | null = { id: '1', username: 'testuser' };
vi.mock('@/stores/authStore', () => ({
  useAuthStore: () => ({ user: mockUser }),
}));

import { FeatureTile, resolveHref } from '../FeatureTile';
import type { FeatureTileProps } from '../FeatureTile';

// Generator for tile configurations
const tileConfigArb = fc.record({
  title: fc.string({ minLength: 1, maxLength: 50 }),
  description: fc.string({ minLength: 1, maxLength: 100 }),
  href: fc.constantFrom('/dashboard', '/join', '/history', '/quizzes', '/settings'),
  authRequired: fc.boolean(),
  gradientFrom: fc.constantFrom('#3b82f6', '#8b5cf6', '#10b981', '#f59e0b', '#ef4444'),
  gradientTo: fc.constantFrom('#1d4ed8', '#6d28d9', '#059669', '#d97706', '#dc2626'),
});

// Generator for keyboard activation keys
const activationKeyArb = fc.constantFrom('Enter', ' ');

describe('Feature Tile Keyboard Accessibility Properties (Property 7)', () => {
  beforeEach(() => {
    mockPush.mockClear();
    mockUser = { id: '1', username: 'testuser' };
  });

  afterEach(() => {
    cleanup();
  });

  it('P7.1: All feature tiles are focusable (contain a native focusable element)', () => {
    fc.assert(
      fc.property(tileConfigArb, (config) => {
        const { container } = render(
          <FeatureTile
            icon={<span>🎯</span>}
            title={config.title}
            description={config.description}
            href={config.href}
            authRequired={config.authRequired}
            gradientFrom={config.gradientFrom}
            gradientTo={config.gradientTo}
          />
        );

        // The tile must contain a focusable element (anchor tag or element with tabIndex >= 0)
        const focusableElement = container.querySelector('a, [tabindex="0"], button');
        expect(focusableElement).not.toBeNull();

        // If it's an anchor, it should not have a negative tabIndex
        if (focusableElement?.tagName === 'A') {
          const tabIndex = focusableElement.getAttribute('tabindex');
          expect(tabIndex === null || parseInt(tabIndex) >= 0).toBe(true);
        }

        cleanup();
      }),
      { numRuns: 100 }
    );
  });

  it('P7.2: Enter key triggers navigation on feature tiles', () => {
    fc.assert(
      fc.property(tileConfigArb, (config) => {
        mockPush.mockClear();
        mockUser = { id: '1', username: 'testuser' };

        const { container } = render(
          <FeatureTile
            icon={<span>🎯</span>}
            title={config.title}
            description={config.description}
            href={config.href}
            authRequired={config.authRequired}
            gradientFrom={config.gradientFrom}
            gradientTo={config.gradientTo}
          />
        );

        const focusableElement = container.querySelector('a, [tabindex="0"], button');
        expect(focusableElement).not.toBeNull();

        // Simulate Enter key press
        fireEvent.keyDown(focusableElement!, { key: 'Enter' });

        // Navigation should be triggered
        expect(mockPush).toHaveBeenCalledTimes(1);

        cleanup();
      }),
      { numRuns: 100 }
    );
  });

  it('P7.3: Space key triggers navigation on feature tiles', () => {
    fc.assert(
      fc.property(tileConfigArb, (config) => {
        mockPush.mockClear();
        mockUser = { id: '1', username: 'testuser' };

        const { container } = render(
          <FeatureTile
            icon={<span>🎯</span>}
            title={config.title}
            description={config.description}
            href={config.href}
            authRequired={config.authRequired}
            gradientFrom={config.gradientFrom}
            gradientTo={config.gradientTo}
          />
        );

        const focusableElement = container.querySelector('a, [tabindex="0"], button');
        expect(focusableElement).not.toBeNull();

        // Simulate Space key press
        fireEvent.keyDown(focusableElement!, { key: ' ' });

        // Navigation should be triggered
        expect(mockPush).toHaveBeenCalledTimes(1);

        cleanup();
      }),
      { numRuns: 100 }
    );
  });

  it('P7.4: For any activation key (Enter or Space), navigation target matches resolved href', () => {
    fc.assert(
      fc.property(tileConfigArb, activationKeyArb, (config, key) => {
        mockPush.mockClear();
        mockUser = { id: '1', username: 'testuser' };

        const expectedHref = resolveHref(config.href, config.authRequired, true);

        const { container } = render(
          <FeatureTile
            icon={<span>🎯</span>}
            title={config.title}
            description={config.description}
            href={config.href}
            authRequired={config.authRequired}
            gradientFrom={config.gradientFrom}
            gradientTo={config.gradientTo}
          />
        );

        const focusableElement = container.querySelector('a, [tabindex="0"], button');
        expect(focusableElement).not.toBeNull();

        // Simulate key press
        fireEvent.keyDown(focusableElement!, { key });

        // Navigation should go to the correct resolved href
        expect(mockPush).toHaveBeenCalledWith(expectedHref);

        cleanup();
      }),
      { numRuns: 100 }
    );
  });

  it('P7.5: Non-activation keys do NOT trigger navigation', () => {
    const nonActivationKeyArb = fc.constantFrom('Tab', 'Escape', 'ArrowDown', 'ArrowUp', 'a', 'z', '1');

    fc.assert(
      fc.property(tileConfigArb, nonActivationKeyArb, (config, key) => {
        mockPush.mockClear();
        mockUser = { id: '1', username: 'testuser' };

        const { container } = render(
          <FeatureTile
            icon={<span>🎯</span>}
            title={config.title}
            description={config.description}
            href={config.href}
            authRequired={config.authRequired}
            gradientFrom={config.gradientFrom}
            gradientTo={config.gradientTo}
          />
        );

        const focusableElement = container.querySelector('a, [tabindex="0"], button');
        expect(focusableElement).not.toBeNull();

        // Simulate non-activation key press
        fireEvent.keyDown(focusableElement!, { key });

        // Navigation should NOT be triggered
        expect(mockPush).not.toHaveBeenCalled();

        cleanup();
      }),
      { numRuns: 100 }
    );
  });

  it('P7.6: Tiles have accessible labels for screen readers', () => {
    fc.assert(
      fc.property(tileConfigArb, (config) => {
        const { container } = render(
          <FeatureTile
            icon={<span>🎯</span>}
            title={config.title}
            description={config.description}
            href={config.href}
            authRequired={config.authRequired}
            gradientFrom={config.gradientFrom}
            gradientTo={config.gradientTo}
          />
        );

        const focusableElement = container.querySelector('a, [tabindex="0"], button');
        expect(focusableElement).not.toBeNull();

        // The element should have an aria-label or accessible name
        const ariaLabel = focusableElement!.getAttribute('aria-label');
        const hasAccessibleName = ariaLabel !== null && ariaLabel.length > 0;
        expect(hasAccessibleName).toBe(true);

        cleanup();
      }),
      { numRuns: 100 }
    );
  });
});
