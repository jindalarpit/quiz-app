'use client';

import { AnimatePresence, motion } from 'framer-motion';
import { useParams, useRouter } from 'next/navigation';
import { useCallback, useEffect, useRef, useState } from 'react';

import { AnimatedLeaderboard } from '@/components/AnimatedLeaderboard';
import { AnswerReveal } from '@/components/quiz/AnswerReveal';
import { Leaderboard } from '@/components/quiz/Leaderboard';
import { QuestionDisplay } from '@/components/quiz/QuestionDisplay';
import { SessionEnd } from '@/components/quiz/SessionEnd';
import { TimerDisplay } from '@/components/quiz/TimerDisplay';
import { useReducedMotion } from '@/hooks/useReducedMotion';
import { api } from '@/lib/api';
import { buildHostView } from '@/lib/buildLeaderboardView';
import { ANIMATION_TIMING } from '@/lib/constants';
import { useWebSocket } from '@/lib/useWebSocket';
import { useAuthStore } from '@/stores/authStore';
import { useSessionStore } from '@/stores/sessionStore';
import type { LeaderboardUpdateEntry, WSMessage } from '@/types';

/** Cross-fade animation variants for state transitions */
const crossFadeVariants = {
  initial: { opacity: 0 },
  animate: { opacity: 1 },
  exit: { opacity: 0 },
};

export default function HostSessionPage() {
  const params = useParams();
  const router = useRouter();
  const pin = params.pin as string;
  const { tokens } = useAuthStore();
  const prefersReducedMotion = useReducedMotion();
  const [isTransitioning, setIsTransitioning] = useState(false);
  const {
    state,
    participants,
    participantCount,
    currentQuestion,
    revealData,
    leaderboard,
    finalLeaderboard,
    sessionSummary,
    leaderboardAnimation,
    setState,
    addParticipant,
    setParticipantCount,
    setCurrentQuestion,
    setRevealData,
    setLeaderboard,
    setFinalLeaderboard,
    setSessionSummary,
    resetSession,
    updateLeaderboardEntries,
  } = useSessionStore();

  const questionInfoRef = useRef({ questionNumber: 0, totalQuestions: 0 });

  /** Duration for cross-fade in seconds (framer-motion uses seconds) */
  const crossFadeDuration = prefersReducedMotion ? 0 : ANIMATION_TIMING.crossFade / 1000;

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
          const payload = message.payload as { questionId: string; text: string; options: { id: string; text: string }[]; type: string; timeLimit: number; serverTimestamp: number; questionNumber?: number; totalQuestions?: number };
          setCurrentQuestion({
            questionId: payload.questionId,
            text: payload.text,
            options: payload.options,
            type: payload.type as 'MCQ' | 'TRUE_FALSE' | 'POLL',
            timeLimit: payload.timeLimit,
            serverTimestamp: payload.serverTimestamp,
          });
          if (payload.questionNumber != null && payload.totalQuestions != null) {
            questionInfoRef.current = { questionNumber: payload.questionNumber, totalQuestions: payload.totalQuestions };
          }
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
        case 'leaderboard.updated': {
          const payload = message.payload as {
            sessionId: string;
            roundNumber: number;
            sequenceNumber: number;
            timestamp: number;
            entries: LeaderboardUpdateEntry[];
          };
          // Update store with new entries (handles sequence validation and stores previous entries)
          updateLeaderboardEntries(payload.entries, payload.sequenceNumber, payload.roundNumber);
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
          const payload = message.payload as { state: string; previousState: string; questionNumber?: number; totalQuestions?: number };
          // Fallback: update questionInfoRef from state change event if available
          if (payload.questionNumber != null && payload.totalQuestions != null) {
            questionInfoRef.current = { questionNumber: payload.questionNumber, totalQuestions: payload.totalQuestions };
          }
          setState(payload.state as 'LOBBY' | 'QUESTION_OPEN' | 'QUESTION_CLOSED' | 'REVEAL' | 'PAUSED' | 'ENDED');
          break;
        }
      }
    },
    [addParticipant, setParticipantCount, setCurrentQuestion, setState, setRevealData, setLeaderboard, setFinalLeaderboard, setSessionSummary, updateLeaderboardEntries]
  );

  const { connectionState } = useWebSocket({
    pin,
    token: tokens?.accessToken,
    onMessage: handleMessage,
    enabled: !!pin && !!tokens,
  });

  useEffect(() => {
    setState('LOBBY');

    // Fallback: fetch session info on mount to initialize questionInfoRef
    // if the question.start event was missed (e.g., reconnection after question started)
    const initQuestionInfo = async () => {
      try {
        const sessionInfo = await api.get<{ state: string; questionNumber?: number; totalQuestions?: number }>(`/api/sessions/${pin}`);
        if (sessionInfo.questionNumber != null && sessionInfo.totalQuestions != null && questionInfoRef.current.questionNumber === 0 && questionInfoRef.current.totalQuestions === 0) {
          questionInfoRef.current = { questionNumber: sessionInfo.questionNumber, totalQuestions: sessionInfo.totalQuestions };
        }
      } catch {
        // Non-critical: fallback will use safe default in REVEAL state
      }
    };
    initQuestionInfo();

    return () => {
      resetSession();
    };
  }, [setState, resetSession, pin]);

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

  // Determine the current view key for AnimatePresence
  const viewKey = state === 'ENDED' ? 'ended' : state === 'LOBBY' || !state ? 'lobby' : 'active';

  return (
    <AnimatePresence
      mode="wait"
      onExitComplete={() => setIsTransitioning(false)}
    >
      <motion.div
        key={viewKey}
        variants={crossFadeVariants}
        initial="initial"
        animate="animate"
        exit="exit"
        transition={{ duration: crossFadeDuration }}
        onAnimationStart={() => {
          if (!prefersReducedMotion) setIsTransitioning(true);
        }}
        onAnimationComplete={() => setIsTransitioning(false)}
      >
        {/* Lobby view */}
        {(state === 'LOBBY' || !state) && (
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
                disabled={participantCount === 0 || isTransitioning}
                className="btn-primary px-8 py-3 text-lg"
                aria-disabled={isTransitioning}
              >
                Start Quiz
              </button>
              <button
                onClick={handleEndSession}
                disabled={isTransitioning}
                className="btn-danger"
                aria-disabled={isTransitioning}
              >
                End
              </button>
            </div>
          </div>
        )}

        {/* Session ended */}
        {state === 'ENDED' && (
          <SessionEnd
            leaderboard={finalLeaderboard}
            summary={sessionSummary}
            onPlayAgain={() => router.push('/dashboard')}
          />
        )}

        {/* Active game view */}
        {state !== 'LOBBY' && state !== 'ENDED' && state && (
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
                    disabled={isTransitioning}
                    className="btn-primary px-8 py-3"
                    aria-disabled={isTransitioning}
                  >
                    Skip → Close Question
                  </button>
                </div>
              </div>
            )}

            {state === 'QUESTION_CLOSED' && currentQuestion && (
              <div className="text-center">
                <p className="text-2xl font-bold text-slate-900 dark:text-white">Time&apos;s up!</p>
                <button
                  onClick={() => api.post(`/api/sessions/${pin}/reveal`)}
                  disabled={isTransitioning}
                  className="btn-primary mt-4"
                  aria-disabled={isTransitioning}
                >
                  Reveal Answer
                </button>
              </div>
            )}

            {state === 'REVEAL' && revealData && (
              <div className="w-full max-w-3xl">
                <AnswerReveal data={revealData} options={currentQuestion?.options || []} />
                {leaderboardAnimation.currentEntries.length > 0 ? (
                  <AnimatedLeaderboard
                    entries={buildHostView(leaderboardAnimation.currentEntries)}
                    previousEntries={buildHostView(leaderboardAnimation.previousEntries)}
                    isHost={true}
                    roundNumber={leaderboardAnimation.roundNumber}
                    animationPhase={leaderboardAnimation.animationPhase}
                  />
                ) : (
                  <Leaderboard entries={leaderboard} />
                )}
                <div className="mt-6 text-center">
                  {questionInfoRef.current.totalQuestions === 0 || questionInfoRef.current.questionNumber < questionInfoRef.current.totalQuestions ? (
                    <button
                      onClick={handleNextQuestion}
                      disabled={isTransitioning}
                      className="btn-primary px-8 py-3"
                      aria-disabled={isTransitioning}
                    >
                      Next Question
                    </button>
                  ) : (
                    <button
                      onClick={handleEndSession}
                      disabled={isTransitioning}
                      className="btn-primary px-8 py-3"
                      aria-disabled={isTransitioning}
                    >
                      End Quiz
                    </button>
                  )}
                </div>
              </div>
            )}

            <div className="fixed bottom-4 right-4">
              <button
                onClick={handleEndSession}
                disabled={isTransitioning}
                className="btn-danger text-sm"
                aria-disabled={isTransitioning}
              >
                End Session
              </button>
            </div>
          </div>
        )}
      </motion.div>
    </AnimatePresence>
  );
}
