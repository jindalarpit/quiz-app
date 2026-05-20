'use client';

import { useParams, useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';

import { ScoringModeSelector } from '@/components/quiz/ScoringModeSelector';
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
}

const TIME_LIMITS = [5, 10, 15, 20, 30, 60];
const POINT_VALUES = [1000, 2000];

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
    });
  };

  const handleAddQuestion = async () => {
    if (!questionForm.text.trim()) return;
    await addQuestion(quizId, {
      type: questionForm.type,
      text: questionForm.text,
      options: questionForm.options.filter((o) => o.text.trim()),
      correctAnswer: questionForm.type === 'POLL' ? null : questionForm.correctAnswer,
      timeLimitSeconds: questionForm.timeLimitSeconds,
      points: questionForm.type === 'POLL' ? 0 : questionForm.points,
    });
    setShowAddForm(false);
    resetForm();
  };

  const handleUpdateQuestion = async (questionId: string) => {
    await updateQuestion(quizId, questionId, {
      type: questionForm.type,
      text: questionForm.text,
      options: questionForm.options.filter((o) => o.text.trim()),
      correctAnswer: questionForm.type === 'POLL' ? null : questionForm.correctAnswer,
      timeLimitSeconds: questionForm.timeLimitSeconds,
      points: questionForm.type === 'POLL' ? 0 : questionForm.points,
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
    });
    setEditingQuestion(questionId);
    setShowAddForm(false);
  };

  const resetForm = () => {
    setQuestionForm({
      type: 'MCQ',
      text: '',
      options: getDefaultOptions('MCQ'),
      correctAnswer: null,
      timeLimitSeconds: 20,
      points: 1000,
    });
  };

  if (!user || isLoading) {
    return (
      <div className="flex min-h-[50vh] items-center justify-center">
        <div className="text-slate-500">Loading...</div>
      </div>
    );
  }

  if (!currentQuiz) return null;

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

        <div className="mt-4 space-y-3">
          {currentQuiz.questions.map((q, index) => (
            <div key={q.id} className="card">
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
                />
              ) : (
                <div className="flex items-start justify-between">
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <span className="text-sm font-medium text-slate-500">Q{index + 1}</span>
                      <span className="rounded bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-600 dark:bg-slate-700 dark:text-slate-300">
                        {q.type}
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
            </div>
          ))}
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
}: {
  form: QuestionFormData;
  setForm: (f: QuestionFormData) => void;
  onTypeChange: (type: QuestionType) => void;
  onSave: () => void;
  onCancel: () => void;
  saveLabel: string;
}) {
  return (
    <div className="space-y-4">
      {/* Type selector */}
      <div className="flex gap-2">
        {(['MCQ', 'TRUE_FALSE', 'POLL'] as QuestionType[]).map((type) => (
          <button
            key={type}
            onClick={() => onTypeChange(type)}
            className={`rounded-lg px-3 py-1.5 text-xs font-medium transition-colors ${
              form.type === type
                ? 'bg-primary-600 text-white'
                : 'bg-slate-100 text-slate-600 hover:bg-slate-200 dark:bg-slate-700 dark:text-slate-300'
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

      {/* Options */}
      <div className="space-y-2">
        <label className="block text-sm font-medium text-slate-700 dark:text-slate-300">Options</label>
        {form.options.map((option, idx) => (
          <div key={option.id} className="flex items-center gap-2">
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
        ))}
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
          disabled={!form.text.trim() || (form.type !== 'POLL' && !form.correctAnswer)}
          className="btn-primary text-sm"
        >
          {saveLabel}
        </button>
      </div>
    </div>
  );
}
