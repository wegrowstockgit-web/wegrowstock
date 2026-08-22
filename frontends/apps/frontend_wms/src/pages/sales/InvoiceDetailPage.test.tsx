import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { InvoiceDetailPage } from './InvoiceDetailPage';
import { apiClient } from '@/api/client';
import { useSessionStore } from '@/stores/session';
import { ToastProvider } from '@/components/ui/Toast';
import type { InvoiceDetail } from '@/api/types';

vi.mock('@/api/client', () => ({
  apiClient: { get: vi.fn(), post: vi.fn(), patch: vi.fn() },
}));

function invoice(status: string): InvoiceDetail {
  return {
    id: 'inv-1',
    number: 'INV-1',
    customerName: 'Acme',
    status,
    total: 40,
    currency: 'USD',
    lines: [{ id: 'il-1', description: 'Line 1', qty: 2, unitPrice: 20, amount: 40, kind: 'ITEM' }],
  };
}

function renderInvoice(roles: string[], status: string) {
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
  vi.mocked(apiClient.get).mockResolvedValue({ data: invoice(status) });
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/invoices/inv-1']}>
        <ToastProvider>
          <Routes>
            <Route path="/invoices/:id" element={<InvoiceDetailPage />} />
          </Routes>
        </ToastProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('InvoiceDetailPage void RBAC', () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
  });

  it('shows void for managers on issued invoices', async () => {
    renderInvoice(['WAREHOUSE_MANAGER'], 'OPEN');
    expect(await screen.findByTestId('void-credit-memo')).toBeInTheDocument();
    expect(screen.queryByTestId('issue-invoice')).not.toBeInTheDocument();
  });

  it('hides void from pickers', async () => {
    renderInvoice(['PICKER'], 'OPEN');
    expect(await screen.findByTestId('invoice-workspace-lock')).toBeInTheDocument();
    expect(screen.queryByTestId('void-credit-memo')).not.toBeInTheDocument();
  });
});
