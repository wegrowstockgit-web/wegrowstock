import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { WarehouseVisualizer } from './WarehouseVisualizer';
import type { TenantLocation } from '@/api/types';
import { apiClient } from '@/api/client';

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
      <WarehouseVisualizer locations={locs} onAddWarehouse={onAdd} />
    </QueryClientProvider>,
  );
  return { onAdd };
}

describe('WarehouseVisualizer', () => {
  beforeEach(() => {
    vi.mocked(apiClient.post).mockReset();
  });

  it('renders nested warehouse hierarchy', () => {
    renderViz();
    expect(screen.getByTestId('warehouse-visualizer')).toBeInTheDocument();
    expect(screen.getByTestId('warehouse-node-WAREHOUSE')).toBeInTheDocument();
    expect(screen.getByTestId('warehouse-node-ZONE')).toBeInTheDocument();
    expect(screen.getByText('Zone 1')).toBeInTheDocument();
  });

  it('prompts to place first warehouse when empty', () => {
    const { onAdd } = renderViz([]);
    fireEvent.click(screen.getByText(/Place your first warehouse/i));
    expect(onAdd).toHaveBeenCalled();
  });

  it('opens inline add-child form and posts location', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ data: {} } as never);
    renderViz();

    fireEvent.click(screen.getByText(/Add aisle/i));
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

  it('renders aisle and bin grid density for deeper hierarchy', () => {
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
    expect(screen.getByTestId('warehouse-node-BIN')).toBeInTheDocument();
    expect(screen.getByText('Bin 1')).toBeInTheDocument();
  });
});
