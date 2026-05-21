// Feature: quiz-enhancements-ui, Property 6: Auth-aware tile navigation produces correct destinations
import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';
import { resolveHref } from '../FeatureTile';

/**
 * Property-based tests for auth-aware tile navigation (Property 6).
 *
 * **Validates: Requirements 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8**
 *
 * Property 6: Auth-aware tile navigation produces correct destinations — For any
 * feature tile configuration and authentication state (authenticated or unauthenticated),
 * the resolved navigation target SHALL be: the tile's configured `href` if `authRequired`
 * is false OR the user is authenticated; otherwise `/login`.
 */

// Generator for web paths (starts with / followed by path segments)
const webPathArb = fc
  .array(fc.stringOf(fc.constantFrom(...'abcdefghijklmnopqrstuvwxyz0123456789-_'.split('')), { minLength: 1, maxLength: 20 }), { minLength: 1, maxLength: 4 })
  .map((segments) => '/' + segments.join('/'));

// Generator for tile navigation configs
const tileNavConfigArb = fc.record({
  authRequired: fc.boolean(),
  authenticated: fc.boolean(),
  href: webPathArb,
});

describe('Auth-Aware Tile Navigation Properties (Property 6)', () => {
  it('P6.1: authRequired=false → always navigates to configured href regardless of auth state', () => {
    fc.assert(
      fc.property(fc.boolean(), webPathArb, (authenticated, href) => {
        const result = resolveHref(href, false, authenticated);
        expect(result).toBe(href);
      }),
      { numRuns: 200 }
    );
  });

  it('P6.2: authRequired=true AND authenticated → navigates to configured href', () => {
    fc.assert(
      fc.property(webPathArb, (href) => {
        const result = resolveHref(href, true, true);
        expect(result).toBe(href);
      }),
      { numRuns: 200 }
    );
  });

  it('P6.3: authRequired=true AND unauthenticated → navigates to /login', () => {
    fc.assert(
      fc.property(webPathArb, (href) => {
        const result = resolveHref(href, true, false);
        expect(result).toBe('/login');
      }),
      { numRuns: 200 }
    );
  });

  it('P6.4: Combined property — authRequired=false OR authenticated → href; otherwise /login', () => {
    fc.assert(
      fc.property(tileNavConfigArb, ({ authRequired, authenticated, href }) => {
        const result = resolveHref(href, authRequired, authenticated);

        if (!authRequired || authenticated) {
          expect(result).toBe(href);
        } else {
          expect(result).toBe('/login');
        }
      }),
      { numRuns: 500 }
    );
  });

  it('P6.5: Resolved href is always a non-empty string starting with /', () => {
    fc.assert(
      fc.property(tileNavConfigArb, ({ authRequired, authenticated, href }) => {
        const result = resolveHref(href, authRequired, authenticated);

        expect(result.length).toBeGreaterThan(0);
        expect(result.startsWith('/')).toBe(true);
      }),
      { numRuns: 200 }
    );
  });

  it('P6.6: Specific tile configurations match requirements', () => {
    // Requirement 4.1: "Create Quizzes" → /dashboard (authRequired: true), authenticated → /dashboard
    expect(resolveHref('/dashboard', true, true)).toBe('/dashboard');
    // Requirement 4.2: "Create Quizzes" → /dashboard (authRequired: true), unauthenticated → /login
    expect(resolveHref('/dashboard', true, false)).toBe('/login');
    // Requirement 4.3: "Play Live" → /join (authRequired: false)
    expect(resolveHref('/join', false, false)).toBe('/join');
    expect(resolveHref('/join', false, true)).toBe('/join');
    // Requirement 4.6: "Analytics" → /history (authRequired: true), authenticated → /history
    expect(resolveHref('/history', true, true)).toBe('/history');
    // Requirement 4.7: "Analytics" → /history (authRequired: true), unauthenticated → /login
    expect(resolveHref('/history', true, false)).toBe('/login');
    // Requirement 4.8: "Mobile First" → /join (authRequired: false)
    expect(resolveHref('/join', false, true)).toBe('/join');
    expect(resolveHref('/join', false, false)).toBe('/join');
  });
});
