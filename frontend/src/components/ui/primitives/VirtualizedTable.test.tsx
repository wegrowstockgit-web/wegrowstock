import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import {
  hideableColumnMeta,
  VirtualizedTable,
  type VirtualizedColumnDef,
} from './VirtualizedTable';
import { useGridColumnStore } from '@/stores/gridColumnStore';
import { usePreferencesStore } from '@/stores/preferencesStore';

vi.mock('@tanstack/react-virtual', () => ({
  useVirtualizer: ({ count }: { count: number }) => {
    // Middle window: paddingTop + paddingBottom; onEndReached uses a high threshold in tests.
    const startIndex = count > 3 ? 1 : 0;
    const visible = count > 3 ? 2 : count;
    const items = Array.from({ length: visible }, (_, i) => {
      const index = startIndex + i;
      return {
        index,
        start: index * 40,
        end: (index + 1) * 40,
        size: 40,
        key: index,
      };
    });
    return {
      getVirtualItems: () => items,
      getTotalSize: () => count * 40,
      measure: vi.fn(),
    };
  },
}));

type Row = { id: string; sku: string; name: string; barcode: string };

const columns: VirtualizedColumnDef<Row>[] = [
  { id: 'sku', header: 'SKU', width: 120, cell: (r) => r.sku },
  { id: 'name', header: 'Name', width: 160, cell: (r) => r.name },
  { id: 'barcode', header: 'Barcode', width: 100, cell: (r) => r.barcode },
];

const rows: Row[] = [
  { id: '1', sku: 'A-1', name: 'Alpha', barcode: '111' },
  { id: '2', sku: 'B-2', name: 'Beta', barcode: '222' },
];

describe('VirtualizedTable', () => {
  beforeEach(() => {
    localStorage.clear();
    useGridColumnStore.setState({
      columnVisibility: { sku: true, name: true, barcode: true },
      pinnedColumns: ['sku', 'name'],
      columnOrder: ['sku', 'name', 'barcode'],
    });
    usePreferencesStore.setState({ densityMode: 'cozy' });
  });

  it('renders sticky pinned cells with pin-edge styling', () => {
    render(
      <VirtualizedTable
        columns={columns}
        rows={rows}
        getRowId={(r) => r.id}
      />,
    );

    expect(screen.getByTestId('virtualized-table')).toBeInTheDocument();
    expect(screen.getByText('SKU')).toBeInTheDocument();
    expect(screen.getByText('Alpha')).toBeInTheDocument();

    const skuHeader = screen.getByText('SKU').closest('th');
    const nameHeader = screen.getByText('Name').closest('th');
    expect(skuHeader?.style.position).toBe('sticky');
    expect(nameHeader?.style.position).toBe('sticky');
    // Rightmost pinned column carries the freeze-edge shadow.
    expect(nameHeader?.className).toMatch(/border-r/);
  });

  it('hides columns when visibility is toggled off', () => {
    useGridColumnStore.setState({
      columnVisibility: { sku: true, name: true, barcode: false },
      pinnedColumns: ['sku', 'name'],
      columnOrder: ['sku', 'name', 'barcode'],
    });

    render(
      <VirtualizedTable columns={columns} rows={rows} getRowId={(r) => r.id} />,
    );

    expect(screen.queryByText('Barcode')).not.toBeInTheDocument();
    expect(screen.queryByText('111')).not.toBeInTheDocument();
  });

  it('invokes onRowClick and respects compact density row class', () => {
    usePreferencesStore.setState({ densityMode: 'compact' });
    const onRowClick = vi.fn();

    render(
      <VirtualizedTable
        columns={columns}
        rows={rows}
        getRowId={(r) => r.id}
        onRowClick={onRowClick}
      />,
    );

    fireEvent.click(screen.getByText('Alpha'));
    expect(onRowClick).toHaveBeenCalledWith(rows[0]);
    expect(screen.getByText('SKU').closest('tr')?.className).toMatch(/h-8/);
  });

  it('renders empty state when no rows', () => {
    render(
      <VirtualizedTable
        columns={columns}
        rows={[]}
        getRowId={(r) => r.id}
        empty={<p>Nothing here</p>}
      />,
    );
    expect(screen.getByText('Nothing here')).toBeInTheDocument();
  });

  it('exposes hideable column meta and fires onEndReached', async () => {
    expect(hideableColumnMeta(columns as VirtualizedColumnDef<unknown>[])).toEqual([
      { id: 'sku', label: 'SKU' },
      { id: 'name', label: 'Name' },
      { id: 'barcode', label: 'Barcode' },
    ]);
    expect(
      hideableColumnMeta([
        { id: 'sku', header: <span>SKU</span>, width: 80, cell: () => null, hideable: false },
        { id: 'extra', header: <span>Extra</span>, width: 80, cell: () => null },
      ]),
    ).toEqual([{ id: 'extra', label: 'extra' }]);

    const onEndReached = vi.fn();
    const many = Array.from({ length: 8 }, (_, i) => ({
      id: String(i),
      sku: `S-${i}`,
      name: `N-${i}`,
      barcode: `${i}`,
    }));
    render(
      <VirtualizedTable
        columns={[
          ...columns,
          {
            id: 'right',
            header: 'Right',
            width: 80,
            align: 'right',
            cell: (r) => r.barcode,
          },
        ]}
        rows={many}
        getRowId={(r) => r.id}
        onEndReached={onEndReached}
        endReachedThreshold={8}
      />,
    );
    await vi.waitFor(() => {
      expect(onEndReached).toHaveBeenCalled();
    });
  });

  it('falls back to declaration order when columnOrder is empty', () => {
    useGridColumnStore.setState({
      columnVisibility: { sku: true, name: true, barcode: true },
      pinnedColumns: [],
      columnOrder: [],
    });
    render(
      <VirtualizedTable columns={columns} rows={rows} getRowId={(r) => r.id} />,
    );
    expect(screen.getByText('SKU')).toBeInTheDocument();
    expect(screen.getByText('Barcode')).toBeInTheDocument();
  });
});
