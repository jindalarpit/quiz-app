'use client';

import { cn } from '@/lib/utils';
import type { AnswerRevealData, QuestionOption } from '@/types';

interface AnswerRevealProps {
  data: AnswerRevealData;
  options: QuestionOption[];
  selectedAnswer?: string | null;
}

export function AnswerReveal({ data, options, selectedAnswer }: AnswerRevealProps) {
  const totalAnswers = Object.values(data.stats).reduce((sum, count) => sum + count, 0);

  return (
    <div className="w-full animate-fade-in">
      <h3 className="mb-4 text-center text-lg font-semibold text-slate-900 dark:text-white">
        Answer Revealed
      </h3>

      <div className="space-y-3">
        {options.map((option) => {
          const isCorrect = option.id === data.correctAnswer;
          const isSelected = option.id === selectedAnswer;
          const count = data.stats[option.id] || 0;
          const percentage = totalAnswers > 0 ? Math.round((count / totalAnswers) * 100) : 0;

          return (
            <div
              key={option.id}
              className={cn(
                'relative overflow-hidden rounded-lg border-2 p-4 transition-all',
                isCorrect
                  ? 'border-green-500 bg-green-50 dark:bg-green-900/20'
                  : isSelected
                    ? 'border-red-500 bg-red-50 dark:bg-red-900/20'
                    : 'border-slate-200 bg-white dark:border-slate-700 dark:bg-slate-800'
              )}
            >
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  {isCorrect && <span className="text-green-600">✓</span>}
                  {isSelected && !isCorrect && <span className="text-red-600">✗</span>}
                  <span className="font-medium text-slate-900 dark:text-white">
                    {option.id}. {option.text}
                  </span>
                </div>
                <span className="text-sm text-slate-500">
                  {count} ({percentage}%)
                </span>
              </div>
              {/* Progress bar */}
              <div className="mt-2 h-1.5 w-full overflow-hidden rounded-full bg-slate-200 dark:bg-slate-700">
                <div
                  className={cn(
                    'h-full rounded-full transition-all duration-500',
                    isCorrect ? 'bg-green-500' : 'bg-slate-400'
                  )}
                  style={{ width: `${percentage}%` }}
                />
              </div>
            </div>
          );
        })}
      </div>

      {data.yourScore !== undefined && (
        <div className="mt-4 text-center">
          <p className="text-sm text-slate-500">
            {data.yourStreak && data.yourStreak > 0 && (
              <span className="mr-2">🔥 Streak: {data.yourStreak}</span>
            )}
            {data.yourMultiplier && data.yourMultiplier > 1 && (
              <span className="font-semibold text-primary-600">{data.yourMultiplier}x multiplier</span>
            )}
          </p>
        </div>
      )}
    </div>
  );
}
