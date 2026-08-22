import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { SalesOrderDetailPage } from './SalesOrderDetailPage';
import { apiClient } from '@/api/client';
import { useSessionStore } from '@/stores/session';
import { ToastProvider } from '@/components/ui/Toast';
import type { SalesOrderDetail } from '@/api/types';

vi.mock('@/api/client', () => ({
  apiClient: { get: vi.fn(), post: vi.fn(), patch: vi.fn() },
}));

function order(status: string, qtyShipped = 0): SalesOrderDetail {
  return {
    id: 'so-1',
    number: 'SO-WS-1',
    customerName: 'Acme',
    status,
    lines: [
      {
        id: 'line-1',
        variantId: 'var-1',
        sku: 'WIDGET-S',
        name: 'Widget S',
        qtyOrdered: 10,
        qtyAllocated: status === 'DRAFT' ? 0 : 10,
        qtyShipped,
        unitPrice: 8,
      },
    ],
  };
}

function renderWorkspace(roles: string[], status = 'DRAFT', qtyShipped = 0) {
  useSessionStore.setState({
    authenticated: true,
    user: {
      id: 'u1',
      email: 'user@demo.test',
      displayName: 'User',
      roles,
      warehouseIds: [],
      avatarUrl: null,
      tenantId: 't1',
    },
    lastRequestId: null,
    primarySession: null,
  });
  vi.mocked(apiClient.get).mockResolvedValue({ data: order(status, qtyShipped) });
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/sales/orders/so-1']}>
        <ToastProvider>
          <Routes>
            <Route path="/sales/orders/:id" element={<SalesOrderDetailPage />} />
          </Routes>
        </ToastProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('SalesOrderDetailPage ledger lock', () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
  });

  it('enables inline editors on drafts', async () => {
    renderWorkspace(['WAREHOUSE_MANAGER']);
    expect(await screen.findByTestId('so-workspace')).toHaveAttribute('data-locked', 'false');
    expect(screen.getByTestId('so-credit-status')).toHaveTextContent(/Credit CLEAR/i);
    expect(screen.getByTestId('submit-so')).toBeInTheDocument();
    expect(screen.getByTestId('so-line-qty-line-1')).toBeInTheDocument();
  });

  it('locks the grid after submit and shows cancel when nothing shipped', async () => {
    renderWorkspace(['WAREHOUSE_MANAGER'], 'CONFIRMED');
    expect(await screen.findByTestId('so-workspace')).toHaveAttribute('data-locked', 'true');
    expect(screen.getByTestId('so-line-qty-locked-line-1')).toBeInTheDocument();
    expect(screen.getByTestId('cancel-so')).toBeInTheDocument();
    expect(screen.queryByTestId('reverse-fulfillment')).not.toBeInTheDocument();
  });

  it('shows credit-hold override only for finance or admin', async () => {
    renderWorkspace(['ADMIN'], 'CREDIT_HOLD');
    expect(await screen.findByTestId('override-credit-hold')).toBeInTheDocument();
    expect(screen.getByTestId('so-credit-status')).toHaveTextContent(/Credit HOLD/i);
  });

  it('hides credit-hold override from warehouse managers', async () => {
    renderWorkspace(['WAREHOUSE_MANAGER'], 'CREDIT_HOLD');
    expect(await screen.findByTestId('so-workspace')).toBeInTheDocument();
    expect(screen.queryByTestId('override-credit-hold')).not.toBeInTheDocument();
  });

  it('shows reverse fulfillment only for managers after ship', async () => {
    renderWorkspace(['WAREHOUSE_MANAGER'], 'SHIPPED', 10);
    expect(await screen.findByTestId('reverse-fulfillment')).toBeInTheDocument();
    expect(screen.queryByTestId('cancel-so')).not.toBeInTheDocument();
  });
});
