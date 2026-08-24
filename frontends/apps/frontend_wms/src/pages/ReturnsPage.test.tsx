import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { AxiosError, AxiosHeaders } from 'axios';
import { ReturnsPage } from './ReturnsPage';
import { apiClient } from '@/api/client';
import { useSessionStore } from '@/stores/session';
import { ToastProvider } from '@/components/ui/Toast';

vi.mock('@/api/client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
  },
}));

const rma = {
  id: 'rma-1',
  number: 'RMA-1001',
  status: 'APPROVED',
  salesOrderId: 'so-1',
  customerName: 'Buyer Co',
  lines: [
    {
      id: 'line-1',
      returnId: 'rma-1',
      salesOrderLineId: 'sol-1',
      sku: 'WIDGET-S',
      quantityExpected: 1,
      quantityReceived: 0,
      disposition: 'RESTOCK',
      restockLocationId: null,
    },
  ],
};

function axiosError(data: unknown, status = 422): AxiosError {
  return new AxiosError('Request failed', 'ERR_BAD_REQUEST', undefined, undefined, {
    status,
    statusText: 'Unprocessable Entity',
    headers: new AxiosHeaders(),
    config: { headers: new AxiosHeaders() },
    data,
  });
}

function renderPage() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <ToastProvider>
          <ReturnsPage />
        </ToastProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('ReturnsPage RMA exceptions', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'IntersectionObserver',
      class {
        observe() {}
        unobserve() {}
        disconnect() {}
      },
    );
    vi.mocked(apiClient.get).mockReset();
    vi.mocked(apiClient.post).mockReset();
    vi.mocked(apiClient.put).mockReset();
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
      const path = String(url);
      if (path.includes('/locations')) {
        return { data: [{ id: 'bin-1', type: 'BIN', code: 'B-01', name: 'Pick', path: 'Z-A/B-01' }] };
      }
      if (path.includes('status=PENDING_REVIEW')) {
        return { data: [] };
      }
      return { data: { items: [rma], hasMore: false } };
    });
  });

  it('blocks complete and marks the restock bin when RESTOCK has no target', async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByRole('button', { name: /RMA-1001/ }));
    const bin = await screen.findByTestId('rma-restock-bin-WIDGET-S');
    expect(bin).toHaveAttribute('aria-invalid', 'true');
    expect(bin).toHaveClass('border-danger');
    expect(screen.getByTestId('rma-complete-disposition')).toBeDisabled();
  });

  it('toasts the backend detail when Escalate to RTV fails', async () => {
    const user = userEvent.setup();
    vi.mocked(apiClient.post).mockRejectedValueOnce(
      axiosError({
        title: 'NO_DEFECT_LINES',
        detail: 'Escalate to RTV requires a DEFECTIVE_PRODUCT reason or SCRAP disposition',
      }),
    );
    renderPage();
    await user.click(await screen.findByRole('button', { name: /RMA-1001/ }));
    await user.click(await screen.findByTestId('rma-escalate-rtv'));
    expect(await screen.findByTestId('app-toast')).toHaveTextContent(
      'Escalate to RTV requires a DEFECTIVE_PRODUCT reason or SCRAP disposition',
    );
  });

  it('toasts a generic message when the payload has no detail', async () => {
    const user = userEvent.setup();
    vi.mocked(apiClient.post).mockRejectedValueOnce(axiosError({ title: 'CLIENT_ERROR' }));
    renderPage();
    await user.click(await screen.findByRole('button', { name: /RMA-1001/ }));
    await user.click(await screen.findByTestId('rma-escalate-rtv'));
    expect(await screen.findByTestId('app-toast')).toHaveTextContent(
      'An unexpected error occurred while processing the RMA.',
    );
    await waitFor(() => {
      expect(apiClient.post).toHaveBeenCalledWith('/api/v1/returns/rma-1/escalate-rtv');
    });
  });
});
