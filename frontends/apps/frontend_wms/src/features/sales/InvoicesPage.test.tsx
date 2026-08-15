import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { InvoicesPage } from './InvoicesPage';
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
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <InvoicesPage />
      </ToastProvider>
    </QueryClientProvider>,
  );
}

describe('InvoicesPage PDF actions', () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
    vi.mocked(apiClient.post).mockReset();
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
      if (url === '/api/v1/invoices') {
        return {
          data: [
            {
              id: 'inv-1',
              number: 'INV-1002',
              customerName: 'Buyer Co',
              status: 'OPEN',
              total: 20,
              currency: 'USD',
            },
          ],
        };
      }
      if (url === '/api/v1/invoices/inv-1') {
        return {
          data: {
            id: 'inv-1',
            number: 'INV-1002',
            customerName: 'Buyer Co',
            status: 'OPEN',
            total: 20,
            currency: 'USD',
            documentUrl: 's3://invsys-media/t1/invoices/inv-1.pdf',
          },
        };
      }
      if (String(url).includes('/documents/invoice/inv-1/pdf')) {
        return { data: new Blob(['%PDF-1.4'], { type: 'application/pdf' }) };
      }
      return { data: [] };
    });
  });

  it('downloads PDF and emails invoice from peek drawer', async () => {
    const user = userEvent.setup();
    vi.mocked(apiClient.post).mockResolvedValue({
      data: { sent: true, to: 'ap@buyer.test', documentUrl: 's3://x', invoiceNumber: 'INV-1002' },
    });

    const createObjectURL = vi.fn(() => 'blob:invoice');
    const revokeObjectURL = vi.fn();
    vi.stubGlobal('URL', { ...URL, createObjectURL, revokeObjectURL });

    renderPage();
    expect(await screen.findByText('INV-1002')).toBeInTheDocument();
    await user.click(screen.getByText('INV-1002'));

    expect(await screen.findByTestId('invoice-download-pdf')).toBeInTheDocument();
    await user.click(screen.getByTestId('invoice-download-pdf'));
    await waitFor(() => {
      expect(apiClient.get).toHaveBeenCalledWith(
        '/api/v1/documents/invoice/inv-1/pdf',
        expect.objectContaining({ responseType: 'blob' }),
      );
    });

    await user.click(screen.getByTestId('invoice-email-pdf'));
    await waitFor(() => {
      expect(apiClient.post).toHaveBeenCalledWith('/api/v1/documents/invoice/inv-1/email');
    });
  });
});
