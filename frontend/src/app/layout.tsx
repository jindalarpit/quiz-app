import type { Metadata } from 'next';

import '@/styles/globals.css';

import { Header } from '@/components/layout/Header';
import { ThemeProvider } from '@/components/providers/ThemeProvider';

export const metadata: Metadata = {
  title: 'Quiz Platform - Live Interactive Quizzes',
  description: 'Host and play live interactive quizzes with real-time leaderboards',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body className="min-h-screen bg-white antialiased dark:bg-slate-900">
        <ThemeProvider>
          <Header />
          <main className="mx-auto w-full max-w-7xl px-4 sm:px-6 lg:px-8">{children}</main>
        </ThemeProvider>
      </body>
    </html>
  );
}
