import { describe, expect, it } from 'vitest';
import {
  canConfigureRetailPos,
  hasRetailPosModule,
  normalizePosCurrency,
} from './posSettingsAccess';

describe('posSettingsAccess', () => {
  it('requires an explicit RETAIL_POS module — empty entitlements stay closed', () => {
    expect(hasRetailPosModule([])).toBe(false);
    expect(hasRetailPosModule(undefined)).toBe(false);
    expect(hasRetailPosModule(['CORE', 'RETAIL_POS'])).toBe(true);
  });

  it('gates the settings tab to OWNER/ADMIN plus the POS module', () => {
    expect(canConfigureRetailPos(['OWNER'], ['RETAIL_POS'])).toBe(true);
    expect(canConfigureRetailPos(['ADMIN'], ['CORE', 'RETAIL_POS'])).toBe(true);
    expect(canConfigureRetailPos(['WAREHOUSE_MANAGER'], ['RETAIL_POS'])).toBe(false);
    expect(canConfigureRetailPos(['OWNER'], ['CORE'])).toBe(false);
    expect(canConfigureRetailPos(['OWNER'], [])).toBe(false);
  });

  it('normalizes POS currency to USD or MXN', () => {
    expect(normalizePosCurrency('mxn')).toBe('MXN');
    expect(normalizePosCurrency('USD')).toBe('USD');
    expect(normalizePosCurrency('EUR')).toBe('USD');
    expect(normalizePosCurrency(null)).toBe('USD');
  });
});
