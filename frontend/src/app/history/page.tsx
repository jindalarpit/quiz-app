'use client';

import { useRouter } from 'next/navigation';

import { QuizHistoryPage } from '@/components/history/QuizHistoryPage';

export default function HistoryPage() {
  const router = useRouter();

  return (
    <QuizHistoryPage
      onSessionSelect={(sessionId) => router.push(`/history/${sessionId}`)}
    />
  );
}
