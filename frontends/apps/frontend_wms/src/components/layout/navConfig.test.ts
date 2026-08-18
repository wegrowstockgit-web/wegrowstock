import { describe, expect, it } from 'vitest';
import { assertNoShowroomNav, NAV_MATRIX } from './navConfig';

describe('navConfig B2B and mesh activation', () => {
  it('keeps the buyer showroom portal out of the office sidebar', () => {
    expect(assertNoShowroomNav()).toBe(true);
    const paths = [
      ...NAV_MATRIX.solos.map((item) => item.to),
      ...NAV_MATRIX.categories.flatMap((group) => group.items.map((item) => item.to)),
    ];
    expect(paths.some((to) => to.startsWith('/showroom'))).toBe(false);
  });

  it('exposes Mesh Network as a top-level item gated by MESH_NETWORK', () => {
    const mesh = NAV_MATRIX.solos.find((item) => item.id === 'mesh-network');
    expect(mesh).toMatchObject({
      to: '/mesh-network',
      labelKey: 'nav.meshNetwork',
      modules: ['MESH_NETWORK'],
      requiredModule: 'MESH_NETWORK',
    });
    const inboundMesh = NAV_MATRIX.categories
      .find((group) => group.id === 'inbound')
      ?.items.some((item) => item.to === '/mesh-network');
    expect(inboundMesh).toBe(false);
  });

  it('keeps a single Sales Orders and Customers leaf under Outbound', () => {
    const outbound = NAV_MATRIX.categories.find((group) => group.id === 'outbound');
    const paths = outbound?.items.map((item) => item.to) ?? [];
    expect(paths).toContain('/sales-orders');
    expect(paths).toContain('/customers');
    expect(paths).not.toContain('/sales/orders');
    expect(paths).not.toContain('/sales/customers');
  });

  it('assigns requiredModule on premium commercial routes', () => {
    const manufacturing = NAV_MATRIX.categories.find((group) => group.id === 'manufacturing');
    expect(manufacturing?.items.map((item) => item.requiredModule)).toEqual([
      'MANUFACTURING',
      'MANUFACTURING',
    ]);
    const inbound = NAV_MATRIX.categories.find((group) => group.id === 'inbound');
    expect(inbound?.items.find((item) => item.to === '/mrp')?.requiredModule).toBe('MRP');
    const outbound = NAV_MATRIX.categories.find((group) => group.id === 'outbound');
    expect(outbound?.items.find((item) => item.to === '/cluster-pick')?.requiredModule).toBe(
      'ADVANCED_FULFILLMENT',
    );
    const admin = NAV_MATRIX.categories.find((group) => group.id === 'admin');
    expect(admin?.items.find((item) => item.to === '/rtls')?.requiredModule).toBe('RTLS_TELEMETRY');
  });

  it('only gates nav with commercial AppModule codes the admin drawer can toggle', () => {
    const catalog = new Set([
      'CORE',
      'SHOPIFY',
      'ACCOUNTING',
      'ADVANCED_FULFILLMENT',
      'MANUFACTURING',
      'DOCUMENTS',
      'MRP',
      'B2B_SHOWROOM',
      'FINTECH',
      'MESH_NETWORK',
      'RTLS_TELEMETRY',
      'AI_COPILOT',
      'RETAIL_POS',
    ]);
    const gated = [
      ...NAV_MATRIX.solos,
      ...NAV_MATRIX.categories.flatMap((group) => group.items),
    ].flatMap((item) => [
      ...(item.requiredModule ? [item.requiredModule] : []),
      ...(item.modules ?? []),
    ]);
    expect(gated.length).toBeGreaterThan(0);
    for (const moduleName of gated) {
      expect(catalog.has(moduleName), `unknown commercial module ${moduleName}`).toBe(true);
    }
  });
});
