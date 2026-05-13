'use client';

import Link from 'next/link';

import { useAuthStore } from '@/stores/authStore';

export default function HomePage() {
  const { user } = useAuthStore();

  return (
    <div className="flex min-h-[calc(100vh-4rem)] flex-col">
      {/* Hero Section */}
      <section className="flex flex-1 flex-col items-center justify-center px-4 py-16 text-center">
        <div className="mb-6 inline-flex items-center rounded-full bg-primary-50 px-4 py-1.5 text-sm font-medium text-primary-700 ring-1 ring-inset ring-primary-600/20 dark:bg-primary-900/30 dark:text-primary-300 dark:ring-primary-500/30">
          🎮 Real-time quiz battles for up to 1000 players
        </div>

        <h1 className="max-w-4xl text-4xl font-extrabold tracking-tight text-slate-900 dark:text-white sm:text-5xl md:text-6xl lg:text-7xl">
          Make Learning
          <span className="bg-gradient-to-r from-primary-500 to-indigo-600 bg-clip-text text-transparent">
            {' '}Fun & Interactive
          </span>
        </h1>

        <p className="mx-auto mt-6 max-w-2xl text-lg leading-relaxed text-slate-600 dark:text-slate-300 sm:text-xl">
          Create engaging quizzes, host live sessions, and watch participants compete
          in real-time with leaderboards, streaks, and instant feedback.
        </p>

        <div className="mt-10 flex flex-col items-center gap-4 sm:flex-row">
          <Link
            href={user ? '/dashboard' : '/login'}
            className="group relative inline-flex w-full items-center justify-center overflow-hidden rounded-xl bg-gradient-to-r from-primary-600 to-indigo-600 px-8 py-4 text-base font-semibold text-white shadow-lg shadow-primary-500/25 transition-all hover:shadow-xl hover:shadow-primary-500/30 sm:w-auto"
          >
            <span className="relative z-10">Host a Quiz</span>
            <div className="absolute inset-0 bg-gradient-to-r from-primary-700 to-indigo-700 opacity-0 transition-opacity group-hover:opacity-100" />
          </Link>
          <Link
            href="/join"
            className="inline-flex w-full items-center justify-center rounded-xl border-2 border-slate-200 bg-white px-8 py-4 text-base font-semibold text-slate-700 shadow-sm transition-all hover:border-primary-300 hover:bg-primary-50 hover:text-primary-700 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-200 dark:hover:border-primary-500 dark:hover:bg-slate-700 sm:w-auto"
          >
            Join a Quiz
          </Link>
        </div>
      </section>

      {/* Features Section */}
      <section className="border-t border-slate-100 bg-slate-50/50 py-20 dark:border-slate-800 dark:bg-slate-800/30">
        <div className="mx-auto max-w-6xl px-4">
          <h2 className="mb-12 text-center text-2xl font-bold text-slate-900 dark:text-white sm:text-3xl">
            Everything you need for live quizzes
          </h2>

          <div className="grid grid-cols-1 gap-8 sm:grid-cols-2 lg:grid-cols-3">
            {/* Feature 1 */}
            <div className="group rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-all hover:border-primary-200 hover:shadow-md dark:border-slate-700 dark:bg-slate-800 dark:hover:border-primary-700">
              <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-xl bg-gradient-to-br from-quiz-red to-red-600 text-white shadow-lg shadow-red-500/20">
                <svg className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 6v6m0 0v6m0-6h6m-6 0H6" />
                </svg>
              </div>
              <h3 className="mb-2 text-lg font-semibold text-slate-900 dark:text-white">Create Quizzes</h3>
              <p className="text-sm leading-relaxed text-slate-600 dark:text-slate-400">
                Multiple choice, true/false, and poll questions with customizable time limits and point values.
              </p>
            </div>

            {/* Feature 2 */}
            <div className="group rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-all hover:border-primary-200 hover:shadow-md dark:border-slate-700 dark:bg-slate-800 dark:hover:border-primary-700">
              <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-xl bg-gradient-to-br from-quiz-blue to-blue-700 text-white shadow-lg shadow-blue-500/20">
                <svg className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
                </svg>
              </div>
              <h3 className="mb-2 text-lg font-semibold text-slate-900 dark:text-white">Play Live</h3>
              <p className="text-sm leading-relaxed text-slate-600 dark:text-slate-400">
                Host real-time sessions with up to 1000 participants. Share a PIN and start playing instantly.
              </p>
            </div>

            {/* Feature 3 */}
            <div className="group rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-all hover:border-primary-200 hover:shadow-md dark:border-slate-700 dark:bg-slate-800 dark:hover:border-primary-700">
              <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-xl bg-gradient-to-br from-quiz-green to-green-700 text-white shadow-lg shadow-green-500/20">
                <svg className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
                </svg>
              </div>
              <h3 className="mb-2 text-lg font-semibold text-slate-900 dark:text-white">Compete & Win</h3>
              <p className="text-sm leading-relaxed text-slate-600 dark:text-slate-400">
                Real-time leaderboards, answer streaks with multipliers, and speed-based scoring.
              </p>
            </div>

            {/* Feature 4 */}
            <div className="group rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-all hover:border-primary-200 hover:shadow-md dark:border-slate-700 dark:bg-slate-800 dark:hover:border-primary-700">
              <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-xl bg-gradient-to-br from-quiz-yellow to-amber-600 text-white shadow-lg shadow-amber-500/20">
                <svg className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
              </div>
              <h3 className="mb-2 text-lg font-semibold text-slate-900 dark:text-white">Synced Timers</h3>
              <p className="text-sm leading-relaxed text-slate-600 dark:text-slate-400">
                Server-authoritative timing ensures fair play. Everyone gets the exact same time window.
              </p>
            </div>

            {/* Feature 5 */}
            <div className="group rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-all hover:border-primary-200 hover:shadow-md dark:border-slate-700 dark:bg-slate-800 dark:hover:border-primary-700">
              <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-xl bg-gradient-to-br from-purple-500 to-purple-700 text-white shadow-lg shadow-purple-500/20">
                <svg className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 17v-2m3 2v-4m3 4v-6m2 10H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                </svg>
              </div>
              <h3 className="mb-2 text-lg font-semibold text-slate-900 dark:text-white">Analytics</h3>
              <p className="text-sm leading-relaxed text-slate-600 dark:text-slate-400">
                Detailed reports after each session — question accuracy, response times, and participant engagement.
              </p>
            </div>

            {/* Feature 6 */}
            <div className="group rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-all hover:border-primary-200 hover:shadow-md dark:border-slate-700 dark:bg-slate-800 dark:hover:border-primary-700">
              <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-xl bg-gradient-to-br from-pink-500 to-rose-600 text-white shadow-lg shadow-pink-500/20">
                <svg className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 18h.01M8 21h8a2 2 0 002-2V5a2 2 0 00-2-2H8a2 2 0 00-2 2v14a2 2 0 002 2z" />
                </svg>
              </div>
              <h3 className="mb-2 text-lg font-semibold text-slate-900 dark:text-white">Mobile First</h3>
              <p className="text-sm leading-relaxed text-slate-600 dark:text-slate-400">
                Responsive design works on any device. Participants just need a phone and a PIN to join.
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* CTA Section */}
      <section className="py-16 text-center">
        <h2 className="text-2xl font-bold text-slate-900 dark:text-white sm:text-3xl">
          Ready to get started?
        </h2>
        <p className="mx-auto mt-3 max-w-md text-slate-600 dark:text-slate-400">
          Create your first quiz in minutes. No credit card required.
        </p>
        <div className="mt-8">
          <Link
            href={user ? '/dashboard' : '/register'}
            className="inline-flex items-center justify-center rounded-xl bg-gradient-to-r from-primary-600 to-indigo-600 px-8 py-4 text-base font-semibold text-white shadow-lg shadow-primary-500/25 transition-all hover:shadow-xl hover:shadow-primary-500/30"
          >
            Create Free Account
          </Link>
        </div>
      </section>
    </div>
  );
}
