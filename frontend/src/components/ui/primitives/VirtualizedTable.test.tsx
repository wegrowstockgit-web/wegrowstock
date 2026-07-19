import { beforeEach, describe, expect, it, vi } from 'vitest';
import { act, fireEvent, render, screen } from '@testing-library/react';
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
      layouts: {
        products: {
          columnVisibility: { sku: true, name: true, barcode: true },
          pinnedColumns: ['sku', 'name'],
          columnOrder: ['sku', 'name', 'barcode'],
        },
      },
    });
    usePreferencesStore.setState({ densityMode: 'cozy' });
  });

  it('keeps non-hideable thumb before pinned sku/name (not between name and barcode)', () => {
    const withThumb: VirtualizedColumnDef<Row>[] = [
      {
        id: 'thumb',
        header: '',
        width: 48,
        hideable: false,
        cell: () => '🖼',
      },
      ...columns,
    ];
    useGridColumnStore.setState({
      layouts: {
        products: {
          columnVisibility: { thumb: true, sku: true, name: true, barcode: true },
          pinnedColumns: ['sku', 'name'],
          columnOrder: ['thumb', 'sku', 'name', 'barcode'],
        },
      },
    });
    render(
      <VirtualizedTable
        gridId="products"
        columns={withThumb}
        rows={rows}
        getRowId={(r) => r.id}
      />,
    );
    expect(screen.getByTestId('virtualized-table-grid')).toHaveAttribute(
      'data-visible-columns',
      'thumb,sku,name,barcode',
    );
  });

  it('exposes data-table-width at least the sum of base column widths', () => {
    render(
      <VirtualizedTable
        gridId="products"
        columns={columns}
        rows={rows}
        getRowId={(r) => r.id}
      />,
    );
    const grid = screen.getByTestId('virtualized-table-grid');
    const widthAttr = Number(grid.getAttribute('data-table-width'));
    expect(widthAttr).toBeGreaterThanOrEqual(120 + 160 + 100);
  });

  it('drops hidden columns from width and data-visible-columns', async () => {
    const { rerender } = render(
      <VirtualizedTable
        gridId="products"
        columns={columns}
        rows={rows}
        getRowId={(r) => r.id}
      />,
    );
    expect(screen.getByTestId('virtualized-table-grid')).toHaveAttribute(
      'data-visible-columns',
      'sku,name,barcode',
    );
    act(() => {
      useGridColumnStore.getState().toggleColumnVisibility('products', 'barcode');
    });
    rerender(
      <VirtualizedTable
        gridId="products"
        columns={columns}
        rows={rows}
        getRowId={(r) => r.id}
      />,
    );
    expect(screen.getByTestId('virtualized-table-grid')).toHaveAttribute(
      'data-visible-columns',
      'sku,name',
    );
    expect(screen.queryByText('Barcode')).not.toBeInTheDocument();
  });

  it('renders sticky pinned cells with pin-edge styling', () => {
    render(
      <VirtualizedTable
        gridId="products"
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
      layouts: {
        products: {
          columnVisibility: { sku: true, name: true, barcode: false },
          pinnedColumns: ['sku', 'name'],
          columnOrder: ['sku', 'name', 'barcode'],
        },
      },
    });

    render(
      <VirtualizedTable gridId="products" columns={columns} rows={rows} getRowId={(r) => r.id} />,
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
      layouts: {
        products: {
          columnVisibility: { sku: true, name: true, barcode: true },
          pinnedColumns: [],
          columnOrder: [],
        },
      },
    });
    render(
      <VirtualizedTable gridId="products" columns={columns} rows={rows} getRowId={(r) => r.id} />,
    );
    expect(screen.getByText('SKU')).toBeInTheDocument();
    expect(screen.getByText('Barcode')).toBeInTheDocument();
  });

  it('sorts rows when a sortable header is clicked', () => {
    const sortableCols: VirtualizedColumnDef<Row>[] = [
      {
        id: 'sku',
        header: 'SKU',
        width: 120,
        sortable: true,
        sortValue: (r) => r.sku,
        cell: (r) => r.sku,
      },
      {
        id: 'name',
        header: 'Name',
        width: 160,
        sortable: true,
        sortValue: (r) => r.name,
        cell: (r) => r.name,
      },
    ];
    render(
      <VirtualizedTable columns={sortableCols} rows={rows} getRowId={(r) => r.id} />,
    );
    fireEvent.click(screen.getByRole('button', { name: /name/i }));
    const bodyRows = screen.getAllByRole('row').slice(1);
    expect(bodyRows[0]).toHaveTextContent('Alpha');
    fireEvent.click(screen.getByRole('button', { name: /name/i }));
    const descRows = screen.getAllByRole('row').slice(1);
    expect(descRows[0]).toHaveTextContent('Beta');
  });

  it('supports controlled selection and keyboard activation', () => {
    const onRowClick = vi.fn();
    render(
      <VirtualizedTable
        columns={columns}
        rows={rows}
        getRowId={(r) => r.id}
        selectedRowId="2"
        onRowClick={onRowClick}
      />,
    );

    const selected = screen.getByText('Beta').closest('tr');
    expect(selected?.getAttribute('data-state')).toBe('selected');

    const alpha = screen.getByText('Alpha').closest('tr')!;
    fireEvent.keyDown(alpha, { key: 'Enter' });
    expect(onRowClick).toHaveBeenCalledWith(rows[0]);
    fireEvent.keyDown(alpha, { key: ' ' });
    expect(onRowClick).toHaveBeenCalledTimes(2);
  });

  it('exports hideableColumnMeta and renders empty state', () => {
    expect(
      hideableColumnMeta([
        ...columns,
        { id: 'locked', header: 'Locked', width: 80, hideable: false, cell: () => null },
      ]).map((c) => c.id),
    ).toEqual(['sku', 'name', 'barcode']);

    render(
      <VirtualizedTable
        columns={columns}
        rows={[]}
        getRowId={(r) => r.id}
        empty={<p data-testid="vt-empty">No rows</p>}
      />,
    );
    expect(screen.getByTestId('vt-empty')).toBeInTheDocument();
  });

  it('fires onEndReached from scroll near bottom', () => {
    const onEndReached = vi.fn();
    const many = Array.from({ length: 20 }, (_, i) => ({
      id: String(i),
      sku: `S-${i}`,
      name: `N-${i}`,
      barcode: `${i}`,
    }));
    render(
      <VirtualizedTable
        columns={columns}
        rows={many}
        getRowId={(r) => r.id}
        onEndReached={onEndReached}
        endReachedThreshold={5}
      />,
    );
    const scroll = screen.getByTestId('virtualized-table-scrollport');
    Object.defineProperty(scroll, 'scrollHeight', { configurable: true, value: 800 });
    Object.defineProperty(scroll, 'clientHeight', { configurable: true, value: 200 });
    Object.defineProperty(scroll, 'scrollTop', { configurable: true, value: 580 });
    fireEvent.scroll(scroll);
    expect(onEndReached).toHaveBeenCalled();
  });

  it('keeps header and body column ids in the same order from the grid store', () => {
    useGridColumnStore.setState({
      layouts: {
        products: {
          columnVisibility: { sku: true, name: true, barcode: true },
          pinnedColumns: ['sku'],
          columnOrder: ['sku', 'name', 'barcode'],
        },
      },
    });
    render(
      <VirtualizedTable gridId="products" columns={columns} rows={rows} getRowId={(r) => r.id} />,
    );
    const headerIds = screen
      .getAllByRole('columnheader')
      .map((th) => th.getAttribute('data-column-id'));
    const firstDataRow = screen.getByText('Alpha').closest('tr');
    const bodyIds = Array.from(firstDataRow?.querySelectorAll('td[data-column-id]') ?? []).map(
      (td) => td.getAttribute('data-column-id'),
    );
    expect(headerIds).toEqual(['sku', 'name', 'barcode']);
    expect(bodyIds).toEqual(headerIds);
    expect(screen.getByTestId('virtualized-table-scrollport').className).toMatch(/scrollbar-thin/);
  });

  it('grows table min width when additional columns become visible', () => {
    useGridColumnStore.setState({
      layouts: {
        products: {
          columnVisibility: { sku: true, name: true, barcode: false },
          pinnedColumns: ['sku', 'name'],
          columnOrder: ['sku', 'name', 'barcode'],
        },
      },
    });
    const { rerender } = render(
      <VirtualizedTable gridId="products" columns={columns} rows={rows} getRowId={(r) => r.id} />,
    );
    const before = Number(
      screen.getByTestId('virtualized-table-grid').getAttribute('data-table-width'),
    );
    act(() => {
      useGridColumnStore.getState().toggleColumnVisibility('products', 'barcode');
    });
    rerender(
      <VirtualizedTable gridId="products" columns={columns} rows={rows} getRowId={(r) => r.id} />,
    );
    const after = Number(
      screen.getByTestId('virtualized-table-grid').getAttribute('data-table-width'),
    );
    expect(after).toBeGreaterThanOrEqual(before + 100);
    expect(screen.getByTestId('virtualized-table-grid')).toHaveAttribute(
      'data-visible-columns',
      'sku,name,barcode',
    );
  });
});
