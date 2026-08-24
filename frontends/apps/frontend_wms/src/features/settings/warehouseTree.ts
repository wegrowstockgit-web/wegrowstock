import type { TenantLocation } from '@/api/types';

export const LOCATION_HIERARCHY = ['WAREHOUSE', 'ZONE', 'AISLE', 'BIN'] as const;

export type LocationType = (typeof LOCATION_HIERARCHY)[number] | string;

export const CHILD_TYPE: Record<string, LocationType | null> = {
  WAREHOUSE: 'ZONE',
  VEHICLE: 'ZONE',
  ZONE: 'AISLE',
  AISLE: 'BIN',
  BIN: null,
};

export interface LocationNode extends TenantLocation {
  children: LocationNode[];
}

export function buildLocationTree(locations: TenantLocation[]): LocationNode[] {
  const byId = new Map<string, LocationNode>();
  for (const loc of locations) {
    byId.set(loc.id, { ...loc, children: [] });
  }
  const roots: LocationNode[] = [];
  for (const node of byId.values()) {
    const parentId = node.parentLocationId;
    if (parentId && byId.has(parentId)) {
      byId.get(parentId)!.children.push(node);
    } else {
      roots.push(node);
    }
  }
  const sortRec = (nodes: LocationNode[]) => {
    nodes.sort((a, b) => a.path.localeCompare(b.path));
    nodes.forEach((n) => sortRec(n.children));
  };
  sortRec(roots);
  return roots;
}

export function locationMatchesQuery(node: TenantLocation, query: string): boolean {
  const q = query.trim().toLowerCase();
  if (!q) return true;
  return [node.name, node.code, node.path, node.type].some((value) =>
    (value ?? '').toLowerCase().includes(q),
  );
}

/** Keep ancestors of matches so a bin search still shows its warehouse path. */
export function filterLocationTree(nodes: LocationNode[], query: string): LocationNode[] {
  if (!query.trim()) return nodes;
  const walk = (node: LocationNode): LocationNode | null => {
    const children = node.children
      .map((child) => walk(child))
      .filter((child): child is LocationNode => child != null);
    if (locationMatchesQuery(node, query) || children.length > 0) {
      return { ...node, children };
    }
    return null;
  };
  return nodes.map((node) => walk(node)).filter((node): node is LocationNode => node != null);
}

export function defaultExpandedIds(nodes: LocationNode[]): string[] {
  const ids: string[] = [];
  const walk = (node: LocationNode, depth: number) => {
    if (node.children.length > 0 && depth < 2) {
      ids.push(node.id);
      node.children.forEach((child) => walk(child, depth + 1));
    }
  };
  nodes.forEach((node) => walk(node, 0));
  return ids;
}

export function matchingExpandedIds(nodes: LocationNode[]): string[] {
  const ids: string[] = [];
  const walk = (node: LocationNode) => {
    if (node.children.length > 0) {
      ids.push(node.id);
      node.children.forEach(walk);
    }
  };
  nodes.forEach(walk);
  return ids;
}

export function flattenVisibleRows(
  nodes: LocationNode[],
  expanded: ReadonlySet<string>,
  depth = 0,
): Array<{ node: LocationNode; depth: number }> {
  const rows: Array<{ node: LocationNode; depth: number }> = [];
  for (const node of nodes) {
    rows.push({ node, depth });
    if (node.children.length > 0 && expanded.has(node.id)) {
      rows.push(...flattenVisibleRows(node.children, expanded, depth + 1));
    }
  }
  return rows;
}

export function formatLocationType(type: string): string {
  switch (type) {
    case 'WAREHOUSE':
      return 'Warehouse';
    case 'VEHICLE':
      return 'Vehicle';
    case 'ZONE':
      return 'Zone';
    case 'AISLE':
      return 'Aisle';
    case 'BIN':
      return 'Bin';
    default:
      return type;
  }
}

export function formatLocationStatus(loc: TenantLocation): string {
  const behavior = loc.zoneBehavior?.trim();
  if (!behavior || behavior === 'STANDARD') return 'Active';
  return behavior
    .toLowerCase()
    .split('_')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');
}

export function formatLocationCapacity(loc: TenantLocation): string {
  if (loc.maxWeightKg != null && Number.isFinite(Number(loc.maxWeightKg))) {
    return `${Number(loc.maxWeightKg)} kg`;
  }
  if (loc.weightCapacityLimit != null && Number.isFinite(Number(loc.weightCapacityLimit))) {
    return `${Number(loc.weightCapacityLimit)} lb`;
  }
  if (loc.maxPalletPositions != null) {
    return `${loc.maxPalletPositions} pallets`;
  }
  return '—';
}
