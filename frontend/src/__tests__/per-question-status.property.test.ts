import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';

// Feature: quiz-results-leaderboard, Property 14: Per-question status mapping

/**
 * Property-based tests for per-question status mapping.
 *
 * **Validates: Requirements 7.3**
 *
 * For any set of questions in a quiz and any participant's answer submissions:
 * - Each question SHALL map to exactly one status: CORRECT if a correct answer was submitted,
 *   INCORRECT if a wrong answer was submitted, or UNANSWERED if no submission exists for that question
 */

type AnswerStatus = 'CORRECT' | 'INCORRECT' | 'UNANSWERED';

interface QuestionBreakdownItem {
  questionNumber: number;
  status: AnswerStatus;
}

interface AnswerSubmission {
  questionNumber: number;
  isCorrect: boolean;
}

/**
 * Maps raw answer submissions to per-question status breakdown.
 * This mirrors the logic used in the PerQuestionBreakdown component.
 *
 * For each question in the quiz:
 * - If a submission exists and isCorrect is true → CORRECT
 * - If a submission exists and isCorrect is false → INCORRECT
 * - If no submission exists → UNANSWERED
 */
export function mapQuestionStatuses(
  totalQuestions: number,
  submissions: AnswerSubmission[]
): QuestionBreakdownItem[] {
  const submissionMap = new Map<number, AnswerSubmission>();
  for (const submission of submissions) {
    submissionMap.set(submission.questionNumber, submission);
  }

  const breakdown: QuestionBreakdownItem[] = [];
  for (let q = 1; q <= totalQuestions; q++) {
    const submission = submissionMap.get(q);
    let status: AnswerStatus;
    if (!submission) {
      status = 'UNANSWERED';
    } else if (submission.isCorrect) {
      status = 'CORRECT';
    } else {
      status = 'INCORRECT';
    }
    breakdown.push({ questionNumber: q, status });
  }

  return breakdown;
}

// Generator for total number of questions in a quiz
const totalQuestionsArb = fc.integer({ min: 1, max: 50 });

// Generator for answer submissions given a total question count
function submissionsArb(totalQuestions: number) {
  // Generate a subset of question numbers that have submissions
  return fc
    .subarray(
      Array.from({ length: totalQuestions }, (_, i) => i + 1),
      { minLength: 0 }
    )
    .chain((answeredQuestions) =>
      fc.tuple(
        fc.constant(answeredQuestions),
        fc.array(fc.boolean(), {
          minLength: answeredQuestions.length,
          maxLength: answeredQuestions.length,
        })
      )
    )
    .map(([answeredQuestions, correctness]) =>
      answeredQuestions.map((qNum, idx) => ({
        questionNumber: qNum,
        isCorrect: correctness[idx],
      }))
    );
}

describe('Per-Question Status Mapping Properties (Property 14)', () => {
  it('Property 14.1: Each question maps to exactly one status', () => {
    fc.assert(
      fc.property(
        totalQuestionsArb.chain((total) =>
          fc.tuple(fc.constant(total), submissionsArb(total))
        ),
        ([totalQuestions, submissions]) => {
          const breakdown = mapQuestionStatuses(totalQuestions, submissions);

          // Every question from 1 to totalQuestions should appear exactly once
          expect(breakdown.length).toBe(totalQuestions);

          const questionNumbers = breakdown.map((b) => b.questionNumber);
          const uniqueNumbers = new Set(questionNumbers);
          expect(uniqueNumbers.size).toBe(totalQuestions);

          // Each question number is in range [1, totalQuestions]
          for (const item of breakdown) {
            expect(item.questionNumber).toBeGreaterThanOrEqual(1);
            expect(item.questionNumber).toBeLessThanOrEqual(totalQuestions);
          }
        }
      ),
      { numRuns: 100 }
    );
  });

  it('Property 14.2: Status is exactly one of CORRECT, INCORRECT, or UNANSWERED', () => {
    fc.assert(
      fc.property(
        totalQuestionsArb.chain((total) =>
          fc.tuple(fc.constant(total), submissionsArb(total))
        ),
        ([totalQuestions, submissions]) => {
          const breakdown = mapQuestionStatuses(totalQuestions, submissions);
          const validStatuses: AnswerStatus[] = ['CORRECT', 'INCORRECT', 'UNANSWERED'];

          for (const item of breakdown) {
            expect(validStatuses).toContain(item.status);
          }
        }
      ),
      { numRuns: 100 }
    );
  });

  it('Property 14.3: Questions with correct submissions map to CORRECT', () => {
    fc.assert(
      fc.property(
        totalQuestionsArb.chain((total) =>
          fc.tuple(fc.constant(total), submissionsArb(total))
        ),
        ([totalQuestions, submissions]) => {
          const breakdown = mapQuestionStatuses(totalQuestions, submissions);

          const correctSubmissions = submissions.filter((s) => s.isCorrect);
          for (const sub of correctSubmissions) {
            const item = breakdown.find((b) => b.questionNumber === sub.questionNumber);
            expect(item).toBeDefined();
            expect(item!.status).toBe('CORRECT');
          }
        }
      ),
      { numRuns: 100 }
    );
  });

  it('Property 14.4: Questions with incorrect submissions map to INCORRECT', () => {
    fc.assert(
      fc.property(
        totalQuestionsArb.chain((total) =>
          fc.tuple(fc.constant(total), submissionsArb(total))
        ),
        ([totalQuestions, submissions]) => {
          const breakdown = mapQuestionStatuses(totalQuestions, submissions);

          const incorrectSubmissions = submissions.filter((s) => !s.isCorrect);
          for (const sub of incorrectSubmissions) {
            const item = breakdown.find((b) => b.questionNumber === sub.questionNumber);
            expect(item).toBeDefined();
            expect(item!.status).toBe('INCORRECT');
          }
        }
      ),
      { numRuns: 100 }
    );
  });

  it('Property 14.5: Questions without submissions map to UNANSWERED', () => {
    fc.assert(
      fc.property(
        totalQuestionsArb.chain((total) =>
          fc.tuple(fc.constant(total), submissionsArb(total))
        ),
        ([totalQuestions, submissions]) => {
          const breakdown = mapQuestionStatuses(totalQuestions, submissions);

          const answeredQuestions = new Set(submissions.map((s) => s.questionNumber));
          for (const item of breakdown) {
            if (!answeredQuestions.has(item.questionNumber)) {
              expect(item.status).toBe('UNANSWERED');
            }
          }
        }
      ),
      { numRuns: 100 }
    );
  });

  it('Property 14.6: Status mapping is exhaustive - CORRECT + INCORRECT + UNANSWERED = totalQuestions', () => {
    fc.assert(
      fc.property(
        totalQuestionsArb.chain((total) =>
          fc.tuple(fc.constant(total), submissionsArb(total))
        ),
        ([totalQuestions, submissions]) => {
          const breakdown = mapQuestionStatuses(totalQuestions, submissions);

          const correctCount = breakdown.filter((b) => b.status === 'CORRECT').length;
          const incorrectCount = breakdown.filter((b) => b.status === 'INCORRECT').length;
          const unansweredCount = breakdown.filter((b) => b.status === 'UNANSWERED').length;

          expect(correctCount + incorrectCount + unansweredCount).toBe(totalQuestions);
        }
      ),
      { numRuns: 100 }
    );
  });
});
