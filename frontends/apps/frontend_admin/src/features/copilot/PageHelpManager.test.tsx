import { describe, expect, it, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ToastProvider } from '@invsys/shared-ui';
import { PageHelpManager } from './PageHelpManager';
import { fetchPageHelp } from './pageHelpApi';

vi.mock('./pageHelpApi', () => ({
  PAGE_HELP_CATEGORIES: [
    'Core',
    'Inbound',
    'Fulfillment',
    'Inventory',
    'Manufacturing',
    'Field',
    'Sales',
    'Showroom',
    'Settings',
    'Platform',
  ],
  fetchPageHelp: vi.fn(),
  createPageHelp: vi.fn(),
  updatePageHelp: vi.fn(),
  deletePageHelp: vi.fn(),
}));

function renderManager() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <PageHelpManager />
      </ToastProvider>
    </QueryClientProvider>,
  );
}

describe('PageHelpManager', () => {
  beforeEach(() => {
    vi.mocked(fetchPageHelp).mockReset();
    vi.mocked(fetchPageHelp).mockResolvedValue([
      {
        id: '1',
        routePattern: '/dashboard',
        category: 'Core',
        title: 'Command Center',
        summary: 'Daily weGrowStock KPIs.',
        rolePrivileges: 'Everyone',
        keyActions: ['Scan KPIs'],
        commonMistakes: [
          {
            mistake: 'Fat-fingered a receive',
            solution: 'Reverse the ledger entry.',
            requiredRole: 'WAREHOUSE_MANAGER',
          },
        ],
        proTip: 'Work the queue first.',
        updatedAt: '2026-08-21T00:00:00Z',
      },
    ]);
  });

  it('lists seeded routes and opens a live preview editor', async () => {
    renderManager();
    expect(screen.getByTestId('page-help-manager')).toBeTruthy();
    expect(await screen.findByText('Command Center')).toBeTruthy();
    expect(screen.getByText('/dashboard')).toBeTruthy();

    fireEvent.click(screen.getByTestId('page-help-create'));
    expect(await screen.findByTestId('page-help-editor')).toBeTruthy();
    expect(screen.getByTestId('page-help-preview').textContent).toMatch(/Required Privileges/i);
    expect(screen.getByTestId('page-help-preview').textContent).toMatch(/Common Mistakes/i);
  });
});
