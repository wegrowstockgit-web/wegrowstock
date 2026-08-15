import { describe, expect, it, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { LedgerHistoryTable } from './LedgerHistoryTable';
import * as inventoryApi from '@/api/inventory';
import { ToastProvider } from '@/components/ui/Toast';
import { useSessionStore } from '@/stores/session';

vi.mock('@/api/inventory', () => ({
  listLedgerTransactions: vi.fn(),
  reverseLedgerTransaction: vi.fn(),
}));

function renderTable() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <LedgerHistoryTable />
      </ToastProvider>
    </QueryClientProvider>,
  );
}

describe('LedgerHistoryTable', () => {
  beforeEach(() => {
    vi.mocked(inventoryApi.listLedgerTransactions).mockReset();
    vi.mocked(inventoryApi.reverseLedgerTransaction).mockReset();

    HTMLDialogElement.prototype.showModal = function showModal(this: HTMLDialogElement) {
      this.setAttribute('open', '');
    };
    HTMLDialogElement.prototype.close = function close(this: HTMLDialogElement) {
      this.removeAttribute('open');
    };

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
      primarySession: null,
      lastRequestId: null,
    });
  });

  it('shows undo for reversible rows and hides it for ERROR_CORRECTION', async () => {
    vi.mocked(inventoryApi.listLedgerTransactions).mockResolvedValue([
      {
        id: 'led-1',
        variantId: 'v1',
        locationId: 'l1',
        lotId: null,
        movementType: 'RECEIVE',
        quantityDelta: 10,
        reasonCode: null,
        referenceType: null,
        referenceId: null,
        reversalOfLedgerId: null,
        unitCost: null,
        createdAt: '2026-07-01T12:00:00Z',
      },
      {
        id: 'led-2',
        variantId: 'v1',
        locationId: 'l1',
        lotId: null,
        movementType: 'ADJUST',
        quantityDelta: -10,
        reasonCode: 'ERROR_CORRECTION',
        referenceType: null,
        referenceId: null,
        reversalOfLedgerId: 'led-0',
        unitCost: null,
        createdAt: '2026-07-01T13:00:00Z',
      },
    ]);

    renderTable();

    await waitFor(() => expect(screen.getByTestId('reverse-ledger-led-1')).toBeInTheDocument());
    expect(screen.queryByTestId('reverse-ledger-led-2')).not.toBeInTheDocument();
  });

  it('confirms reversal via AlertDialog and calls the mutation', async () => {
    vi.mocked(inventoryApi.listLedgerTransactions).mockResolvedValue([
      {
        id: 'led-9',
        variantId: 'v1',
        locationId: 'l1',
        lotId: null,
        movementType: 'ADJUST',
        quantityDelta: 3,
        reasonCode: 'CYCLE_COUNT',
        referenceType: null,
        referenceId: null,
        reversalOfLedgerId: null,
        unitCost: 1.5,
        createdAt: '2026-07-02T10:00:00Z',
      },
    ]);
    vi.mocked(inventoryApi.reverseLedgerTransaction).mockResolvedValue({
      id: 'led-rev',
      variantId: 'v1',
      locationId: 'l1',
      lotId: null,
      movementType: 'ADJUST',
      quantityDelta: -3,
      reasonCode: 'ERROR_CORRECTION',
      referenceType: null,
      referenceId: null,
      reversalOfLedgerId: 'led-9',
      unitCost: 1.5,
      createdAt: '2026-07-02T10:05:00Z',
    });

    renderTable();
    await waitFor(() => expect(screen.getByTestId('reverse-ledger-led-9')).toBeInTheDocument());

    fireEvent.click(screen.getByTestId('reverse-ledger-led-9'));
    expect(screen.getByText('Reverse Transaction?')).toBeInTheDocument();
    fireEvent.click(screen.getByTestId('alert-dialog-confirm'));

    await waitFor(() => {
      expect(inventoryApi.reverseLedgerTransaction).toHaveBeenCalledWith('led-9');
    });
    await waitFor(() => {
      expect(screen.getByText('Transaction reversed')).toBeInTheDocument();
    });
  });
});
