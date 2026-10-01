import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { ProblemList } from './ProblemList';
import type { Problem } from '../../types';

function makeProblem(overrides: Partial<Problem> & Pick<Problem, 'id'>): Problem {
  return {
    patientId: 1,
    icdCode: `E11.${overrides.id}`,
    icdDescription: 'Type 2 diabetes mellitus',
    description: `Problem ${overrides.id}`,
    status: 'ACTIVE',
    isPrincipal: false,
    isChronic: false,
    recordedAt: '2024-01-01T00:00:00Z',
    ...overrides,
  };
}

const active = makeProblem({ id: 1, status: 'ACTIVE', description: 'Hypertension' });
const resolved = makeProblem({ id: 2, status: 'RESOLVED', description: 'Fractured wrist' });
const inactive = makeProblem({ id: 3, status: 'INACTIVE', description: 'Seasonal allergies' });

function rowDescriptions() {
  const rows = screen.getAllByRole('row').slice(1);
  return rows.map((row) => within(row).getAllByRole('cell')[1].textContent);
}

describe('ProblemList filter mapping', () => {
  it('shows only ACTIVE problems by default', () => {
    render(<ProblemList problems={[active, resolved, inactive]} />);

    expect(rowDescriptions()).toEqual(['Hypertension']);
  });

  it('shows RESOLVED and INACTIVE problems under the resolved filter', async () => {
    render(<ProblemList problems={[active, resolved, inactive]} />);

    await userEvent.click(screen.getByRole('button', { name: 'Resolved (2)' }));

    expect(rowDescriptions()).toEqual(['Fractured wrist', 'Seasonal allergies']);
  });

  it('shows every problem under the all filter', async () => {
    render(<ProblemList problems={[active, resolved, inactive]} />);

    await userEvent.click(screen.getByRole('button', { name: 'All (3)' }));

    expect(rowDescriptions()).toEqual(['Hypertension', 'Fractured wrist', 'Seasonal allergies']);
  });

  it('renders an empty state when no problem matches the filter', () => {
    render(<ProblemList problems={[resolved]} />);

    expect(screen.getByText('No problems found')).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });
});

describe('ProblemList compact view', () => {
  it('lists at most five active problems and counts the remainder', () => {
    const problems = Array.from({ length: 7 }, (_, i) =>
      makeProblem({ id: i + 1, description: `Active ${i + 1}` }),
    );

    render(<ProblemList problems={problems} compact />);

    const items = screen.getAllByRole('listitem');
    expect(items.map((item) => item.textContent)).toEqual([
      expect.stringContaining('Active 1'),
      expect.stringContaining('Active 2'),
      expect.stringContaining('Active 3'),
      expect.stringContaining('Active 4'),
      expect.stringContaining('Active 5'),
      '+2 more',
    ]);
  });

  it('omits the more indicator at exactly five active problems', () => {
    const problems = Array.from({ length: 5 }, (_, i) =>
      makeProblem({ id: i + 1, description: `Active ${i + 1}` }),
    );

    render(<ProblemList problems={problems} compact />);

    expect(screen.getAllByRole('listitem')).toHaveLength(5);
    expect(screen.queryByText(/more$/)).not.toBeInTheDocument();
  });

  it('ignores non-active problems and shows an empty message', () => {
    render(<ProblemList problems={[resolved, inactive]} compact />);

    expect(screen.getByText('No active problems')).toBeInTheDocument();
    expect(screen.queryByRole('listitem')).not.toBeInTheDocument();
  });
});
