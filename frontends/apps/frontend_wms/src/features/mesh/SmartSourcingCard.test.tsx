import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { SmartSourcingCard } from './SmartSourcingCard';
import { fetchMeshSourcingSuggestions } from '@/api/mesh';

const navigate = vi.fn();

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return { ...actual, useNavigate: () => navigate };
});

vi.mock('@/api/mesh', async () => {
  const actual = await vi.importActual<typeof import('@/api/mesh')>('@/api/mesh');
  return { ...actual, fetchMeshSourcingSuggestions: vi.fn() };
});

function renderCard() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <SmartSourcingCard />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('SmartSourcingCard', () => {
  it('renders a low-stock partner suggestion and drafts a PO', async () => {
    const user = userEvent.setup();
    vi.mocked(fetchMeshSourcingSuggestions).mockResolvedValue([
      {
        variantId: 'v1',
        productName: 'Grow Medium',
        sku: 'GM-1',
        partnerTenantId: 't2',
        partnerName: 'Northwind',
        supplierId: 'sup-1',
        meshPartnerSku: 'GM-1',
      },
    ]);

    renderCard();

    expect(await screen.findByTestId('smart-sourcing-card')).toBeInTheDocument();
    expect(
      screen.getByText((_, el) =>
        el?.textContent ===
        'You are running low on Grow Medium. Your Mesh Partner Northwind has this in stock.',
      ),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Draft PO' }));
    expect(navigate).toHaveBeenCalledWith('/purchase-orders/new?meshPartnerSku=GM-1&supplierId=sup-1');
  });
});
