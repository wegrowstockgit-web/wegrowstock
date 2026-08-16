import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { ShowroomApplyPage } from './ShowroomApplyPage';
import { applyForWholesale } from '@/api/portal';

vi.mock('@/api/portal', () => ({
  applyForWholesale: vi.fn(),
}));

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <ShowroomApplyPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('ShowroomApplyPage', () => {
  it('submits the wholesale application and shows the review confirmation', async () => {
    const user = userEvent.setup();
    vi.mocked(applyForWholesale).mockResolvedValue({
      id: 'app-1',
      companyName: 'Northwind',
      taxId: '12-3456789',
      contactName: 'Ada',
      email: 'ada@northwind.test',
      status: 'PENDING',
      createdAt: new Date().toISOString(),
    });
    renderPage();

    await user.type(screen.getByLabelText('Company Name'), 'Northwind');
    await user.type(screen.getByLabelText('Tax/VAT ID (RFC/EIN)'), '12-3456789');
    await user.type(screen.getByLabelText('Contact Name'), 'Ada');
    await user.type(screen.getByLabelText('Email'), 'ada@northwind.test');
    await user.type(screen.getByLabelText('Phone'), '555-0100');
    await user.click(screen.getByRole('button', { name: 'Submit application' }));

    expect(await screen.findByTestId('showroom-apply-success')).toBeInTheDocument();
    expect(screen.getByText('Application Submitted')).toBeInTheDocument();
    expect(screen.getByText(/Under Review by our Wholesale Team/i)).toBeInTheDocument();
    expect(applyForWholesale).toHaveBeenCalledWith({
      companyName: 'Northwind',
      taxId: '12-3456789',
      contactName: 'Ada',
      email: 'ada@northwind.test',
      phone: '555-0100',
    });
  });
});
