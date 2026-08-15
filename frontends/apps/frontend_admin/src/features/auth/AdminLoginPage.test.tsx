import { describe, expect, it, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AdminLoginPage } from './AdminLoginPage';

vi.mock('@/features/tenants/api', () => ({
  adminLogin: vi.fn(),
}));

import { adminLogin } from '@/features/tenants/api';

function renderLogin() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <AdminLoginPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('AdminLoginPage', () => {
  beforeEach(() => {
    vi.mocked(adminLogin).mockReset();
  });

  it('renders email and password fields', () => {
    renderLogin();
    expect(screen.getByTestId('admin-login-email')).toBeTruthy();
    expect(screen.getByTestId('admin-login-password')).toBeTruthy();
    expect(screen.getByTestId('admin-login-submit')).toBeTruthy();
  });

  it('submits credentials to the login API', async () => {
    vi.mocked(adminLogin).mockResolvedValue({ email: 'admin@invsys.com' });
    renderLogin();

    fireEvent.change(screen.getByTestId('admin-login-email'), {
      target: { value: 'admin@invsys.com' },
    });
    fireEvent.change(screen.getByTestId('admin-login-password'), {
      target: { value: 'secret' },
    });
    fireEvent.click(screen.getByTestId('admin-login-submit'));

    await waitFor(() => {
      expect(adminLogin).toHaveBeenCalledWith('admin@invsys.com', 'secret');
    });
  });
});
