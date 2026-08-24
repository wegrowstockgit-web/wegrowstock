import { describe, expect, it } from 'vitest';
import {
  isMainFadeScrollRoute,
  isSettingsOwnedScrollRoute,
  isViewportLockedRoute,
} from './appShellScroll';

describe('AppShell scroll ownership', () => {
  it('lets document list pages use the flush-right main scrollbar', () => {
    expect(isViewportLockedRoute('/suppliers')).toBe(false);
    expect(isViewportLockedRoute('/customers')).toBe(false);
    expect(isViewportLockedRoute('/invoices')).toBe(false);
    expect(isViewportLockedRoute('/returns')).toBe(false);
    expect(isSettingsOwnedScrollRoute('/suppliers')).toBe(false);
    expect(isMainFadeScrollRoute('/suppliers')).toBe(false);
  });

  it('keeps virtualized grids and MRP locked so only the table scrollport moves', () => {
    expect(isViewportLockedRoute('/products')).toBe(true);
    expect(isViewportLockedRoute('/products/sku-1')).toBe(true);
    expect(isViewportLockedRoute('/purchase-orders')).toBe(true);
    expect(isViewportLockedRoute('/sales-orders')).toBe(true);
    expect(isViewportLockedRoute('/mrp')).toBe(true);
    expect(isViewportLockedRoute('/purchasing/mrp')).toBe(true);
  });

  it('leaves settings subpages to SettingsSubpageShell / SettingsPage panes', () => {
    expect(isSettingsOwnedScrollRoute('/settings')).toBe(true);
    expect(isSettingsOwnedScrollRoute('/settings/profile')).toBe(true);
    expect(isSettingsOwnedScrollRoute('/settings/users')).toBe(true);
    expect(isViewportLockedRoute('/settings')).toBe(false);
  });
});
