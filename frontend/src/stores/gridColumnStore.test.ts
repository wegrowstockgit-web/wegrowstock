import { beforeEach, describe, expect, it } from 'vitest';
import { selectGridLayout, useGridColumnStore } from '@/stores/gridColumnStore';

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
