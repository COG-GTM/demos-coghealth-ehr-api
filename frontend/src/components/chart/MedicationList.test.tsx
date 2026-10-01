import { describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MedicationList } from './MedicationList';
import type { Medication } from '../../types';

function makeMedication(overrides: Partial<Medication> & Pick<Medication, 'id' | 'drugName'>): Medication {
  return {
    patientId: 1,
    genericName: undefined,
    dose: '500',
    doseUnit: 'mg',
    route: 'Oral',
    frequency: 'Once daily',
    sig: 'Take 1 tablet by mouth once daily',
    startDate: '2024-01-01',
    status: 'ACTIVE',
    isPrn: false,
    prescribedAt: '2024-01-01T00:00:00Z',
    ...overrides,
  };
}

const lisinopril = makeMedication({ id: 1, drugName: 'Lisinopril' });
const metformin = makeMedication({ id: 2, drugName: 'Metformin' });
const ibuprofen = makeMedication({ id: 3, drugName: 'Ibuprofen', isPrn: true });
const warfarin = makeMedication({ id: 4, drugName: 'Warfarin', status: 'DISCONTINUED' });
const amoxicillin = makeMedication({ id: 5, drugName: 'Amoxicillin', status: 'COMPLETED' });

const allMeds = [lisinopril, metformin, ibuprofen, warfarin, amoxicillin];

function section(title: string) {
  const header = screen.getByText(title);
  const card = header.closest('.card');
  if (!card) throw new Error(`No card found for section "${title}"`);
  return within(card as HTMLElement);
}

describe('MedicationList', () => {
  it('partitions active medications into scheduled and PRN sections by default', () => {
    render(<MedicationList medications={allMeds} />);

    const scheduled = section('Scheduled Medications');
    expect(scheduled.getByText('Lisinopril')).toBeInTheDocument();
    expect(scheduled.getByText('Metformin')).toBeInTheDocument();
    expect(scheduled.queryByText('Ibuprofen')).not.toBeInTheDocument();

    const prn = section('PRN Medications');
    expect(prn.getByText('Ibuprofen')).toBeInTheDocument();
    expect(prn.queryByText('Lisinopril')).not.toBeInTheDocument();

    expect(screen.queryByText('Warfarin')).not.toBeInTheDocument();
    expect(screen.queryByText('Amoxicillin')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Active \(3\)/ })).toBeInTheDocument();
  });

  it('omits the PRN section when no active medication is PRN', () => {
    render(<MedicationList medications={[lisinopril, warfarin]} />);

    expect(screen.getByText('Scheduled Medications')).toBeInTheDocument();
    expect(screen.queryByText('PRN Medications')).not.toBeInTheDocument();
  });

  it('shows only discontinued medications in a flat list on the Discontinued tab', async () => {
    const user = userEvent.setup();
    render(<MedicationList medications={allMeds} />);

    await user.click(screen.getByRole('button', { name: 'Discontinued' }));

    expect(screen.getByText('Warfarin')).toBeInTheDocument();
    expect(screen.queryByText('Lisinopril')).not.toBeInTheDocument();
    expect(screen.queryByText('Ibuprofen')).not.toBeInTheDocument();
    expect(screen.queryByText('Scheduled Medications')).not.toBeInTheDocument();
    expect(screen.queryByText('PRN Medications')).not.toBeInTheDocument();
  });

  it('shows every medication in a flat list on the All tab', async () => {
    const user = userEvent.setup();
    render(<MedicationList medications={allMeds} />);

    await user.click(screen.getByRole('button', { name: 'All' }));

    for (const med of allMeds) {
      expect(screen.getByText(med.drugName)).toBeInTheDocument();
    }
    expect(screen.queryByText('Scheduled Medications')).not.toBeInTheDocument();
  });

  it('renders the empty state when the filter matches nothing', async () => {
    const user = userEvent.setup();
    render(<MedicationList medications={[lisinopril]} />);

    await user.click(screen.getByRole('button', { name: 'Discontinued' }));

    expect(screen.getByText('No medications found')).toBeInTheDocument();
    expect(screen.queryByText('Lisinopril')).not.toBeInTheDocument();
  });

  it('calls onRefill with the medication id', async () => {
    const user = userEvent.setup();
    const onRefill = vi.fn();
    render(<MedicationList medications={[lisinopril, ibuprofen]} onRefill={onRefill} />);

    await user.click(section('PRN Medications').getByTitle('Refill'));

    expect(onRefill).toHaveBeenCalledTimes(1);
    expect(onRefill).toHaveBeenCalledWith(ibuprofen.id);
  });

  it('calls onDiscontinue with the medication id and a reason', async () => {
    const user = userEvent.setup();
    const onDiscontinue = vi.fn();
    render(<MedicationList medications={[lisinopril]} onDiscontinue={onDiscontinue} />);

    await user.click(screen.getByTitle('Discontinue'));

    expect(onDiscontinue).toHaveBeenCalledTimes(1);
    expect(onDiscontinue).toHaveBeenCalledWith(lisinopril.id, '');
  });

  it('hides refill and discontinue actions for medications that are not active', async () => {
    const user = userEvent.setup();
    render(<MedicationList medications={allMeds} onRefill={vi.fn()} onDiscontinue={vi.fn()} />);

    await user.click(screen.getByRole('button', { name: 'All' }));

    expect(screen.getAllByTitle('Refill')).toHaveLength(3);
    expect(screen.getAllByTitle('Discontinue')).toHaveLength(3);
    expect(screen.getAllByTitle('History')).toHaveLength(allMeds.length);
  });

  it('opens the add medication modal from the New Rx button', async () => {
    const user = userEvent.setup();
    render(<MedicationList medications={allMeds} onAdd={vi.fn()} />);

    await user.click(screen.getByRole('button', { name: /New Rx/ }));

    expect(screen.getByText('New Prescription')).toBeInTheDocument();
  });

  it('hides the add medication affordances when onAdd is not provided', () => {
    render(<MedicationList medications={allMeds} />);

    expect(screen.queryByRole('button', { name: /New Rx/ })).not.toBeInTheDocument();
  });

  describe('compact mode', () => {
    it('lists at most five active medications and counts the rest', () => {
      const meds = Array.from({ length: 7 }, (_, i) =>
        makeMedication({ id: i + 1, drugName: `Drug ${i + 1}` })
      );
      render(<MedicationList medications={[...meds, warfarin]} compact />);

      for (let i = 1; i <= 5; i += 1) {
        expect(screen.getByText(`Drug ${i}`)).toBeInTheDocument();
      }
      expect(screen.queryByText('Drug 6')).not.toBeInTheDocument();
      expect(screen.getByText('+2 more')).toBeInTheDocument();
      expect(screen.queryByText('Warfarin')).not.toBeInTheDocument();
    });

    it('shows a placeholder when no medication is active', () => {
      render(<MedicationList medications={[warfarin]} compact />);

      expect(screen.getByText('No active medications')).toBeInTheDocument();
    });
  });
});
