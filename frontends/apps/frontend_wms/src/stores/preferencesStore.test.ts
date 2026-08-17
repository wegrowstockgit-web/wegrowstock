import { beforeEach, describe, expect, it } from 'vitest';
import { DENSITY_STYLES, usePreferencesStore } from '@/stores/preferencesStore';
import i18n, { WMS_LANG_STORAGE_KEY } from '@/lib/i18n';

describe('preferencesStore density', () => {
  beforeEach(() => {
    localStorage.clear();
    usePreferencesStore.setState({ densityMode: 'cozy', tableDensityById: {}, language: 'en' });
  });

  it('defaults to cozy with table-friendly styles', () => {
    expect(usePreferencesStore.getState().densityMode).toBe('cozy');
    expect(DENSITY_STYLES.cozy).toMatchObject({
      cell: 'py-2 px-4',
      typography: 'text-sm',
      row: 'h-11',
    });
  });

  it('persists densityMode changes', () => {
    usePreferencesStore.getState().setDensityMode('compact');
    expect(usePreferencesStore.getState().densityMode).toBe('compact');
    expect(DENSITY_STYLES.compact.row).toBe('h-8');
    expect(DENSITY_STYLES.spacious.typography).toBe('text-base');
  });

  it('stores per-table density without changing the global default', () => {
    usePreferencesStore.getState().setTableDensity('purchase-orders', 'compact');
    expect(usePreferencesStore.getState().tableDensityById['purchase-orders']).toBe('compact');
    expect(usePreferencesStore.getState().densityMode).toBe('cozy');
  });

  it('persists language preference', async () => {
    usePreferencesStore.getState().setLanguage('es');
    expect(usePreferencesStore.getState().language).toBe('es');
    expect(localStorage.getItem(WMS_LANG_STORAGE_KEY)).toBe('es');
    expect(i18n.language).toMatch(/^es/);
    usePreferencesStore.getState().setLanguage('en');
    await i18n.changeLanguage('en');
  });
});
