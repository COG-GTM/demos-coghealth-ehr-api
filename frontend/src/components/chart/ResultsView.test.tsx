import { describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ResultsView } from './ResultsView';
import type { ImagingResult, LabPanel, LabResult } from '../../types';

function labResult(overrides: Partial<LabResult> & { id: number }): LabResult {
  return {
    patientId: 1,
    testCode: 'GLU',
    testName: 'Glucose',
    value: '98',
    unit: 'mg/dL',
    referenceRange: '70-110',
    status: 'FINAL',
    resultedAt: '2026-01-02T10:00:00Z',
    ...overrides,
  };
}

function labPanel(overrides: Partial<LabPanel> & { results: LabResult[] }): LabPanel {
  return {
    panelName: 'Basic Metabolic Panel',
    orderedAt: '2026-01-02T08:00:00Z',
    resultedAt: '2026-01-02T10:00:00Z',
    status: 'FINAL',
    ...overrides,
  };
}

function imagingResult(overrides: Partial<ImagingResult> & { id: number }): ImagingResult {
  return {
    patientId: 1,
    studyType: 'XR',
    studyDescription: 'Chest X-Ray',
    bodyPart: 'Chest',
    status: 'FINAL',
    orderedAt: '2026-01-02T08:00:00Z',
    performedAt: '2026-01-02T09:00:00Z',
    ...overrides,
  };
}

describe('ResultsView', () => {
  it('badges each tab with the number of unreviewed results', () => {
    render(
      <ResultsView
        labPanels={[
          labPanel({
            results: [
              labResult({ id: 1 }),
              labResult({ id: 2, reviewedAt: '2026-01-03T10:00:00Z' }),
              labResult({ id: 3 }),
            ],
          }),
        ]}
        imagingResults={[
          imagingResult({ id: 10 }),
          imagingResult({ id: 11, reviewedAt: '2026-01-03T10:00:00Z' }),
        ]}
      />
    );

    expect(within(screen.getByRole('button', { name: /Lab Results/ })).getByText('2')).toBeInTheDocument();
    expect(within(screen.getByRole('button', { name: /Imaging/ })).getByText('1')).toBeInTheDocument();
  });

  it('omits the tab badges when everything is reviewed', () => {
    render(
      <ResultsView
        labPanels={[labPanel({ results: [labResult({ id: 1, reviewedAt: '2026-01-03T10:00:00Z' })] })]}
        imagingResults={[imagingResult({ id: 10, reviewedAt: '2026-01-03T10:00:00Z' })]}
      />
    );

    expect(within(screen.getByRole('button', { name: /Lab Results/ })).queryByText('1')).not.toBeInTheDocument();
    expect(within(screen.getByRole('button', { name: /Imaging/ })).queryByText('1')).not.toBeInTheDocument();
  });

  it('marks every unreviewed result of a lab panel as reviewed', async () => {
    const onMarkReviewed = vi.fn();
    render(
      <ResultsView
        labPanels={[
          labPanel({
            results: [
              labResult({ id: 1 }),
              labResult({ id: 2, reviewedAt: '2026-01-03T10:00:00Z' }),
              labResult({ id: 3 }),
            ],
          }),
        ]}
        imagingResults={[]}
        onMarkReviewed={onMarkReviewed}
      />
    );

    await userEvent.click(screen.getByRole('button', { name: /Mark Reviewed/ }));

    expect(onMarkReviewed.mock.calls).toEqual([
      ['lab', 1],
      ['lab', 3],
    ]);
  });

  it('hides the lab mark reviewed action when the panel is fully reviewed', () => {
    render(
      <ResultsView
        labPanels={[labPanel({ results: [labResult({ id: 1, reviewedAt: '2026-01-03T10:00:00Z' })] })]}
        imagingResults={[]}
        onMarkReviewed={vi.fn()}
      />
    );

    expect(screen.queryByRole('button', { name: /Mark Reviewed/ })).not.toBeInTheDocument();
  });

  it('marks an imaging study as reviewed', async () => {
    const onMarkReviewed = vi.fn();
    render(
      <ResultsView labPanels={[]} imagingResults={[imagingResult({ id: 10 })]} onMarkReviewed={onMarkReviewed} />
    );

    await userEvent.click(screen.getByRole('button', { name: /Imaging/ }));
    await userEvent.click(screen.getByRole('button', { name: /Mark Reviewed/ }));

    expect(onMarkReviewed).toHaveBeenCalledExactlyOnceWith('imaging', 10);
  });

  it.each([
    ['LOW' as const, 'L'],
    ['HIGH' as const, 'H'],
    ['CRITICAL_LOW' as const, 'LL'],
    ['CRITICAL_HIGH' as const, 'HH'],
    ['ABNORMAL' as const, 'A'],
  ])('renders the %s flag as a %s badge', (flag, label) => {
    render(
      <ResultsView
        labPanels={[labPanel({ results: [labResult({ id: 1, flag })] })]}
        imagingResults={[]}
      />
    );

    expect(within(screen.getByRole('row', { name: /Glucose/ })).getByText(label)).toBeInTheDocument();
  });

  it('renders no flag badge for a normal result', () => {
    render(
      <ResultsView
        labPanels={[labPanel({ results: [labResult({ id: 1, flag: 'NORMAL' })] })]}
        imagingResults={[]}
      />
    );

    const row = within(screen.getByRole('row', { name: /Glucose/ }));
    ['L', 'H', 'LL', 'HH', 'A'].forEach((label) => {
      expect(row.queryByText(label)).not.toBeInTheDocument();
    });
  });

  it('opens the trend modal for the selected result', async () => {
    render(
      <ResultsView
        labPanels={[labPanel({ results: [labResult({ id: 1, testName: 'Potassium' })] })]}
        imagingResults={[]}
      />
    );

    await userEvent.click(screen.getByRole('button', { name: 'View Trend' }));

    expect(screen.getByRole('heading', { name: 'Potassium Trend' })).toBeInTheDocument();
  });

  it('shows empty states for both tabs', async () => {
    render(<ResultsView labPanels={[]} imagingResults={[]} />);

    expect(screen.getByText('No lab results')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: /Imaging/ }));

    expect(screen.getByText('No imaging results')).toBeInTheDocument();
  });
});
