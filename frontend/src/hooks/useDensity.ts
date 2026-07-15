import { useEffect } from 'react';
import {
  DENSITY_STYLES,
  usePreferencesStore,
  type DensityMode,
} from '@/stores/preferencesStore';
import { cn } from '@/lib/utils';

/**
 * Syncs densityMode onto <html data-density> and returns composable class names
 * for Surface A table / list grids.
 */
export function useDensity() {
  const densityMode = usePreferencesStore((s) => s.densityMode);
  const setDensityMode = usePreferencesStore((s) => s.setDensityMode);
  const styles = DENSITY_STYLES[densityMode];

  useEffect(() => {
    document.documentElement.setAttribute('data-density', densityMode);
  }, [densityMode]);

  return {
    densityMode,
    setDensityMode,
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
