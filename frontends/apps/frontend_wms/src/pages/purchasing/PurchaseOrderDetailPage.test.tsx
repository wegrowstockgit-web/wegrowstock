import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { PurchaseOrderDetailPage } from './PurchaseOrderDetailPage';
import { apiClient } from '@/api/client';
import { useSessionStore } from '@/stores/session';
import { ToastProvider } from '@/components/ui/Toast';
import type { PurchaseOrderDetail } from '@/api/types';

vi.mock('@/api/client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    patch: vi.fn(),
  },
}));

function po(
  status: string,
  qtyReceived = 0,
  extras: Partial<PurchaseOrderDetail> = {},
): PurchaseOrderDetail {
  return {
    id: 'po-1',
    number: 'PO-WS-1',
    supplierName: 'Acme Supply',
    status,
    isMeshPartner: false,
    lines: [
      {
        id: 'line-1',
        variantId: 'var-1',
        qtyOrdered: 10,
        qtyReceived,
        unitCost: 4.5,
      },
    ],
    ...extras,
  };
}

function renderWorkspace(roles: string[]) {
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
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/purchasing/orders/po-1']}>
        <ToastProvider>
          <Routes>
            <Route path="/purchasing/orders/:id" element={<PurchaseOrderDetailPage />} />
          </Routes>
        </ToastProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('PurchaseOrderDetailPage ledger lock', () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
    vi.mocked(apiClient.get).mockImplementation(async (url: string) => {
      if (String(url).includes('/variants')) {
        return { data: { items: [{ id: 'var-1', sku: 'WIDGET-S', name: 'Widget S' }] } };
      }
      return { data: po('DRAFT') };
    });
  });

  it('enables inline editors and submit on drafts', async () => {
    renderWorkspace(['WAREHOUSE_MANAGER']);
    expect(await screen.findByTestId('po-workspace')).toHaveAttribute('data-locked', 'false');
    expect(screen.getByTestId('submit-po')).toBeInTheDocument();
    expect(screen.getByTestId('po-add-item')).toBeInTheDocument();
    expect(screen.getByTestId('po-line-qty-line-1')).toBeInTheDocument();
    expect(screen.queryByTestId('cancel-po')).not.toBeInTheDocument();
  });

  it('locks the grid after submit and shows cancel when nothing is received', async () => {
    vi.mocked(apiClient.get).mockImplementation(async (url: string) => {
      if (String(url).includes('/variants')) {
        return { data: { items: [] } };
      }
      return { data: po('SUBMITTED') };
    });
    renderWorkspace(['WAREHOUSE_MANAGER']);
    expect(await screen.findByTestId('po-workspace')).toHaveAttribute('data-locked', 'true');
    expect(screen.queryByTestId('submit-po')).not.toBeInTheDocument();
    expect(screen.queryByTestId('po-add-item')).not.toBeInTheDocument();
    expect(screen.getByTestId('po-line-qty-locked-line-1')).toBeInTheDocument();
    expect(screen.getByTestId('cancel-po')).toBeInTheDocument();
    expect(screen.getByTestId('mark-in-transit')).toBeEnabled();
    expect(screen.queryByTestId('reverse-receipt')).not.toBeInTheDocument();
  });

  it('hides cancel after the PO is in transit and shows tracking', async () => {
    vi.mocked(apiClient.get).mockImplementation(async (url: string) => {
      if (String(url).includes('/variants')) {
        return { data: { items: [] } };
      }
      return { data: po('IN_TRANSIT', 0, { trackingNumber: '1Z999', carrier: 'UPS' }) };
    });
    renderWorkspace(['WAREHOUSE_MANAGER']);
    expect(await screen.findByTestId('po-workspace-status')).toHaveTextContent(/IN TRANSIT/i);
    expect(screen.queryByTestId('cancel-po')).not.toBeInTheDocument();
    expect(screen.getByTestId('po-tracking-details')).toHaveTextContent(/1Z999/);
    expect(screen.getByTestId('revert-to-submitted')).toBeInTheDocument();
  });

  it('disables manual transit for mesh partners', async () => {
    vi.mocked(apiClient.get).mockImplementation(async (url: string) => {
      if (String(url).includes('/variants')) {
        return { data: { items: [] } };
      }
      return { data: po('SUBMITTED', 0, { isMeshPartner: true }) };
    });
    renderWorkspace(['WAREHOUSE_MANAGER']);
    expect(await screen.findByTestId('mark-in-transit')).toBeDisabled();
    expect(screen.getByTestId('po-mesh-badge')).toBeInTheDocument();
    expect(screen.getByTestId('po-mesh-automation-banner')).toBeInTheDocument();
  });

  it('shows reverse receipt only for managers after stock is received', async () => {
    vi.mocked(apiClient.get).mockImplementation(async (url: string) => {
      if (String(url).includes('/variants')) {
        return { data: { items: [] } };
      }
      return { data: po('PARTIALLY_RECEIVED', 4) };
    });
    renderWorkspace(['WAREHOUSE_MANAGER']);
    expect(await screen.findByTestId('reverse-receipt')).toBeInTheDocument();
    expect(screen.queryByTestId('cancel-po')).not.toBeInTheDocument();
  });

  it('hides reverse receipt from pickers even when stock is received', async () => {
    vi.mocked(apiClient.get).mockImplementation(async (url: string) => {
      if (String(url).includes('/variants')) {
        return { data: { items: [] } };
      }
      return { data: po('RECEIVED', 10) };
    });
    renderWorkspace(['PICKER']);
    expect(await screen.findByTestId('po-workspace-lock')).toBeInTheDocument();
    expect(screen.queryByTestId('reverse-receipt')).not.toBeInTheDocument();
  });
});
