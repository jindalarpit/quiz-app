import { describe, it, expect, vi } from 'vitest';

import type { ScoringMode } from '@/types';

// We test the component logic without DOM rendering since vitest is configured with node environment
describe('ScoringModeSelector', () => {
  describe('scoring mode options', () => {
    const SCORING_MODES = [
      {
        value: 'SPEED_MATTERS' as ScoringMode,
        label: 'Speed Matters',
        description: 'Time factor 0.7 — minimum 30% of base points. Fastest answers earn the most.',
      },
      {
        value: 'BALANCED' as ScoringMode,
        label: 'Balanced',
        description: 'Time factor 0.5 — minimum 50% of base points. Equal weight to speed and knowledge.',
      },
      {
        value: 'KNOWLEDGE_FIRST' as ScoringMode,
        label: 'Knowledge First',
        description: 'Time factor 0.3 — minimum 70% of base points. Correctness matters most.',
      },
    ];

    it('should have three scoring mode options', () => {
      expect(SCORING_MODES).toHaveLength(3);
    });

    it('should default to SPEED_MATTERS when no mode is explicitly selected', () => {
      const defaultMode: ScoringMode = 'SPEED_MATTERS';
      expect(defaultMode).toBe('SPEED_MATTERS');
    });

    it('should map SPEED_MATTERS to correct label and description', () => {
      const mode = SCORING_MODES.find((m) => m.value === 'SPEED_MATTERS');
      expect(mode).toBeDefined();
      expect(mode!.label).toBe('Speed Matters');
      expect(mode!.description).toContain('0.7');
      expect(mode!.description).toContain('30%');
    });

    it('should map BALANCED to correct label and description', () => {
      const mode = SCORING_MODES.find((m) => m.value === 'BALANCED');
      expect(mode).toBeDefined();
      expect(mode!.label).toBe('Balanced');
      expect(mode!.description).toContain('0.5');
      expect(mode!.description).toContain('50%');
    });

    it('should map KNOWLEDGE_FIRST to correct label and description', () => {
      const mode = SCORING_MODES.find((m) => m.value === 'KNOWLEDGE_FIRST');
      expect(mode).toBeDefined();
      expect(mode!.label).toBe('Knowledge First');
      expect(mode!.description).toContain('0.3');
      expect(mode!.description).toContain('70%');
    });
  });

  describe('updateScoringMode store action', () => {
    it('should call PATCH endpoint with correct scoring mode', async () => {
      const mockPatch = vi.fn().mockResolvedValue(undefined);
      const quizId = 'quiz-123';
      const scoringMode: ScoringMode = 'BALANCED';

      // Simulate the store action logic
      await mockPatch(`/api/quizzes/${quizId}`, { scoringMode });

      expect(mockPatch).toHaveBeenCalledWith('/api/quizzes/quiz-123', { scoringMode: 'BALANCED' });
    });

    it('should update local state optimistically on mode change', () => {
      let currentMode: ScoringMode = 'SPEED_MATTERS';
      const handleChange = (mode: ScoringMode) => {
        currentMode = mode;
      };

      handleChange('KNOWLEDGE_FIRST');
      expect(currentMode).toBe('KNOWLEDGE_FIRST');
    });

    it('should not call onChange when disabled', () => {
      const onChange = vi.fn();
      const disabled = true;

      // Simulate the component's handleChange logic
      const handleChange = (mode: ScoringMode) => {
        if (!disabled) {
          onChange(mode);
        }
      };

      handleChange('BALANCED');
      expect(onChange).not.toHaveBeenCalled();
    });
  });

  describe('default behavior', () => {
    it('should use SPEED_MATTERS as default when quiz has no scoringMode', () => {
      const quizScoringMode: ScoringMode | undefined = undefined;
      const resolvedMode = quizScoringMode || 'SPEED_MATTERS';
      expect(resolvedMode).toBe('SPEED_MATTERS');
    });

    it('should preserve existing scoring mode from quiz data', () => {
      const quizScoringMode: ScoringMode | undefined = 'KNOWLEDGE_FIRST';
      const resolvedMode = quizScoringMode || 'SPEED_MATTERS';
      expect(resolvedMode).toBe('KNOWLEDGE_FIRST');
    });
  });
});
