import { beforeEach, describe, expect, it } from 'vitest';
import { useGridColumnStore } from '@/stores/gridColumnStore';

describe('gridColumnStore', () => {
  beforeEach(() => {
    localStorage.clear();
    useGridColumnStore.setState({
      columnVisibility: {},
      pinnedColumns: ['sku', 'name'],
      columnOrder: [],
    });
  });

  it('seeds defaults with sku and name pinned', () => {
    useGridColumnStore.getState().ensureColumns(['sku', 'name', 'barcode', 'onHand']);
    const state = useGridColumnStore.getState();
    expect(state.pinnedColumns).toEqual(['sku', 'name']);
    expect(state.columnOrder).toEqual(['sku', 'name', 'barcode', 'onHand']);
    expect(state.columnVisibility.barcode).toBe(true);
  });

  it('toggles visibility but keeps pinned columns visible', () => {
    useGridColumnStore.getState().ensureColumns(['sku', 'name', 'barcode']);
    useGridColumnStore.getState().toggleColumnVisibility('barcode');
    expect(useGridColumnStore.getState().columnVisibility.barcode).toBe(false);

    useGridColumnStore.getState().toggleColumnVisibility('sku');
    expect(useGridColumnStore.getState().columnVisibility.sku).not.toBe(false);
  });

  it('pins and unpins columns', () => {
    useGridColumnStore.getState().ensureColumns(['sku', 'name', 'barcode']);
    useGridColumnStore.getState().pinColumn('barcode');
    expect(useGridColumnStore.getState().pinnedColumns).toContain('barcode');
    useGridColumnStore.getState().unpinColumn('barcode');
    expect(useGridColumnStore.getState().pinnedColumns).not.toContain('barcode');
  });
});
