import { describe, expect, it } from 'vitest';
import { isTenantGlobalPath, isWarehouseScopedPath } from './useWarehouseContextGate';

describe('warehouse context route gate', () => {
  it('scopes floor and facility routes', () => {
    expect(isWarehouseScopedPath('/fulfillment')).toBe(true);
    expect(isWarehouseScopedPath('/inbound/receive')).toBe(true);
    expect(isWarehouseScopedPath('/inventory/ledger')).toBe(true);
    expect(isWarehouseScopedPath('/cycle-counts')).toBe(true);
    expect(isWarehouseScopedPath('/digital-twin')).toBe(true);
    expect(isWarehouseScopedPath('/locations')).toBe(true);
    expect(isWarehouseScopedPath('/field/truck')).toBe(true);
  });

  it('treats office master-data as tenant global', () => {
    expect(isWarehouseScopedPath('/products')).toBe(false);
    expect(isWarehouseScopedPath('/customers')).toBe(false);
    expect(isWarehouseScopedPath('/suppliers')).toBe(false);
    expect(isWarehouseScopedPath('/settings/users')).toBe(false);
    expect(isWarehouseScopedPath('/billing')).toBe(false);
    expect(isWarehouseScopedPath('/reports')).toBe(false);
    expect(isWarehouseScopedPath('/dashboard')).toBe(false);
    expect(isTenantGlobalPath('/sales-orders')).toBe(true);
  });
});
