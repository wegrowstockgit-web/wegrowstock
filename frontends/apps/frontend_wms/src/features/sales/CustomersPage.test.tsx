import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { CustomersPage } from './CustomersPage';
import { apiClient } from '@/api/client';
import { listCustomers } from '@/api/operational';
import { useSessionStore } from '@/stores/session';
import type { Customer } from '@/api/types';

vi.mock('@/api/client', () => ({
  apiClient: { get: vi.fn(), post: vi.fn() },
}));

vi.mock('@/api/operational', () => ({
  listCustomers: vi.fn(),
}));

function customer(overrides: Partial<Customer> = {}): Customer {
  return {
    id: 'cust-1',
    name: 'Northwind Farms',
    email: 'buyer@northwind.test',
    paymentTerms: 'NET30',
    creditLimit: 50000,
    availableCredit: 15000,
    priceTierName: 'Wholesale',
    portalStatus: 'PENDING',
    customerStatus: 'ACTIVE',
    ...overrides,
  };
}

function renderPage(roles = ['ADMIN']) {
  useSessionStore.setState({
    authenticated: true,
    user: {
      id: 'u1',
      email: 'admin@demo.test',
      displayName: 'Admin',
      roles,
      warehouseIds: [],
      avatarUrl: null,
      tenantId: 't1',
    },
    lastRequestId: null,
    primarySession: null,
  });
  vi.mocked(listCustomers).mockResolvedValue({
    items: [customer()],
    totalElements: 1,
    totalPages: 1,
    page: 1,
    size: 50,
    hasMore: false,
  });
  vi.mocked(apiClient.get).mockImplementation(async (url: string) => {
    if (String(url).includes('/customers/summary')) {
      return {
        data: { activeB2bAccounts: 12, accountsOnCreditHold: 2, totalCreditExtended: 125000 },
      };
    }
    return { data: {} };
  });
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/customers']}>
        <CustomersPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('CustomersPage B2B grid', () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
    vi.mocked(listCustomers).mockReset();
  });

  it('renders KPI cards, enriched columns, and formatted credit', async () => {
    renderPage();
    expect(await screen.findByTestId('customer-kpi-row')).toBeInTheDocument();
    expect(await screen.findByTestId('kpi-active-b2b')).toHaveTextContent('12');
    expect(screen.getByTestId('kpi-credit-hold')).toHaveTextContent('2');
    expect(screen.getByRole('columnheader', { name: 'Name & Email' })).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: 'Price Tier' })).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: 'Credit Available' })).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: 'Portal Status' })).toBeInTheDocument();
    expect(screen.getByTestId('customer-tier-cust-1')).toHaveTextContent('Wholesale');
    expect(screen.getByTestId('customer-credit-cust-1')).toHaveTextContent(/Limit/);
    expect(screen.getByTestId('customer-portal-cust-1')).toHaveTextContent('Pending');
    expect(screen.getByRole('button', { name: 'Add customer' })).toBeInTheDocument();
  });
});
