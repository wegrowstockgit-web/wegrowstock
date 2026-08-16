import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { WholesaleApplicationsPanel } from './WholesaleApplicationsPanel';
import { approveWholesaleApplication, listWholesaleApplications } from '@/api/portal';
import { ToastProvider } from '@/components/ui/Toast';

vi.mock('@/api/portal', () => ({
  listWholesaleApplications: vi.fn(),
  approveWholesaleApplication: vi.fn(),
}));

function renderPanel() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <WholesaleApplicationsPanel />
      </ToastProvider>
    </QueryClientProvider>,
  );
}

describe('WholesaleApplicationsPanel', () => {
  beforeEach(() => {
    vi.mocked(listWholesaleApplications).mockResolvedValue([
      {
        id: 'app-1',
        companyName: 'Northwind Growers',
        taxId: '12-3456789',
        contactName: 'Ada Buyer',
        email: 'ada@northwind.test',
        phone: '555-0100',
        status: 'PENDING',
        createdAt: new Date().toISOString(),
      },
    ]);
    vi.mocked(approveWholesaleApplication).mockResolvedValue({
      id: 'app-1',
      companyName: 'Northwind Growers',
      taxId: '12-3456789',
      contactName: 'Ada Buyer',
      email: 'ada@northwind.test',
      status: 'APPROVED',
      createdAt: new Date().toISOString(),
      customerId: 'cust-1',
    });
  });

  it('opens a review drawer and approves with a welcome link', async () => {
    const user = userEvent.setup();
    renderPanel();

    await user.click(await screen.findByText('Northwind Growers'));
    expect(await screen.findByTestId('wholesale-application-drawer')).toBeInTheDocument();
    expect(screen.getAllByText('12-3456789').length).toBeGreaterThan(0);

    await user.click(screen.getByTestId('approve-welcome-link'));
    expect((await screen.findAllByText(/welcome link sent/i)).length).toBeGreaterThan(0);
    expect(approveWholesaleApplication).toHaveBeenCalledWith('app-1');
  });
});
