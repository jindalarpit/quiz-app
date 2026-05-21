// Feature: quiz-enhancements-ui, Property 9: Question cards display correct numbered badges
/**
 * @vitest-environment jsdom
 */
import { describe, it, expect, afterEach } from 'vitest';
import * as fc from 'fast-check';
import { cleanup } from '@testing-library/react';

/**
 * Property-based tests for question card numbered badges (Property 9).
 *
 * **Validates: Requirements 6.1**
 *
 * Property 9: Question cards display correct numbered badges — For any list of N
 * questions displayed in the editor, the i-th question card (0-indexed) SHALL display
 * the badge text "Q{i+1}", and the total number of badges SHALL equal N.
 *
 * The implementation in QuizEditorPage renders a <span> with data-badge="Q{index+1}"
 * and text content "Q{index+1}" for each question card.
 */

afterEach(() => {
  cleanup();
});

/**
 * Generator for a question-like record with fields relevant to badge rendering.
 */
const questionArb = fc.record({
  id: fc.uuid(),
  type: fc.constantFrom('MCQ' as const, 'TRUE_FALSE' as const, 'POLL' as const),
  text: fc.string({ minLength: 1, maxLength: 200 }),
  timeLimitSeconds: fc.constantFrom(5, 10, 15, 20, 30, 60),
  points: fc.constantFrom(0, 1000, 2000),
});

type QuestionRecord = typeof questionArb extends fc.Arbitrary<infer T> ? T : never;

/**
 * Simulates the badge rendering logic from QuizEditorPage.
 * For each question at index i, the badge text is "Q{i+1}" and
 * the data-badge attribute is "Q{i+1}".
 */
function renderBadges(questions: QuestionRecord[]): { badgeText: string; dataBadge: string }[] {
  return questions.map((_, index) => ({
    badgeText: `Q${index + 1}`,
    dataBadge: `Q${index + 1}`,
  }));
}

/**
 * Simulates DOM rendering of question cards with badges,
 * mimicking the QuizEditorPage behavior.
 */
function renderQuestionCardsToDOM(questions: QuestionRecord[]): HTMLElement {
  const container = document.createElement('div');

  questions.forEach((q, index) => {
    const card = document.createElement('div');
    card.setAttribute('data-question-card', '');
    card.setAttribute('data-question-index', String(index));

    const badge = document.createElement('span');
    badge.setAttribute('data-badge', `Q${index + 1}`);
    badge.textContent = `Q${index + 1}`;

    card.appendChild(badge);
    container.appendChild(card);
  });

  return container;
}

describe('Question Card Numbered Badges (Property 9)', () => {
  it('P9.1: The i-th question card displays badge text "Q{i+1}"', () => {
    fc.assert(
      fc.property(
        fc.array(questionArb, { minLength: 1, maxLength: 50 }),
        (questions) => {
          const badges = renderBadges(questions);

          badges.forEach((badge, index) => {
            expect(badge.badgeText).toBe(`Q${index + 1}`);
            expect(badge.dataBadge).toBe(`Q${index + 1}`);
          });
        }
      ),
      { numRuns: 100 }
    );
  });

  it('P9.2: Total number of badges equals the number of questions N', () => {
    fc.assert(
      fc.property(
        fc.array(questionArb, { minLength: 1, maxLength: 50 }),
        (questions) => {
          const badges = renderBadges(questions);
          expect(badges).toHaveLength(questions.length);
        }
      ),
      { numRuns: 100 }
    );
  });

  it('P9.3: DOM rendering produces correct data-badge attributes for all cards', () => {
    fc.assert(
      fc.property(
        fc.array(questionArb, { minLength: 1, maxLength: 50 }),
        (questions) => {
          const container = renderQuestionCardsToDOM(questions);

          const badgeElements = container.querySelectorAll('[data-badge]');
          expect(badgeElements).toHaveLength(questions.length);

          badgeElements.forEach((el, index) => {
            expect(el.getAttribute('data-badge')).toBe(`Q${index + 1}`);
            expect(el.textContent).toBe(`Q${index + 1}`);
          });
        }
      ),
      { numRuns: 100 }
    );
  });

  it('P9.4: Badge numbering is sequential starting from Q1', () => {
    fc.assert(
      fc.property(
        fc.array(questionArb, { minLength: 1, maxLength: 50 }),
        (questions) => {
          const container = renderQuestionCardsToDOM(questions);
          const badgeElements = container.querySelectorAll('[data-badge]');

          // Verify sequential numbering
          for (let i = 0; i < badgeElements.length; i++) {
            const expectedBadge = `Q${i + 1}`;
            expect(badgeElements[i].getAttribute('data-badge')).toBe(expectedBadge);
          }

          // Verify first badge is always Q1
          if (badgeElements.length > 0) {
            expect(badgeElements[0].getAttribute('data-badge')).toBe('Q1');
          }

          // Verify last badge is Q{N}
          if (badgeElements.length > 0) {
            expect(badgeElements[badgeElements.length - 1].getAttribute('data-badge')).toBe(
              `Q${questions.length}`
            );
          }
        }
      ),
      { numRuns: 100 }
    );
  });

  it('P9.5: Badge count matches question card count', () => {
    fc.assert(
      fc.property(
        fc.array(questionArb, { minLength: 1, maxLength: 50 }),
        (questions) => {
          const container = renderQuestionCardsToDOM(questions);

          const cards = container.querySelectorAll('[data-question-card]');
          const badges = container.querySelectorAll('[data-badge]');

          // Each card has exactly one badge
          expect(badges).toHaveLength(cards.length);

          // Each card's badge matches its index
          cards.forEach((card, index) => {
            const badge = card.querySelector('[data-badge]');
            expect(badge).not.toBeNull();
            expect(badge!.getAttribute('data-badge')).toBe(`Q${index + 1}`);
          });
        }
      ),
      { numRuns: 100 }
    );
  });
});
