import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { IntegrationsHubPage } from './IntegrationsHubPage';

vi.mock('@/api/client', () => ({
  apiClient: {
    get: vi.fn(),
  },
}));

import { apiClient } from '@/api/client';

describe('IntegrationsHubPage', () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockResolvedValue({
      data: {
        categories: [
          {
            id: 'ECOMMERCE',
            label: 'E-Commerce',
            integrations: [
              { id: 'SHOPIFY', name: 'Shopify', status: 'DISCONNECTED', connected: false },
              { id: 'AMAZON', name: 'Amazon Seller Central', status: 'CONNECTED', connected: true },
            ],
          },
          {
            id: 'ACCOUNTING',
            label: 'Accounting',
            integrations: [
              { id: 'NETSUITE', name: 'NetSuite', status: 'DISCONNECTED', connected: false },
              { id: 'XERO', name: 'Xero', status: 'DISCONNECTED', connected: false },
              { id: 'QUICKBOOKS', name: 'QuickBooks', status: 'DISCONNECTED', connected: false },
            ],
          },
          {
            id: 'EDI',
            label: 'B2B / EDI',
            integrations: [{ id: 'AS2', name: 'AS2 Trading Partners', status: 'DISCONNECTED', connected: false }],
          },
        ],
      },
    } as never);
  });

  it('renders category grids with connect/options actions', async () => {
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter>
          <IntegrationsHubPage />
        </MemoryRouter>
      </QueryClientProvider>,
    );

    expect(await screen.findByTestId('integrations-hub-page')).toBeInTheDocument();
    expect(await screen.findByTestId('integration-card-SHOPIFY')).toBeInTheDocument();
    expect(screen.getByTestId('integration-action-SHOPIFY')).toHaveTextContent('Connect');
    expect(screen.getByTestId('integration-action-AMAZON')).toHaveTextContent('Options');
    expect(screen.getByTestId('integrations-hub-category-ACCOUNTING')).toBeInTheDocument();
    expect(screen.getByTestId('integration-card-AS2')).toBeInTheDocument();
  });
});
