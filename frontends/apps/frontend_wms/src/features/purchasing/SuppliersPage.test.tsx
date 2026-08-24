import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { SuppliersPage } from './SuppliersPage';
import { listSuppliers } from '@/api/operational';
import { useSessionStore } from '@/stores/session';
import { ToastProvider } from '@/components/ui/Toast';

vi.mock('@/api/client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

vi.mock('@/api/operational', () => ({
  listSuppliers: vi.fn(),
}));

function renderPage() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/suppliers']}>
        <ToastProvider>
          <Routes>
            <Route path="/suppliers" element={<SuppliersPage />} />
            <Route
              path="/purchasing/suppliers/:id"
              element={<div data-testid="supplier-workspace">Supplier workspace</div>}
            />
          </Routes>
        </ToastProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('SuppliersPage list routing', () => {
  beforeEach(() => {
    vi.mocked(listSuppliers).mockReset();
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

    vi.mocked(listSuppliers).mockResolvedValue({
      items: [
        {
          id: 'sup-1',
          name: 'Acme Parts',
          paymentTerms: 'NET30',
          supplierClass: 'PACKAGING',
          defaultLeadTimeDays: 7,
          activePoCount: 2,
          supplierRating: 4,
          isMeshPartner: false,
          portalAccess: true,
        },
      ],
      totalElements: 1,
      totalPages: 1,
      page: 1,
      size: 50,
      hasMore: false,
    });
  });

  it('links the name and Open Workspace menu to the workspace without a peek drawer', async () => {
    const user = userEvent.setup();
    renderPage();

    const nameLink = await screen.findByTestId('supplier-name-link-sup-1');
    expect(screen.getByTestId('suppliers-page').className).not.toMatch(/overflow-y-auto/);
    expect(nameLink).toHaveAttribute('href', '/purchasing/suppliers/sup-1');
    expect(nameLink).toHaveClass('text-accent');
    expect(nameLink).toHaveClass('hover:underline');
    expect(screen.queryByTestId('right-peek-drawer')).not.toBeInTheDocument();
    expect(screen.queryByTestId('open-supplier-workspace')).not.toBeInTheDocument();

    await user.click(nameLink);
    expect(await screen.findByTestId('supplier-workspace')).toBeInTheDocument();
  });

  it('opens the workspace from the row actions menu', async () => {
    const user = userEvent.setup();
    renderPage();

    await screen.findByTestId('supplier-row-sup-1');
    await user.click(screen.getByTestId('supplier-row-actions'));
    await user.click(screen.getByTestId('supplier-open-workspace'));
    expect(await screen.findByTestId('supplier-workspace')).toBeInTheDocument();
  });
});
