import type { ReactElement } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes, useNavigate } from 'react-router-dom';
import { PageHelpOverlay } from './PageHelpOverlay';

vi.mock('@/lib/pageKnowledge/dynamicApi', () => ({
  fetchAllPageKnowledge: vi.fn().mockResolvedValue([]),
  fetchPageKnowledgeByRoute: vi.fn(),
}));

function renderHelp(ui: ReactElement) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
}

function mockMatchMedia(matches: boolean) {
  window.matchMedia = vi.fn().mockImplementation((query: string) => ({
    matches,
    media: query,
    onchange: null,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn(),
  }));
}

function NavigateButton({ to }: { to: string }) {
  const navigate = useNavigate();
  return (
    <button type="button" onClick={() => navigate(to)}>
      Go {to}
    </button>
  );
}

describe('PageHelpOverlay', () => {
  beforeEach(() => {
    mockMatchMedia(false);
  });

  it('opens portaled panel with sales-order playbook', async () => {
    const user = userEvent.setup();
    renderHelp(
      <MemoryRouter initialEntries={['/sales-orders']}>
        <PageHelpOverlay />
      </MemoryRouter>,
    );

    await user.click(screen.getByTestId('page-help-trigger'));
    await waitFor(() => expect(screen.getByTestId('page-help-panel')).toBeInTheDocument());
    expect(screen.getByTestId('page-help-panel').parentElement?.parentElement).toBe(document.body);
    expect(screen.getByTestId('page-help-body')).toHaveTextContent(/Confirm customer demand/i);
    expect(screen.getByTestId('page-help-body')).toHaveTextContent(/Un-allocate/i);
    expect(screen.getByTestId('page-help-body')).toHaveTextContent(/Step-by-step/i);
    expect(screen.getByTestId('page-help-body')).toHaveTextContent(/How to undo a mistake/i);
    expect(screen.getByTestId('page-help-roles')).toHaveTextContent(/Warehouse Managers/i);
    expect(screen.getByTestId('page-help-data-origin')).toHaveTextContent(/Where this comes from/i);
    expect(screen.getByTestId('page-help-body')).toHaveTextContent(/Who else this affects/i);
    expect(screen.getByTestId('page-help-body')).toHaveTextContent(/Components, columns & statuses/i);
    expect(screen.getByTestId('page-help-statuses')).toHaveTextContent(/ALLOCATED/i);
    expect(screen.getByTestId('page-help-quick-actions')).toHaveTextContent(/Go to Fulfillment/i);
    expect(screen.getByTestId('page-help-troubleshooting')).toHaveTextContent(/Unallocated/i);
  });

  it('opens inbound receive playbook', async () => {
    const user = userEvent.setup();
    renderHelp(
      <MemoryRouter initialEntries={['/inbound/receive']}>
        <PageHelpOverlay />
      </MemoryRouter>,
    );

    await user.click(screen.getByTestId('page-help-trigger'));
    await waitFor(() => expect(screen.getByTestId('page-help-drawer')).toBeInTheDocument());
    expect(screen.getByTestId('page-help-body')).toHaveTextContent(/Check in newly arrived inventory/i);
  });

  it('renders preloaded dynamic knowledge above the static playbook', async () => {
    const { fetchAllPageKnowledge } = await import('@/lib/pageKnowledge/dynamicApi');
    vi.mocked(fetchAllPageKnowledge).mockResolvedValueOnce([
      {
        id: 'po',
        routePattern: '/purchase-orders',
        category: 'Inbound',
        title: 'Purchase Orders',
        summary: 'Order stock from a supplier. Purpose: inbound contracts.',
        rolePrivileges: 'Warehouse Managers create POs.',
        keyActions: ['Click New PO'],
        commonMistakes: [
          {
            mistake: 'Wrong price',
            solution: 'Reverse the receipt. Never delete history.',
            requiredRole: 'WAREHOUSE_MANAGER',
          },
        ],
        proTip: 'Confirm unit cost out loud.',
      },
    ]);
    const user = userEvent.setup();
    renderHelp(
      <MemoryRouter initialEntries={['/purchase-orders']}>
        <PageHelpOverlay />
      </MemoryRouter>,
    );
    await user.click(screen.getByTestId('page-help-trigger'));
    await waitFor(() => expect(screen.getByTestId('page-help-dynamic')).toBeInTheDocument());
    expect(screen.getByTestId('page-help-dynamic')).toHaveTextContent(/Inbound/i);
    expect(screen.getByTestId('page-help-mistakes')).toHaveTextContent(/Reverse the receipt/i);
    expect(screen.getByTestId('page-help-pro-tip')).toHaveTextContent(/unit cost/i);
  });

  it('shows fallback playbook for unregistered routes', async () => {
    const user = userEvent.setup();
    renderHelp(
      <MemoryRouter initialEntries={['/totally-unknown-route']}>
        <PageHelpOverlay />
      </MemoryRouter>,
    );

    await user.click(screen.getByTestId('page-help-trigger'));
    await waitFor(() => expect(screen.getByTestId('page-help-fallback')).toBeInTheDocument());
    expect(screen.getByTestId('page-help-fallback')).toHaveTextContent(/totally-unknown-route/);
    expect(screen.getByTestId('page-help-fallback')).toHaveTextContent(/never erase inventory history/i);
  });

  it('closes via Escape and the X control', async () => {
    const user = userEvent.setup();
    renderHelp(
      <MemoryRouter initialEntries={['/sales-orders']}>
        <PageHelpOverlay />
      </MemoryRouter>,
    );

    await user.click(screen.getByTestId('page-help-trigger'));
    await waitFor(() => expect(screen.getByTestId('page-help-body')).toBeVisible());

    fireEvent.keyDown(document, { key: 'Escape' });
    await waitFor(() =>
      expect(screen.queryByTestId('page-help-body')).not.toBeInTheDocument(),
    );

    await user.click(screen.getByTestId('page-help-trigger'));
    await waitFor(() => expect(screen.getByTestId('page-help-body')).toBeVisible());
    await user.click(screen.getByRole('button', { name: 'Close', exact: true }));
    await waitFor(() =>
      expect(screen.queryByTestId('page-help-body')).not.toBeInTheDocument(),
    );
  });

  it('keeps the drawer open and cross-fades content when the route changes', async () => {
    const user = userEvent.setup();
    renderHelp(
      <MemoryRouter initialEntries={['/sales-orders']}>
        <Routes>
          <Route
            path="*"
            element={
              <>
                <PageHelpOverlay />
                <NavigateButton to="/products" />
              </>
            }
          />
        </Routes>
      </MemoryRouter>,
    );

    await user.click(screen.getByTestId('page-help-trigger'));
    await waitFor(() => expect(screen.getByTestId('page-help-panel')).toBeInTheDocument());
    expect(screen.getByTestId('page-help-title')).toHaveTextContent(/Sales Orders/i);

    await user.click(screen.getByRole('button', { name: 'Go /products' }));
    await waitFor(() => expect(screen.getByTestId('page-help-panel')).toBeInTheDocument());
    await waitFor(() =>
      expect(screen.getByTestId('page-help-title')).toHaveTextContent(/Product Catalog/i),
    );
    expect(screen.getByTestId('page-help-route')).toHaveTextContent('/products');
  });

  it('swaps settings-tab playbooks when search params change while open', async () => {
    const user = userEvent.setup();
    function TabNav() {
      const navigate = useNavigate();
      return (
        <>
          <PageHelpOverlay />
          <button type="button" onClick={() => navigate('/settings?tab=users')}>
            Users tab
          </button>
          <button type="button" onClick={() => navigate('/settings?tab=operations')}>
            Operations tab
          </button>
        </>
      );
    }

    renderHelp(
      <MemoryRouter initialEntries={['/settings?tab=users']}>
        <Routes>
          <Route path="*" element={<TabNav />} />
        </Routes>
      </MemoryRouter>,
    );

    await user.click(screen.getByTestId('page-help-trigger'));
    await waitFor(() => expect(screen.getByTestId('page-help-title')).toHaveTextContent(/Users/i));
    expect(screen.getByTestId('page-help-body').textContent).toMatch(/OWNER|PICKER|LBAC/i);

    await user.click(screen.getByRole('button', { name: 'Operations tab' }));
    await waitFor(() =>
      expect(screen.getByTestId('page-help-title')).toHaveTextContent(/Operations/i),
    );
    expect(screen.getByTestId('page-help-route')).toHaveTextContent('/settings?tab=operations');
    expect(screen.getByTestId('page-help-body').textContent).toMatch(/Audit|adjustment/i);
  });

  it('navigates from a quick action and closes the overlay', async () => {
    const user = userEvent.setup();
    renderHelp(
      <MemoryRouter initialEntries={['/sales-orders']}>
        <Routes>
          <Route
            path="*"
            element={
              <>
                <PageHelpOverlay />
              </>
            }
          />
        </Routes>
      </MemoryRouter>,
    );

    await user.click(screen.getByTestId('page-help-trigger'));
    await waitFor(() => expect(screen.getByTestId('page-help-quick-actions')).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: /Go to Fulfillment/i }));
    await waitFor(() => expect(screen.queryByTestId('page-help-body')).not.toBeInTheDocument());
  });

  it('renders Spanish playbook chrome when the profile language is es', async () => {
    const { default: i18n } = await import('@/lib/i18n');
    await i18n.changeLanguage('es');
    const user = userEvent.setup();
    const view = renderHelp(
      <MemoryRouter initialEntries={['/dashboard']}>
        <PageHelpOverlay />
      </MemoryRouter>,
    );
    try {
      await user.click(screen.getByTestId('page-help-trigger'));
      await waitFor(() => expect(screen.getByTestId('page-help-title')).toHaveTextContent(/Centro de mando/i));
      expect(screen.getByTestId('page-help-body')).toHaveTextContent(/Tomar acción/i);
      expect(screen.getByTestId('page-help-quick-actions')).toHaveTextContent(/Ver mis tareas/i);
    } finally {
      view.unmount();
      await i18n.changeLanguage('en');
    }
  });

  it('translates remaining route playbooks in Spanish and French', async () => {
    const { default: i18n } = await import('@/lib/i18n');
    const user = userEvent.setup();
    try {
      await i18n.changeLanguage('es');
      const esView = renderHelp(
        <MemoryRouter initialEntries={['/inventory/ledger']}>
          <PageHelpOverlay />
        </MemoryRouter>,
      );
      await user.click(screen.getByTestId('page-help-trigger'));
      await waitFor(() =>
        expect(screen.getByTestId('page-help-title')).toHaveTextContent(/Libro de inventario/i),
      );
      esView.unmount();

      await i18n.changeLanguage('fr');
      const frView = renderHelp(
        <MemoryRouter initialEntries={['/settings?tab=users']}>
          <PageHelpOverlay />
        </MemoryRouter>,
      );
      await user.click(screen.getByTestId('page-help-trigger'));
      await waitFor(() =>
        expect(screen.getByTestId('page-help-title')).toHaveTextContent(/Utilisateurs/i),
      );
      frView.unmount();
    } finally {
      await i18n.changeLanguage('en');
    }
  });
});
