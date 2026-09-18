import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { PatientHeader } from './PatientHeader';
import { makeAllergy, makePatient } from '../../test/fixtures';
import type { Allergy, Patient } from '../../types';

function renderHeader(props: { patient?: Patient; allergies?: Allergy[]; alerts?: string[]; compact?: boolean } = {}) {
  const { patient = makePatient(), ...rest } = props;
  return render(
    <MemoryRouter>
      <PatientHeader patient={patient} {...rest} />
    </MemoryRouter>
  );
}

function allergyBanner() {
  return screen.getByText('Allergies:').closest('div') as HTMLElement;
}

describe('PatientHeader allergy banner', () => {
  it('highlights the banner as severe for a LIFE_THREATENING active allergy', () => {
    renderHeader({ allergies: [makeAllergy({ allergen: 'Peanuts', severity: 'LIFE_THREATENING' })] });

    expect(screen.getByText('Peanuts')).toBeInTheDocument();
    expect(allergyBanner()).toHaveClass('bg-danger-50');
  });

  it('highlights the banner as severe for a SEVERE active allergy', () => {
    renderHeader({ allergies: [makeAllergy({ severity: 'SEVERE' })] });

    expect(allergyBanner()).toHaveClass('bg-danger-50');
  });

  it('uses the warning style when only mild or moderate allergies are active', () => {
    renderHeader({
      allergies: [makeAllergy({ severity: 'MILD' }), makeAllergy({ id: 2, severity: 'MODERATE' })],
    });

    expect(allergyBanner()).toHaveClass('bg-warning-50');
  });

  it('ignores severe allergies that are no longer active', () => {
    renderHeader({
      allergies: [
        makeAllergy({ allergen: 'Latex', severity: 'LIFE_THREATENING', status: 'INACTIVE' }),
        makeAllergy({ id: 2, allergen: 'Dust', severity: 'MILD' }),
      ],
    });

    expect(allergyBanner()).toHaveClass('bg-warning-50');
    expect(screen.getByText('Dust')).toBeInTheDocument();
    expect(screen.queryByText(/Latex/)).not.toBeInTheDocument();
  });

  it('renders no allergy banner when no allergy is active', () => {
    renderHeader({ allergies: [makeAllergy({ severity: 'SEVERE', status: 'ENTERED_IN_ERROR' })] });

    expect(screen.queryByText('Allergies:')).not.toBeInTheDocument();
  });

  it('renders extra alerts as badges', () => {
    renderHeader({ alerts: ['Fall Risk'] });

    expect(screen.getByText('Fall Risk')).toBeInTheDocument();
  });
});

describe('PatientHeader status badges', () => {
  it('shows the deceased badge when the patient is deceased', () => {
    renderHeader({ patient: makePatient({ deceased: true }) });

    expect(screen.getByText('Deceased')).toBeInTheDocument();
    expect(screen.queryByText('Inactive')).not.toBeInTheDocument();
  });

  it('shows the inactive badge when the patient record is not active', () => {
    renderHeader({ patient: makePatient({ active: false }) });

    expect(screen.getByText('Inactive')).toBeInTheDocument();
  });

  it('shows no status badge for an active, living patient', () => {
    renderHeader();

    expect(screen.queryByText('Deceased')).not.toBeInTheDocument();
    expect(screen.queryByText('Inactive')).not.toBeInTheDocument();
  });
});

describe('PatientHeader demographics', () => {
  it('renders name, MRN, formatted DOB with age and gender', () => {
    renderHeader({ patient: makePatient({ dateOfBirth: '1980-05-10' }) });

    expect(screen.getByRole('link', { name: 'Lovelace, Ada' })).toHaveAttribute('href', '/patients/1');
    expect(screen.getByText('MRN: MRN-001')).toBeInTheDocument();
    expect(screen.getByText(/DOB: 05\/10\/1980 \(\d+y\)/)).toBeInTheDocument();
    expect(screen.getByText('F')).toBeInTheDocument();
  });

  it('renders the preferred language only when present', () => {
    const { unmount } = renderHeader();
    expect(screen.queryByText('Spanish')).not.toBeInTheDocument();
    unmount();

    renderHeader({ patient: makePatient({ preferredLanguage: 'Spanish' }) });
    expect(screen.getByText('Spanish')).toBeInTheDocument();
  });

  it('renders the contact row with formatted phone, email and city/state', () => {
    renderHeader({
      patient: makePatient({
        phoneMobile: '5551234567',
        email: 'ada@example.com',
        address: { street1: '1 Main St', city: 'Boston', state: 'MA', zipCode: '02108' },
      }),
    });

    expect(screen.getByText('(555) 123-4567')).toBeInTheDocument();
    expect(screen.getByText('ada@example.com')).toBeInTheDocument();
    expect(screen.getByText('Boston, MA')).toBeInTheDocument();
  });

  it('hides the contact row in compact mode', () => {
    renderHeader({
      compact: true,
      patient: makePatient({ phoneMobile: '5551234567', email: 'ada@example.com' }),
    });

    expect(screen.queryByText('(555) 123-4567')).not.toBeInTheDocument();
    expect(screen.queryByText('ada@example.com')).not.toBeInTheDocument();
    expect(screen.getByText('MRN: MRN-001')).toBeInTheDocument();
  });
});
