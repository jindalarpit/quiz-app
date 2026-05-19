'use client';

import { useRouter } from 'next/navigation';
import { FormEvent, useCallback, useState } from 'react';

import { AnswerReveal } from '@/components/quiz/AnswerReveal';
import { QuestionDisplay } from '@/components/quiz/QuestionDisplay';
import { SessionEnd } from '@/components/quiz/SessionEnd';
import { TimerDisplay } from '@/components/quiz/TimerDisplay';
import { api } from '@/lib/api';
import { useWebSocket } from '@/lib/useWebSocket';
import { useSessionStore } from '@/stores/sessionStore';
import type { WSMessage } from '@/types';

type JoinStep = 'pin' | 'nickname' | 'lobby' | 'playing';

export default function JoinPage() {
  const router = useRouter();
  const [step, setStep] = useState<JoinStep>('pin');
  const [pinInput, setPinInput] = useState('');
  const [nicknameInput, setNicknameInput] = useState('');
  const [error, setError] = useState('');
  const [wsEnabled, setWsEnabled] = useState(false);

  const {
    pin,
    participantId,
    state,
    currentQuestion,
    selectedAnswer,
    answerAcknowledged,
    revealData,
    myScore,
    finalLeaderboard,
    sessionSummary,
    setPin,
    setParticipantId,
    setState,
    setCurrentQuestion,
    setSelectedAnswer,
    setAnswerAcknowledged,
    setRevealData,
    setMyRank,
    setMyScore,
    setFinalLeaderboard,
    setSessionSummary,
    resetSession,
  } = useSessionStore();

  const handleMessage = useCallback(
    (message: WSMessage) => {
      switch (message.type) {
        case 'session.started':
          setState('QUESTION_OPEN');
          setStep('playing');
          break;
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
          setStep('playing');
          break;
        }
        case 'question.closed':
          setState('QUESTION_CLOSED');
          break;
        case 'question.reveal': {
          const payload = message.payload as { questionId: string; correctAnswer: string; stats: Record<string, number>; yourScore?: number; yourStreak?: number; yourMultiplier?: number };
          setRevealData(payload);
          if (payload.yourScore !== undefined) setMyScore(payload.yourScore);
          setState('REVEAL');
          break;
        }
        case 'leaderboard.update': {
          const payload = message.payload as { yourRank?: number; yourScore?: number };
          if (payload.yourRank !== undefined) setMyRank(payload.yourRank);
          if (payload.yourScore !== undefined) setMyScore(payload.yourScore);
          break;
        }
        case 'answer.ack':
          setAnswerAcknowledged(true);
          break;
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
          if (payload.state === 'QUESTION_OPEN' && payload.previousState === 'LOBBY') {
            setStep('playing');
          }
          setState(payload.state as 'LOBBY' | 'QUESTION_OPEN' | 'QUESTION_CLOSED' | 'REVEAL' | 'PAUSED' | 'ENDED');
          break;
        }
        case 'participant.kicked':
          alert('You have been removed from the session.');
          resetSession();
          setStep('pin');
          break;
      }
    },
    [setState, setCurrentQuestion, setRevealData, setMyScore, setMyRank, setAnswerAcknowledged, setFinalLeaderboard, setSessionSummary, resetSession]
  );

  const { sendMessage } = useWebSocket({
    pin: pin || '',
    participantId: participantId || undefined,
    onMessage: handleMessage,
    enabled: wsEnabled && !!pin && !!participantId,
  });

  const handlePinSubmit = (e: FormEvent) => {
    e.preventDefault();
    setError('');
    const trimmed = pinInput.trim().toUpperCase();
    if (trimmed.length !== 6) {
      setError('PIN must be 6 characters');
      return;
    }
    setPin(trimmed);
    setStep('nickname');
  };

  const handleNicknameSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    const trimmed = nicknameInput.trim();
    if (trimmed.length < 3 || trimmed.length > 20) {
      setError('Nickname must be 3-20 characters');
      return;
    }
    if (!/^[a-zA-Z0-9 ]+$/.test(trimmed)) {
      setError('Only letters, numbers, and spaces allowed');
      return;
    }

    try {
      const response = await api.post<{ id: string }>(`/api/sessions/${pin}/join`, {
        nickname: trimmed,
      });
      setParticipantId(response.id);
      setWsEnabled(true);
      setStep('lobby');
    } catch (err: unknown) {
      const message = (err as { message?: string })?.message || 'Failed to join session';
      setError(message);
    }
  };

  const handleAnswerSelect = (optionId: string) => {
    if (selectedAnswer || state !== 'QUESTION_OPEN') return;
    setSelectedAnswer(optionId);
    sendMessage('answer.submit', {
      questionId: currentQuestion?.questionId,
      answer: optionId,
      clientTimestamp: Date.now(),
    });
  };

  // PIN input step
  if (step === 'pin') {
    return (
      <div className="flex min-h-[calc(100vh-4rem)] items-center justify-center p-4">
        <div className="w-full max-w-sm text-center">
          <h1 className="text-3xl font-bold text-slate-900 dark:text-white">Join a Quiz</h1>
          <p className="mt-2 text-slate-600 dark:text-slate-400">Enter the game PIN shown on screen</p>

          {error && (
            <div className="mt-4 rounded-lg bg-red-50 p-3 text-sm text-red-700 dark:bg-red-900/20 dark:text-red-400">
              {error}
            </div>
          )}

          <form onSubmit={handlePinSubmit} className="mt-6">
            <input
              type="text"
              value={pinInput}
              onChange={(e) => setPinInput(e.target.value.toUpperCase().slice(0, 6))}
              className="input-field text-center text-3xl font-mono tracking-widest"
              placeholder="______"
              maxLength={6}
              autoFocus
            />
            <button type="submit" className="btn-primary mt-4 w-full py-3 text-lg">
              Enter
            </button>
          </form>
        </div>
      </div>
    );
  }

  // Nickname input step
  if (step === 'nickname') {
    return (
      <div className="flex min-h-[calc(100vh-4rem)] items-center justify-center p-4">
        <div className="w-full max-w-sm text-center">
          <h1 className="text-3xl font-bold text-slate-900 dark:text-white">Choose a Nickname</h1>
          <p className="mt-2 text-slate-600 dark:text-slate-400">This is how others will see you</p>

          {error && (
            <div className="mt-4 rounded-lg bg-red-50 p-3 text-sm text-red-700 dark:bg-red-900/20 dark:text-red-400">
              {error}
            </div>
          )}

          <form onSubmit={handleNicknameSubmit} className="mt-6">
            <input
              type="text"
              value={nicknameInput}
              onChange={(e) => setNicknameInput(e.target.value)}
              className="input-field text-center text-xl"
              placeholder="Your nickname"
              maxLength={20}
              autoFocus
            />
            <button type="submit" className="btn-primary mt-4 w-full py-3 text-lg">
              Join
            </button>
          </form>
          <button
            onClick={() => {
              setStep('pin');
              setError('');
            }}
            className="mt-4 text-sm text-slate-500 hover:text-slate-700"
          >
            ← Back
          </button>
        </div>
      </div>
    );
  }

  // Lobby waiting step
  if (step === 'lobby') {
    return (
      <div className="flex min-h-[calc(100vh-4rem)] flex-col items-center justify-center p-4">
        <div className="text-center">
          <div className="mx-auto h-16 w-16 animate-pulse rounded-full bg-primary-100 dark:bg-primary-900" />
          <h1 className="mt-6 text-2xl font-bold text-slate-900 dark:text-white">You&apos;re in!</h1>
          <p className="mt-2 text-lg text-slate-600 dark:text-slate-400">
            Waiting for the host to start the quiz...
          </p>
          <p className="mt-4 text-sm text-slate-500">Game PIN: {pin}</p>
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
        onPlayAgain={() => {
          resetSession();
          setStep('pin');
          setWsEnabled(false);
          setPinInput('');
          setNicknameInput('');
          router.push('/join');
        }}
      />
    );
  }

  // Paused
  if (state === 'PAUSED') {
    return (
      <div className="flex min-h-[calc(100vh-4rem)] items-center justify-center p-4">
        <div className="text-center">
          <p className="text-3xl font-bold text-yellow-600">⏸ Paused</p>
          <p className="mt-2 text-slate-600 dark:text-slate-400">The host has paused the session</p>
        </div>
      </div>
    );
  }

  // Playing - question display
  return (
    <div className="flex min-h-[calc(100vh-4rem)] flex-col items-center justify-center p-4">
      {currentQuestion && state === 'QUESTION_OPEN' && (
        <div className="w-full max-w-lg">
          <TimerDisplay timeLimit={currentQuestion.timeLimit} serverTimestamp={currentQuestion.serverTimestamp} />
          <QuestionDisplay
            question={currentQuestion}
            selectedAnswer={selectedAnswer}
            onSelectAnswer={handleAnswerSelect}
            disabled={!!selectedAnswer}
          />
          {selectedAnswer && (
            <div className="mt-4 text-center">
              <p className="text-lg font-semibold text-primary-600">
                {answerAcknowledged ? '✓ Answer locked!' : 'Submitting...'}
              </p>
            </div>
          )}
        </div>
      )}

      {state === 'QUESTION_CLOSED' && (
        <div className="text-center">
          <p className="text-3xl font-bold text-slate-900 dark:text-white">⏰ Time&apos;s up!</p>
          {!selectedAnswer && (
            <p className="mt-2 text-slate-600 dark:text-slate-400">You didn&apos;t answer in time</p>
          )}
        </div>
      )}

      {state === 'REVEAL' && revealData && (
        <div className="w-full max-w-lg">
          <AnswerReveal
            data={revealData}
            options={currentQuestion?.options || []}
            selectedAnswer={selectedAnswer}
          />
          {myScore !== null && (
            <div className="mt-4 text-center">
              <p className="text-2xl font-bold text-primary-600 animate-pulse-score">
                +{revealData.yourScore || 0} points
              </p>
              <p className="text-sm text-slate-500">Total: {myScore}</p>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
