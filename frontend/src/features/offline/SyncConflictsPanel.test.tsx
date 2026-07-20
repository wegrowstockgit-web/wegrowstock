import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SyncConflictsPanel } from './SyncConflictsPanel';
import type { ServerSyncConflict } from './syncConflictTypes';

const { getMock, postMock, toastMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
  postMock: vi.fn(),
  toastMock: vi.fn(),
}));

vi.mock('@/api/client', () => ({
  apiClient: {
    get: (...args: unknown[]) => getMock(...args),
    post: (...args: unknown[]) => postMock(...args),
  },
}));

vi.mock('@/components/ui/Toast', () => ({
  useToast: () => ({ toast: toastMock }),
}));

const conflict: ServerSyncConflict = {
  id: 'conflict-1',
  pickerDisplayName: 'Floor Picker',
  actionType: 'INBOUND_RECEIVE',
  actionLabel: 'Inbound Receive',
  errorMessage: 'BIN_FULL: allocated bin location is full',
  humanSummary:
    'Floor Operator [Floor Picker] failed to process an [Inbound Receive] transaction because allocated bin location is full.',
  status: 'PENDING',
  payload: {
    url: '/api/v1/fulfillment/scan',
    body: { barcode: '9900000111112', quantity: 2, mode: 'receive', warehouseId: 'wh-1' },
  },
  schemaMetadata: [
    {
      key: 'quantity',
      label: 'Corrected Quantity Count',
      type: 'number',
      mutable: true,
      constraints: { min: 1 },
    },
    {
      key: 'barcode',
      label: 'Scanned Item Master GTIN',
      type: 'string',
      mutable: false,
    },
  ],
  createdAt: new Date().toISOString(),
};

function renderPanel() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <SyncConflictsPanel />
    </QueryClientProvider>,
  );
}

describe('SyncConflictsPanel', () => {
  beforeEach(() => {
    HTMLDialogElement.prototype.showModal = function showModal(this: HTMLDialogElement) {
      this.setAttribute('open', '');
    };
    HTMLDialogElement.prototype.close = function close(this: HTMLDialogElement) {
      this.removeAttribute('open');
    };
    getMock.mockReset();
    postMock.mockReset();
    toastMock.mockReset();
    getMock.mockResolvedValue({ data: [conflict] });
    postMock.mockResolvedValue({ data: { ...conflict, status: 'RESOLVED_AND_REPLAYED' } });
  });

  it('renders human summary and schema-driven fields without raw JSON', async () => {
    renderPanel();
    await waitFor(() => expect(screen.getByTestId('sync-conflict-human-summary')).toBeInTheDocument());
    expect(screen.getByTestId('sync-conflict-human-summary')).toHaveTextContent(/Floor Picker/);
    expect(screen.getByTestId('conflict-field-readonly-barcode')).toHaveTextContent('9900000111112');
    expect(screen.getByTestId('conflict-input-quantity')).toHaveValue(2);
    expect(screen.queryByText(/payload_json|schema_metadata_json/i)).not.toBeInTheDocument();
  });

  it('approves corrections through the confirmation gate', async () => {
    const user = userEvent.setup();
    renderPanel();
    await waitFor(() => expect(screen.getByTestId('approve-conflict-conflict-1')).toBeInTheDocument());

    const qty = screen.getByTestId('conflict-input-quantity');
    await user.clear(qty);
    await user.type(qty, '4');
    await user.click(screen.getByTestId('approve-conflict-conflict-1'));
    await waitFor(() => expect(screen.getByTestId('confirm-approve-conflict')).toBeInTheDocument());
    await user.click(screen.getByTestId('confirm-approve-conflict'));

    await waitFor(() =>
      expect(postMock).toHaveBeenCalledWith(
        '/api/v1/offline-sync-conflicts/conflict-1/resolve',
        { corrections: { quantity: 4 } },
      ),
    );
  });

  it('discards through the confirmation gate', async () => {
    const user = userEvent.setup();
    renderPanel();
    await waitFor(() => expect(screen.getByTestId('discard-conflict-conflict-1')).toBeInTheDocument());
    await user.click(screen.getByTestId('discard-conflict-conflict-1'));
    await user.click(screen.getByTestId('confirm-discard-conflict'));
    await waitFor(() =>
      expect(postMock).toHaveBeenCalledWith('/api/v1/offline-sync-conflicts/conflict-1/dismiss'),
    );
  });

  it('shows empty state when no pending conflicts', async () => {
    getMock.mockResolvedValue({ data: [] });
    renderPanel();
    await waitFor(() => expect(screen.getByTestId('sync-conflicts-empty')).toBeInTheDocument());
  });

  it('falls back to quantity/barcode fields when schema metadata is absent', async () => {
    getMock.mockResolvedValue({
      data: [
        {
          ...conflict,
          id: 'conflict-2',
          schemaMetadata: [],
          humanSummary: undefined,
        },
      ],
    });
    renderPanel();
    await waitFor(() => expect(screen.getByTestId('conflict-input-quantity')).toBeInTheDocument());
    expect(screen.getByTestId('conflict-field-readonly-barcode')).toHaveTextContent('9900000111112');
  });

  it('toasts when corrected quantity is below the schema minimum', async () => {
    const user = userEvent.setup();
    renderPanel();
    await waitFor(() => expect(screen.getByTestId('conflict-input-quantity')).toBeInTheDocument());
    const qty = screen.getByTestId('conflict-input-quantity');
    await user.clear(qty);
    await user.type(qty, '0');
    await user.click(screen.getByTestId('approve-conflict-conflict-1'));
    await waitFor(() =>
      expect(toastMock).toHaveBeenCalledWith(
        expect.stringMatching(/at least 1/i),
        expect.objectContaining({ tone: 'danger' }),
      ),
    );
  });

  it('edits mutable string fields from schema metadata', async () => {
    getMock.mockResolvedValue({
      data: [
        {
          ...conflict,
          payload: {
            ...conflict.payload,
            body: {
              ...(conflict.payload.body as object),
              lotNumber: 'LOT-A',
            },
          },
          schemaMetadata: [
            ...(conflict.schemaMetadata ?? []),
            {
              key: 'lotNumber',
              label: 'Lot / Batch Number',
              type: 'string',
              mutable: true,
            },
          ],
        },
      ],
    });
    const user = userEvent.setup();
    renderPanel();
    await waitFor(() => expect(screen.getByTestId('conflict-input-lotNumber')).toBeInTheDocument());
    await user.clear(screen.getByTestId('conflict-input-lotNumber'));
    await user.type(screen.getByTestId('conflict-input-lotNumber'), 'LOT-B');
    await user.click(screen.getByTestId('approve-conflict-conflict-1'));
    await user.click(screen.getByTestId('confirm-approve-conflict'));
    await waitFor(() =>
      expect(postMock).toHaveBeenCalledWith(
        '/api/v1/offline-sync-conflicts/conflict-1/resolve',
        expect.objectContaining({
          corrections: expect.objectContaining({ lotNumber: 'LOT-B', quantity: 2 }),
        }),
      ),
    );
  });
});
