import { describe, expect, it } from 'vitest';
import type { TenantLocation } from '@/api/types';
import {
  buildLocationTree,
  defaultExpandedIds,
  filterLocationTree,
  flattenVisibleRows,
  formatLocationCapacity,
  formatLocationStatus,
  formatLocationType,
  matchingExpandedIds,
} from './warehouseTree';

const locations: TenantLocation[] = [
  { id: 'wh-1', type: 'WAREHOUSE', code: 'WH1', name: 'Dallas', path: 'WH1' },
  { id: 'z-1', parentLocationId: 'wh-1', type: 'ZONE', code: 'ZA', name: 'Zone A', path: 'WH1/ZA' },
  { id: 'z-2', parentLocationId: 'wh-1', type: 'ZONE', code: 'ZB', name: 'Zone B', path: 'WH1/ZB' },
  {
    id: 'a-1',
    parentLocationId: 'z-1',
    type: 'AISLE',
    code: 'A1',
    name: 'Aisle 1',
    path: 'WH1/ZA/A1',
  },
  {
    id: 'b-1',
    parentLocationId: 'a-1',
    type: 'BIN',
    code: 'A-01-05',
    name: 'Bin 05',
    path: 'WH1/ZA/A1/A-01-05',
  },
];

describe('warehouseTree', () => {
  it('nests warehouses, zones, aisles, and bins', () => {
    const tree = buildLocationTree(locations);
    expect(tree).toHaveLength(1);
    expect(tree[0].children.map((c) => c.code)).toEqual(['ZA', 'ZB']);
    expect(tree[0].children[0].children[0].children[0].code).toBe('A-01-05');
  });

  it('filters to a matching bin and keeps ancestors', () => {
    const filtered = filterLocationTree(buildLocationTree(locations), 'A-01-05');
    expect(filtered).toHaveLength(1);
    expect(filtered[0].children).toHaveLength(1);
    expect(filtered[0].children[0].children[0].children[0].code).toBe('A-01-05');
    expect(filtered[0].children.some((c) => c.code === 'ZB')).toBe(false);
  });

  it('expands warehouse and zone by default', () => {
    const ids = defaultExpandedIds(buildLocationTree(locations));
    expect(ids).toContain('wh-1');
    expect(ids).toContain('z-1');
    expect(ids).not.toContain('a-1');
  });

  it('expands every ancestor of filtered matches', () => {
    const filtered = filterLocationTree(buildLocationTree(locations), 'A-01-05');
    expect(matchingExpandedIds(filtered)).toEqual(expect.arrayContaining(['wh-1', 'z-1', 'a-1']));
  });

  it('flattens only expanded rows', () => {
    const tree = buildLocationTree(locations);
    const collapsed = flattenVisibleRows(tree, new Set());
    expect(collapsed.map((r) => r.node.id)).toEqual(['wh-1']);
    const open = flattenVisibleRows(tree, new Set(['wh-1', 'z-1']));
    expect(open.map((r) => r.node.code)).toEqual(['WH1', 'ZA', 'A1', 'ZB']);
  });

  it('formats type, status, and capacity labels', () => {
    expect(formatLocationType('BIN')).toBe('Bin');
    expect(formatLocationType('VEHICLE')).toBe('Vehicle');
    expect(formatLocationType('CUSTOM')).toBe('CUSTOM');
    expect(formatLocationStatus({ zoneBehavior: 'STANDARD' } as TenantLocation)).toBe('Active');
    expect(formatLocationStatus({ zoneBehavior: 'PICK_FACE' } as TenantLocation)).toBe('Pick Face');
    expect(formatLocationCapacity({ maxWeightKg: 25 } as TenantLocation)).toBe('25 kg');
    expect(formatLocationCapacity({ weightCapacityLimit: 400 } as TenantLocation)).toBe('400 lb');
    expect(formatLocationCapacity({ maxPalletPositions: 3 } as TenantLocation)).toBe('3 pallets');
    expect(formatLocationCapacity({} as TenantLocation)).toBe('—');
  });
});
