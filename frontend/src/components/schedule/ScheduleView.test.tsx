import { describe, it, expect, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { format, addDays, startOfWeek } from 'date-fns';
import { ScheduleView } from './ScheduleView';
import type { Appointment, Patient, Provider } from '../../types';

const patient: Patient = {
  id: 1,
  mrn: 'MRN-001',
  firstName: 'Ada',
  lastName: 'Lovelace',
  dateOfBirth: '1980-01-01',
  gender: 'FEMALE',
  active: true,
  deceased: false,
  createdAt: '2024-01-01T00:00:00',
  updatedAt: '2024-01-01T00:00:00',
};

const provider: Provider = {
  id: 1,
  npi: '1234567890',
  firstName: 'Grace',
  lastName: 'Hopper',
  active: true,
};

function makeAppointment(id: number, scheduledTime: string, lastName: string): Appointment {
  return {
    id,
    patient: { ...patient, id, lastName },
    provider,
    encounterType: 'OFFICE_VISIT',
    status: 'SCHEDULED',
    scheduledTime,
    duration: 30,
  };
}

const selectedDate = new Date(2024, 2, 13, 12, 0, 0);

function slotLabels(container: HTMLElement): string[] {
  return Array.from(container.querySelectorAll('.w-20')).map((el) => el.textContent ?? '');
}

function slotRow(container: HTMLElement, label: string): HTMLElement {
  const gutter = Array.from(container.querySelectorAll('.w-20')).find(
    (el) => el.textContent === label
  );
  if (!gutter?.parentElement) throw new Error(`No time slot for ${label}`);
  return gutter.parentElement;
}

function renderScheduleView(overrides: Partial<Parameters<typeof ScheduleView>[0]> = {}) {
  const onDateChange = vi.fn();
  const onViewChange = vi.fn();
  const { container } = render(
    <MemoryRouter>
      <ScheduleView
        appointments={[]}
        selectedDate={selectedDate}
        onDateChange={onDateChange}
        view="day"
        onViewChange={onViewChange}
        {...overrides}
      />
    </MemoryRouter>
  );
  return { onDateChange, onViewChange, container };
}

describe('ScheduleView navigation', () => {
  it('shifts by one day in day view', async () => {
    const { onDateChange } = renderScheduleView({ view: 'day' });
    const [prev, next] = screen.getAllByRole('button').slice(1, 3);

    await userEvent.click(prev);
    expect(onDateChange).toHaveBeenLastCalledWith(new Date(2024, 2, 12, 12, 0, 0));

    await userEvent.click(next);
    expect(onDateChange).toHaveBeenLastCalledWith(new Date(2024, 2, 14, 12, 0, 0));
  });

  it('shifts by one week in week view', async () => {
    const { onDateChange } = renderScheduleView({ view: 'week' });
    const [prev, next] = screen.getAllByRole('button').slice(1, 3);

    await userEvent.click(prev);
    expect(onDateChange).toHaveBeenLastCalledWith(new Date(2024, 2, 6, 12, 0, 0));

    await userEvent.click(next);
    expect(onDateChange).toHaveBeenLastCalledWith(new Date(2024, 2, 20, 12, 0, 0));
  });
});

describe('ScheduleView day view', () => {
  const appointments = [
    makeAppointment(1, '2024-03-13T07:00:00', 'Early'),
    makeAppointment(2, '2024-03-13T18:30:00', 'Late'),
    makeAppointment(3, '2024-03-13T21:00:00', 'Evening'),
    makeAppointment(4, '2024-03-13T06:00:00', 'Dawn'),
  ];

  it('renders the 7am-6pm slots only', () => {
    const { container } = renderScheduleView({ view: 'day', appointments });
    const labels = slotLabels(container);

    expect(labels).toHaveLength(12);
    expect(labels[0]).toBe('7:00 AM');
    expect(labels[labels.length - 1]).toBe('6:00 PM');
    expect(labels).not.toContain('6:00 AM');
    expect(labels).not.toContain('9:00 PM');
  });

  it('places in-window appointments in their hour slot and drops out-of-window ones', () => {
    const { container } = renderScheduleView({ view: 'day', appointments });

    expect(within(slotRow(container, '7:00 AM')).getByText(/Early, Ada/)).toBeInTheDocument();
    expect(within(slotRow(container, '6:00 PM')).getByText(/Late, Ada/)).toBeInTheDocument();

    expect(screen.queryByText(/Evening, Ada/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Dawn, Ada/)).not.toBeInTheDocument();
  });

  it('marks empty slots as available', () => {
    renderScheduleView({ view: 'day', appointments });
    expect(screen.getAllByText('Available')).toHaveLength(10);
  });
});

describe('ScheduleView week view', () => {
  const weekStart = startOfWeek(selectedDate);
  const wednesday = addDays(weekStart, 3);
  const appointments = [
    makeAppointment(1, `${format(weekStart, 'yyyy-MM-dd')}T09:00:00`, 'Sunday'),
    makeAppointment(2, `${format(wednesday, 'yyyy-MM-dd')}T22:30:00`, 'Wednesday'),
    makeAppointment(3, `${format(addDays(weekStart, 14), 'yyyy-MM-dd')}T09:00:00`, 'OtherWeek'),
  ];

  it('groups appointments under their own day column', () => {
    const { container } = render(
      <MemoryRouter>
        <ScheduleView
          appointments={appointments}
          selectedDate={selectedDate}
          onDateChange={vi.fn()}
          onViewChange={vi.fn()}
          view="week"
        />
      </MemoryRouter>
    );

    const columns = Array.from(container.querySelectorAll('.grid > div')) as HTMLElement[];
    expect(columns).toHaveLength(7);
    expect(within(columns[0]).getByText(/Sunday, Ada/)).toBeInTheDocument();
    expect(within(columns[3]).getByText(/Wednesday, Ada/)).toBeInTheDocument();
    expect(within(columns[1]).queryByText(/Ada/)).not.toBeInTheDocument();
  });

  it('excludes appointments from other weeks', () => {
    renderScheduleView({ view: 'week', appointments });
    expect(screen.queryByText(/OtherWeek, Ada/)).not.toBeInTheDocument();
  });
});
