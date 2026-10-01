import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, act } from '@testing-library/react';
import { NoteEditor } from './NoteEditor';
import type { ClinicalNote, Patient, Provider } from '../../types';

const provider = {
  id: 1,
  npi: '1234567890',
  firstName: 'Sarah',
  lastName: 'Chen',
} as Provider;

const patient = {
  id: 1,
  mrn: 'MRN-001',
  firstName: 'Jane',
  lastName: 'Doe',
  dateOfBirth: '1980-01-01',
  gender: 'FEMALE',
  active: true,
  deceased: false,
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-01T00:00:00Z',
} as Patient;

const signedNote = {
  id: 42,
  patientId: 1,
  noteType: 'PROGRESS_NOTE',
  status: 'DRAFT',
  title: 'Office visit',
  author: provider,
  createdAt: '2024-01-01T00:00:00Z',
} as ClinicalNote;

const normalExam = 'General: Alert, oriented, no acute distress';

function getObjectiveTextarea() {
  return screen.getByPlaceholderText(/Physical examination findings/);
}

describe('NoteEditor SmartText expansion', () => {
  it('expands a trigger at the end of the value and keeps the preceding text', () => {
    render(<NoteEditor patient={patient} />);
    const objective = getObjectiveTextarea();

    fireEvent.change(objective, { target: { value: 'Exam: .normalexam' } });

    const value = (objective as HTMLTextAreaElement).value;
    expect(value.startsWith('Exam: ')).toBe(true);
    expect(value).toContain(normalExam);
    expect(value).not.toContain('.normalexam');
  });

  it('leaves a value that does not end with a trigger unchanged', () => {
    render(<NoteEditor patient={patient} />);
    const objective = getObjectiveTextarea();

    fireEvent.change(objective, { target: { value: '.normalexam is the shortcut' } });

    expect(objective).toHaveValue('.normalexam is the shortcut');
  });
});

describe('NoteEditor save', () => {
  it('calls onSave with the current fields and DRAFT status', () => {
    const onSave = vi.fn();
    render(<NoteEditor patient={patient} onSave={onSave} />);

    fireEvent.change(screen.getByPlaceholderText('Reason for visit...'), {
      target: { value: 'Cough' },
    });
    fireEvent.change(screen.getByPlaceholderText(/History of present illness/), {
      target: { value: 'Two days of cough' },
    });
    fireEvent.click(screen.getByRole('button', { name: /Save Draft/ }));

    expect(onSave).toHaveBeenCalledTimes(1);
    expect(onSave).toHaveBeenCalledWith({
      noteType: 'PROGRESS_NOTE',
      chiefComplaint: 'Cough',
      subjective: 'Two days of cough',
      objective: '',
      assessment: '',
      plan: '',
      status: 'DRAFT',
    });
  });
});

describe('NoteEditor autosave', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('autosaves the latest text once every 30s while dirty', () => {
    const onSave = vi.fn();
    render(<NoteEditor patient={patient} onSave={onSave} />);

    fireEvent.change(getObjectiveTextarea(), { target: { value: 'first' } });
    fireEvent.change(getObjectiveTextarea(), { target: { value: 'second' } });

    act(() => {
      vi.advanceTimersByTime(30000);
    });

    expect(onSave).toHaveBeenCalledTimes(1);
    expect(onSave).toHaveBeenCalledWith(expect.objectContaining({ objective: 'second' }));

    act(() => {
      vi.advanceTimersByTime(30000);
    });

    expect(onSave).toHaveBeenCalledTimes(1);
  });

  it('does not autosave when nothing has changed', () => {
    const onSave = vi.fn();
    render(<NoteEditor patient={patient} onSave={onSave} />);

    act(() => {
      vi.advanceTimersByTime(60000);
    });

    expect(onSave).not.toHaveBeenCalled();
  });
});

describe('NoteEditor sign', () => {
  it('calls onSign with the note id when the note is saved', () => {
    const onSign = vi.fn();
    render(<NoteEditor patient={patient} note={signedNote} onSign={onSign} />);

    fireEvent.click(screen.getByRole('button', { name: /Sign Note/ }));

    expect(onSign).toHaveBeenCalledWith(42);
  });

  it('does nothing when the note has not been saved yet', () => {
    const onSign = vi.fn();
    render(<NoteEditor patient={patient} onSign={onSign} />);

    fireEvent.click(screen.getByRole('button', { name: /Sign Note/ }));

    expect(onSign).not.toHaveBeenCalled();
  });
});

describe('NoteEditor templates', () => {
  it('sets the note type from the selected template and closes the modal', () => {
    render(<NoteEditor patient={patient} />);
    const noteTypeSelect = screen.getByRole('combobox');

    fireEvent.click(screen.getByRole('button', { name: /Templates/ }));
    fireEvent.click(screen.getByRole('button', { name: /Annual Physical/ }));

    expect(noteTypeSelect).toHaveValue('H_AND_P');
    expect(screen.queryByText('Note Templates')).not.toBeInTheDocument();
  });
});
