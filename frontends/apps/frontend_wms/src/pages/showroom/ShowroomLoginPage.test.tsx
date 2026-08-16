import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { ShowroomLoginPage } from './ShowroomLoginPage';
import { requestShowroomMagicLink } from '@/api/portal';

vi.mock('@/api/portal', () => ({
  requestShowroomMagicLink: vi.fn(),
}));

vi.mock('@/api/client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <ShowroomLoginPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('ShowroomLoginPage', () => {
  it('is passwordless and confirms after requesting a magic link', async () => {
    const user = userEvent.setup();
    vi.mocked(requestShowroomMagicLink).mockResolvedValue({ status: 'accepted' });
    renderPage();

    expect(screen.queryByLabelText(/password/i)).not.toBeInTheDocument();
    await user.type(screen.getByLabelText('Email'), 'buyer@acme.test');
    await user.click(screen.getByRole('button', { name: 'Email me a login link' }));

    expect(await screen.findByTestId('showroom-login-sent')).toBeInTheDocument();
    expect(screen.getByText('Check your email for your login link')).toBeInTheDocument();
    expect(requestShowroomMagicLink).toHaveBeenCalledWith('buyer@acme.test');
  });
});
