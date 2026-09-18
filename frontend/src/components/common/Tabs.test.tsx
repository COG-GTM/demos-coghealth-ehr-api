import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { Tab, TabList, TabPanel, Tabs } from './Tabs';

afterEach(cleanup);

function renderTabs(onChange?: (tab: string) => void) {
  return render(
    <Tabs defaultTab="results" onChange={onChange}>
      <TabList>
        <Tab value="results">Results</Tab>
        <Tab value="imaging">Imaging</Tab>
      </TabList>
      <TabPanel value="results">Lab results panel</TabPanel>
      <TabPanel value="imaging">Imaging panel</TabPanel>
    </Tabs>,
  );
}

describe('Tabs', () => {
  it('renders only the default panel', () => {
    renderTabs();

    expect(screen.getByText('Lab results panel')).toBeTruthy();
    expect(screen.queryByText('Imaging panel')).toBeNull();
  });

  it('switches panels and reports the new tab on click', () => {
    const onChange = vi.fn();
    renderTabs(onChange);

    fireEvent.click(screen.getByRole('button', { name: 'Imaging' }));

    expect(screen.getByText('Imaging panel')).toBeTruthy();
    expect(screen.queryByText('Lab results panel')).toBeNull();
    expect(onChange).toHaveBeenCalledTimes(1);
    expect(onChange).toHaveBeenCalledWith('imaging');
  });

  it('marks the active tab button', () => {
    renderTabs();

    expect(screen.getByRole('button', { name: 'Results' }).className).toContain('active');
    expect(screen.getByRole('button', { name: 'Imaging' }).className).not.toContain('active');
  });

  it('does not call onChange when it is not provided', () => {
    renderTabs();

    fireEvent.click(screen.getByRole('button', { name: 'Imaging' }));

    expect(screen.getByText('Imaging panel')).toBeTruthy();
  });

  it('throws when Tab is used outside Tabs', () => {
    expect(() => render(<Tab value="results">Results</Tab>)).toThrow(
      'Tab must be used within Tabs',
    );
  });

  it('throws when TabPanel is used outside Tabs', () => {
    expect(() => render(<TabPanel value="results">Panel</TabPanel>)).toThrow(
      'TabPanel must be used within Tabs',
    );
  });
});
