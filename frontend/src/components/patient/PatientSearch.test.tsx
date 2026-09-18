import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { PatientSearch } from './PatientSearch';
import type { Patient } from '../../types';

const navigate = vi.fn();
const usePatientSearch = vi.fn();

vi.mock('react-router-dom', () => ({
  useNavigate: () => navigate,
}));

vi.mock('../../hooks', () => ({
  usePatientSearch: (query: string) => usePatientSearch(query),
}));

function makePatient(overrides: Partial<Patient> = {}): Patient {
  return {
    id: 1,
    mrn: 'MRN-001',
    firstName: 'Ada',
    lastName: 'Lovelace',
    dateOfBirth: '1980-05-02',
    gender: 'FEMALE',
    active: true,
    deceased: false,
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
    ...overrides,
  };
}

function mockSearch({ data, isLoading = false }: { data?: Patient[]; isLoading?: boolean }) {
  usePatientSearch.mockReturnValue({ data, isLoading });
}

describe('PatientSearch', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockSearch({ data: [] });
  });

  it('keeps the dropdown closed until the query reaches two characters', async () => {
    const user = userEvent.setup();
    mockSearch({ data: [makePatient()] });
    render(<PatientSearch />);

    const input = screen.getByPlaceholderText('Search patients...');
    await user.type(input, 'A');
    expect(screen.queryByRole('list')).not.toBeInTheDocument();

    await user.type(input, 'd');
    expect(screen.getByRole('list')).toBeInTheDocument();
  });

  it('shows the loading state while the search is in flight', async () => {
    const user = userEvent.setup();
    mockSearch({ isLoading: true });
    render(<PatientSearch />);

    await user.type(screen.getByPlaceholderText('Search patients...'), 'ad');

    expect(screen.getByText('Searching...')).toBeInTheDocument();
    expect(screen.queryByRole('list')).not.toBeInTheDocument();
  });

  it('lists matching patients with their demographics', async () => {
    const user = userEvent.setup();
    mockSearch({ data: [makePatient(), makePatient({ id: 2, firstName: 'Grace', lastName: 'Hopper', mrn: 'MRN-002' })] });
    render(<PatientSearch />);

    await user.type(screen.getByPlaceholderText('Search patients...'), 'ad');

    const options = screen.getAllByRole('listitem');
    expect(options).toHaveLength(2);
    expect(within(options[0]).getByText('Lovelace, Ada')).toBeInTheDocument();
    expect(within(options[0]).getByText(/MRN: MRN-001/)).toBeInTheDocument();
    expect(within(options[1]).getByText('Hopper, Grace')).toBeInTheDocument();
  });

  it('shows the empty state when the search returns no patients', async () => {
    const user = userEvent.setup();
    mockSearch({ data: [] });
    render(<PatientSearch />);

    await user.type(screen.getByPlaceholderText('Search patients...'), 'zz');

    expect(screen.getByText('No patients found for "zz"')).toBeInTheDocument();
  });

  it('navigates to the patient chart when no onSelect handler is given', async () => {
    const user = userEvent.setup();
    const patient = makePatient({ id: 42 });
    mockSearch({ data: [patient] });
    render(<PatientSearch />);

    const input = screen.getByPlaceholderText('Search patients...');
    await user.type(input, 'ad');
    await user.click(screen.getByRole('button', { name: /Lovelace, Ada/ }));

    expect(navigate).toHaveBeenCalledWith('/patients/42');
    expect(input).toHaveValue('');
    expect(screen.queryByRole('list')).not.toBeInTheDocument();
  });

  it('calls onSelect instead of navigating when a handler is given', async () => {
    const user = userEvent.setup();
    const patient = makePatient({ id: 42 });
    const onSelect = vi.fn();
    mockSearch({ data: [patient] });
    render(<PatientSearch onSelect={onSelect} />);

    await user.type(screen.getByPlaceholderText('Search patients...'), 'ad');
    await user.click(screen.getByRole('button', { name: /Lovelace, Ada/ }));

    expect(onSelect).toHaveBeenCalledWith(patient);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('closes the dropdown when clicking outside the component', async () => {
    const user = userEvent.setup();
    mockSearch({ data: [makePatient()] });
    render(
      <div>
        <PatientSearch />
        <button>outside</button>
      </div>,
    );

    await user.type(screen.getByPlaceholderText('Search patients...'), 'ad');
    expect(screen.getByRole('list')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'outside' }));
    expect(screen.queryByRole('list')).not.toBeInTheDocument();
  });

  it('clears the query with the clear button', async () => {
    const user = userEvent.setup();
    mockSearch({ data: [makePatient()] });
    render(<PatientSearch />);

    const input = screen.getByPlaceholderText('Search patients...');
    await user.type(input, 'ad');

    await user.click(screen.getByRole('button', { name: 'Clear search' }));

    expect(input).toHaveValue('');
    expect(screen.queryByRole('list')).not.toBeInTheDocument();
  });
});
