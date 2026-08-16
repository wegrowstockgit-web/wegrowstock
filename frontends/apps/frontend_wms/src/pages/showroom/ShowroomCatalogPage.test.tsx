import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { ShowroomCatalogPage } from './ShowroomCatalogPage';
import { fetchPublicShowroomCatalog } from '@/api/portal';
import { useSessionStore } from '@/stores/session';

vi.mock('@/api/portal', async () => {
  const actual = await vi.importActual<typeof import('@/api/portal')>('@/api/portal');
  return {
    ...actual,
    fetchPublicShowroomCatalog: vi.fn(),
  };
});

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
        <ShowroomCatalogPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('ShowroomCatalogPage gated browsing', () => {
  beforeEach(() => {
    useSessionStore.setState({ authenticated: false, user: null });
    vi.mocked(fetchPublicShowroomCatalog).mockResolvedValue([
      {
        id: 'v1',
        sku: 'WG-1',
        name: 'Grow Light',
        unitPrice: 40,
        currency: 'USD',
      },
    ]);
  });

  it('lets guests see products without wholesale prices or cart controls', async () => {
    renderPage();
    expect(await screen.findByText('Grow Light')).toBeInTheDocument();
    expect(screen.getByTestId('showroom-gated-banner')).toHaveTextContent(
      'Log in or Apply for a Wholesale Account to unlock B2B pricing.',
    );
    expect(screen.getByTestId('showroom-price-gated')).toBeInTheDocument();
    expect(screen.queryByText(/\$40/)).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /add|plus/i })).not.toBeInTheDocument();
  });
});
