import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { MeshNetworkPage } from './MeshNetworkPage';
import {
  fetchMeshDiscover,
  fetchMeshNetwork,
  fetchMeshSharedCatalog,
  requestMeshConnection,
  updateMeshListing,
} from '@/api/mesh';

vi.mock('@/api/mesh', () => ({
  fetchMeshDiscover: vi.fn(),
  fetchMeshNetwork: vi.fn(),
  fetchMeshSharedCatalog: vi.fn(),
  requestMeshConnection: vi.fn(),
  approveMeshConnection: vi.fn(),
  updateMeshListing: vi.fn(),
}));

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <MeshNetworkPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('MeshNetworkPage', () => {
  it('shows discover cards and requests a connection', async () => {
    const user = userEvent.setup();
    vi.mocked(fetchMeshDiscover).mockResolvedValue([
      {
        variantId: 'v1',
        productName: 'Partner Widget',
        imageUrl: null,
        sellerName: 'Northwind',
        sellerTenantId: 't-sell',
      },
    ]);
    vi.mocked(fetchMeshNetwork).mockResolvedValue([]);
    vi.mocked(fetchMeshSharedCatalog).mockResolvedValue([]);
    vi.mocked(requestMeshConnection).mockResolvedValue({
      id: 'c1',
      tenantId: 't-buy',
      partnerTenantId: 't-sell',
      connectionStatus: 'REQUESTED',
    });

    renderPage();

    expect(await screen.findByTestId('mesh-discover-grid')).toBeInTheDocument();
    expect(screen.getByText('Partner Widget')).toBeInTheDocument();
    expect(screen.getByText('Northwind')).toBeInTheDocument();
    expect(screen.queryByText(/18\.50|in stock|qty/i)).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Request Connection' }));
    expect(requestMeshConnection).toHaveBeenCalledWith({ variantId: 'v1' });
  });

  it('lists network statuses and shared catalog publish controls', async () => {
    const user = userEvent.setup();
    vi.mocked(fetchMeshDiscover).mockResolvedValue([]);
    vi.mocked(fetchMeshNetwork).mockResolvedValue([
      {
        id: 'c1',
        partnerTenantId: 't-sell',
        partnerName: 'Northwind',
        role: 'BUYER',
        displayStatus: 'REQUESTED',
        connectionStatus: 'REQUESTED',
        canApprove: false,
      },
    ]);
    vi.mocked(fetchMeshSharedCatalog).mockResolvedValue([
      {
        variantId: 'v2',
        sku: 'SKU-2',
        productName: 'Local Widget',
        published: false,
        meshWholesalePrice: 11,
      },
    ]);
    vi.mocked(updateMeshListing).mockResolvedValue({
      variantId: 'v2',
      sku: 'SKU-2',
      productName: 'Local Widget',
      published: true,
      meshWholesalePrice: 11,
    });

    renderPage();

    await user.click(screen.getByTestId('mesh-tab-network'));
    expect(await screen.findByTestId('mesh-network-table')).toBeInTheDocument();
    expect(screen.getByText('Northwind')).toBeInTheDocument();
    expect(screen.getByText('REQUESTED')).toBeInTheDocument();

    await user.click(screen.getByTestId('mesh-tab-catalog'));
    expect(await screen.findByTestId('mesh-shared-catalog')).toBeInTheDocument();
    await user.click(screen.getByLabelText('Publish SKU-2 to network'));
    expect(updateMeshListing).toHaveBeenCalledWith('v2', true, 11);
    expect(screen.getByLabelText('Mesh wholesale price for SKU-2')).toBeInTheDocument();
  });
});
