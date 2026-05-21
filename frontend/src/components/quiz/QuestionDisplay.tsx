'use client';

import { cn } from '@/lib/utils';
import type { QuestionDisplay as QuestionDisplayType } from '@/types';
import { QuestionImage } from './QuestionImage';

const OPTION_COLORS = [
  'bg-quiz-red hover:bg-quiz-red/90 text-white',
  'bg-quiz-blue hover:bg-quiz-blue/90 text-white',
  'bg-quiz-green hover:bg-quiz-green/90 text-white',
  'bg-quiz-yellow hover:bg-quiz-yellow/90 text-white',
];

const OPTION_COLORS_SELECTED = [
  'bg-quiz-red ring-4 ring-white text-white',
  'bg-quiz-blue ring-4 ring-white text-white',
  'bg-quiz-green ring-4 ring-white text-white',
  'bg-quiz-yellow ring-4 ring-white text-white',
];

interface QuestionDisplayProps {
  question: QuestionDisplayType;
  selectedAnswer?: string | null;
  onSelectAnswer?: (optionId: string) => void;
  disabled?: boolean;
}

export function QuestionDisplay({ question, selectedAnswer, onSelectAnswer, disabled }: QuestionDisplayProps) {
  return (
    <div className="w-full animate-fade-in">
      <div className="mb-6 text-center">
        <h2 className="text-xl font-bold text-slate-900 dark:text-white sm:text-2xl">
          {question.text}
        </h2>
      </div>

      {question.mediaUrl && <QuestionImage mediaUrl={question.mediaUrl} />}

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        {question.options.map((option, index) => {
          const isSelected = selectedAnswer === option.id;
          const colorClass = isSelected
            ? OPTION_COLORS_SELECTED[index % 4]
            : OPTION_COLORS[index % 4];

          return (
            <button
              key={option.id}
              onClick={() => !disabled && onSelectAnswer?.(option.id)}
              disabled={disabled}
              className={cn(
                'flex min-h-[60px] items-center justify-center rounded-lg p-4 text-lg font-semibold transition-all sm:min-h-[80px]',
                colorClass,
                disabled && !isSelected && 'opacity-60 cursor-not-allowed',
                isSelected && 'scale-95'
              )}
              aria-label={`Option ${option.id}: ${option.text}`}
            >
              <span className="mr-2 text-sm opacity-75">{option.id}.</span>
              {option.text}
            </button>
          );
        })}
      </div>
    </div>
  );
}
