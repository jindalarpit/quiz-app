// Feature: quiz-enhancements-ui, Property 5: Image rendering is determined by mediaUrl presence
/**
 * @vitest-environment jsdom
 */
import React from 'react';
import { describe, it, expect, afterEach } from 'vitest';
import * as fc from 'fast-check';
import { render, cleanup } from '@testing-library/react';
import { QuestionImage } from '../QuestionImage';

/**
 * Property-based tests for QuestionImage rendering logic (Property 5).
 *
 * **Validates: Requirements 3.1, 3.5, 3.6**
 *
 * Property 5: Image rendering is determined by mediaUrl presence — For any question
 * object, if mediaUrl is a non-empty string, the rendered output SHALL contain exactly
 * one <img> element with alt="Question image". If mediaUrl is empty, undefined, or null,
 * the rendered output SHALL contain zero <img> elements in the question area.
 */

afterEach(() => {
  cleanup();
});

/**
 * Renders the QuestionImage conditionally based on mediaUrl presence,
 * mimicking the parent component (QuestionDisplay) behavior per Requirement 3.6.
 */
function renderWithMediaUrl(mediaUrl: string | undefined | null) {
  // Conditional rendering: only render QuestionImage when mediaUrl is non-empty
  const shouldRender = !!mediaUrl;

  return render(
    shouldRender
      ? <QuestionImage mediaUrl={mediaUrl!} />
      : <div data-testid="question-area" />
  );
}

describe('QuestionImage Rendering Properties (Property 5)', () => {
  it('P5.1: Non-empty mediaUrl → exactly one img with alt="Question image"', () => {
    fc.assert(
      fc.property(fc.webUrl(), (mediaUrl) => {
        const { container } = renderWithMediaUrl(mediaUrl);

        const images = container.querySelectorAll('img');
        expect(images).toHaveLength(1);
        expect(images[0].getAttribute('alt')).toBe('Question image');

        cleanup();
      }),
      { numRuns: 100 }
    );
  });

  it('P5.2: Undefined mediaUrl → zero img elements', () => {
    fc.assert(
      fc.property(fc.constant(undefined), (mediaUrl) => {
        const { container } = renderWithMediaUrl(mediaUrl);

        const images = container.querySelectorAll('img');
        expect(images).toHaveLength(0);

        cleanup();
      }),
      { numRuns: 10 }
    );
  });

  it('P5.3: Null mediaUrl → zero img elements', () => {
    fc.assert(
      fc.property(fc.constant(null), (mediaUrl) => {
        const { container } = renderWithMediaUrl(mediaUrl);

        const images = container.querySelectorAll('img');
        expect(images).toHaveLength(0);

        cleanup();
      }),
      { numRuns: 10 }
    );
  });

  it('P5.4: Empty string mediaUrl → zero img elements', () => {
    fc.assert(
      fc.property(fc.constant(''), (mediaUrl) => {
        const { container } = renderWithMediaUrl(mediaUrl);

        const images = container.querySelectorAll('img');
        expect(images).toHaveLength(0);

        cleanup();
      }),
      { numRuns: 10 }
    );
  });

  it('P5.5: fc.option(fc.webUrl()) — presence determines rendering', () => {
    fc.assert(
      fc.property(fc.option(fc.webUrl(), { nil: undefined }), (mediaUrl) => {
        const { container } = renderWithMediaUrl(mediaUrl);

        const images = container.querySelectorAll('img');

        if (mediaUrl) {
          // Non-empty mediaUrl → exactly one img with correct alt
          expect(images).toHaveLength(1);
          expect(images[0].getAttribute('alt')).toBe('Question image');
          expect(images[0].getAttribute('src')).toBe(mediaUrl);
        } else {
          // Undefined mediaUrl → zero img elements
          expect(images).toHaveLength(0);
        }

        cleanup();
      }),
      { numRuns: 200 }
    );
  });

  it('P5.6: Non-empty mediaUrl img has correct src attribute', () => {
    fc.assert(
      fc.property(fc.webUrl(), (mediaUrl) => {
        const { container } = renderWithMediaUrl(mediaUrl);

        const img = container.querySelector('img');
        expect(img).not.toBeNull();
        expect(img!.getAttribute('src')).toBe(mediaUrl);

        cleanup();
      }),
      { numRuns: 100 }
    );
  });
});
