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
});
