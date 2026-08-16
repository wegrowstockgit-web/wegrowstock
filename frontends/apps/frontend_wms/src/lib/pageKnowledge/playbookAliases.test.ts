import { describe, expect, it } from 'vitest';
import { PLAYBOOK_ROUTE_ALIASES } from './playbookAliases';

describe('playbook route aliases', () => {
  it('maps help-action shortcuts onto screens that exist in the WMS', () => {
    const byFrom = Object.fromEntries(PLAYBOOK_ROUTE_ALIASES.map((alias) => [alias.from, alias.to]));
    expect(byFrom['/tasks/my-queue']).toBe('/fulfillment');
    expect(byFrom['/dashboard/labor']).toBe('/dashboard');
    expect(byFrom['/inventory']).toBe('/products');
    expect(byFrom['/returns/vendor']).toBe('/purchasing/rtv');
    expect(byFrom['/settings/roles']).toBe('/settings?tab=users');
    expect(byFrom['/exceptions/pending']).toBe('/exceptions');
  });
});
