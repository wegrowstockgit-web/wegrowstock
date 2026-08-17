import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { LoginPage, loginWithPassword } from './LoginPage';

describe('LoginPage', () => {
  it('navigates home after a successful login', async () => {
    const user = userEvent.setup();
    const fetchImpl = vi.fn().mockResolvedValue({ ok: true });
    vi.stubGlobal('fetch', fetchImpl);
    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>,
    );
    await user.click(screen.getByRole('button', { name: 'Open register' }));
    expect(fetchImpl).toHaveBeenCalled();
    vi.unstubAllGlobals();
  });

  it('surfaces API errors', async () => {
    const user = userEvent.setup();
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false }));
    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>,
    );
    await user.click(screen.getByRole('button', { name: 'Open register' }));
    expect(await screen.findByTestId('pos-login-error')).toHaveTextContent('Invalid email or password');
    vi.unstubAllGlobals();
  });

  it('loginWithPassword posts credentials', async () => {
    const fetchImpl = vi.fn().mockResolvedValue({ ok: true });
    await loginWithPassword('a@b.c', 'secret', fetchImpl);
    expect(fetchImpl).toHaveBeenCalledWith(
      '/api/v1/auth/login',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ email: 'a@b.c', password: 'secret', targetApp: 'POS' }),
      }),
    );
    await expect(loginWithPassword('a', 'b', vi.fn().mockResolvedValue({ ok: false }))).rejects.toThrow(
      /Invalid/,
    );
    await expect(
      loginWithPassword('a', 'b', vi.fn().mockResolvedValue({ ok: false, status: 403 })),
    ).rejects.toThrow(/POS access denied/);
  });
});
