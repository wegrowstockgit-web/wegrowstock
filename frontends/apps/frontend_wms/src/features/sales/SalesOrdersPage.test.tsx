import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SalesOrdersPage } from './SalesOrdersPage';
import { apiClient } from '@/api/client';
import { useSessionStore } from '@/stores/session';
import { ToastProvider } from '@/components/ui/Toast';

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
      <ToastProvider>
        <SalesOrdersPage />
      </ToastProvider>
    </QueryClientProvider>,
  );
}

describe('SalesOrdersPage RFQ inbox', () => {
  beforeEach(() => {
    useSessionStore.setState({
      authenticated: true,
      user: {
        id: 'u1',
        email: 'owner@demo.test',
        displayName: 'Owner',
        roles: ['OWNER'],
        warehouseIds: [],
        avatarUrl: null,
        tenantId: 't1',
      },
      lastRequestId: null,
      primarySession: null,
    });
    vi.mocked(apiClient.get).mockImplementation(async (url: string) => {
      if (url === '/api/v1/sales-orders') {
        return {
          data: [
            {
              id: 'rfq-1',
              number: 'SO-RFQ-1',
              customerName: 'Buyer Co',
              status: 'PENDING_REP_APPROVAL',
              createdAt: '2026-08-01T00:00:00Z',
              allocationPolicy: 'SHIP_COMPLETE',
            },
            {
              id: 'bo-1',
              number: 'SO-BO-1',
              customerName: 'Buyer Co',
              status: 'BACKORDERED',
              createdAt: '2026-08-02T00:00:00Z',
              allocationPolicy: 'SHIP_COMPLETE',
            },
          ],
        };
      }
      if (url === '/api/v1/sales-orders/rfq-1') {
        return {
          data: {
            id: 'rfq-1',
            number: 'SO-RFQ-1',
            customerName: 'Buyer Co',
            status: 'PENDING_REP_APPROVAL',
            allocationPolicy: 'SHIP_COMPLETE',
            lines: [
              {
                id: 'line-1',
                variantId: 'v1',
                sku: 'WG-1',
                name: 'Grow Light',
                qtyOrdered: 4,
                qtyShipped: 0,
                unitPrice: 40,
              },
            ],
          },
        };
      }
      if (url === '/api/v1/sales-orders/bo-1') {
        return {
          data: {
            id: 'bo-1',
            number: 'SO-BO-1',
            customerName: 'Buyer Co',
            status: 'BACKORDERED',
            allocationPolicy: 'SHIP_COMPLETE',
            lines: [
              {
                id: 'line-2',
                variantId: 'v1',
                sku: 'WG-1',
                name: 'Grow Light',
                qtyOrdered: 4,
                qtyAllocated: 0,
                qtyBackordered: 4,
                qtyShipped: 0,
                unitPrice: 40,
              },
            ],
          },
        };
      }
      return { data: [] };
    });
    vi.mocked(apiClient.post).mockResolvedValue({ data: {} });
  });

  it('highlights RFQ rows and sends a customer quote from the drawer', async () => {
    const user = userEvent.setup();
    renderPage();

    const rfqRow = await screen.findByText('SO-RFQ-1');
    expect(rfqRow.closest('tr')?.getAttribute('data-rfq')).toBe('true');
    await user.click(rfqRow);
    expect(await screen.findByRole('button', { name: 'Send Quote to Customer' })).toBeInTheDocument();
    await user.clear(screen.getByLabelText('Global flat discount'));
    await user.type(screen.getByLabelText('Global flat discount'), '15');
    await user.click(screen.getByRole('button', { name: 'Send Quote to Customer' }));
    expect(apiClient.post).toHaveBeenCalledWith(
      '/api/v1/sales-orders/rfq-1/quote',
      expect.objectContaining({ manualDiscountTotal: 15 }),
    );
  });

  it('shows ship-complete hold badge on backordered orders', async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByText('SO-BO-1'));
    expect(await screen.findByTestId('allocation-hold-badge')).toHaveTextContent(/Ship Complete/i);
  });
});
