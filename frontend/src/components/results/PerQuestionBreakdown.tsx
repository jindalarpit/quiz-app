'use client';

import React, { useState } from 'react';

import { cn } from '@/lib/utils';

export type QuestionStatus = 'CORRECT' | 'INCORRECT' | 'UNANSWERED';

export interface QuestionBreakdownItem {
  questionNumber: number;
  status: QuestionStatus;
}

interface PerQuestionBreakdownProps {
  questionBreakdown: QuestionBreakdownItem[];
}

const STATUS_CONFIG: Record<
  QuestionStatus,
  { label: string; icon: string; className: string }
> = {
  CORRECT: {
    label: 'Correct',
    icon: '✓',
    className:
      'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300',
  },
  INCORRECT: {
    label: 'Incorrect',
    icon: '✗',
    className:
      'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300',
  },
  UNANSWERED: {
    label: 'Unanswered',
    icon: '—',
    className:
      'bg-slate-100 text-slate-600 dark:bg-slate-700 dark:text-slate-400',
  },
};

export function PerQuestionBreakdown({ questionBreakdown }: PerQuestionBreakdownProps) {
  const [isExpanded, setIsExpanded] = useState(false);

  if (questionBreakdown.length === 0) return null;

  return (
    <div className="w-full rounded-lg border border-slate-200 dark:border-slate-700">
      <button
        type="button"
        onClick={() => setIsExpanded(!isExpanded)}
        className="flex w-full items-center justify-between px-4 py-3 text-left"
        aria-expanded={isExpanded}
        aria-controls="question-breakdown-list"
      >
        <span className="text-sm font-semibold text-slate-900 dark:text-white">
          Per-Question Breakdown
        </span>
        <span
          className={cn(
            'text-slate-500 transition-transform duration-200 dark:text-slate-400',
            isExpanded && 'rotate-180'
          )}
          aria-hidden="true"
        >
          ▼
        </span>
      </button>

      {isExpanded && (
        <ul
          id="question-breakdown-list"
          className="border-t border-slate-200 px-4 py-2 dark:border-slate-700"
          role="list"
        >
          {questionBreakdown.map((item) => {
            const config = STATUS_CONFIG[item.status];
            return (
              <li
                key={item.questionNumber}
                className="flex items-center justify-between py-2"
              >
                <span className="text-sm text-slate-700 dark:text-slate-300">
                  Question {item.questionNumber}
                </span>
                <span
                  className={cn(
                    'inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-medium',
                    config.className
                  )}
                  aria-label={`Question ${item.questionNumber}: ${config.label}`}
                >
                  <span aria-hidden="true">{config.icon}</span>
                  {config.label}
                </span>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
