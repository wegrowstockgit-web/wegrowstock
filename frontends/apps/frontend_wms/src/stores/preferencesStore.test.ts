import { beforeEach, describe, expect, it } from 'vitest';
import { DENSITY_STYLES, usePreferencesStore } from '@/stores/preferencesStore';

describe('preferencesStore density', () => {
  beforeEach(() => {
    localStorage.clear();
    usePreferencesStore.setState({ densityMode: 'cozy', language: 'en' });
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

  it('persists language preference', () => {
    usePreferencesStore.getState().setLanguage('es');
    expect(usePreferencesStore.getState().language).toBe('es');
  });
});
