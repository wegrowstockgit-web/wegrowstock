import { beforeEach, describe, expect, it } from 'vitest';
import {
  selectColumnOrder,
  selectColumnPinned,
  selectColumnVisibilityMap,
  selectColumnVisible,
  selectGridLayout,
  selectPinnedColumns,
  useGridColumnStore,
} from '@/stores/gridColumnStore';

const GRID = 'products';

describe('gridColumnStore', () => {
  beforeEach(() => {
    localStorage.clear();
    useGridColumnStore.setState({ layouts: {} });
  });

  it('seeds defaults with sku and name pinned per grid', () => {
    useGridColumnStore.getState().ensureColumns(GRID, ['sku', 'name', 'barcode', 'onHand']);
    const state = selectGridLayout(useGridColumnStore.getState(), GRID);
    expect(state.pinnedColumns).toEqual(['sku', 'name']);
    expect(state.columnOrder).toEqual(['sku', 'name', 'barcode', 'onHand']);
    expect(state.columnVisibility.barcode).toBe(true);
  });

  it('exposes atomic selectors that do not allocate new maps per read', () => {
    useGridColumnStore.getState().ensureColumns(GRID, ['sku', 'name', 'barcode']);
    const a = useGridColumnStore.getState();
    const b = useGridColumnStore.getState();
    expect(selectColumnVisibilityMap(a, GRID)).toBe(selectColumnVisibilityMap(b, GRID));
    expect(selectPinnedColumns(a, GRID)).toBe(selectPinnedColumns(b, GRID));
    expect(selectColumnOrder(a, GRID)).toBe(selectColumnOrder(b, GRID));
    expect(selectColumnVisible(a, GRID, 'barcode')).toBe(true);
    expect(selectColumnPinned(a, GRID, 'sku')).toBe(true);

    useGridColumnStore.getState().toggleColumnVisibility(GRID, 'barcode');
    const c = useGridColumnStore.getState();
    expect(selectColumnVisible(c, GRID, 'barcode')).toBe(false);
    // Pin list reference stays stable when only visibility flips
    expect(selectPinnedColumns(c, GRID)).toBe(selectPinnedColumns(a, GRID));
  });

  it('toggles visibility but keeps pinned columns visible', () => {
    useGridColumnStore.getState().ensureColumns(GRID, ['sku', 'name', 'barcode']);
    useGridColumnStore.getState().toggleColumnVisibility(GRID, 'barcode');
    expect(selectGridLayout(useGridColumnStore.getState(), GRID).columnVisibility.barcode).toBe(
      false,
    );

    useGridColumnStore.getState().toggleColumnVisibility(GRID, 'sku');
    expect(
      selectGridLayout(useGridColumnStore.getState(), GRID).columnVisibility.sku,
    ).not.toBe(false);
  });

  it('setColumnVisibilityMap applies show-all / ops-only while forcing pins visible', () => {
    useGridColumnStore
      .getState()
      .ensureColumns(GRID, ['sku', 'name', 'barcode', 'onHand', 'weight']);

    useGridColumnStore.getState().setColumnVisibilityMap(GRID, {
      sku: true,
      name: true,
      barcode: true,
      onHand: true,
      weight: true,
    });
    expect(selectColumnVisible(useGridColumnStore.getState(), GRID, 'weight')).toBe(true);

    useGridColumnStore.getState().setColumnVisibilityMap(GRID, {
      sku: false,
      name: false,
      barcode: true,
      onHand: true,
      weight: false,
    });
    // Pinned sku/name stay forced on.
    expect(selectColumnVisible(useGridColumnStore.getState(), GRID, 'sku')).toBe(true);
    expect(selectColumnVisible(useGridColumnStore.getState(), GRID, 'name')).toBe(true);
    expect(selectColumnVisible(useGridColumnStore.getState(), GRID, 'barcode')).toBe(true);
    expect(selectColumnVisible(useGridColumnStore.getState(), GRID, 'weight')).toBe(false);
  });

  it('pins and unpins columns', () => {
    useGridColumnStore.getState().ensureColumns(GRID, ['sku', 'name', 'barcode']);
    useGridColumnStore.getState().pinColumn(GRID, 'barcode');
    expect(selectGridLayout(useGridColumnStore.getState(), GRID).pinnedColumns).toContain(
      'barcode',
    );
    useGridColumnStore.getState().unpinColumn(GRID, 'barcode');
    expect(selectGridLayout(useGridColumnStore.getState(), GRID).pinnedColumns).not.toContain(
      'barcode',
    );
  });

  it('isolates layouts between grids', () => {
    useGridColumnStore.getState().ensureColumns('products', ['sku', 'name', 'barcode']);
    useGridColumnStore.getState().toggleColumnVisibility('products', 'barcode');
    useGridColumnStore.getState().ensureColumns('customers', ['name', 'email']);
    expect(
      selectGridLayout(useGridColumnStore.getState(), 'customers').columnVisibility.barcode,
    ).toBeUndefined();
    expect(
      selectGridLayout(useGridColumnStore.getState(), 'products').columnVisibility.barcode,
    ).toBe(false);
  });

  it('seeds advanced columns as hidden without overwriting user toggles', () => {
    useGridColumnStore.getState().ensureColumns(
      GRID,
      ['sku', 'name', 'hsTariffCode', 'isHazmat'],
      { columnVisibility: { hsTariffCode: false, isHazmat: false } },
    );
    expect(selectGridLayout(useGridColumnStore.getState(), GRID).columnVisibility.hsTariffCode).toBe(
      false,
    );

    useGridColumnStore.getState().toggleColumnVisibility(GRID, 'hsTariffCode');
    expect(selectGridLayout(useGridColumnStore.getState(), GRID).columnVisibility.hsTariffCode).toBe(
      true,
    );

    useGridColumnStore.getState().ensureColumns(
      GRID,
      ['sku', 'name', 'hsTariffCode', 'isHazmat'],
      { columnVisibility: { hsTariffCode: false, isHazmat: false } },
    );
    expect(selectGridLayout(useGridColumnStore.getState(), GRID).columnVisibility.hsTariffCode).toBe(
      true,
    );
  });
});
