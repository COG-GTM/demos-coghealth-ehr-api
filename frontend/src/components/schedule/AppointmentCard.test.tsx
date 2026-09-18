import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { AppointmentCard } from './AppointmentCard';
import type { Appointment, EncounterStatus, Patient, Provider } from '../../types';

const patient: Patient = {
  id: 42,
  mrn: 'MRN-0042',
  firstName: 'Ada',
  lastName: 'Lovelace',
  dateOfBirth: '1990-04-01',
  gender: 'FEMALE',
  active: true,
  deceased: false,
  createdAt: '2024-01-01T00:00:00',
  updatedAt: '2024-01-01T00:00:00',
};

const provider: Provider = {
  id: 7,
  npi: '1234567890',
  firstName: 'Grace',
  lastName: 'Hopper',
  active: true,
};

function makeAppointment(overrides: Partial<Appointment> = {}): Appointment {
  return {
    id: 1,
    patient,
    provider,
    encounterType: 'OFFICE_VISIT',
    status: 'SCHEDULED',
    scheduledTime: '2024-05-06T09:30:00',
    duration: 30,
    ...overrides,
  };
}

function renderCard(appointment: Appointment, onAction?: (action: string, appointment: Appointment) => void) {
  return render(
    <MemoryRouter>
      <AppointmentCard appointment={appointment} onAction={onAction} />
    </MemoryRouter>
  );
}

function badgeFor(label: string): HTMLElement {
  const badge = screen.getByText(label);
  expect(badge).toHaveClass('badge');
  return badge;
}

describe('AppointmentCard status rendering', () => {
  it('renders the checked-in status with the success badge variant', () => {
    renderCard(makeAppointment({ status: 'CHECKED_IN' }));

    expect(badgeFor('Checked In')).toHaveClass('badge-success');
  });

  it('renders the no-show status with the danger badge variant', () => {
    renderCard(makeAppointment({ status: 'NO_SHOW' }));

    expect(badgeFor('No Show')).toHaveClass('badge-danger');
  });

  it('renders any other known status with the gray badge variant', () => {
    renderCard(makeAppointment({ status: 'IN_PROGRESS' }));

    expect(badgeFor('In Progress')).toHaveClass('badge-gray');
  });

  it('falls back to the scheduled config for an unknown status', () => {
    renderCard(makeAppointment({ status: 'RESCHEDULED' as EncounterStatus }));

    expect(badgeFor('Scheduled')).toHaveClass('badge-gray');
  });

  it('renders the scheduled time and patient summary', () => {
    renderCard(makeAppointment());

    expect(screen.getByText('9:30 AM')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Lovelace, Ada' })).toHaveAttribute('href', '/patients/42');
    expect(screen.getByText(/^F, \d+y$/)).toBeInTheDocument();
  });
});

describe('AppointmentCard encounter type label', () => {
  it('maps a known encounter type to its display label', () => {
    renderCard(makeAppointment({ encounterType: 'ANNUAL_PHYSICAL' }));

    expect(screen.getByText('Annual Physical')).toBeInTheDocument();
  });

  it('falls back to the raw value for an unknown encounter type', () => {
    renderCard(makeAppointment({ encounterType: 'HOME_VISIT' as Appointment['encounterType'] }));

    expect(screen.getByText('HOME_VISIT')).toBeInTheDocument();
  });
});

describe('AppointmentCard conditional sections', () => {
  it('renders the room and chief complaint when present', () => {
    renderCard(makeAppointment({ room: 'Exam 3', chiefComplaint: 'Persistent cough' }));

    expect(screen.getByText('Exam 3')).toBeInTheDocument();
    expect(screen.getByText(/CC: Persistent cough/)).toBeInTheDocument();
  });

  it('omits the room and chief complaint when absent', () => {
    renderCard(makeAppointment());

    expect(screen.queryByText('Exam 3')).not.toBeInTheDocument();
    expect(screen.queryByText(/CC:/)).not.toBeInTheDocument();
  });

  it('omits the action button when no handler is provided', () => {
    renderCard(makeAppointment());

    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });

  it('calls the handler with the menu action and the appointment', async () => {
    const onAction = vi.fn();
    const appointment = makeAppointment();
    renderCard(appointment, onAction);

    await userEvent.click(screen.getByRole('button'));

    expect(onAction).toHaveBeenCalledTimes(1);
    expect(onAction).toHaveBeenCalledWith('menu', appointment);
  });
});
