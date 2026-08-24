import { useEffect, useMemo, useState } from 'react';
import { ChevronDown, ChevronRight, Warehouse } from 'lucide-react';
import type { TenantLocation } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Modal } from '@/components/ui/Modal';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/Table';
import { DigitalTwinMap } from '@/features/settings/DigitalTwinMap';
import { WarehouseLocationDrawer } from '@/features/settings/WarehouseLocationDrawer';
import {
  buildLocationTree,
  defaultExpandedIds,
  filterLocationTree,
  flattenVisibleRows,
  formatLocationCapacity,
  formatLocationStatus,
  formatLocationType,
  matchingExpandedIds,
} from '@/features/settings/warehouseTree';
import { cn } from '@/lib/utils';

interface WarehouseVisualizerProps {
  locations: TenantLocation[];
  onAddWarehouse: () => void;
}

/**
 * Warehouse layout workspace — list-first hierarchy for data entry, map for spatial layout.
 */
export function WarehouseVisualizer({ locations, onAddWarehouse }: WarehouseVisualizerProps) {
  const [viewMode, setViewMode] = useState<'list' | 'map'>('list');
  const [query, setQuery] = useState('');
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [bulkOpen, setBulkOpen] = useState(false);
  const [expanded, setExpanded] = useState<Set<string>>(new Set());

  const tree = useMemo(() => buildLocationTree(locations), [locations]);
  const filtered = useMemo(() => filterLocationTree(tree, query), [tree, query]);
  const rows = useMemo(() => flattenVisibleRows(filtered, expanded), [filtered, expanded]);
  const selected = useMemo(
    () => locations.find((loc) => loc.id === selectedId) ?? null,
    [locations, selectedId],
  );

  useEffect(() => {
    if (query.trim()) {
      setExpanded(new Set(matchingExpandedIds(filtered)));
      return;
    }
    setExpanded(new Set(defaultExpandedIds(tree)));
  }, [query, tree, filtered]);

  const toggleExpanded = (id: string) => {
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const warehouses = tree.filter(
    (n) => n.type === 'WAREHOUSE' || n.type === 'VEHICLE' || !n.parentLocationId,
  );

  return (
    <div className="space-y-4" data-testid="warehouse-visualizer">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-text-muted">
          Manage locations in the list. Switch to Map View for spatial arrangement.
        </p>
        <div className="flex flex-wrap items-center justify-end gap-2">
          {viewMode === 'list' && locations.length > 0 && (
            <Button
              type="button"
              size="sm"
              variant="secondary"
              onClick={() => setBulkOpen(true)}
              data-testid="bulk-generate-bins"
            >
              Bulk Generate Bins
            </Button>
          )}
          <Button type="button" size="sm" onClick={onAddWarehouse} data-testid="add-warehouse">
            <Warehouse className="h-4 w-4" />
            Add warehouse
          </Button>
          <div
            role="group"
            aria-label="Warehouse view"
            data-testid="warehouse-view-toggle"
            className="inline-flex rounded-md border border-border bg-surface p-0.5"
          >
            <Button
              type="button"
              size="sm"
              variant={viewMode === 'list' ? 'primary' : 'ghost'}
              aria-pressed={viewMode === 'list'}
              data-testid="warehouse-view-list"
              className="shadow-none"
              onClick={() => setViewMode('list')}
            >
              List View
            </Button>
            <Button
              type="button"
              size="sm"
              variant={viewMode === 'map' ? 'primary' : 'ghost'}
              aria-pressed={viewMode === 'map'}
              data-testid="warehouse-view-map"
              className="shadow-none"
              onClick={() => setViewMode('map')}
            >
              Map View
            </Button>
          </div>
        </div>
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
      ) : viewMode === 'map' ? (
        <DigitalTwinMap locations={locations} onSelectLocation={setSelectedId} />
      ) : (
        <div className="space-y-3">
          <div className="max-w-md">
            <Input
              label="Search locations"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Find a bin, aisle, or zone — e.g. B-01"
              data-testid="warehouse-location-search"
              autoComplete="off"
            />
          </div>

          {rows.length === 0 ? (
            <p className="rounded-md border border-dashed border-border px-4 py-8 text-center text-sm text-text-muted">
              No locations match your search.
            </p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Name</TableHead>
                  <TableHead>Type</TableHead>
                  <TableHead>Barcode / Code</TableHead>
                  <TableHead>Capacity</TableHead>
                  <TableHead>Status</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {rows.map(({ node, depth }) => {
                  const hasChildren = node.children.length > 0;
                  const isOpen = expanded.has(node.id);
                  return (
                    <TableRow
                      key={node.id}
                      data-testid={`warehouse-node-${node.type}`}
                      data-location-id={node.id}
                      data-location-code={node.code}
                      selected={selectedId === node.id}
                      onClick={() => setSelectedId(node.id)}
                    >
                      <TableCell>
                        <div
                          className="flex min-w-0 items-center gap-1"
                          style={{ paddingLeft: depth * 16 }}
                        >
                          {hasChildren ? (
                            <button
                              type="button"
                              className="inline-flex h-7 w-7 shrink-0 items-center justify-center rounded-sm text-text-muted hover:bg-surface-overlay hover:text-text"
                              aria-expanded={isOpen}
                              aria-label={isOpen ? `Collapse ${node.name}` : `Expand ${node.name}`}
                              data-testid={`warehouse-expand-${node.id}`}
                              onClick={(e) => {
                                e.stopPropagation();
                                toggleExpanded(node.id);
                              }}
                            >
                              {isOpen ? (
                                <ChevronDown className="h-4 w-4" aria-hidden />
                              ) : (
                                <ChevronRight className="h-4 w-4" aria-hidden />
                              )}
                            </button>
                          ) : (
                            <span className="inline-block h-7 w-7 shrink-0" aria-hidden />
                          )}
                          <span className="truncate font-medium text-text">{node.name}</span>
                        </div>
                      </TableCell>
                      <TableCell>{formatLocationType(node.type)}</TableCell>
                      <TableCell mono>{node.code}</TableCell>
                      <TableCell>{formatLocationCapacity(node)}</TableCell>
                      <TableCell>{formatLocationStatus(node)}</TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          )}
        </div>
      )}

      <WarehouseLocationDrawer location={selected} onClose={() => setSelectedId(null)} />

      <Modal
        open={bulkOpen}
        onClose={() => setBulkOpen(false)}
        title="Bulk Generate Bins"
        description="Create a matrix of aisles, bays, and levels in one pass."
      >
        <div data-testid="bulk-generate-modal" className="space-y-4">
          <p className="text-sm text-text-muted">
            This generator is coming in a later release. You will be able to define a starting
            aisle, bay count, and level count to stamp bins into the selected zone.
          </p>
          <div className="flex justify-end">
            <Button type="button" variant="secondary" onClick={() => setBulkOpen(false)}>
              Close
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
