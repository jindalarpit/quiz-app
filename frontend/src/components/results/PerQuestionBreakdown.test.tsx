/**
 * @vitest-environment jsdom
 */
import React from 'react';
import { describe, expect, it } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';

import {
  PerQuestionBreakdown,
  type QuestionBreakdownItem,
} from './PerQuestionBreakdown';

const sampleBreakdown: QuestionBreakdownItem[] = [
  { questionNumber: 1, status: 'CORRECT' },
  { questionNumber: 2, status: 'INCORRECT' },
  { questionNumber: 3, status: 'UNANSWERED' },
];

describe('PerQuestionBreakdown', () => {
  it('renders nothing when questionBreakdown is empty', () => {
    const { container } = render(<PerQuestionBreakdown questionBreakdown={[]} />);
    expect(container.firstChild).toBeNull();
  });

  it('renders the expand button when there are questions', () => {
    render(<PerQuestionBreakdown questionBreakdown={sampleBreakdown} />);
    expect(screen.getByRole('button', { name: /per-question breakdown/i })).toBeInTheDocument();
  });

  it('starts collapsed by default', () => {
    render(<PerQuestionBreakdown questionBreakdown={sampleBreakdown} />);
    const button = screen.getByRole('button');
    expect(button).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByRole('list')).not.toBeInTheDocument();
  });

  it('expands to show questions when clicked', () => {
    render(<PerQuestionBreakdown questionBreakdown={sampleBreakdown} />);
    const button = screen.getByRole('button');

    fireEvent.click(button);

    expect(button).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByRole('list')).toBeInTheDocument();
    expect(screen.getAllByRole('listitem')).toHaveLength(3);
  });

  it('collapses when clicked again', () => {
    render(<PerQuestionBreakdown questionBreakdown={sampleBreakdown} />);
    const button = screen.getByRole('button');

    fireEvent.click(button);
    fireEvent.click(button);

    expect(button).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByRole('list')).not.toBeInTheDocument();
  });

  it('displays correct status labels for each question', () => {
    render(<PerQuestionBreakdown questionBreakdown={sampleBreakdown} />);
    fireEvent.click(screen.getByRole('button'));

    expect(screen.getByText('Question 1')).toBeInTheDocument();
    expect(screen.getByText('Correct')).toBeInTheDocument();
    expect(screen.getByText('Question 2')).toBeInTheDocument();
    expect(screen.getByText('Incorrect')).toBeInTheDocument();
    expect(screen.getByText('Question 3')).toBeInTheDocument();
    expect(screen.getByText('Unanswered')).toBeInTheDocument();
  });

  it('provides accessible labels for each question status', () => {
    render(<PerQuestionBreakdown questionBreakdown={sampleBreakdown} />);
    fireEvent.click(screen.getByRole('button'));

    expect(screen.getByLabelText('Question 1: Correct')).toBeInTheDocument();
    expect(screen.getByLabelText('Question 2: Incorrect')).toBeInTheDocument();
    expect(screen.getByLabelText('Question 3: Unanswered')).toBeInTheDocument();
  });
});
