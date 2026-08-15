import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export interface GridColumnState {
  columnVisibility: Record<string, boolean>;
  /** Left-frozen identifier columns (default seed: sku + name). */
  pinnedColumns: string[];
  columnOrder: string[];
}

interface GridColumnStore {
  /** One local layout per grid (products, etc.) — persisted; no server Save View. */
  layouts: Record<string, GridColumnState>;
  toggleColumnVisibility: (gridId: string, id: string) => void;
  /**
   * Bulk-set visibility for known column ids. Pinned columns stay forced visible.
   */
  setColumnVisibilityMap: (
    gridId: string,
    visibility: Record<string, boolean>,
  ) => void;
  pinColumn: (gridId: string, id: string) => void;
  unpinColumn: (gridId: string, id: string) => void;
  setColumnOrder: (gridId: string, order: string[]) => void;
  applyLayout: (gridId: string, layout: Partial<GridColumnState>) => void;
  /**
   * Seeds order/visibility on first mount without wiping a customized
   * desktop workspace previously persisted to localStorage.
   */
  ensureColumns: (
    gridId: string,
    columnIds: string[],
    defaults?: Partial<GridColumnState>,
  ) => void;
}

const DEFAULT_PINNED = ['sku', 'name'] as const;

const EMPTY_LAYOUT: GridColumnState = {
  columnVisibility: {},
  pinnedColumns: [...DEFAULT_PINNED],
  columnOrder: [],
};

function defaultVisibility(columnIds: string[]): Record<string, boolean> {
  return Object.fromEntries(columnIds.map((id) => [id, true]));
}

function sanitizePinned(pinned: string[], columnIds: string[]): string[] {
  const allowed = new Set(columnIds);
  return pinned.filter((id) => allowed.has(id));
}

function layoutOrEmpty(
  layouts: Record<string, GridColumnState>,
  gridId: string,
): GridColumnState {
  return layouts[gridId] ?? EMPTY_LAYOUT;
}

function patchLayout(
  layouts: Record<string, GridColumnState>,
  gridId: string,
  patch: Partial<GridColumnState>,
): Record<string, GridColumnState> {
  const current = layoutOrEmpty(layouts, gridId);
  return {
    ...layouts,
    [gridId]: {
      columnVisibility: patch.columnVisibility ?? current.columnVisibility,
      pinnedColumns: patch.pinnedColumns ?? current.pinnedColumns,
      columnOrder: patch.columnOrder ?? current.columnOrder,
    },
  };
}

export function selectGridLayout(
  state: GridColumnStore,
  gridId: string,
): GridColumnState {
  return layoutOrEmpty(state.layouts, gridId);
}

/** Atomic: visibility for one column — shields unrelated header/body cells. */
export function selectColumnVisible(
  state: GridColumnStore,
  gridId: string,
  columnId: string,
): boolean {
  return layoutOrEmpty(state.layouts, gridId).columnVisibility[columnId] !== false;
}

/** Atomic: pin membership for one column. */
export function selectColumnPinned(
  state: GridColumnStore,
  gridId: string,
  columnId: string,
): boolean {
  return layoutOrEmpty(state.layouts, gridId).pinnedColumns.includes(columnId);
}

/** Stable reference when this grid's visibility map is unchanged. */
export function selectColumnVisibilityMap(
  state: GridColumnStore,
  gridId: string,
): Record<string, boolean> {
  return layoutOrEmpty(state.layouts, gridId).columnVisibility;
}

/** Stable reference when this grid's pin list is unchanged. */
export function selectPinnedColumns(
  state: GridColumnStore,
  gridId: string,
): readonly string[] {
  return layoutOrEmpty(state.layouts, gridId).pinnedColumns;
}

/** Stable reference when this grid's column order is unchanged. */
export function selectColumnOrder(
  state: GridColumnStore,
  gridId: string,
): readonly string[] {
  return layoutOrEmpty(state.layouts, gridId).columnOrder;
}

export const useGridColumnStore = create<GridColumnStore>()(
  persist(
    (set, get) => ({
      layouts: {},

      toggleColumnVisibility: (gridId, id) =>
        set((state) => {
          const current = layoutOrEmpty(state.layouts, gridId);
          if (current.pinnedColumns.includes(id)) {
            return state;
          }
          const visible = current.columnVisibility[id] !== false;
          return {
            layouts: patchLayout(state.layouts, gridId, {
              columnVisibility: {
                ...current.columnVisibility,
                [id]: !visible,
              },
            }),
          };
        }),

      setColumnVisibilityMap: (gridId, visibility) =>
        set((state) => {
          const current = layoutOrEmpty(state.layouts, gridId);
          const next = { ...current.columnVisibility, ...visibility };
          for (const pinnedId of current.pinnedColumns) {
            next[pinnedId] = true;
          }
          return {
            layouts: patchLayout(state.layouts, gridId, {
              columnVisibility: next,
            }),
          };
        }),

      pinColumn: (gridId, id) =>
        set((state) => {
          const current = layoutOrEmpty(state.layouts, gridId);
          if (current.pinnedColumns.includes(id)) return state;
          return {
            layouts: patchLayout(state.layouts, gridId, {
              pinnedColumns: [...current.pinnedColumns, id],
              columnVisibility: { ...current.columnVisibility, [id]: true },
            }),
          };
        }),

      unpinColumn: (gridId, id) =>
        set((state) => {
          const current = layoutOrEmpty(state.layouts, gridId);
          return {
            layouts: patchLayout(state.layouts, gridId, {
              pinnedColumns: current.pinnedColumns.filter((col) => col !== id),
            }),
          };
        }),

      setColumnOrder: (gridId, order) =>
        set((state) => ({
          layouts: patchLayout(state.layouts, gridId, { columnOrder: order }),
        })),

      applyLayout: (gridId, layout) =>
        set((state) => ({
          layouts: patchLayout(state.layouts, gridId, layout),
        })),

      ensureColumns: (gridId, columnIds, defaults) => {
        const state = get();
        const current = layoutOrEmpty(state.layouts, gridId);
        const hasOrder = current.columnOrder.length > 0;
        const nextOrder = hasOrder
          ? [
              ...current.columnOrder.filter((id) => columnIds.includes(id)),
              ...columnIds.filter((id) => !current.columnOrder.includes(id)),
            ]
          : defaults?.columnOrder?.length
            ? defaults.columnOrder
            : columnIds;

        // Defaults apply only for columns not already persisted so "hidden by
        // default" enterprise fields do not overwrite a user's Columns toggle.
        const seededDefaults: Record<string, boolean> = {};
        for (const [id, visible] of Object.entries(defaults?.columnVisibility ?? {})) {
          if (!(id in current.columnVisibility)) {
            seededDefaults[id] = visible;
          }
        }
        const nextVisibility = {
          ...defaultVisibility(columnIds),
          ...seededDefaults,
          ...current.columnVisibility,
        };

        const seedPinned = defaults?.pinnedColumns ?? [...DEFAULT_PINNED];
        const nextPinned = sanitizePinned(
          current.pinnedColumns.length > 0 ? current.pinnedColumns : seedPinned,
          columnIds,
        );
        const pinned =
          nextPinned.length > 0
            ? nextPinned
            : sanitizePinned([...DEFAULT_PINNED], columnIds);

        const sameOrder =
          nextOrder.length === current.columnOrder.length &&
          nextOrder.every((id, i) => id === current.columnOrder[i]);
        const samePinned =
          pinned.length === current.pinnedColumns.length &&
          pinned.every((id, i) => id === current.pinnedColumns[i]);
        const sameVisibility = columnIds.every(
          (id) =>
            (nextVisibility[id] !== false) ===
            (current.columnVisibility[id] !== false),
        );
        if (sameOrder && samePinned && sameVisibility && state.layouts[gridId]) {
          return;
        }

        set({
          layouts: patchLayout(state.layouts, gridId, {
            columnOrder: nextOrder,
            columnVisibility: nextVisibility,
            pinnedColumns: pinned,
          }),
        });
      },
    }),
    {
      name: 'invsys-grid-columns',
      version: 1,
      migrate: (persisted) => {
        const raw = persisted as {
          layouts?: Record<string, GridColumnState>;
          columnVisibility?: Record<string, boolean>;
          pinnedColumns?: string[];
          columnOrder?: string[];
        };
        if (raw?.layouts && typeof raw.layouts === 'object') {
          return { layouts: raw.layouts };
        }
        // v0 flat shape → products grid (only surface that used Save View)
        if (raw?.columnVisibility || raw?.columnOrder || raw?.pinnedColumns) {
          return {
            layouts: {
              products: {
                columnVisibility: raw.columnVisibility ?? {},
                pinnedColumns: raw.pinnedColumns ?? [...DEFAULT_PINNED],
                columnOrder: raw.columnOrder ?? [],
              },
            },
          };
        }
        return { layouts: {} };
      },
      partialize: (state) => ({ layouts: state.layouts }),
    },
  ),
);
