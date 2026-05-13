'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';

import { api } from '@/lib/api';
import { useAuthStore } from '@/stores/authStore';
import { useQuizStore } from '@/stores/quizStore';

export default function DashboardPage() {
  const router = useRouter();
  const { user } = useAuthStore();
  const { quizzes, isLoading, fetchQuizzes, deleteQuiz, createQuiz } = useQuizStore();
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [newTitle, setNewTitle] = useState('');
  const [newDescription, setNewDescription] = useState('');

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
        <button onClick={() => setShowCreateModal(true)} className="btn-primary">
          Create Quiz
        </button>
      </div>

      {isLoading && quizzes.length === 0 ? (
        <div className="mt-8 space-y-4">
          {[1, 2, 3].map((i) => (
            <div key={i} className="card animate-pulse">
              <div className="h-5 w-1/3 rounded bg-slate-200 dark:bg-slate-700" />
              <div className="mt-2 h-4 w-2/3 rounded bg-slate-200 dark:bg-slate-700" />
            </div>
          ))}
        </div>
      ) : quizzes.length === 0 ? (
        <div className="mt-12 text-center">
          <svg className="mx-auto h-12 w-12 text-slate-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
          </svg>
          <h3 className="mt-4 text-lg font-medium text-slate-900 dark:text-white">No quizzes yet</h3>
          <p className="mt-2 text-sm text-slate-600 dark:text-slate-400">
            Create your first quiz to get started.
          </p>
          <button onClick={() => setShowCreateModal(true)} className="btn-primary mt-4">
            Create your first quiz
          </button>
        </div>
      ) : (
        <div className="mt-8 space-y-4">
          {quizzes.map((quiz) => (
            <div key={quiz.id} className="card flex items-center justify-between">
              <div className="min-w-0 flex-1">
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
                  className="btn-primary text-xs"
                >
                  Start Session
                </button>
                <Link
                  href={`/quizzes/${quiz.id}/edit`}
                  className="btn-secondary text-xs"
                >
                  Edit
                </Link>
                <button
                  onClick={() => handleDelete(quiz.id)}
                  className="btn-danger text-xs"
                >
                  Delete
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Create Quiz Modal */}
      {showCreateModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div className="card w-full max-w-md">
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
              <button onClick={() => setShowCreateModal(false)} className="btn-secondary">
                Cancel
              </button>
              <button onClick={handleCreate} disabled={!newTitle.trim()} className="btn-primary">
                Create
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
