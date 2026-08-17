import { createContext, createElement, useContext, useEffect, type ReactNode } from 'react';
import {
  DENSITY_STYLES,
  usePreferencesStore,
  type DensityMode,
} from '@/stores/preferencesStore';
import { cn } from '@/lib/utils';

const TableDensityContext = createContext<string | undefined>(undefined);

/** Scopes Cozy/Compact/Spacious to one master grid so other tables keep their own density. */
export function TableDensityScope({
  gridId,
  children,
}: {
  gridId: string;
  children: ReactNode;
}) {
  return createElement(TableDensityContext.Provider, { value: gridId }, children);
}

export function resolveDensityMode(
  densityMode: DensityMode,
  tableDensityById: Record<string, DensityMode>,
  gridId?: string,
): DensityMode {
  if (!gridId) return densityMode;
  return tableDensityById[gridId] ?? densityMode;
}

/**
 * Returns composable class names for Surface A table / list grids.
 * Pass `gridId` (or wrap with TableDensityScope) to read/write that table only.
 * Unscoped usage still syncs the global profile default onto <html data-density>.
 */
export function useDensity(gridId?: string) {
  const scopedGridId = useContext(TableDensityContext);
  const resolvedGridId = gridId ?? scopedGridId;
  const densityMode = usePreferencesStore((s) =>
    resolveDensityMode(s.densityMode, s.tableDensityById, resolvedGridId),
  );
  const setGlobalDensityMode = usePreferencesStore((s) => s.setDensityMode);
  const setTableDensity = usePreferencesStore((s) => s.setTableDensity);
  const styles = DENSITY_STYLES[densityMode];

  const setDensityMode = (mode: DensityMode) => {
    if (resolvedGridId) setTableDensity(resolvedGridId, mode);
    else setGlobalDensityMode(mode);
  };

  useEffect(() => {
    if (resolvedGridId) return;
    document.documentElement.setAttribute('data-density', densityMode);
  }, [resolvedGridId, densityMode]);

  return {
    densityMode,
    setDensityMode,
    gridId: resolvedGridId,
    styles,
    tableClass: cn('density-table', styles.typography),
    cellClass: cn('density-cell', styles.cell, styles.typography),
    headClass: cn('density-cell', styles.cell, styles.typography),
    rowClass: cn('density-row', styles.row),
    rowPx: styles.rowPx,
  };
}

/** Non-hook read for modules that only need current mode (e.g. virtualizer size). */
export function getDensityStyles(mode?: DensityMode) {
  const resolved = mode ?? usePreferencesStore.getState().densityMode;
  return DENSITY_STYLES[resolved];
}
