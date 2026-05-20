'use client';

import { useCallback } from 'react';

import type { ScoringMode } from '@/types';

interface ScoringModeOption {
  value: ScoringMode;
  label: string;
  description: string;
}

const SCORING_MODES: ScoringModeOption[] = [
  {
    value: 'SPEED_MATTERS',
    label: 'Speed Matters',
    description: 'Time factor 0.7 — minimum 30% of base points. Fastest answers earn the most.',
  },
  {
    value: 'BALANCED',
    label: 'Balanced',
    description: 'Time factor 0.5 — minimum 50% of base points. Equal weight to speed and knowledge.',
  },
  {
    value: 'KNOWLEDGE_FIRST',
    label: 'Knowledge First',
    description: 'Time factor 0.3 — minimum 70% of base points. Correctness matters most.',
  },
];

interface ScoringModeSelectorProps {
  value: ScoringMode;
  onChange: (mode: ScoringMode) => void;
  disabled?: boolean;
}

export function ScoringModeSelector({ value, onChange, disabled = false }: ScoringModeSelectorProps) {
  const handleChange = useCallback(
    (mode: ScoringMode) => {
      if (!disabled) {
        onChange(mode);
      }
    },
    [onChange, disabled]
  );

  return (
    <fieldset disabled={disabled}>
      <legend className="block text-sm font-medium text-slate-700 dark:text-slate-300">
        Scoring Mode
      </legend>
      <div className="mt-2 space-y-2" role="radiogroup" aria-label="Scoring mode selection">
        {SCORING_MODES.map((mode) => (
          <label
            key={mode.value}
            className={`flex cursor-pointer items-start gap-3 rounded-lg border p-3 transition-colors ${
              value === mode.value
                ? 'border-primary-500 bg-primary-50 dark:border-primary-400 dark:bg-primary-900/20'
                : 'border-slate-200 hover:border-slate-300 dark:border-slate-600 dark:hover:border-slate-500'
            } ${disabled ? 'cursor-not-allowed opacity-60' : ''}`}
          >
            <input
              type="radio"
              name="scoringMode"
              value={mode.value}
              checked={value === mode.value}
              onChange={() => handleChange(mode.value)}
              disabled={disabled}
              className="mt-0.5 h-4 w-4 text-primary-600 focus:ring-primary-500"
              aria-describedby={`scoring-mode-desc-${mode.value}`}
            />
            <div className="min-w-0 flex-1">
              <span className="block text-sm font-medium text-slate-900 dark:text-white">
                {mode.label}
              </span>
              <span
                id={`scoring-mode-desc-${mode.value}`}
                className="mt-0.5 block text-xs text-slate-500 dark:text-slate-400"
              >
                {mode.description}
              </span>
            </div>
          </label>
        ))}
      </div>
    </fieldset>
  );
}
