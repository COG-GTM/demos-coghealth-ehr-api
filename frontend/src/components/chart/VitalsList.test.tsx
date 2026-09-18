import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { VitalsList } from './VitalsList';
import { formatDateTime } from '../../utils';
import type { Vital } from '../../types';

function vital(id: number, recordedAt: string, overrides: Partial<Vital> = {}): Vital {
  return { id, patientId: 1, recordedAt, ...overrides };
}

const oldestFirst = [
  vital(1, '2024-01-02T10:00:00Z', { heartRate: 70, bloodPressureSystolic: 120, bloodPressureDiastolic: 80 }),
  vital(2, '2024-03-02T10:00:00Z', { heartRate: 30, bloodPressureSystolic: 185, bloodPressureDiastolic: 95 }),
];

describe('VitalsList compact', () => {
  it('shows the newest reading even when vitals arrive oldest-first', () => {
    render(<VitalsList vitals={oldestFirst} compact />);

    expect(screen.getByText('30 bpm')).toBeInTheDocument();
    expect(screen.getByText('185/95 mmHg')).toBeInTheDocument();
  });

  it('marks critical readings in bold danger styling', () => {
    render(<VitalsList vitals={oldestFirst} compact />);

    expect(screen.getByText('30 bpm').className).toContain('font-bold');
  });

  it('renders an empty state when there are no vitals', () => {
    render(<VitalsList vitals={[]} compact />);

    expect(screen.getByText('No vitals recorded')).toBeInTheDocument();
  });
});

describe('VitalsList full', () => {
  it('renders the empty state when there are no vitals', () => {
    render(<VitalsList vitals={[]} />);

    expect(screen.getByText('No vital signs have been recorded for this patient')).toBeInTheDocument();
  });

  it('shows the Record Vitals button only when onAdd is provided', () => {
    const { unmount } = render(<VitalsList vitals={oldestFirst} />);
    expect(screen.queryByRole('button', { name: 'Record Vitals' })).not.toBeInTheDocument();
    unmount();

    render(<VitalsList vitals={oldestFirst} onAdd={vi.fn()} />);
    expect(screen.getByRole('button', { name: 'Record Vitals' })).toBeInTheDocument();
  });

  it('shows the newest reading in the latest vitals card', () => {
    render(<VitalsList vitals={oldestFirst} />);

    expect(screen.getByText('Heart Rate').parentElement).toHaveTextContent('30');
    expect(screen.getByText('Blood Pressure').parentElement).toHaveTextContent('185/95');
  });

  it('lists history newest-first', () => {
    render(<VitalsList vitals={oldestFirst} />);

    const historyDates = screen
      .getAllByRole('row')
      .slice(1)
      .map((row) => row.firstElementChild?.textContent);
    expect(historyDates).toEqual([
      formatDateTime(oldestFirst[1].recordedAt),
      formatDateTime(oldestFirst[0].recordedAt),
    ]);
  });
});
