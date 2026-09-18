import { screen, waitFor } from '@testing-library/react';
import { useOutletContext } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { patientsApi } from '../../api';
import { ChartLayout } from './ChartLayout';
import { renderRoute } from '../../test/utils';
import type { Allergy, Patient } from '../../types';

vi.mock('../../api', () => ({
  patientsApi: {
    getById: vi.fn(),
    getAllergies: vi.fn(),
  },
}));

const getById = vi.mocked(patientsApi.getById);
const getAllergies = vi.mocked(patientsApi.getAllergies);

const patient: Patient = {
  id: 1,
  mrn: 'MRN-0001',
  firstName: 'Ada',
  lastName: 'Lovelace',
  dateOfBirth: '1980-12-10',
  gender: 'FEMALE',
  active: true,
  deceased: false,
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-01T00:00:00Z',
};

const allergies: Allergy[] = [
  {
    id: 10,
    patientId: 1,
    allergen: 'Penicillin',
    allergenType: 'MEDICATION',
    severity: 'SEVERE',
    status: 'ACTIVE',
    recordedAt: '2024-01-02T00:00:00Z',
  },
];

function OutletProbe() {
  const context = useOutletContext<{ patient?: Patient; allergies?: Allergy[] }>();
  return (
    <div>
      <span data-testid="outlet-patient">{context.patient?.mrn}</span>
      <span data-testid="outlet-allergies">{context.allergies?.map((a) => a.allergen).join(',')}</span>
    </div>
  );
}

function renderChart(initialEntry = '/patients/1') {
  return renderRoute({
    initialEntries: [initialEntry],
    path: '/patients/:patientId',
    element: <ChartLayout />,
    children: <OutletProbe />,
  });
}

describe('ChartLayout', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders skeletons while the patient query is pending', () => {
    getById.mockReturnValue(new Promise<Patient>(() => {}));
    getAllergies.mockResolvedValue([]);

    const { container } = renderChart();

    expect(container.querySelectorAll('.skeleton')).toHaveLength(2);
    expect(screen.queryByText('Patient not found')).not.toBeInTheDocument();
  });

  it('renders the not-found state when the patient request fails', async () => {
    getById.mockRejectedValue(new Error('404'));
    getAllergies.mockResolvedValue([]);

    const { container } = renderChart();

    expect(await screen.findByText('Patient not found')).toBeInTheDocument();
    expect(container.querySelectorAll('.skeleton')).toHaveLength(0);
  });

  it('renders the not-found state without loading when patientId is not numeric', () => {
    const { container } = renderChart('/patients/abc');

    expect(screen.getByText('Patient not found')).toBeInTheDocument();
    expect(container.querySelectorAll('.skeleton')).toHaveLength(0);
    expect(getById).not.toHaveBeenCalled();
    expect(getAllergies).not.toHaveBeenCalled();
  });

  it('renders the header, sidebar and outlet once the patient loads', async () => {
    getById.mockResolvedValue(patient);
    getAllergies.mockResolvedValue(allergies);

    renderChart();

    expect(await screen.findByText('Lovelace, Ada')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Problems/ })).toBeInTheDocument();
    await waitFor(() => expect(screen.getByTestId('outlet-allergies')).toHaveTextContent('Penicillin'));
    expect(screen.getByTestId('outlet-patient')).toHaveTextContent('MRN-0001');
    expect(getById).toHaveBeenCalledWith(1);
    expect(getAllergies).toHaveBeenCalledWith(1);
  });
});
