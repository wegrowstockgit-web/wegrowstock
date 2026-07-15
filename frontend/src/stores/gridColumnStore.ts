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

        set({
          columnOrder: nextOrder,
          columnVisibility: nextVisibility,
          pinnedColumns:
            nextPinned.length > 0
              ? nextPinned
              : sanitizePinned([...DEFAULT_PINNED], columnIds),
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
