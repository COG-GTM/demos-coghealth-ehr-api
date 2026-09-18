import { describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AllergyList } from './AllergyList';
import type { Allergy } from '../../types';

function makeAllergy(overrides: Partial<Allergy> = {}): Allergy {
  return {
    id: 1,
    patientId: 100,
    allergen: 'Penicillin',
    allergenType: 'MEDICATION',
    reaction: 'Hives',
    severity: 'SEVERE',
    status: 'ACTIVE',
    recordedAt: '2024-01-15T10:00:00Z',
    ...overrides,
  };
}

describe('AllergyList compact', () => {
  it('shows the active allergy count and no NKDA badge when an active allergy exists', () => {
    render(<AllergyList compact allergies={[makeAllergy()]} />);

    expect(screen.queryByText('NKDA')).not.toBeInTheDocument();
    expect(screen.getByText('1')).toBeInTheDocument();
    expect(screen.getByText('Penicillin')).toBeInTheDocument();
    expect(screen.getByText('Hives')).toBeInTheDocument();
  });

  it('counts only ACTIVE allergies', () => {
    render(
      <AllergyList
        compact
        allergies={[
          makeAllergy({ id: 1 }),
          makeAllergy({ id: 2, allergen: 'Peanuts', allergenType: 'FOOD' }),
          makeAllergy({ id: 3, allergen: 'Latex', status: 'INACTIVE' }),
        ]}
      />
    );

    expect(screen.getByText('2')).toBeInTheDocument();
    expect(screen.queryByText('Latex')).not.toBeInTheDocument();
  });

  it('shows NKDA only when no allergy is ACTIVE', () => {
    render(
      <AllergyList
        compact
        allergies={[
          makeAllergy({ id: 1, status: 'INACTIVE' }),
          makeAllergy({ id: 2, status: 'ENTERED_IN_ERROR' }),
        ]}
      />
    );

    expect(screen.getByText('NKDA')).toBeInTheDocument();
    expect(screen.getByText('No known drug allergies')).toBeInTheDocument();
    expect(screen.queryByText('Penicillin')).not.toBeInTheDocument();
  });

  it('renders the severity of each active allergy', () => {
    render(
      <AllergyList
        compact
        allergies={[
          makeAllergy({ id: 1, severity: 'LIFE_THREATENING' }),
          makeAllergy({ id: 2, allergen: 'Peanuts', severity: 'MILD' }),
        ]}
      />
    );

    expect(screen.getByText('LIFE_THREATENING')).toBeInTheDocument();
    expect(screen.getByText('MILD')).toBeInTheDocument();
  });
});

describe('AllergyList full', () => {
  it('splits active allergies into medication and other sections', () => {
    render(
      <AllergyList
        allergies={[
          makeAllergy({ id: 1, allergen: 'Penicillin', allergenType: 'MEDICATION' }),
          makeAllergy({ id: 2, allergen: 'Peanuts', allergenType: 'FOOD' }),
          makeAllergy({ id: 3, allergen: 'Pollen', allergenType: 'ENVIRONMENTAL' }),
          makeAllergy({ id: 4, allergen: 'Latex', allergenType: 'OTHER' }),
        ]}
      />
    );

    const medicationSection = screen.getByText('Medication Allergies').parentElement!;
    const otherSection = screen.getByText('Other Allergies').parentElement!;

    expect(within(medicationSection).getByText('Penicillin')).toBeInTheDocument();
    expect(within(medicationSection).queryByText('Peanuts')).not.toBeInTheDocument();

    for (const allergen of ['Peanuts', 'Pollen', 'Latex']) {
      expect(within(otherSection).getByText(allergen)).toBeInTheDocument();
    }
  });

  it('omits a section when no active allergy belongs to it', () => {
    render(<AllergyList allergies={[makeAllergy({ allergenType: 'MEDICATION' })]} />);

    expect(screen.getByText('Medication Allergies')).toBeInTheDocument();
    expect(screen.queryByText('Other Allergies')).not.toBeInTheDocument();
  });

  it('offers NKDA documentation and the empty state only when no allergy is active', () => {
    const { rerender } = render(
      <AllergyList allergies={[makeAllergy({ status: 'INACTIVE' })]} />
    );

    expect(screen.getByRole('button', { name: /document nkda/i })).toBeInTheDocument();
    expect(screen.getByText('No Known Drug Allergies')).toBeInTheDocument();

    rerender(<AllergyList allergies={[makeAllergy()]} />);

    expect(screen.queryByRole('button', { name: /document nkda/i })).not.toBeInTheDocument();
    expect(screen.queryByText('No Known Drug Allergies')).not.toBeInTheDocument();
  });

  it('renders severity and type labels for each allergy row', () => {
    render(<AllergyList allergies={[makeAllergy({ severity: 'MODERATE', allergenType: 'FOOD' })]} />);

    expect(screen.getByText('MODERATE')).toBeInTheDocument();
    expect(screen.getByText('Food')).toBeInTheDocument();
    expect(screen.getByText('Reaction: Hives')).toBeInTheDocument();
  });

  it('shows the add allergy controls only when onAdd is provided', () => {
    const { rerender } = render(<AllergyList allergies={[makeAllergy()]} />);
    expect(screen.queryByRole('button', { name: /add allergy/i })).not.toBeInTheDocument();

    rerender(<AllergyList allergies={[makeAllergy()]} onAdd={vi.fn()} />);
    expect(screen.getByRole('button', { name: /add allergy/i })).toBeInTheDocument();
  });

  it('opens the add allergy modal', async () => {
    const user = userEvent.setup();
    render(<AllergyList allergies={[makeAllergy()]} onAdd={vi.fn()} />);

    await user.click(screen.getByRole('button', { name: /add allergy/i }));

    expect(screen.getByText('Add Allergy', { selector: 'h2' })).toBeInTheDocument();
    expect(screen.getByPlaceholderText('Search or enter allergen...')).toBeInTheDocument();
  });
});
