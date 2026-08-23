import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { InvoicesPage } from './InvoicesPage';
import { apiClient } from '@/api/client';
import { listInvoices } from '@/api/operational';
import { useSessionStore } from '@/stores/session';
import { ToastProvider } from '@/components/ui/Toast';

vi.mock('@/api/client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

vi.mock('@/api/operational', () => ({
  listInvoices: vi.fn(),
}));

function renderPage() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <ToastProvider>
          <InvoicesPage />
        </ToastProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('InvoicesPage AR grid', () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
    vi.mocked(apiClient.post).mockReset();
    vi.mocked(listInvoices).mockReset();
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

    vi.mocked(listInvoices).mockResolvedValue({
      items: [
        {
          id: 'inv-1',
          number: 'INV-1002',
          customerName: 'Buyer Co',
          status: 'OPEN',
          total: 100,
          amountPaid: 25,
          balanceDue: 75,
          currency: 'USD',
          dueAt: '2020-01-01T00:00:00Z',
        },
      ],
      totalElements: 1,
      totalPages: 1,
      page: 1,
      size: 50,
      hasMore: false,
    });

    vi.mocked(apiClient.get).mockImplementation(async (url: string) => {
      if (String(url).includes('/documents/invoice/inv-1/pdf')) {
        return { data: new Blob(['%PDF-1.4'], { type: 'application/pdf' }) };
      }
      return { data: [] };
    });
  });

  it('shows balance due, overdue badge, row PDF/email, and bulk email bar', async () => {
    const user = userEvent.setup();
    vi.mocked(apiClient.post).mockResolvedValue({
      data: { sent: 1, failed: 0, to: 'ap@buyer.test' },
    });

    const createObjectURL = vi.fn(() => 'blob:invoice');
    const revokeObjectURL = vi.fn();
    vi.stubGlobal('URL', { ...URL, createObjectURL, revokeObjectURL });

    renderPage();
    expect(await screen.findByRole('columnheader', { name: 'Balance Due' })).toBeInTheDocument();
    expect(screen.getByTestId('invoice-balance-inv-1')).toBeInTheDocument();
    expect(screen.getByTestId('invoice-status-inv-1')).toHaveTextContent('OVERDUE');
    expect(screen.getByTestId('invoice-due-inv-1')).toHaveClass('text-danger');
    expect(screen.queryByTestId('open-invoice-workspace')).not.toBeInTheDocument();

    await user.click(screen.getByTestId('invoice-row-actions-inv-1'));
    await user.click(screen.getByTestId('invoice-download-pdf-inv-1'));
    await waitFor(() => {
      expect(apiClient.get).toHaveBeenCalledWith(
        '/api/v1/documents/invoice/inv-1/pdf',
        expect.objectContaining({ responseType: 'blob' }),
      );
    });

    await user.click(screen.getByTestId('invoice-row-actions-inv-1'));
    await user.click(screen.getByTestId('invoice-email-inv-1'));
    await waitFor(() => {
      expect(apiClient.post).toHaveBeenCalledWith('/api/v1/documents/invoice/inv-1/email');
    });

    await user.click(screen.getByTestId('invoice-select-inv-1'));
    expect(screen.getByTestId('invoice-bulk-bar')).toBeInTheDocument();
    await user.click(screen.getByTestId('invoice-bulk-email'));
    await waitFor(() => {
      expect(apiClient.post).toHaveBeenCalledWith('/api/v1/documents/invoices/email', {
        invoiceIds: ['inv-1'],
      });
    });
  });
});
