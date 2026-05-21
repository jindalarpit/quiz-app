'use client';

import { AnimatePresence, motion } from 'framer-motion';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';

import { useReducedMotion } from '@/hooks/useReducedMotion';
import { api } from '@/lib/api';
import { ANIMATION_TIMING } from '@/lib/constants';
import { useAuthStore } from '@/stores/authStore';
import { useQuizStore } from '@/stores/quizStore';

export default function DashboardPage() {
  const router = useRouter();
  const { user } = useAuthStore();
  const { quizzes, isLoading, fetchQuizzes, deleteQuiz, createQuiz } = useQuizStore();
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [newTitle, setNewTitle] = useState('');
  const [newDescription, setNewDescription] = useState('');
  const prefersReducedMotion = useReducedMotion();

  useEffect(() => {
    if (!user) {
      router.push('/login');
      return;
    }
    fetchQuizzes();
  }, [user, router, fetchQuizzes]);

  const handleCreate = async () => {
    if (!newTitle.trim()) return;
    try {
      const quiz = await createQuiz(newTitle.trim(), newDescription.trim());
      setShowCreateModal(false);
      setNewTitle('');
      setNewDescription('');
      router.push(`/quizzes/${quiz.id}/edit`);
    } catch {
      // Error handled in store
    }
  };

  const handleDelete = async (id: string) => {
    if (confirm('Are you sure you want to delete this quiz?')) {
      await deleteQuiz(id);
    }
  };

  const handleStartSession = async (quizId: string) => {
    try {
      const session = await api.post<{ pin: string }>('/api/sessions', { quizId });
      router.push(`/sessions/${session.pin}/host`);
    } catch {
      alert('Failed to start session. Make sure the quiz has at least one question.');
    }
  };

  // Animation variants for staggered card entrance
  const containerVariants = {
    hidden: {},
    visible: {
      transition: {
        staggerChildren: prefersReducedMotion ? 0 : ANIMATION_TIMING.staggerDelay / 1000,
      },
    },
  };

  const cardVariants = {
    hidden: prefersReducedMotion
      ? { opacity: 1, y: 0 }
      : { opacity: 0, y: 20 },
    visible: {
      opacity: 1,
      y: 0,
      transition: prefersReducedMotion
        ? { duration: 0 }
        : { duration: ANIMATION_TIMING.cardEntrance / 1000, ease: 'easeOut' },
    },
  };

  if (!user) return null;

  return (
    <div className="py-8">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 dark:text-white">My Quizzes</h1>
          <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
            Manage your quizzes and start live sessions.
          </p>
        </div>
        <button
          onClick={() => setShowCreateModal(true)}
          className="group relative inline-flex items-center justify-center overflow-hidden rounded-xl bg-gradient-to-r from-primary-600 to-indigo-600 px-6 py-3 text-sm font-semibold text-white shadow-lg shadow-primary-500/25 transition-all duration-150 hover:shadow-xl hover:shadow-primary-500/30"
        >
          <span className="relative z-10">Create Quiz</span>
          <div className="absolute inset-0 bg-gradient-to-r from-primary-700 to-indigo-700 opacity-0 transition-opacity duration-150 group-hover:opacity-100" />
        </button>
      </div>

      {isLoading && quizzes.length === 0 ? (
        <div className="mt-8 space-y-4">
          {[1, 2, 3].map((i) => (
            <div key={i} className="animate-pulse rounded-2xl border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-700 dark:bg-slate-800">
              <div className="h-5 w-1/3 rounded bg-slate-200 dark:bg-slate-700" />
              <div className="mt-2 h-4 w-2/3 rounded bg-slate-200 dark:bg-slate-700" />
            </div>
          ))}
        </div>
      ) : quizzes.length === 0 ? (
        <div className="mt-12 text-center">
          <svg
            className="mx-auto h-16 w-16 text-slate-300 dark:text-slate-600"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={1.5}
              d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
            />
          </svg>
          <h3 className="mt-4 text-lg font-semibold text-slate-900 dark:text-white">
            No quizzes yet
          </h3>
          <p className="mx-auto mt-2 max-w-sm text-sm text-slate-600 dark:text-slate-400">
            Create your first quiz to get started. Add questions, set time limits, and host live sessions with participants.
          </p>
          <button
            onClick={() => setShowCreateModal(true)}
            className="group relative mt-6 inline-flex items-center justify-center overflow-hidden rounded-xl bg-gradient-to-r from-primary-600 to-indigo-600 px-8 py-4 text-base font-semibold text-white shadow-lg shadow-primary-500/25 transition-all duration-150 hover:shadow-xl hover:shadow-primary-500/30"
          >
            <span className="relative z-10">Create your first quiz</span>
            <div className="absolute inset-0 bg-gradient-to-r from-primary-700 to-indigo-700 opacity-0 transition-opacity duration-150 group-hover:opacity-100" />
          </button>
        </div>
      ) : (
        <motion.div
          className="mt-8 space-y-4"
          variants={containerVariants}
          initial="hidden"
          animate="visible"
        >
          {quizzes.map((quiz, index) => (
            <motion.div
              key={quiz.id}
              variants={cardVariants}
              className="group relative flex items-center justify-between overflow-hidden rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-all duration-200 hover:border-primary-200 hover:shadow-md dark:border-slate-700 dark:bg-slate-800 dark:hover:border-primary-700"
            >
              {/* Gradient accent left-border */}
              <div className="absolute inset-y-0 left-0 w-1 bg-gradient-to-b from-primary-500 to-indigo-600" />

              <div className="min-w-0 flex-1 pl-4">
                <h3 className="truncate text-lg font-semibold text-slate-900 dark:text-white">
                  {quiz.title}
                </h3>
                <div className="mt-1 flex items-center gap-4 text-sm text-slate-500 dark:text-slate-400">
                  <span>{quiz.questionCount} questions</span>
                  <span>Created {new Date(quiz.createdAt).toLocaleDateString()}</span>
                </div>
              </div>
              <div className="ml-4 flex items-center gap-2">
                <button
                  onClick={() => handleStartSession(quiz.id)}
                  className="group/btn relative inline-flex items-center justify-center overflow-hidden rounded-lg bg-gradient-to-r from-primary-600 to-indigo-600 px-4 py-2 text-xs font-semibold text-white shadow-sm transition-all duration-150 hover:shadow-md hover:shadow-primary-500/25"
                >
                  <span className="relative z-10">Start Session</span>
                  <div className="absolute inset-0 bg-gradient-to-r from-primary-700 to-indigo-700 opacity-0 transition-opacity duration-150 group-hover/btn:opacity-100" />
                </button>
                <Link
                  href={`/quizzes/${quiz.id}/edit`}
                  className="group/btn relative inline-flex items-center justify-center overflow-hidden rounded-lg bg-gradient-to-r from-slate-600 to-slate-700 px-4 py-2 text-xs font-semibold text-white shadow-sm transition-all duration-150 hover:shadow-md dark:from-slate-500 dark:to-slate-600"
                >
                  <span className="relative z-10">Edit</span>
                  <div className="absolute inset-0 bg-gradient-to-r from-slate-700 to-slate-800 opacity-0 transition-opacity duration-150 group-hover/btn:opacity-100 dark:from-slate-600 dark:to-slate-700" />
                </Link>
                <button
                  onClick={() => handleDelete(quiz.id)}
                  className="group/btn relative inline-flex items-center justify-center overflow-hidden rounded-lg bg-gradient-to-r from-red-500 to-red-600 px-4 py-2 text-xs font-semibold text-white shadow-sm transition-all duration-150 hover:shadow-md hover:shadow-red-500/25"
                >
                  <span className="relative z-10">Delete</span>
                  <div className="absolute inset-0 bg-gradient-to-r from-red-600 to-red-700 opacity-0 transition-opacity duration-150 group-hover/btn:opacity-100" />
                </button>
              </div>
            </motion.div>
          ))}
        </motion.div>
      )}

      {/* Create Quiz Modal */}
      <AnimatePresence>
        {showCreateModal && (
          <motion.div
            className="fixed inset-0 z-50 flex items-center justify-center p-4"
            initial={prefersReducedMotion ? undefined : { opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={prefersReducedMotion ? undefined : { opacity: 0 }}
            transition={prefersReducedMotion ? { duration: 0 } : { duration: ANIMATION_TIMING.modalBackdrop / 1000 }}
          >
            {/* Backdrop */}
            <div
              className="absolute inset-0 bg-black/50"
              onClick={() => setShowCreateModal(false)}
            />

            {/* Modal content */}
            <motion.div
              className="relative w-full max-w-md rounded-2xl border border-slate-200 bg-white p-6 shadow-xl dark:border-slate-700 dark:bg-slate-800"
              initial={prefersReducedMotion ? undefined : { scale: 0.95, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              exit={prefersReducedMotion ? undefined : { scale: 0.95, opacity: 0 }}
              transition={prefersReducedMotion ? { duration: 0 } : { duration: ANIMATION_TIMING.modalContent / 1000, ease: 'easeOut' }}
            >
              <h2 className="text-xl font-bold text-slate-900 dark:text-white">Create New Quiz</h2>
              <div className="mt-4 space-y-4">
                <div>
                  <label htmlFor="quiz-title" className="block text-sm font-medium text-slate-700 dark:text-slate-300">
                    Title
                  </label>
                  <input
                    id="quiz-title"
                    type="text"
                    value={newTitle}
                    onChange={(e) => setNewTitle(e.target.value)}
                    className="input-field mt-1"
                    placeholder="My Awesome Quiz"
                    maxLength={100}
                  />
                </div>
                <div>
                  <label htmlFor="quiz-desc" className="block text-sm font-medium text-slate-700 dark:text-slate-300">
                    Description (optional)
                  </label>
                  <textarea
                    id="quiz-desc"
                    value={newDescription}
                    onChange={(e) => setNewDescription(e.target.value)}
                    className="input-field mt-1"
                    rows={3}
                    placeholder="A brief description..."
                    maxLength={500}
                  />
                </div>
              </div>
              <div className="mt-6 flex justify-end gap-3">
                <button
                  onClick={() => setShowCreateModal(false)}
                  className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 transition-colors duration-150 hover:bg-slate-50 dark:border-slate-600 dark:text-slate-300 dark:hover:bg-slate-700"
                >
                  Cancel
                </button>
                <button
                  onClick={handleCreate}
                  disabled={!newTitle.trim()}
                  className="group/btn relative inline-flex items-center justify-center overflow-hidden rounded-lg bg-gradient-to-r from-primary-600 to-indigo-600 px-4 py-2 text-sm font-semibold text-white shadow-sm transition-all duration-150 hover:shadow-md hover:shadow-primary-500/25 disabled:opacity-50 disabled:shadow-none"
                >
                  <span className="relative z-10">Create</span>
                  <div className="absolute inset-0 bg-gradient-to-r from-primary-700 to-indigo-700 opacity-0 transition-opacity duration-150 group-hover/btn:opacity-100" />
                </button>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
