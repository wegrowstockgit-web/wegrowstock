import { beforeEach, describe, expect, it } from 'vitest';
import {
  defineModule,
  getDisabledNavPaths,
  getEnabledModules,
  getEnabledOfficeRoutes,
  isNavPathEnabled,
  registerAppModules,
} from './moduleRegistry';

describe('moduleRegistry', () => {
  beforeEach(() => {
    registerAppModules([
      defineModule({
        id: 'products',
        enabled: true,
        officeRoutes: [{ path: 'products', element: null }],
        navItems: [{ to: '/products', label: 'Products', moduleId: 'products' }],
      }),
      defineModule({
        id: 'fintech',
        enabled: false,
        officeRoutes: [{ path: 'settings/fintech', element: null }],
        navItems: [{ to: '/settings/fintech', label: 'Fintech', moduleId: 'fintech' }],
      }),
    ]);
  });

  it('omits disabled module routes and nav paths', () => {
    expect(getEnabledModules().map((m) => m.id)).toEqual(['products']);
    expect(getEnabledOfficeRoutes().map((r) => r.path)).toEqual(['products']);
    expect(getDisabledNavPaths().has('/settings/fintech')).toBe(true);
    expect(isNavPathEnabled('/products')).toBe(true);
    expect(isNavPathEnabled('/settings/fintech')).toBe(false);
  });
});
