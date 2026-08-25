import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { PurchaseOrdersPage } from './PurchaseOrdersPage';
import { listPurchaseOrders } from '@/api/operational';
import { useSessionStore } from '@/stores/session';
import { ToastProvider } from '@/components/ui/Toast';

vi.mock('@/api/client', () => ({
  apiClient: {
    get: vi.fn().mockResolvedValue({ data: [] }),
    post: vi.fn(),
  },
}));

vi.mock('@/api/operational', () => ({
  listPurchaseOrders: vi.fn(),
}));

function stubMatchMedia(isMobile: boolean) {
  window.matchMedia = vi.fn().mockImplementation((query: string) => ({
    matches: isMobile && query.includes('max-width: 767px'),
    media: query,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn(),
    onchange: null,
  }));
}

function renderPage(initialEntries = ['/purchase-orders']) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={initialEntries}>
        <ToastProvider>
          <PurchaseOrdersPage />
        </ToastProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('PurchaseOrdersPage mobile cards and header', () => {
  const originalMatchMedia = window.matchMedia;

  beforeEach(() => {
    stubMatchMedia(false);
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
    vi.mocked(listPurchaseOrders).mockResolvedValue({
      items: [
        {
          id: 'po-1',
          number: 'PO-2026-00001',
          supplierName: 'Greenleaf Supply',
          status: 'SUBMITTED',
          createdAt: '2026-08-01T00:00:00Z',
          totalAmount: 420,
          totalQtyOrdered: 10,
          totalQtyReceived: 0,
        },
      ],
      totalElements: 1,
      totalPages: 1,
      page: 1,
      size: 50,
      hasMore: false,
    });
  });

  afterEach(() => {
    cleanup();
    window.matchMedia = originalMatchMedia;
  });

  it('keeps Floor receive and New PO in a wrapping header', async () => {
    renderPage();
    expect(await screen.findByRole('heading', { name: 'Purchase Orders' })).toBeInTheDocument();
    const actions = screen.getByTestId('purchase-orders-header-actions');
    expect(actions).toHaveClass('w-full');
    expect(screen.getByRole('button', { name: 'Floor receive' })).toHaveClass('w-full');
    expect(screen.getByRole('button', { name: 'New PO' })).toHaveClass('w-full');
  });

  it('renders the desktop table above 767px', async () => {
    renderPage();
    expect(await screen.findByTestId('purchase-orders-table-view')).toBeInTheDocument();
    expect(screen.getByText('PO-2026-00001')).toBeInTheDocument();
    expect(screen.queryByTestId('purchase-orders-mobile-list')).not.toBeInTheDocument();
    expect(screen.getByTestId('pagination')).toBeInTheDocument();
  });

  it('renders paginated mobile cards below 768px and opens the peek drawer', async () => {
    stubMatchMedia(true);
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByTestId('purchase-orders-mobile-list')).toBeInTheDocument();
    const card = screen.getByTestId('purchase-order-mobile-card-po-1');
    expect(card).toHaveTextContent('PO-2026-00001');
    expect(card).toHaveTextContent('Greenleaf Supply');
    expect(card).toHaveTextContent('SUBMITTED');
    expect(screen.queryByTestId('purchase-orders-table-view')).not.toBeInTheDocument();
    expect(screen.getByTestId('pagination')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Floor receive' })).toBeVisible();
    expect(screen.getByRole('button', { name: 'New PO' })).toBeVisible();

    await user.click(card);
    expect(await screen.findByTestId('open-po-workspace')).toBeInTheDocument();
  });

  it('caps the server page size to 25 on mobile', async () => {
    stubMatchMedia(true);
    renderPage(['/purchase-orders?size=50']);
    await screen.findByTestId('purchase-orders-mobile-list');
    await waitFor(() => {
      expect(listPurchaseOrders).toHaveBeenCalledWith(
        expect.objectContaining({ size: 25, page: 1 }),
      );
    });
  });
});
