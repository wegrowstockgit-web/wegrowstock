import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { ShowroomCheckoutPage } from './ShowroomCheckoutPage';
import { apiClient } from '@/api/client';

vi.mock('@/api/client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

function seedCart() {
  sessionStorage.setItem(
    'showroom-cart',
    JSON.stringify([
      {
        item: {
          id: 'var-1',
          sku: 'WG-1',
          name: 'Grow Light',
          unitPrice: 40,
          currency: 'USD',
        },
        quantity: 2,
      },
    ]),
  );
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <ShowroomCheckoutPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('ShowroomCheckoutPage RFQ', () => {
  beforeEach(() => {
    sessionStorage.clear();
    seedCart();
    vi.mocked(apiClient.get).mockImplementation(async (url: string) => {
      if (url.includes('payment-terms')) return { data: { terms: 'NET 30' } };
      if (url.includes('credit')) return { data: { availableCredit: 1000, creditLimit: 2000, status: 'OK' } };
      return { data: {} };
    });
    vi.mocked(apiClient.post).mockResolvedValue({ data: { id: 'so-1', number: 'SO-1', status: 'DRAFT' } });
  });

  it('offers instant checkout and custom quote with allocation preference', async () => {
    const user = userEvent.setup();
    renderPage();

    expect(screen.getByText('Grow Light × 2')).toBeInTheDocument();
    await user.click(screen.getByLabelText(/Ship Complete/i));
    await user.click(screen.getByRole('button', { name: 'Continue' }));

    expect(screen.getByRole('button', { name: 'Instant Checkout' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Request Custom Quote' })).toBeInTheDocument();
    expect(screen.getByText(/Ship complete/i)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Request Custom Quote' }));
    expect(await screen.findByText('Quote requested')).toBeInTheDocument();
    expect(apiClient.post).toHaveBeenCalledWith(
      '/api/v1/portal/quotes',
      expect.objectContaining({ allocationPolicy: 'SHIP_COMPLETE' }),
    );
  });

  it('blocks instant checkout when the cart exceeds available credit', async () => {
    vi.mocked(apiClient.get).mockImplementation(async (url: string) => {
      if (url.includes('payment-terms')) return { data: { terms: 'NET 30' } };
      if (url.includes('credit')) return { data: { availableCredit: 10, creditLimit: 10, status: 'OK' } };
      return { data: {} };
    });
    const user = userEvent.setup();
    renderPage();
    await user.type(screen.getByLabelText('Your PO number'), 'PO-9');
    await user.click(screen.getByRole('button', { name: 'Continue' }));
    expect(screen.getByText(/exceeds your available credit/i)).toBeInTheDocument();
    expect(screen.getByText('PO-9')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Instant Checkout' })).toBeDisabled();
  });

  it('submits instant checkout to portal orders', async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(screen.getByRole('button', { name: 'Continue' }));
    await user.click(screen.getByRole('button', { name: 'Instant Checkout' }));
    expect(await screen.findByText('Order submitted')).toBeInTheDocument();
    expect(apiClient.post).toHaveBeenCalledWith(
      '/api/v1/portal/orders',
      expect.objectContaining({ allocationPolicy: 'ALLOW_PARTIAL' }),
    );
  });
});
