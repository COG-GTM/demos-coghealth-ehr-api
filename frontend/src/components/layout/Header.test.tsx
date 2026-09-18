import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Header } from './Header';

const navigate = vi.fn();

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return { ...actual, useNavigate: () => navigate };
});

function renderHeader() {
  return render(
    <MemoryRouter>
      <Header onMenuClick={() => {}} />
    </MemoryRouter>
  );
}

describe('Header', () => {
  beforeEach(() => {
    navigate.mockClear();
  });

  it('navigates to the encoded patient search and clears the input on submit', async () => {
    const user = userEvent.setup();
    renderHeader();

    const input = screen.getByPlaceholderText<HTMLInputElement>('Search patients (Ctrl+K)');
    await user.type(input, "  O'Brien Smith ");
    fireEvent.submit(input.closest('form')!);

    expect(navigate).toHaveBeenCalledWith("/patients?q=O'Brien%20Smith");
    expect(input.value).toBe('');
  });

  it('does not navigate when the search query is blank', async () => {
    const user = userEvent.setup();
    renderHeader();

    const input = screen.getByPlaceholderText('Search patients (Ctrl+K)');
    await user.type(input, '   ');
    fireEvent.submit(input.closest('form')!);

    expect(navigate).not.toHaveBeenCalled();
  });

  it('caps the inbox badge at 9+', () => {
    renderHeader();

    expect(screen.getByText('9+')).toBeInTheDocument();
  });

  it('closes an open dropdown on a mousedown outside of it', async () => {
    const user = userEvent.setup();
    renderHeader();

    await user.click(screen.getByRole('button', { name: /dr\. chen/i }));
    expect(screen.getByText('Sign out')).toBeInTheDocument();

    fireEvent.mouseDown(document.body);

    expect(screen.queryByText('Sign out')).not.toBeInTheDocument();
  });

  it('clears the auth token and navigates to /login on sign out', async () => {
    const user = userEvent.setup();
    localStorage.setItem('auth_token', 'token-123');
    renderHeader();

    await user.click(screen.getByRole('button', { name: /dr\. chen/i }));
    await user.click(screen.getByText('Sign out'));

    expect(localStorage.getItem('auth_token')).toBeNull();
    expect(navigate).toHaveBeenCalledWith('/login');
  });
});
