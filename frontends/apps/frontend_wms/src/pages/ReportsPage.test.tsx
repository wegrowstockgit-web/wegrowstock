import { describe, expect, it, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { ReportsPage } from './ReportsPage';
import { apiClient } from '@/api/client';
import { useSessionStore } from '@/stores/session';

vi.mock('@/api/client', () => ({
  apiClient: {
    get: vi.fn(),
  },
}));

vi.mock('@tanstack/react-virtual', () => ({
  useVirtualizer: ({ count, estimateSize }: { count: number; estimateSize?: () => number }) => {
    const size = estimateSize?.() ?? 40;
    const items = Array.from({ length: count }, (_, index) => ({
      index,
      start: index * size,
      end: (index + 1) * size,
      size,
      key: index,
    }));
    return {
      getVirtualItems: () => items,
      getTotalSize: () => count * size,
      measure: vi.fn(),
    };
  },
}));

function setSession(roles: string[], enabledModules: string[] = ['CORE', 'MRP']) {
  useSessionStore.setState({
    authenticated: true,
    user: {
      id: 'u1',
      email: 'owner@demo.test',
      displayName: 'Owner',
      roles,
      warehouseIds: [],
      avatarUrl: null,
      tenantId: 't1',
      enabledModules,
    },
    primarySession: null,
    lastRequestId: null,
  });
}

function renderReports(path = '/reports') {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/reports" element={<ReportsPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('ReportsPage enterprise UX', () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
    vi.mocked(apiClient.get).mockImplementation(async (url: string) => {
      const path = String(url);
      if (path.includes('/settings')) return { data: { timezone: 'America/New_York' } };
      if (path.includes('/profit-margin')) {
        return {
          data: {
            currency: 'USD',
            totalRevenue: 10,
            totalCogs: 4,
            grossProfit: 6,
            grossMarginPercent: 60,
            revenueByMonth: [],
            profitByMonth: [],
            byProduct: [
              {
                variantId: 'v1',
                sku: 'WIDGET-S',
                productName: 'Widget',
                revenue: 10,
                cogs: 4,
                grossProfit: 6,
                marginPercent: 60,
              },
            ],
            byCustomer: [],
          },
        };
      }
      if (path.includes('/fulfillment-summary')) {
        return {
          data: {
            unitsShipped30d: 1,
            openOrderLines: 0,
            fillRatePercent: 100,
            ordersByStatus: [],
            shippedByWeek: [],
          },
        };
      }
      return { data: {} };
    });
    setSession(['OWNER']);
  });

  it('shows an inline error when the selected window exceeds 90 days', async () => {
    renderReports();
    expect(await screen.findByTestId('report-date-range')).toBeInTheDocument();

    fireEvent.change(screen.getByTestId('report-start-date'), { target: { value: '2025-01-01' } });
    fireEvent.change(screen.getByTestId('report-end-date'), { target: { value: '2025-12-31' } });

    expect(await screen.findByTestId('report-range-error')).toHaveTextContent(
      'Date range cannot exceed 90 days. Narrow the search.',
    );
  });

  it('applies the Last 7 Days preset and keeps the window valid', async () => {
    renderReports();
    expect(await screen.findByTestId('report-date-presets')).toBeInTheDocument();
    fireEvent.click(screen.getByTestId('report-preset-7d'));
    expect(screen.queryByTestId('report-range-error')).not.toBeInTheDocument();
    const start = (screen.getByTestId('report-start-date') as HTMLInputElement).value;
    const end = (screen.getByTestId('report-end-date') as HTMLInputElement).value;
    expect(start < end || start === end).toBe(true);
  });

  it('offers CSV and print from the export menu and links SKUs', async () => {
    const user = userEvent.setup();
    const print = vi.spyOn(window, 'print').mockImplementation(() => undefined);
    renderReports();
    expect(await screen.findByTestId('report-sku-link')).toHaveAttribute('href', '/products?q=WIDGET-S');
    await user.click(screen.getByTestId('report-export-menu'));
    expect(await screen.findByTestId('report-export-csv')).toBeInTheDocument();
    await user.click(screen.getByTestId('report-export-print'));
    expect(print).toHaveBeenCalled();
    print.mockRestore();
  });

  it('hides financial tabs from warehouse managers', async () => {
    setSession(['WAREHOUSE_MANAGER'], ['CORE']);
    renderReports();
    expect(await screen.findByTestId('reports-tab-fulfillment')).toBeInTheDocument();
    expect(screen.queryByTestId('reports-tab-profit')).not.toBeInTheDocument();
    expect(screen.queryByTestId('reports-tab-cogs')).not.toBeInTheDocument();
  });

  it('shows the upgrade page when demand sensing is missing MRP', async () => {
    setSession(['OWNER'], ['CORE']);
    renderReports('/reports?tab=demand');
    expect(await screen.findByTestId('upgrade-page')).toBeInTheDocument();
  });
});
