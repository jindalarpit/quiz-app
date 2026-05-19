'use client';

import { useParams, useRouter } from 'next/navigation';
import { useCallback, useEffect } from 'react';

import { AnswerReveal } from '@/components/quiz/AnswerReveal';
import { Leaderboard } from '@/components/quiz/Leaderboard';
import { QuestionDisplay } from '@/components/quiz/QuestionDisplay';
import { SessionEnd } from '@/components/quiz/SessionEnd';
import { TimerDisplay } from '@/components/quiz/TimerDisplay';
import { api } from '@/lib/api';
import { useWebSocket } from '@/lib/useWebSocket';
import { useAuthStore } from '@/stores/authStore';
import { useSessionStore } from '@/stores/sessionStore';
import type { WSMessage } from '@/types';

export default function HostSessionPage() {
  const params = useParams();
  const router = useRouter();
  const pin = params.pin as string;
  const { tokens } = useAuthStore();
  const {
    state,
    participants,
    participantCount,
    currentQuestion,
    revealData,
    leaderboard,
    finalLeaderboard,
    sessionSummary,
    setState,
    addParticipant,
    setParticipantCount,
    setCurrentQuestion,
    setRevealData,
    setLeaderboard,
    setFinalLeaderboard,
    setSessionSummary,
    resetSession,
  } = useSessionStore();

  const handleMessage = useCallback(
    (message: WSMessage) => {
      switch (message.type) {
        case 'session.joined': {
          const payload = message.payload as { participantId: string; nickname: string; count: number };
          addParticipant({ id: payload.participantId, nickname: payload.nickname, score: 0, streak: 0, isConnected: true });
          setParticipantCount(payload.count);
          break;
        }
        case 'question.start': {
          const payload = message.payload as { questionId: string; text: string; options: { id: string; text: string }[]; type: string; timeLimit: number; serverTimestamp: number };
          setCurrentQuestion({
            questionId: payload.questionId,
            text: payload.text,
            options: payload.options,
            type: payload.type as 'MCQ' | 'TRUE_FALSE' | 'POLL',
            timeLimit: payload.timeLimit,
            serverTimestamp: payload.serverTimestamp,
          });
          setState('QUESTION_OPEN');
          break;
        }
        case 'question.closed':
          setState('QUESTION_CLOSED');
          break;
        case 'question.reveal': {
          const payload = message.payload as { questionId: string; correctAnswer: string; stats: Record<string, number> };
          setRevealData({ questionId: payload.questionId, correctAnswer: payload.correctAnswer, stats: payload.stats });
          setState('REVEAL');
          break;
        }
        case 'leaderboard.update': {
          const payload = message.payload as { top5: { rank: number; participantId: string; nickname: string; score: number; rankChange: number }[] };
          setLeaderboard(payload.top5);
          break;
        }
        case 'session.ended': {
          const payload = message.payload as {
            leaderboard?: { rank: number; participantId?: string; nickname: string; score: number; rankChange?: number }[];
            finalLeaderboard?: { rank: number; participantId: string; nickname: string; score: number; rankChange: number }[];
            summary?: { totalQuestions: number; totalParticipants: number; durationSeconds: number };
          };
          const entries = (payload.finalLeaderboard || payload.leaderboard || []).map((e, i) => ({
            rank: e.rank,
            participantId: e.participantId || `participant-${i}`,
            nickname: e.nickname,
            score: e.score,
            rankChange: e.rankChange || 0,
          }));
          setFinalLeaderboard(entries);
          if (payload.summary) {
            setSessionSummary(payload.summary);
          }
          setState('ENDED');
          break;
        }
        case 'session.paused':
          setState('PAUSED');
          break;
        case 'session.resumed': {
          const payload = message.payload as { state: string };
          setState(payload.state as 'QUESTION_OPEN' | 'QUESTION_CLOSED' | 'REVEAL');
          break;
        }
        case 'session.state_changed': {
          const payload = message.payload as { state: string; previousState: string };
          setState(payload.state as 'LOBBY' | 'QUESTION_OPEN' | 'QUESTION_CLOSED' | 'REVEAL' | 'PAUSED' | 'ENDED');
          break;
        }
      }
    },
    [addParticipant, setParticipantCount, setCurrentQuestion, setState, setRevealData, setLeaderboard, setFinalLeaderboard, setSessionSummary]
  );

  const { connectionState } = useWebSocket({
    pin,
    token: tokens?.accessToken,
    onMessage: handleMessage,
    enabled: !!pin && !!tokens,
  });

  useEffect(() => {
    setState('LOBBY');
    return () => {
      resetSession();
    };
  }, [setState, resetSession]);

  const handleStartQuiz = async () => {
    try {
      await api.post(`/api/sessions/${pin}/next`);
    } catch (error: unknown) {
      const apiError = error as { message?: string; status?: number };
      console.error('Failed to start quiz:', apiError.message);
      // Could show a toast/notification here in the future
    }
  };

  const handleNextQuestion = async () => {
    await api.post(`/api/sessions/${pin}/next`);
  };

  const handleEndSession = async () => {
    await api.post(`/api/sessions/${pin}/end`);
  };

  // Lobby view
  if (state === 'LOBBY' || !state) {
    return (
      <div className="flex min-h-screen flex-col items-center justify-center p-4">
        <div className="text-center">
          <p className="text-sm font-medium text-slate-500 dark:text-slate-400">Join at</p>
          <p className="text-lg text-slate-600 dark:text-slate-300">quizplatform.com/join</p>
          <div className="mt-4 rounded-2xl bg-white p-8 shadow-lg dark:bg-slate-800">
            <p className="text-sm font-medium text-slate-500 dark:text-slate-400">Game PIN</p>
            <p className="mt-2 font-mono text-6xl font-bold tracking-widest text-slate-900 dark:text-white">
              {pin}
            </p>
          </div>
        </div>

        <div className="mt-8 w-full max-w-md">
          <div className="flex items-center justify-between">
            <h2 className="text-lg font-semibold text-slate-900 dark:text-white">
              Players ({participantCount})
            </h2>
            <span className={`h-2 w-2 rounded-full ${connectionState === 'connected' ? 'bg-green-500' : 'bg-yellow-500'}`} />
          </div>
          <div className="mt-4 flex flex-wrap gap-2">
            {participants.map((p) => (
              <span
                key={p.id}
                className="rounded-full bg-primary-100 px-3 py-1 text-sm font-medium text-primary-700 dark:bg-primary-900 dark:text-primary-300"
              >
                {p.nickname}
              </span>
            ))}
          </div>
        </div>

        <div className="mt-8 flex gap-4">
          <button
            onClick={handleStartQuiz}
            disabled={participantCount === 0}
            className="btn-primary px-8 py-3 text-lg"
          >
            Start Quiz
          </button>
          <button onClick={handleEndSession} className="btn-danger">
            End
          </button>
        </div>
      </div>
    );
  }

  // Session ended
  if (state === 'ENDED') {
    return (
      <SessionEnd
        leaderboard={finalLeaderboard}
        summary={sessionSummary}
        onPlayAgain={() => router.push('/dashboard')}
      />
    );
  }

  // Active game view
  return (
    <div className="flex min-h-screen flex-col items-center justify-center p-4">
      {state === 'PAUSED' && (
        <div className="mb-8 rounded-lg bg-yellow-100 px-6 py-3 text-lg font-semibold text-yellow-800">
          ⏸ Session Paused
        </div>
      )}

      {currentQuestion && state === 'QUESTION_OPEN' && (
        <div className="w-full max-w-3xl">
          <TimerDisplay
            timeLimit={currentQuestion.timeLimit}
            serverTimestamp={currentQuestion.serverTimestamp}
            onExpire={() => {
              api.post(`/api/sessions/${pin}/skip`).catch(() => {});
            }}
          />
          <QuestionDisplay question={currentQuestion} disabled />
          <div className="mt-6 text-center">
            <button
              onClick={() => api.post(`/api/sessions/${pin}/skip`).catch(() => {})}
              className="btn-primary px-8 py-3"
            >
              Skip → Close Question
            </button>
          </div>
        </div>
      )}

      {state === 'QUESTION_CLOSED' && currentQuestion && (
        <div className="text-center">
          <p className="text-2xl font-bold text-slate-900 dark:text-white">Time&apos;s up!</p>
          <button onClick={() => api.post(`/api/sessions/${pin}/reveal`)} className="btn-primary mt-4">
            Reveal Answer
          </button>
        </div>
      )}

      {state === 'REVEAL' && revealData && (
        <div className="w-full max-w-3xl">
          <AnswerReveal data={revealData} options={currentQuestion?.options || []} />
          <Leaderboard entries={leaderboard} />
          <div className="mt-6 text-center">
            <button onClick={handleNextQuestion} className="btn-primary px-8 py-3">
              Next Question
            </button>
          </div>
        </div>
      )}

      <div className="fixed bottom-4 right-4">
        <button onClick={handleEndSession} className="btn-danger text-sm">
          End Session
        </button>
      </div>
    </div>
  );
}
