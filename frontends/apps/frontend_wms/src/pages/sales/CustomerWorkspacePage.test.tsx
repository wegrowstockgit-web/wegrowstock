import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { AxiosError, AxiosHeaders } from 'axios';
import { CustomerWorkspacePage } from './CustomerWorkspacePage';
import { apiClient } from '@/api/client';
import { useSessionStore } from '@/stores/session';
import { ToastProvider } from '@/components/ui/Toast';
import type { Customer } from '@/api/types';

vi.mock('@/api/client', () => ({
  apiClient: { get: vi.fn(), post: vi.fn() },
}));

function customer(overrides: Partial<Customer> = {}): Customer {
  return {
    id: 'cust-1',
    name: 'Northwind Farms',
    email: 'buyer@demo.test',
    portalStatus: 'NOT_INVITED',
    customerStatus: 'ACTIVE',
    priceTierName: 'Wholesale',
    creditLimit: 15000,
    availableCredit: 15000,
    ...overrides,
  };
}

function axiosError(data: unknown, status = 409): AxiosError {
  return new AxiosError('Request failed', 'ERR_BAD_REQUEST', undefined, undefined, {
    status,
    statusText: 'Conflict',
    headers: new AxiosHeaders(),
    config: { headers: new AxiosHeaders() },
    data,
  });
}

function renderWorkspace(data: Customer) {
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
    if (path.includes('/customers/cust-1/billing') || path.includes('/billing')) {
      return { data: { sla: null, unbilledAccruals: [], unbilledTotal: 0 } };
    }
    return { data };
  });
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/sales/customers/cust-1']}>
        <ToastProvider>
          <Routes>
            <Route path="/sales/customers/:id" element={<CustomerWorkspacePage />} />
          </Routes>
        </ToastProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('CustomerWorkspacePage portal invite', () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
    vi.mocked(apiClient.post).mockReset();
  });

  it('shows Invite Pending and disables the button when portal status is PENDING', async () => {
    renderWorkspace(customer({ portalStatus: 'PENDING' }));
    expect(await screen.findByRole('heading', { name: 'Northwind Farms' })).toBeInTheDocument();
    const button = screen.getByTestId('customer-portal-invite');
    expect(button).toHaveTextContent('Invite Pending');
    expect(button).toBeDisabled();
  });

  it('enables Send B2B Portal Invite when the customer is not invited', async () => {
    renderWorkspace(customer({ portalStatus: 'NOT_INVITED' }));
    expect(await screen.findByRole('heading', { name: 'Northwind Farms' })).toBeInTheDocument();
    const button = screen.getByTestId('customer-portal-invite');
    expect(button).toHaveTextContent('Send B2B Portal Invite');
    await waitFor(() => expect(button).toBeEnabled());
  });

  it('toasts the Problem Details detail when invite fails', async () => {
    const user = userEvent.setup();
    vi.mocked(apiClient.post).mockRejectedValueOnce(
      axiosError({ detail: 'An open invitation already exists for this email' }),
    );
    renderWorkspace(customer({ portalStatus: 'NOT_INVITED' }));
    expect(await screen.findByRole('heading', { name: 'Northwind Farms' })).toBeInTheDocument();
    await user.click(screen.getByTestId('customer-portal-invite'));
    expect(await screen.findByTestId('app-toast')).toHaveTextContent(
      'An open invitation already exists for this email',
    );
  });

  it('toasts the invite fallback when the payload has no detail', async () => {
    const user = userEvent.setup();
    vi.mocked(apiClient.post).mockRejectedValueOnce(axiosError({ title: 'CONFLICT' }));
    renderWorkspace(customer({ portalStatus: 'NOT_INVITED' }));
    expect(await screen.findByRole('heading', { name: 'Northwind Farms' })).toBeInTheDocument();
    await user.click(screen.getByTestId('customer-portal-invite'));
    expect(await screen.findByTestId('app-toast')).toHaveTextContent('Failed to send invitation');
  });

  it('toasts the Problem Details detail when credit hold fails', async () => {
    const user = userEvent.setup();
    vi.mocked(apiClient.post).mockRejectedValueOnce(
      axiosError({ detail: 'Credit hold is not available for this account' }, 422),
    );
    renderWorkspace(customer());
    expect(await screen.findByRole('heading', { name: 'Northwind Farms' })).toBeInTheDocument();
    await user.click(screen.getByTestId('customer-credit-hold'));
    expect(await screen.findByTestId('app-toast')).toHaveTextContent(
      'Credit hold is not available for this account',
    );
  });
});
