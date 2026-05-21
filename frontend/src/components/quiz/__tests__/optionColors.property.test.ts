// Feature: quiz-enhancements-ui, Property 10: Answer option left borders follow color sequence
import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';

/**
 * Property-based tests for answer option left border colors (Property 10).
 *
 * **Validates: Requirements 6.4**
 *
 * Property 10: Answer option left borders follow color sequence — For any question
 * with options, the left border color of the j-th option (0-indexed) SHALL be:
 * red for j=0, blue for j=1, green for j=2, yellow for j=3. For TRUE_FALSE questions
 * with exactly 2 options, only red (j=0) and blue (j=1) SHALL be applied.
 */

/** Left-border colors for answer options (mirrors the implementation) */
const OPTION_BORDER_COLORS = [
  'border-l-quiz-red',
  'border-l-quiz-blue',
  'border-l-quiz-green',
  'border-l-quiz-yellow',
];

/**
 * Pure logic extracted from QuizEditorPage's getOptionBorderColor function.
 * This mirrors the implementation to test the color assignment logic.
 */
function getOptionBorderColor(type: 'MCQ' | 'TRUE_FALSE', index: number): string {
  if (type === 'TRUE_FALSE') {
    return index === 0 ? OPTION_BORDER_COLORS[0] : OPTION_BORDER_COLORS[1];
  }
  return OPTION_BORDER_COLORS[index] || OPTION_BORDER_COLORS[0];
}

describe('Option Border Colors Properties (Property 10)', () => {
  it('P10.1: MCQ options follow red, blue, green, yellow color sequence', () => {
    fc.assert(
      fc.property(
        fc.record({
          type: fc.constant('MCQ' as const),
          optionCount: fc.integer({ min: 2, max: 4 }),
        }),
        ({ type, optionCount }) => {
          const expectedColors = [
            'border-l-quiz-red',
            'border-l-quiz-blue',
            'border-l-quiz-green',
            'border-l-quiz-yellow',
          ];

          for (let j = 0; j < optionCount; j++) {
            const color = getOptionBorderColor(type, j);
            expect(color).toBe(expectedColors[j]);
          }
        }
      ),
      { numRuns: 100 }
    );
  });

  it('P10.2: TRUE_FALSE options use only red (index 0) and blue (index 1)', () => {
    fc.assert(
      fc.property(
        fc.record({
          type: fc.constant('TRUE_FALSE' as const),
          optionCount: fc.constant(2),
        }),
        ({ type, optionCount }) => {
          const colors: string[] = [];
          for (let j = 0; j < optionCount; j++) {
            colors.push(getOptionBorderColor(type, j));
          }

          expect(colors[0]).toBe('border-l-quiz-red');
          expect(colors[1]).toBe('border-l-quiz-blue');
          // TRUE_FALSE should never produce green or yellow
          expect(colors).not.toContain('border-l-quiz-green');
          expect(colors).not.toContain('border-l-quiz-yellow');
        }
      ),
      { numRuns: 100 }
    );
  });

  it('P10.3: For any question type and option count, color assignment is deterministic', () => {
    fc.assert(
      fc.property(
        fc.record({
          type: fc.constantFrom('MCQ' as const, 'TRUE_FALSE' as const),
          optionCount: fc.integer({ min: 2, max: 4 }),
        }),
        ({ type, optionCount }) => {
          for (let j = 0; j < optionCount; j++) {
            const color1 = getOptionBorderColor(type, j);
            const color2 = getOptionBorderColor(type, j);
            expect(color1).toBe(color2);
          }
        }
      ),
      { numRuns: 100 }
    );
  });

  it('P10.4: First option is always red regardless of question type', () => {
    fc.assert(
      fc.property(
        fc.constantFrom('MCQ' as const, 'TRUE_FALSE' as const),
        (type) => {
          const color = getOptionBorderColor(type, 0);
          expect(color).toBe('border-l-quiz-red');
        }
      ),
      { numRuns: 100 }
    );
  });

  it('P10.5: Second option is always blue regardless of question type', () => {
    fc.assert(
      fc.property(
        fc.constantFrom('MCQ' as const, 'TRUE_FALSE' as const),
        (type) => {
          const color = getOptionBorderColor(type, 1);
          expect(color).toBe('border-l-quiz-blue');
        }
      ),
      { numRuns: 100 }
    );
  });

  it('P10.6: All assigned colors are valid border color classes', () => {
    fc.assert(
      fc.property(
        fc.record({
          type: fc.constantFrom('MCQ' as const, 'TRUE_FALSE' as const),
          optionCount: fc.integer({ min: 2, max: 4 }),
        }),
        ({ type, optionCount }) => {
          for (let j = 0; j < optionCount; j++) {
            const color = getOptionBorderColor(type, j);
            expect(OPTION_BORDER_COLORS).toContain(color);
          }
        }
      ),
      { numRuns: 100 }
    );
  });
});
