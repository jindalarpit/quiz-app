import { create } from 'zustand';

import { api } from '@/lib/api';
import type { Question, Quiz, QuizListItem } from '@/types';

interface QuizState {
  quizzes: QuizListItem[];
  currentQuiz: Quiz | null;
  isLoading: boolean;
  error: string | null;

  fetchQuizzes: () => Promise<void>;
  fetchQuiz: (id: string) => Promise<void>;
  createQuiz: (title: string, description: string) => Promise<Quiz>;
  updateQuiz: (id: string, data: { title: string; description: string }) => Promise<void>;
  deleteQuiz: (id: string) => Promise<void>;
  addQuestion: (quizId: string, question: Omit<Question, 'id' | 'quizId' | 'position'>) => Promise<void>;
  updateQuestion: (quizId: string, questionId: string, question: Partial<Question>) => Promise<void>;
  deleteQuestion: (quizId: string, questionId: string) => Promise<void>;
  reorderQuestions: (quizId: string, questionIds: string[]) => Promise<void>;
  clearError: () => void;
}

export const useQuizStore = create<QuizState>((set, get) => ({
  quizzes: [],
  currentQuiz: null,
  isLoading: false,
  error: null,

  fetchQuizzes: async () => {
    set({ isLoading: true, error: null });
    try {
      const response = await api.get<{ content: QuizListItem[] }>('/api/quizzes');
      set({ quizzes: response.content || [], isLoading: false });
    } catch (err: unknown) {
      const message = (err as { message?: string })?.message || 'Failed to fetch quizzes';
      set({ error: message, isLoading: false });
    }
  },

  fetchQuiz: async (id: string) => {
    set({ isLoading: true, error: null });
    try {
      const quiz = await api.get<Quiz>(`/api/quizzes/${id}`);
      set({ currentQuiz: quiz, isLoading: false });
    } catch (err: unknown) {
      const message = (err as { message?: string })?.message || 'Failed to fetch quiz';
      set({ error: message, isLoading: false });
    }
  },

  createQuiz: async (title: string, description: string) => {
    set({ isLoading: true, error: null });
    try {
      const quiz = await api.post<Quiz>('/api/quizzes', { title, description });
      const { quizzes } = get();
      set({
        quizzes: [{ id: quiz.id, title: quiz.title, description: quiz.description, questionCount: 0, createdAt: quiz.createdAt, updatedAt: quiz.updatedAt }, ...quizzes],
        isLoading: false,
      });
      return quiz;
    } catch (err: unknown) {
      const message = (err as { message?: string })?.message || 'Failed to create quiz';
      set({ error: message, isLoading: false });
      throw err;
    }
  },

  updateQuiz: async (id: string, data: { title: string; description: string }) => {
    set({ isLoading: true, error: null });
    try {
      const quiz = await api.put<Quiz>(`/api/quizzes/${id}`, data);
      set({ currentQuiz: quiz, isLoading: false });
      const { quizzes } = get();
      set({
        quizzes: quizzes.map((q) =>
          q.id === id ? { ...q, title: quiz.title, description: quiz.description, updatedAt: quiz.updatedAt } : q
        ),
      });
    } catch (err: unknown) {
      const message = (err as { message?: string })?.message || 'Failed to update quiz';
      set({ error: message, isLoading: false });
    }
  },

  deleteQuiz: async (id: string) => {
    set({ isLoading: true, error: null });
    try {
      await api.delete(`/api/quizzes/${id}`);
      const { quizzes } = get();
      set({ quizzes: quizzes.filter((q) => q.id !== id), isLoading: false });
    } catch (err: unknown) {
      const message = (err as { message?: string })?.message || 'Failed to delete quiz';
      set({ error: message, isLoading: false });
    }
  },

  addQuestion: async (quizId: string, question) => {
    try {
      const newQuestion = await api.post<Question>(`/api/quizzes/${quizId}/questions`, question);
      const { currentQuiz } = get();
      if (currentQuiz && currentQuiz.id === quizId) {
        set({ currentQuiz: { ...currentQuiz, questions: [...currentQuiz.questions, newQuestion] } });
      }
    } catch (err: unknown) {
      const message = (err as { message?: string })?.message || 'Failed to add question';
      set({ error: message });
    }
  },

  updateQuestion: async (quizId: string, questionId: string, question) => {
    try {
      const updated = await api.put<Question>(`/api/quizzes/${quizId}/questions/${questionId}`, question);
      const { currentQuiz } = get();
      if (currentQuiz && currentQuiz.id === quizId) {
        set({
          currentQuiz: {
            ...currentQuiz,
            questions: currentQuiz.questions.map((q) => (q.id === questionId ? updated : q)),
          },
        });
      }
    } catch (err: unknown) {
      const message = (err as { message?: string })?.message || 'Failed to update question';
      set({ error: message });
    }
  },

  deleteQuestion: async (quizId: string, questionId: string) => {
    try {
      await api.delete(`/api/quizzes/${quizId}/questions/${questionId}`);
      const { currentQuiz } = get();
      if (currentQuiz && currentQuiz.id === quizId) {
        set({
          currentQuiz: {
            ...currentQuiz,
            questions: currentQuiz.questions.filter((q) => q.id !== questionId),
          },
        });
      }
    } catch (err: unknown) {
      const message = (err as { message?: string })?.message || 'Failed to delete question';
      set({ error: message });
    }
  },

  reorderQuestions: async (quizId: string, questionIds: string[]) => {
    try {
      await api.put(`/api/quizzes/${quizId}/questions/reorder`, { questionIds });
      const { currentQuiz } = get();
      if (currentQuiz && currentQuiz.id === quizId) {
        const reordered = questionIds
          .map((id, index) => {
            const q = currentQuiz.questions.find((question) => question.id === id);
            return q ? { ...q, position: index } : null;
          })
          .filter(Boolean) as Question[];
        set({ currentQuiz: { ...currentQuiz, questions: reordered } });
      }
    } catch (err: unknown) {
      const message = (err as { message?: string })?.message || 'Failed to reorder questions';
      set({ error: message });
    }
  },

  clearError: () => set({ error: null }),
}));
