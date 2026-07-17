import { create } from 'zustand';
import { apiClient } from '@/api/client';
import { useGridColumnStore, type GridColumnState } from '@/stores/gridColumnStore';

export interface SavedGridView {
  id: string;
  gridIdentifier: string;
  name: string;
  state: GridColumnState;
  createdAt?: string;
}

interface SavedViewsStore {
  viewsByGrid: Record<string, SavedGridView[]>;
  loading: boolean;
  error: string | null;
  fetchViews: (gridId: string) => Promise<SavedGridView[]>;
  saveView: (name: string, gridId: string, state: GridColumnState) => Promise<SavedGridView>;
  applyView: (viewId: string, gridId?: string) => void;
}

function asLayout(state: unknown): GridColumnState {
  const raw = (state ?? {}) as Partial<GridColumnState>;
  return {
    columnVisibility: raw.columnVisibility ?? {},
    pinnedColumns: Array.isArray(raw.pinnedColumns) ? raw.pinnedColumns : [],
    columnOrder: Array.isArray(raw.columnOrder) ? raw.columnOrder : [],
  };
}

export const useSavedViewsStore = create<SavedViewsStore>((set, get) => ({
  viewsByGrid: {},
  loading: false,
  error: null,

  fetchViews: async (gridId) => {
    set({ loading: true, error: null });
    try {
      const res = await apiClient.get<
        Array<{
          id: string;
          gridIdentifier: string;
          name: string;
          state: unknown;
          createdAt?: string;
        }>
      >('/api/v1/users/me/views', { params: { grid: gridId } });
      const views: SavedGridView[] = (res.data ?? []).map((row) => ({
        id: row.id,
        gridIdentifier: row.gridIdentifier,
        name: row.name,
        state: asLayout(row.state),
        createdAt: row.createdAt,
      }));
      set((s) => ({
        viewsByGrid: { ...s.viewsByGrid, [gridId]: views },
        loading: false,
      }));
      return views;
    } catch (err) {
      set({
        loading: false,
        error: err instanceof Error ? err.message : 'Failed to load saved views',
      });
      throw err;
    }
  },

  saveView: async (name, gridId, state) => {
    const res = await apiClient.post<{
      id: string;
      gridIdentifier: string;
      name: string;
      state: unknown;
      createdAt?: string;
    }>('/api/v1/users/me/views', {
      name,
      gridIdentifier: gridId,
      state,
      // Prompt alternate: raw JSON string of the layout
      stateJson: JSON.stringify(state),
    });
    const saved: SavedGridView = {
      id: res.data.id,
      gridIdentifier: res.data.gridIdentifier,
      name: res.data.name,
      state: asLayout(res.data.state),
      createdAt: res.data.createdAt,
    };
    set((s) => {
      const existing = s.viewsByGrid[gridId] ?? [];
      const withoutDup = existing.filter((v) => v.id !== saved.id && v.name !== saved.name);
      return {
        viewsByGrid: { ...s.viewsByGrid, [gridId]: [...withoutDup, saved] },
      };
    });
    // Persist into local grid store immediately so reload hydration matches
    useGridColumnStore.getState().applyLayout(saved.state);
    return saved;
  },

  applyView: (viewId, gridId) => {
    const grids = Object.values(get().viewsByGrid);
    const pool = gridId
      ? (get().viewsByGrid[gridId] ?? [])
      : grids.flatMap((list) => list);
    const view = pool.find((v) => v.id === viewId);
    if (!view) return;
    useGridColumnStore.getState().applyLayout({
      columnOrder: view.state.columnOrder,
      pinnedColumns: view.state.pinnedColumns,
      columnVisibility: view.state.columnVisibility,
    });
  },
}));
