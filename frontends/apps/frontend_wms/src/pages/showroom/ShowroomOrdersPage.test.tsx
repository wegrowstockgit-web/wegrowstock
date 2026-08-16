import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { ShowroomOrdersPage } from './ShowroomOrdersPage';
import { apiClient } from '@/api/client';

vi.mock('@/api/client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <ShowroomOrdersPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('ShowroomOrdersPage quote accept', () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockImplementation(async (url: string) => {
      if (url === '/api/v1/portal/orders') {
        return {
          data: [
            {
              id: 'q-1',
              number: 'SO-QUOTE-1',
              status: 'QUOTE_READY',
              total: 90,
              currency: 'USD',
              createdAt: '2026-08-01T00:00:00Z',
              manualDiscountTotal: 10,
              quoteExpiresAt: '2026-08-30T00:00:00Z',
              quoteNotes: 'NET 45 approved',
            },
          ],
        };
      }
      return { data: [] };
    });
    vi.mocked(apiClient.post).mockResolvedValue({
      data: { id: 'q-1', status: 'UNALLOCATED' },
    });
  });

  it('shows quote-ready banner and accepts the quote', async () => {
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByRole('heading', { name: 'SO-QUOTE-1' })).toBeInTheDocument();
    expect(screen.getByText(/custom pricing/i)).toBeInTheDocument();
    expect(screen.getByText('NET 45 approved')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Accept & Convert to Order' }));
    expect(apiClient.post).toHaveBeenCalledWith('/api/v1/portal/orders/q-1/accept-quote');
  });
});
