import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export interface GridColumnState {
  columnVisibility: Record<string, boolean>;
  /** Left-frozen identifier columns (default seed: sku + name). */
  pinnedColumns: string[];
  columnOrder: string[];
}

interface GridColumnStore extends GridColumnState {
  toggleColumnVisibility: (id: string) => void;
  pinColumn: (id: string) => void;
  unpinColumn: (id: string) => void;
  setColumnOrder: (order: string[]) => void;
  /** Replace layout from a saved view (also updates persist middleware → localStorage). */
  applyLayout: (layout: Partial<GridColumnState>) => void;
  /**
   * Seeds order/visibility on first mount without wiping a customized
   * desktop workspace previously persisted to localStorage.
   */
  ensureColumns: (columnIds: string[], defaults?: Partial<GridColumnState>) => void;
}

const DEFAULT_PINNED = ['sku', 'name'] as const;

function defaultVisibility(columnIds: string[]): Record<string, boolean> {
  return Object.fromEntries(columnIds.map((id) => [id, true]));
}

function sanitizePinned(pinned: string[], columnIds: string[]): string[] {
  const allowed = new Set(columnIds);
  return pinned.filter((id) => allowed.has(id));
}

export const useGridColumnStore = create<GridColumnStore>()(
  persist(
    (set, get) => ({
      columnVisibility: {},
      pinnedColumns: [...DEFAULT_PINNED],
      columnOrder: [],

      toggleColumnVisibility: (id) =>
        set((state) => {
          if (state.pinnedColumns.includes(id)) {
            // Pinned identifiers stay visible — unpin first to hide.
            return state;
          }
          const current = state.columnVisibility[id] !== false;
          return {
            columnVisibility: {
              ...state.columnVisibility,
              [id]: !current,
            },
          };
        }),

      pinColumn: (id) =>
        set((state) => {
          if (state.pinnedColumns.includes(id)) return state;
          return {
            pinnedColumns: [...state.pinnedColumns, id],
            columnVisibility: { ...state.columnVisibility, [id]: true },
          };
        }),

      unpinColumn: (id) =>
        set((state) => ({
          pinnedColumns: state.pinnedColumns.filter((col) => col !== id),
        })),

      setColumnOrder: (order) => set({ columnOrder: order }),

      applyLayout: (layout) =>
        set((state) => ({
          columnVisibility: layout.columnVisibility ?? state.columnVisibility,
          pinnedColumns: layout.pinnedColumns ?? state.pinnedColumns,
          columnOrder: layout.columnOrder ?? state.columnOrder,
        })),

      ensureColumns: (columnIds, defaults) => {
        const state = get();
        const hasOrder = state.columnOrder.length > 0;
        const nextOrder = hasOrder
          ? [
              ...state.columnOrder.filter((id) => columnIds.includes(id)),
              ...columnIds.filter((id) => !state.columnOrder.includes(id)),
            ]
          : defaults?.columnOrder?.length
            ? defaults.columnOrder
            : columnIds;

        const nextVisibility = {
          ...defaultVisibility(columnIds),
          ...state.columnVisibility,
          ...(defaults?.columnVisibility ?? {}),
        };

        const seedPinned = defaults?.pinnedColumns ?? [...DEFAULT_PINNED];
        const nextPinned = sanitizePinned(
          state.pinnedColumns.length > 0 ? state.pinnedColumns : seedPinned,
          columnIds,
        );
        const pinned =
          nextPinned.length > 0
            ? nextPinned
            : sanitizePinned([...DEFAULT_PINNED], columnIds);

        const sameOrder =
          nextOrder.length === state.columnOrder.length &&
          nextOrder.every((id, i) => id === state.columnOrder[i]);
        const samePinned =
          pinned.length === state.pinnedColumns.length &&
          pinned.every((id, i) => id === state.pinnedColumns[i]);
        const sameVisibility = columnIds.every(
          (id) => (nextVisibility[id] !== false) === (state.columnVisibility[id] !== false),
        );
        if (sameOrder && samePinned && sameVisibility) {
          return;
        }

        set({
          columnOrder: nextOrder,
          columnVisibility: nextVisibility,
          pinnedColumns: pinned,
        });
      },
    }),
    {
      name: 'invsys-grid-columns',
      partialize: (state) => ({
        columnVisibility: state.columnVisibility,
        pinnedColumns: state.pinnedColumns,
        columnOrder: state.columnOrder,
      }),
    },
  ),
);
