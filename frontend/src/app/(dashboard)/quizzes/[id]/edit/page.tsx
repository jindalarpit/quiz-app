'use client';

import { AnimatePresence, motion } from 'framer-motion';
import { useParams, useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';

import { MediaUploadWidget } from '@/components/quiz/MediaUploadWidget';
import { ScoringModeSelector } from '@/components/quiz/ScoringModeSelector';
import { useReducedMotion } from '@/hooks/useReducedMotion';
import { api } from '@/lib/api';
import { ANIMATION_TIMING } from '@/lib/constants';
import { useAuthStore } from '@/stores/authStore';
import { useQuizStore } from '@/stores/quizStore';
import type { QuestionType, ScoringMode } from '@/types';

interface QuestionFormData {
  type: QuestionType;
  text: string;
  options: { id: string; text: string }[];
  correctAnswer: string | null;
  timeLimitSeconds: number;
  points: number;
  mediaUrl?: string;
  pendingFile?: File;
}

const TIME_LIMITS = [5, 10, 15, 20, 30, 60];
const POINT_VALUES = [1000, 2000];

/** Left-border colors for answer options (red, blue, green, yellow) */
const OPTION_BORDER_COLORS = [
  'border-l-quiz-red',
  'border-l-quiz-blue',
  'border-l-quiz-green',
  'border-l-quiz-yellow',
];

/** Type indicator badge colors */
const TYPE_BADGE_COLORS: Record<QuestionType, string> = {
  MCQ: 'bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-300',
  TRUE_FALSE: 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-300',
  POLL: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-300',
};

function getDefaultOptions(type: QuestionType) {
  if (type === 'TRUE_FALSE') {
    return [
      { id: 'A', text: 'True' },
      { id: 'B', text: 'False' },
    ];
  }
  return [
    { id: 'A', text: '' },
    { id: 'B', text: '' },
    { id: 'C', text: '' },
    { id: 'D', text: '' },
  ];
}

export default function QuizEditorPage() {
  const params = useParams();
  const router = useRouter();
  const quizId = params.id as string;
  const { user } = useAuthStore();
  const { currentQuiz, isLoading, fetchQuiz, updateQuiz, updateScoringMode, addQuestion, updateQuestion, deleteQuestion } = useQuizStore();
  const reducedMotion = useReducedMotion();

  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [scoringMode, setScoringMode] = useState<ScoringMode>('SPEED_MATTERS');
  const [editingQuestion, setEditingQuestion] = useState<string | null>(null);
  const [showAddForm, setShowAddForm] = useState(false);
  const [questionForm, setQuestionForm] = useState<QuestionFormData>({
    type: 'MCQ',
    text: '',
    options: getDefaultOptions('MCQ'),
    correctAnswer: null,
    timeLimitSeconds: 20,
    points: 1000,
  });
  const [isImageUploading, setIsImageUploading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [newlyAddedId, setNewlyAddedId] = useState<string | null>(null);

  useEffect(() => {
    if (!user) {
      router.push('/login');
      return;
    }
    fetchQuiz(quizId);
  }, [user, quizId, router, fetchQuiz]);

  useEffect(() => {
    if (currentQuiz) {
      setTitle(currentQuiz.title);
      setDescription(currentQuiz.description || '');
      setScoringMode(currentQuiz.scoringMode || 'SPEED_MATTERS');
    }
  }, [currentQuiz]);

  const handleSaveMetadata = async () => {
    await updateQuiz(quizId, { title, description });
  };

  const handleScoringModeChange = async (mode: ScoringMode) => {
    setScoringMode(mode);
    await updateScoringMode(quizId, mode);
  };

  const handleTypeChange = (type: QuestionType) => {
    setQuestionForm({
      ...questionForm,
      type,
      options: getDefaultOptions(type),
      correctAnswer: null,
      points: type === 'POLL' ? 0 : questionForm.points,
      mediaUrl: questionForm.mediaUrl,
      pendingFile: questionForm.pendingFile,
    });
  };

  const uploadImage = async (questionId: string, file: File): Promise<string> => {
    const formData = new FormData();
    formData.append('file', file);

    const response = await api.request<{ url: string }>(
      `/api/quizzes/${quizId}/questions/${questionId}/image`,
      {
        method: 'POST',
        body: formData,
        headers: {}, // Let browser set Content-Type with boundary for multipart
      }
    );
    return response.url;
  };

  const handleAddQuestion = async () => {
    if (!questionForm.text.trim()) return;
    if (isImageUploading) return;

    setUploadError(null);

    const newQuestion = await addQuestion(quizId, {
      type: questionForm.type,
      text: questionForm.text,
      options: questionForm.options.filter((o) => o.text.trim()),
      correctAnswer: questionForm.type === 'POLL' ? null : questionForm.correctAnswer,
      timeLimitSeconds: questionForm.timeLimitSeconds,
      points: questionForm.type === 'POLL' ? 0 : questionForm.points,
      mediaUrl: questionForm.mediaUrl,
    });

    // If there's a pending file, upload it after the question is created
    if (questionForm.pendingFile && newQuestion) {
      setIsImageUploading(true);
      try {
        const url = await uploadImage(newQuestion.id, questionForm.pendingFile);
        // Update the question with the uploaded image URL
        await updateQuestion(quizId, newQuestion.id, { mediaUrl: url });
      } catch {
        setUploadError('Image upload failed. The question was saved without the image.');
        setIsImageUploading(false);
        return;
      }
      setIsImageUploading(false);
    }

    // Track newly added question for entrance animation
    if (newQuestion) {
      setNewlyAddedId(newQuestion.id);
      setTimeout(() => setNewlyAddedId(null), 500);
    }

    setShowAddForm(false);
    resetForm();
  };

  const handleUpdateQuestion = async (questionId: string) => {
    if (isImageUploading) return;

    setUploadError(null);

    // If there's a pending file, upload it first
    let mediaUrl = questionForm.mediaUrl;
    if (questionForm.pendingFile) {
      setIsImageUploading(true);
      try {
        mediaUrl = await uploadImage(questionId, questionForm.pendingFile);
      } catch {
        setUploadError('Image upload failed. Please try again or remove the image.');
        setIsImageUploading(false);
        return;
      }
      setIsImageUploading(false);
    }

    await updateQuestion(quizId, questionId, {
      type: questionForm.type,
      text: questionForm.text,
      options: questionForm.options.filter((o) => o.text.trim()),
      correctAnswer: questionForm.type === 'POLL' ? null : questionForm.correctAnswer,
      timeLimitSeconds: questionForm.timeLimitSeconds,
      points: questionForm.type === 'POLL' ? 0 : questionForm.points,
      mediaUrl,
    });
    setEditingQuestion(null);
    resetForm();
  };

  const handleDeleteQuestion = async (questionId: string) => {
    if (confirm('Delete this question?')) {
      await deleteQuestion(quizId, questionId);
    }
  };

  const startEditing = (questionId: string) => {
    const q = currentQuiz?.questions.find((question) => question.id === questionId);
    if (!q) return;
    setQuestionForm({
      type: q.type,
      text: q.text,
      options: q.options,
      correctAnswer: q.correctAnswer,
      timeLimitSeconds: q.timeLimitSeconds,
      points: q.points,
      mediaUrl: q.mediaUrl,
      pendingFile: undefined,
    });
    setEditingQuestion(questionId);
    setShowAddForm(false);
    setUploadError(null);
  };

  const resetForm = () => {
    setQuestionForm({
      type: 'MCQ',
      text: '',
      options: getDefaultOptions('MCQ'),
      correctAnswer: null,
      timeLimitSeconds: 20,
      points: 1000,
      mediaUrl: undefined,
      pendingFile: undefined,
    });
    setUploadError(null);
  };

  if (!user || isLoading) {
    return (
      <div className="flex min-h-[50vh] items-center justify-center">
        <div className="text-slate-500">Loading...</div>
      </div>
    );
  }

  if (!currentQuiz) return null;

  // Animation variants for new question card entrance
  const cardEntranceVariants = {
    hidden: { opacity: 0, y: -20 },
    visible: { opacity: 1, y: 0 },
  };

  const cardTransition = reducedMotion
    ? { duration: 0 }
    : { duration: ANIMATION_TIMING.cardEntrance / 1000, ease: 'easeOut' };

  return (
    <div className="py-8">
      {/* Quiz Metadata */}
      <div className="card">
        <h1 className="text-xl font-bold text-slate-900 dark:text-white">Edit Quiz</h1>
        <div className="mt-4 space-y-4">
          <div>
            <label htmlFor="edit-title" className="block text-sm font-medium text-slate-700 dark:text-slate-300">
              Title
            </label>
            <input
              id="edit-title"
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              className="input-field mt-1"
              maxLength={100}
            />
          </div>
          <div>
            <label htmlFor="edit-desc" className="block text-sm font-medium text-slate-700 dark:text-slate-300">
              Description
            </label>
            <textarea
              id="edit-desc"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              className="input-field mt-1"
              rows={2}
              maxLength={500}
            />
          </div>
          <ScoringModeSelector
            value={scoringMode}
            onChange={handleScoringModeChange}
          />
          <button onClick={handleSaveMetadata} className="btn-primary">
            Save
          </button>
        </div>
      </div>

      {/* Questions List */}
      <div className="mt-8">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-semibold text-slate-900 dark:text-white">
            Questions ({currentQuiz.questions.length})
          </h2>
          <button
            onClick={() => {
              resetForm();
              setShowAddForm(true);
              setEditingQuestion(null);
            }}
            className="btn-primary text-sm"
          >
            Add Question
          </button>
        </div>

        {/* Question cards with minimum 8px (gap-2) vertical spacing */}
        <div className="mt-4 space-y-3" style={{ gap: '8px' }}>
          <AnimatePresence initial={false}>
            {currentQuiz.questions.map((q, index) => {
              const isNewlyAdded = q.id === newlyAddedId;
              return (
                <motion.div
                  key={q.id}
                  data-question-card
                  data-question-index={index}
                  variants={cardEntranceVariants}
                  initial={isNewlyAdded ? 'hidden' : false}
                  animate="visible"
                  transition={cardTransition}
                  className="card"
                >
                  {editingQuestion === q.id ? (
                    <QuestionForm
                      form={questionForm}
                      setForm={setQuestionForm}
                      onTypeChange={handleTypeChange}
                      onSave={() => handleUpdateQuestion(q.id)}
                      onCancel={() => {
                        setEditingQuestion(null);
                        resetForm();
                      }}
                      saveLabel="Update"
                      isUploading={isImageUploading}
                      uploadError={uploadError}
                      reducedMotion={reducedMotion}
                    />
                  ) : (
                    <div className="flex items-start justify-between">
                      <div className="min-w-0 flex-1">
                        <div className="flex items-center gap-2">
                          {/* Numbered badge */}
                          <span
                            data-badge={`Q${index + 1}`}
                            className="inline-flex h-6 w-8 items-center justify-center rounded-md bg-primary-100 text-xs font-bold text-primary-700 dark:bg-primary-900/30 dark:text-primary-300"
                          >
                            Q{index + 1}
                          </span>
                          {/* Type indicator label with colored background */}
                          <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${TYPE_BADGE_COLORS[q.type]}`}>
                            {q.type.replace('_', '/')}
                          </span>
                          <span className="text-xs text-slate-500">{q.timeLimitSeconds}s • {q.points}pts</span>
                        </div>
                        <p className="mt-1 text-sm text-slate-900 dark:text-white">{q.text}</p>
                      </div>
                      <div className="ml-4 flex gap-2">
                        <button onClick={() => startEditing(q.id)} className="text-xs text-primary-600 hover:text-primary-700">
                          Edit
                        </button>
                        <button onClick={() => handleDeleteQuestion(q.id)} className="text-xs text-red-600 hover:text-red-700">
                          Delete
                        </button>
                      </div>
                    </div>
                  )}
                </motion.div>
              );
            })}
          </AnimatePresence>
        </div>

        {/* Add Question Form */}
        {showAddForm && (
          <div className="card mt-4">
            <QuestionForm
              form={questionForm}
              setForm={setQuestionForm}
              onTypeChange={handleTypeChange}
              onSave={handleAddQuestion}
              onCancel={() => {
                setShowAddForm(false);
                resetForm();
              }}
              saveLabel="Add Question"
              isUploading={isImageUploading}
              uploadError={uploadError}
              reducedMotion={reducedMotion}
            />
          </div>
        )}
      </div>
    </div>
  );
}

function QuestionForm({
  form,
  setForm,
  onTypeChange,
  onSave,
  onCancel,
  saveLabel,
  isUploading,
  uploadError,
  reducedMotion,
}: {
  form: QuestionFormData;
  setForm: (f: QuestionFormData) => void;
  onTypeChange: (type: QuestionType) => void;
  onSave: () => void;
  onCancel: () => void;
  saveLabel: string;
  isUploading?: boolean;
  uploadError?: string | null;
  reducedMotion?: boolean;
}) {
  /**
   * Get the left border color class for an option at the given index.
   * For TRUE_FALSE questions, only red and blue are used.
   */
  const getOptionBorderColor = (index: number): string => {
    if (form.type === 'TRUE_FALSE') {
      return index === 0 ? OPTION_BORDER_COLORS[0] : OPTION_BORDER_COLORS[1];
    }
    return OPTION_BORDER_COLORS[index] || OPTION_BORDER_COLORS[0];
  };

  return (
    <div className="space-y-4">
      {/* Type selector - pill-shaped buttons */}
      <div className="flex gap-2" role="group" aria-label="Question type">
        {(['MCQ', 'TRUE_FALSE', 'POLL'] as QuestionType[]).map((type) => (
          <button
            key={type}
            onClick={() => onTypeChange(type)}
            className={`rounded-pill px-4 py-1.5 text-xs font-medium transition-colors ${
              reducedMotion ? '' : 'duration-200'
            } ${
              form.type === type
                ? 'bg-primary-600 text-white shadow-sm'
                : 'border border-primary-300 bg-transparent text-primary-600 hover:border-primary-500 hover:text-primary-700 dark:border-primary-600 dark:text-primary-400 dark:hover:border-primary-400'
            }`}
          >
            {type.replace('_', '/')}
          </button>
        ))}
      </div>

      {/* Question text */}
      <div>
        <label className="block text-sm font-medium text-slate-700 dark:text-slate-300">Question</label>
        <input
          type="text"
          value={form.text}
          onChange={(e) => setForm({ ...form, text: e.target.value })}
          className="input-field mt-1"
          placeholder="Enter your question..."
        />
      </div>

      {/* Image upload */}
      <div>
        <label className="block text-sm font-medium text-slate-700 dark:text-slate-300">Image (optional)</label>
        <div className="mt-1">
          <MediaUploadWidget
            currentMediaUrl={form.mediaUrl}
            onMediaChange={(url) => {
              setForm({ ...form, mediaUrl: url, pendingFile: url ? form.pendingFile : undefined });
            }}
            onFileSelect={(file) => {
              setForm({ ...form, pendingFile: file });
            }}
            isUploading={isUploading}
          />
        </div>
        {uploadError && (
          <p className="mt-1 text-sm text-red-600" role="alert">
            {uploadError}
          </p>
        )}
      </div>

      {/* Options with colored left borders */}
      <div className="space-y-2">
        <label className="block text-sm font-medium text-slate-700 dark:text-slate-300">Options</label>
        {form.options.map((option, idx) => {
          const isCorrect = form.correctAnswer === option.id;
          const borderColorClass = getOptionBorderColor(idx);

          return (
            <div
              key={option.id}
              data-option-index={idx}
              className={`flex items-center gap-2 rounded-md border-l-4 pl-3 py-1.5 transition-colors ${
                reducedMotion ? '' : 'duration-200'
              } ${borderColorClass} ${
                isCorrect && form.type !== 'POLL'
                  ? 'bg-primary-50/60 dark:bg-primary-900/20'
                  : ''
              }`}
            >
              {form.type !== 'POLL' && (
                <input
                  type="radio"
                  name="correctAnswer"
                  checked={form.correctAnswer === option.id}
                  onChange={() => setForm({ ...form, correctAnswer: option.id })}
                  className="h-4 w-4 text-primary-600"
                  aria-label={`Mark option ${option.id} as correct`}
                />
              )}
              <input
                type="text"
                value={option.text}
                onChange={(e) => {
                  const newOptions = [...form.options];
                  newOptions[idx] = { ...option, text: e.target.value };
                  setForm({ ...form, options: newOptions });
                }}
                className="input-field flex-1"
                placeholder={`Option ${option.id}`}
                disabled={form.type === 'TRUE_FALSE'}
              />
            </div>
          );
        })}
        {form.type === 'MCQ' && form.options.length < 4 && (
          <button
            onClick={() =>
              setForm({
                ...form,
                options: [...form.options, { id: String.fromCharCode(65 + form.options.length), text: '' }],
              })
            }
            className="text-xs text-primary-600 hover:text-primary-700"
          >
            + Add option
          </button>
        )}
      </div>

      {/* Time limit and points */}
      <div className="flex gap-4">
        <div className="flex-1">
          <label className="block text-sm font-medium text-slate-700 dark:text-slate-300">Time Limit</label>
          <select
            value={form.timeLimitSeconds}
            onChange={(e) => setForm({ ...form, timeLimitSeconds: Number(e.target.value) })}
            className="input-field mt-1"
          >
            {TIME_LIMITS.map((t) => (
              <option key={t} value={t}>
                {t} seconds
              </option>
            ))}
          </select>
        </div>
        {form.type !== 'POLL' && (
          <div className="flex-1">
            <label className="block text-sm font-medium text-slate-700 dark:text-slate-300">Points</label>
            <select
              value={form.points}
              onChange={(e) => setForm({ ...form, points: Number(e.target.value) })}
              className="input-field mt-1"
            >
              {POINT_VALUES.map((p) => (
                <option key={p} value={p}>
                  {p} points
                </option>
              ))}
            </select>
          </div>
        )}
      </div>

      {/* Actions */}
      <div className="flex items-center justify-end gap-3">
        {form.type !== 'POLL' && !form.correctAnswer && (
          <span className="text-xs text-amber-600 dark:text-amber-400">
            Select the correct answer using the radio buttons
          </span>
        )}
        <button onClick={onCancel} className="btn-secondary text-sm">
          Cancel
        </button>
        <button
          onClick={onSave}
          disabled={!form.text.trim() || (form.type !== 'POLL' && !form.correctAnswer) || isUploading}
          className="btn-primary text-sm"
        >
          {isUploading ? 'Uploading...' : saveLabel}
        </button>
      </div>
    </div>
  );
}
