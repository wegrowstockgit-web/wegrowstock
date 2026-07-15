import { useMemo, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Plus, Warehouse } from 'lucide-react';
import { apiClient } from '@/api/client';
import type { TenantLocation } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { cn } from '@/lib/utils';

const HIERARCHY = ['WAREHOUSE', 'ZONE', 'AISLE', 'BIN'] as const;
type LocationType = (typeof HIERARCHY)[number] | string;

const CHILD_TYPE: Record<string, LocationType | null> = {
  WAREHOUSE: 'ZONE',
  VEHICLE: 'ZONE',
  ZONE: 'AISLE',
  AISLE: 'BIN',
  BIN: null,
};

const TYPE_TINT: Record<string, string> = {
  WAREHOUSE: 'border-accent/60 bg-accent-muted/40',
  VEHICLE: 'border-accent/60 bg-accent-muted/40',
  ZONE: 'border-border-strong bg-surface-overlay/80',
  AISLE: 'border-border bg-surface-raised',
  BIN: 'border-border-strong/80 bg-surface',
};

interface LocationNode extends TenantLocation {
  children: LocationNode[];
}

function buildTree(locations: TenantLocation[]): LocationNode[] {
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

function AddChildInline({
  parent,
  onDone,
}: {
  parent: TenantLocation;
  onDone: () => void;
}) {
  const queryClient = useQueryClient();
  const childType = CHILD_TYPE[parent.type] ?? 'ZONE';
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [error, setError] = useState('');

  const mutation = useMutation({
    mutationFn: async () => {
      if (!childType) throw new Error('Leaf location');
      const path = `${parent.path}/${code.trim().toUpperCase()}`;
      await apiClient.post('/api/v1/locations', {
        parentLocationId: parent.id,
        type: childType,
        code: code.trim().toUpperCase(),
        name: name.trim() || code.trim().toUpperCase(),
        path,
      });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['locations'] });
      void queryClient.invalidateQueries({ queryKey: ['warehouses'] });
      onDone();
    },
    onError: () => setError('Could not create location. Check code uniqueness.'),
  });

  if (!childType) return null;

  return (
    <form
      className="mt-2 space-y-2 rounded-md border border-border-strong bg-surface-raised p-3"
      data-testid="warehouse-add-child"
      onSubmit={(e) => {
        e.preventDefault();
        setError('');
        mutation.mutate();
      }}
      onClick={(e) => e.stopPropagation()}
    >
      <p className="text-xs font-semibold uppercase tracking-wide text-text-muted">
        Add {childType.toLowerCase()} under {parent.code}
      </p>
      <div className="grid gap-2 sm:grid-cols-2">
        <Input
          label="Code"
          value={code}
          onChange={(e) => setCode(e.target.value.toUpperCase())}
          placeholder={childType === 'BIN' ? 'B01' : 'A'}
          required
          autoFocus
        />
        <Input
          label="Name"
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Optional label"
        />
      </div>
      {error && <p className="text-xs text-danger">{error}</p>}
      <div className="flex justify-end gap-2">
        <Button type="button" size="sm" variant="secondary" onClick={onDone}>
          Cancel
        </Button>
        <Button type="submit" size="sm" loading={mutation.isPending}>
          Add {childType.toLowerCase()}
        </Button>
      </div>
    </form>
  );
}

function LocationBlock({
  node,
  depth,
}: {
  node: LocationNode;
  depth: number;
}) {
  const [adding, setAdding] = useState(false);
  const canAdd = CHILD_TYPE[node.type] != null;
  const tint = TYPE_TINT[node.type] ?? 'border-border bg-surface-raised';

  return (
    <div
      className={cn(
        'rounded-md border-2 p-3 transition-colors',
        tint,
        'hover:border-accent/70',
      )}
      data-testid={`warehouse-node-${node.type}`}
      style={{ minHeight: depth === 0 ? '5rem' : undefined }}
    >
      <button
        type="button"
        className="flex w-full items-start justify-between gap-2 text-left"
        onClick={() => canAdd && setAdding((v) => !v)}
        aria-expanded={adding}
      >
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            {node.type === 'WAREHOUSE' || node.type === 'VEHICLE' ? (
              <Warehouse className="h-4 w-4 shrink-0 text-accent" aria-hidden />
            ) : null}
            <span className="font-mono text-sm font-semibold text-text">{node.code}</span>
            <span className="rounded border border-border-strong px-1.5 py-0.5 text-[10px] font-bold uppercase tracking-wide text-text">
              {node.type}
            </span>
          </div>
          <p className="mt-0.5 truncate text-sm text-text">{node.name}</p>
          <p className="truncate font-mono text-xs text-text-muted">{node.path}</p>
        </div>
        {canAdd && (
          <span className="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-md border border-border-strong bg-surface-raised text-text">
            <Plus className="h-4 w-4" aria-hidden />
          </span>
        )}
      </button>

      {adding && <AddChildInline parent={node} onDone={() => setAdding(false)} />}

      {node.children.length > 0 && (
        <div
          className={cn(
            'mt-3 grid gap-2',
            node.type === 'WAREHOUSE' || node.type === 'VEHICLE'
              ? 'grid-cols-1 sm:grid-cols-2'
              : node.type === 'ZONE'
                ? 'grid-cols-2 sm:grid-cols-3'
                : 'grid-cols-2 sm:grid-cols-4',
          )}
        >
          {node.children.map((child) => (
            <LocationBlock key={child.id} node={child} depth={depth + 1} />
          ))}
        </div>
      )}

      {node.children.length === 0 && canAdd && !adding && (
        <button
          type="button"
          onClick={() => setAdding(true)}
          className={cn(
            'mt-3 flex min-h-14 w-full items-center justify-center gap-2 rounded-md border-2 border-dashed border-border-strong',
            'bg-surface/40 text-sm font-medium text-text hover:border-accent hover:text-accent',
          )}
        >
          <Plus className="h-4 w-4" aria-hidden />
          Add {String(CHILD_TYPE[node.type]).toLowerCase()}
        </button>
      )}
    </div>
  );
}

interface WarehouseVisualizerProps {
  locations: TenantLocation[];
  onAddWarehouse: () => void;
}

/**
 * Spatial warehouse hierarchy editor — Warehouses → Zones → Aisles → Bins.
 * High-contrast borders for AA on both office and warehouse themes.
 */
export function WarehouseVisualizer({ locations, onAddWarehouse }: WarehouseVisualizerProps) {
  const tree = useMemo(() => buildTree(locations), [locations]);
  const warehouses = tree.filter(
    (n) => n.type === 'WAREHOUSE' || n.type === 'VEHICLE' || !n.parentLocationId,
  );

  return (
    <div className="space-y-4" data-testid="warehouse-visualizer">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-text-muted">
          Click a cell to nest the next level. Borders stay high-contrast in dark mode.
        </p>
        <Button size="sm" onClick={onAddWarehouse}>
          <Warehouse className="h-4 w-4" />
          Add warehouse
        </Button>
      </div>

      {warehouses.length === 0 ? (
        <button
          type="button"
          onClick={onAddWarehouse}
          className={cn(
            'flex min-h-32 w-full flex-col items-center justify-center gap-2 rounded-lg border-2 border-dashed border-border-strong',
            'bg-surface-overlay/50 text-text hover:border-accent',
          )}
        >
          <Warehouse className="h-8 w-8 text-text-muted" aria-hidden />
          <span className="text-sm font-medium">Place your first warehouse</span>
        </button>
      ) : (
        <div className="grid gap-4">
          {warehouses.map((wh) => (
            <LocationBlock key={wh.id} node={wh} depth={0} />
          ))}
        </div>
      )}
    </div>
  );
}
