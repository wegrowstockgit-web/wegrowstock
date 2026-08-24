import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { WarehouseVisualizer } from './WarehouseVisualizer';
import type { TenantLocation } from '@/api/types';
import { apiClient } from '@/api/client';
import { ToastProvider } from '@/components/ui/Toast';

vi.mock('@/api/client', () => ({
  apiClient: {
    post: vi.fn(),
    get: vi.fn().mockResolvedValue({ data: [] }),
    patch: vi.fn(),
  },
}));

const locations: TenantLocation[] = [
  {
    id: 'wh-1',
    type: 'WAREHOUSE',
    code: 'WH1',
    name: 'Main',
    path: 'WH1',
  },
  {
    id: 'z-1',
    parentLocationId: 'wh-1',
    type: 'ZONE',
    code: 'Z1',
    name: 'Zone 1',
    path: 'WH1/Z1',
  },
];

function renderViz(locs: TenantLocation[] = locations) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const onAdd = vi.fn();
  render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <WarehouseVisualizer locations={locs} onAddWarehouse={onAdd} />
      </ToastProvider>
    </QueryClientProvider>,
  );
  return { onAdd };
}

describe('WarehouseVisualizer', () => {
  beforeEach(() => {
    vi.mocked(apiClient.post).mockReset();
    vi.mocked(apiClient.patch).mockReset();
  });

  it('renders nested warehouse hierarchy in list view by default', () => {
    renderViz();
    expect(screen.getByTestId('warehouse-visualizer')).toBeInTheDocument();
    expect(screen.getByTestId('warehouse-view-list')).toHaveAttribute('aria-pressed', 'true');
    expect(screen.queryByTestId('digital-twin-map')).not.toBeInTheDocument();
    expect(screen.getByTestId('warehouse-node-WAREHOUSE')).toBeInTheDocument();
    expect(screen.getByTestId('warehouse-node-ZONE')).toBeInTheDocument();
    expect(screen.getByText('Zone 1')).toBeInTheDocument();
  });

  it('shows the digital twin only after switching to map view', () => {
    renderViz();
    fireEvent.click(screen.getByTestId('warehouse-view-map'));
    expect(screen.getByTestId('digital-twin-map')).toBeInTheDocument();
    expect(screen.queryByTestId('warehouse-location-search')).not.toBeInTheDocument();
    fireEvent.click(screen.getByTestId('twin-node-Z1'));
    expect(screen.getByTestId('right-peek-drawer')).toBeInTheDocument();
    fireEvent.click(screen.getByTestId('warehouse-view-list'));
    expect(screen.queryByTestId('digital-twin-map')).not.toBeInTheDocument();
    expect(screen.getByTestId('warehouse-location-search')).toBeInTheDocument();
  });

  it('prompts to place first warehouse when empty', () => {
    const { onAdd } = renderViz([]);
    fireEvent.click(screen.getByText(/Place your first warehouse/i));
    expect(onAdd).toHaveBeenCalled();
  });

  it('filters the tree instantly by bin code', () => {
    const deep: TenantLocation[] = [
      ...locations,
      {
        id: 'a-1',
        parentLocationId: 'z-1',
        type: 'AISLE',
        code: 'A1',
        name: 'Aisle 1',
        path: 'WH1/Z1/A1',
      },
      {
        id: 'b-1',
        parentLocationId: 'a-1',
        type: 'BIN',
        code: 'A-01-05',
        name: 'Bin 05',
        path: 'WH1/Z1/A1/A-01-05',
      },
      {
        id: 'z-2',
        parentLocationId: 'wh-1',
        type: 'ZONE',
        code: 'Z2',
        name: 'Zone 2',
        path: 'WH1/Z2',
      },
    ];
    renderViz(deep);
    fireEvent.change(screen.getByTestId('warehouse-location-search'), {
      target: { value: 'A-01-05' },
    });
    expect(screen.getByTestId('warehouse-node-BIN')).toBeInTheDocument();
    expect(screen.getByText('Bin 05')).toBeInTheDocument();
    expect(screen.queryByText('Zone 2')).not.toBeInTheDocument();
  });

  it('opens the settings drawer from a list row and posts a child location', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ data: {} } as never);
    renderViz();

    fireEvent.click(screen.getByText('Zone 1'));
    const drawer = screen.getByTestId('right-peek-drawer');
    expect(drawer).toBeInTheDocument();
    expect(within(drawer).getByTestId('warehouse-location-form')).toBeInTheDocument();

    fireEvent.click(within(drawer).getByTestId('location-add-child'));
    expect(screen.getByTestId('warehouse-add-child')).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('Code'), { target: { value: 'A1' } });
    fireEvent.click(screen.getByRole('button', { name: /Add aisle/i }));

    await waitFor(() => {
      expect(apiClient.post).toHaveBeenCalledWith(
        '/api/v1/locations',
        expect.objectContaining({
          type: 'AISLE',
          code: 'A1',
          parentLocationId: 'z-1',
        }),
      );
    });
  });

  it('saves location settings from the drawer', async () => {
    vi.mocked(apiClient.patch).mockResolvedValue({ data: {} } as never);
    renderViz();
    fireEvent.click(screen.getByText('Zone 1'));
    fireEvent.change(screen.getByTestId('location-edit-name'), { target: { value: 'Pick Zone' } });
    fireEvent.change(screen.getByTestId('location-edit-max-weight'), { target: { value: '40' } });
    fireEvent.change(screen.getByTestId('location-edit-pick-sequence'), { target: { value: '3' } });
    fireEvent.click(screen.getByTestId('location-edit-save'));

    await waitFor(() => {
      expect(apiClient.patch).toHaveBeenCalledWith(
        '/api/v1/locations/z-1',
        expect.objectContaining({
          name: 'Pick Zone',
          maxWeightKg: 40,
          sequenceIndex: 3,
        }),
      );
    });
  });

  it('opens the bulk generate placeholder from list view', () => {
    renderViz();
    fireEvent.click(screen.getByTestId('bulk-generate-bins'));
    expect(screen.getByTestId('bulk-generate-modal')).toBeInTheDocument();
    expect(screen.getByText(/coming in a later release/i)).toBeInTheDocument();
  });

  it('renders aisle and bin rows for a deeper hierarchy after expand or search', () => {
    const deep: TenantLocation[] = [
      ...locations,
      {
        id: 'a-1',
        parentLocationId: 'z-1',
        type: 'AISLE',
        code: 'A1',
        name: 'Aisle 1',
        path: 'WH1/Z1/A1',
      },
      {
        id: 'b-1',
        parentLocationId: 'a-1',
        type: 'BIN',
        code: 'B1',
        name: 'Bin 1',
        path: 'WH1/Z1/A1/B1',
      },
    ];
    renderViz(deep);
    expect(screen.getByTestId('warehouse-node-AISLE')).toBeInTheDocument();
    expect(screen.queryByTestId('warehouse-node-BIN')).not.toBeInTheDocument();
    fireEvent.click(screen.getByTestId('warehouse-expand-a-1'));
    expect(screen.getByTestId('warehouse-node-BIN')).toBeInTheDocument();
    expect(screen.getByText('Bin 1')).toBeInTheDocument();
  });
});
