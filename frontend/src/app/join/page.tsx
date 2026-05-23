'use client';

import { useRouter } from 'next/navigation';
import { FormEvent, useCallback, useEffect, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';

import { AnimatedLeaderboard } from '@/components/AnimatedLeaderboard';
import { AnswerReveal } from '@/components/quiz/AnswerReveal';
import { QuestionDisplay } from '@/components/quiz/QuestionDisplay';
import { SessionEnd } from '@/components/quiz/SessionEnd';
import { SpeedBonusIndicator } from '@/components/SpeedBonusIndicator';
import { TimerDisplay } from '@/components/quiz/TimerDisplay';
import { useReducedMotion } from '@/hooks/useReducedMotion';
import { api } from '@/lib/api';
import { buildParticipantDisplayView } from '@/lib/buildLeaderboardView';
import { computeScoreBreakdown } from '@/lib/computeScoreBreakdown';
import { useWebSocket } from '@/lib/useWebSocket';
import { useSessionStore } from '@/stores/sessionStore';
import type { LeaderboardUpdateEntry, WSMessage } from '@/types';

type JoinStep = 'pin' | 'nickname' | 'lobby' | 'playing';

export default function JoinPage() {
  const router = useRouter();
  const prefersReducedMotion = useReducedMotion();
  const [step, setStep] = useState<JoinStep>('pin');
  const [pinSlots, setPinSlots] = useState<string[]>(['', '', '', '', '', '']);
  const [nicknameInput, setNicknameInput] = useState('');
  const [error, setError] = useState('');
  const [wsEnabled, setWsEnabled] = useState(false);
  const pinInputRefs = useRef<(HTMLInputElement | null)[]>([]);

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
    leaderboardAnimation,
    revealLeaderboard,
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
    updateLeaderboardEntries,
    setRoundScoreBreakdown,
    setRevealLeaderboard,
  } = useSessionStore();

  // Focus first PIN slot on mount
  useEffect(() => {
    if (step === 'pin') {
      pinInputRefs.current[0]?.focus();
    }
  }, [step]);

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
          const payload = message.payload as { yourRank?: number; yourScore?: number; top5?: { rank: number; nickname: string; score: number }[] };
          if (payload.yourRank !== undefined) setMyRank(payload.yourRank);
          if (payload.yourScore !== undefined) setMyScore(payload.yourScore);
          if (payload.top5 && payload.top5.length > 0) {
            setRevealLeaderboard(payload.top5);
          }
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
          const accepted = updateLeaderboardEntries(payload.entries, payload.sequenceNumber, payload.roundNumber);
          if (accepted && participantId) {
            const myEntry = payload.entries.find((e) => e.participantId === participantId);
            const breakdown = computeScoreBreakdown(myEntry);
            setRoundScoreBreakdown(breakdown);
          }
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
    [setState, setCurrentQuestion, setRevealData, setMyScore, setMyRank, setAnswerAcknowledged, setFinalLeaderboard, setSessionSummary, resetSession, updateLeaderboardEntries, setRoundScoreBreakdown, setRevealLeaderboard, participantId]
  );

  const { sendMessage } = useWebSocket({
    pin: pin || '',
    participantId: participantId || undefined,
    onMessage: handleMessage,
    enabled: wsEnabled && !!pin && !!participantId,
  });

  // PIN slot handlers
  const handlePinSlotChange = (index: number, value: string) => {
    const char = value.toUpperCase().slice(-1);
    const newSlots = [...pinSlots];
    newSlots[index] = char;
    setPinSlots(newSlots);

    // Auto-advance to next slot
    if (char && index < 5) {
      pinInputRefs.current[index + 1]?.focus();
    }
  };

  const handlePinSlotKeyDown = (index: number, e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Backspace' && !pinSlots[index] && index > 0) {
      pinInputRefs.current[index - 1]?.focus();
    }
  };

  const handlePinSlotPaste = (e: React.ClipboardEvent<HTMLInputElement>) => {
    e.preventDefault();
    const pasted = e.clipboardData.getData('text').toUpperCase().slice(0, 6);
    const newSlots = [...pinSlots];
    for (let i = 0; i < 6; i++) {
      newSlots[i] = pasted[i] || '';
    }
    setPinSlots(newSlots);
    // Focus last filled slot or the next empty one
    const lastIndex = Math.min(pasted.length, 5);
    pinInputRefs.current[lastIndex]?.focus();
  };

  const handlePinSubmit = (e: FormEvent) => {
    e.preventDefault();
    setError('');
    const trimmed = pinSlots.join('').trim();
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

  // Animation variants
  const fadeInUp = prefersReducedMotion
    ? { initial: {}, animate: {}, transition: { duration: 0 } }
    : {
        initial: { opacity: 0, y: 20 },
        animate: { opacity: 1, y: 0 },
        transition: { duration: 0.4, ease: 'easeOut' },
      };

  const errorSlideIn = prefersReducedMotion
    ? { initial: {}, animate: {}, exit: {}, transition: { duration: 0 } }
    : {
        initial: { opacity: 0, x: -20, height: 0 },
        animate: { opacity: 1, x: 0, height: 'auto' },
        exit: { opacity: 0, x: -20, height: 0 },
        transition: { duration: 0.3, ease: 'easeOut' },
      };

  // PIN input step
  if (step === 'pin') {
    return (
      <div className="flex min-h-[calc(100vh-4rem)] items-center justify-center p-4">
        <motion.div
          className="w-full max-w-sm text-center"
          {...fadeInUp}
        >
          <h1 className="text-3xl font-bold text-slate-900 dark:text-white">Join a Quiz</h1>
          <p className="mt-2 text-slate-600 dark:text-slate-400">Enter the game PIN shown on screen</p>

          <AnimatePresence>
            {error && (
              <motion.div
                key="pin-error"
                {...errorSlideIn}
                className="mt-4 rounded-lg bg-red-50 border border-red-200 p-3 text-sm text-red-700 dark:bg-red-900/20 dark:border-red-800 dark:text-red-400"
              >
                {error}
              </motion.div>
            )}
          </AnimatePresence>

          <form onSubmit={handlePinSubmit} className="mt-6">
            <div className="flex justify-center" style={{ gap: '10px' }}>
              {pinSlots.map((slot, index) => (
                <input
                  key={index}
                  ref={(el) => { pinInputRefs.current[index] = el; }}
                  type="text"
                  inputMode="text"
                  value={slot}
                  onChange={(e) => handlePinSlotChange(index, e.target.value)}
                  onKeyDown={(e) => handlePinSlotKeyDown(index, e)}
                  onPaste={index === 0 ? handlePinSlotPaste : undefined}
                  className="w-12 h-14 text-center font-mono font-bold rounded-lg border border-slate-300 bg-white text-slate-900 transition-colors focus:border-primary-500 focus:outline-none focus:ring-2 focus:ring-primary-500/20 dark:border-slate-600 dark:bg-slate-800 dark:text-white"
                  style={{ fontSize: '24px' }}
                  maxLength={1}
                  aria-label={`PIN digit ${index + 1}`}
                />
              ))}
            </div>
            <button
              type="submit"
              className="mt-6 w-full py-3 text-lg font-semibold text-white rounded-lg transition-all focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 hover:shadow-lg"
              style={{
                background: 'linear-gradient(135deg, #0ea5e9, #0369a1)',
              }}
            >
              Enter
            </button>
          </form>
        </motion.div>
      </div>
    );
  }

  // Nickname input step
  if (step === 'nickname') {
    return (
      <div className="flex min-h-[calc(100vh-4rem)] items-center justify-center p-4">
        <motion.div
          className="w-full max-w-sm"
          {...fadeInUp}
        >
          <div className="relative overflow-hidden rounded-2xl border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-700 dark:bg-slate-800" style={{ borderRadius: '16px' }}>
            {/* Gradient accent element */}
            <div
              className="absolute top-0 left-0 right-0 h-1"
              style={{ background: 'linear-gradient(90deg, #0ea5e9, #38bdf8, #7dd3fc)' }}
            />

            <div className="text-center">
              <h1 className="text-3xl font-bold text-slate-900 dark:text-white">Choose a Nickname</h1>
              <p className="mt-2 text-slate-600 dark:text-slate-400">This is how others will see you</p>
            </div>

            <AnimatePresence>
              {error && (
                <motion.div
                  key="nickname-error"
                  {...errorSlideIn}
                  className="mt-4 rounded-lg bg-red-50 border border-red-200 p-3 text-sm text-red-700 dark:bg-red-900/20 dark:border-red-800 dark:text-red-400"
                >
                  {error}
                </motion.div>
              )}
            </AnimatePresence>

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
              <button
                type="submit"
                className="mt-4 w-full py-3 text-lg font-semibold text-white rounded-lg transition-all focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 hover:shadow-lg"
                style={{
                  background: 'linear-gradient(135deg, #0ea5e9, #0369a1)',
                }}
              >
                Join
              </button>
            </form>
            <button
              onClick={() => {
                setStep('pin');
                setError('');
                setPinSlots(['', '', '', '', '', '']);
              }}
              className="mt-4 w-full text-sm text-slate-500 hover:text-slate-700 dark:hover:text-slate-300"
            >
              ← Back
            </button>
          </div>
        </motion.div>
      </div>
    );
  }

  // Lobby waiting step
  if (step === 'lobby') {
    return (
      <div className="flex min-h-[calc(100vh-4rem)] flex-col items-center justify-center p-4">
        <motion.div
          className="text-center"
          {...fadeInUp}
        >
          {/* Animated pulsing indicator */}
          <div className="relative mx-auto h-16 w-16">
            <div
              className={`absolute inset-0 rounded-full bg-primary-400/30 ${prefersReducedMotion ? '' : 'animate-ping'}`}
            />
            <div
              className={`relative h-16 w-16 rounded-full bg-gradient-to-br from-primary-400 to-primary-600 ${prefersReducedMotion ? '' : 'animate-pulse'}`}
            />
          </div>
          <h1 className="mt-6 text-2xl font-bold text-slate-900 dark:text-white">You&apos;re in!</h1>
          <p className="mt-2 text-lg text-slate-600 dark:text-slate-400">
            Waiting for the host to start the quiz...
          </p>
          <p className="mt-4 font-semibold text-slate-700 dark:text-slate-300" style={{ fontSize: '18px' }}>
            Game PIN: <span className="font-mono">{pin}</span>
          </p>
        </motion.div>
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
          setPinSlots(['', '', '', '', '', '']);
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
          <TimerDisplay timeLimit={currentQuestion.timeLimit} serverTimestamp={currentQuestion.serverTimestamp} questionId={currentQuestion.questionId} sessionState={state} />
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
          {leaderboardAnimation.roundScoreBreakdown && (
            <div className="mt-4">
              <SpeedBonusIndicator
                baseComponent={leaderboardAnimation.roundScoreBreakdown.baseComponent}
                speedBonus={leaderboardAnimation.roundScoreBreakdown.speedBonus}
                streakMultiplier={leaderboardAnimation.roundScoreBreakdown.streakMultiplier}
                totalScore={leaderboardAnimation.roundScoreBreakdown.totalScore}
                speedPercentage={leaderboardAnimation.roundScoreBreakdown.speedPercentage}
                isCorrect={leaderboardAnimation.roundScoreBreakdown.isCorrect}
              />
            </div>
          )}
          {!leaderboardAnimation.roundScoreBreakdown && myScore !== null && (
            <div className="mt-4 text-center">
              <p className="text-2xl font-bold text-primary-600 animate-pulse-score">
                +{revealData.yourScore || 0} points
              </p>
              <p className="text-sm text-slate-500">Total: {myScore}</p>
            </div>
          )}
          {leaderboardAnimation.currentEntries.length > 0 && participantId && (
            <div className="mt-4">
              <AnimatedLeaderboard
                entries={buildParticipantDisplayView(leaderboardAnimation.currentEntries, participantId)}
                previousEntries={buildParticipantDisplayView(leaderboardAnimation.previousEntries, participantId)}
                isHost={false}
                roundNumber={leaderboardAnimation.roundNumber}
                animationPhase={leaderboardAnimation.animationPhase}
              />
            </div>
          )}
          {leaderboardAnimation.currentEntries.length === 0 && revealLeaderboard && revealLeaderboard.length > 0 && (
            <div className="mt-4">
              <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-800">
                <h3 className="mb-3 text-center text-lg font-semibold text-slate-900 dark:text-white">Leaderboard</h3>
                <div className="space-y-2">
                  {revealLeaderboard.map((entry) => (
                    <div
                      key={`${entry.rank}-${entry.nickname}`}
                      className="flex items-center justify-between rounded-lg bg-slate-50 px-3 py-2 dark:bg-slate-700/50"
                    >
                      <div className="flex items-center gap-3">
                        <span className="flex h-7 w-7 items-center justify-center rounded-full bg-primary-100 text-sm font-bold text-primary-700 dark:bg-primary-900/30 dark:text-primary-300">
                          {entry.rank}
                        </span>
                        <span className="font-medium text-slate-800 dark:text-slate-200">{entry.nickname}</span>
                      </div>
                      <span className="font-semibold text-slate-900 dark:text-white">{entry.score}</span>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
