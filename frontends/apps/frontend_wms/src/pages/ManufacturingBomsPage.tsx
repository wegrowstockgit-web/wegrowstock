import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ChevronRight, Factory, Plus, Trash2 } from 'lucide-react';
import { apiClient } from '@/api/client';
import type { Bom, BomLine, PaginatedResponse, ProductVariant } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Modal } from '@/components/ui/Modal';
import { Select } from '@/components/ui/Select';
import { TableSkeleton } from '@/components/ui/Skeleton';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/Table';
import { DataListToolbar } from '@/components/ui/DensityToggle';
import { TableDensityScope } from '@/hooks/useDensity';
import { ListPageState } from '@/components/layout/ListPageState';
import { useClientSort } from '@/hooks/useClientSort';
import { cn } from '@/lib/utils';

interface DraftLine {
  componentVariantId: string;
  quantityRequired: string;
}

function ActiveBomsTable({
  boms,
  selectedBomId,
  onSelect,
}: {
  boms: Bom[];
  selectedBomId: string | null;
  onSelect: (id: string) => void;
}) {
  const { sort, toggle, sorted } = useClientSort(
    boms,
    {
      parentSku: (bom) => bom.parentSku ?? bom.parentVariantId,
      name: (bom) => bom.name,
      status: (bom) => (bom.isActive ? 'Active' : 'Inactive'),
    },
    { key: 'parentSku', dir: 'asc' },
  );

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead sortable sortKey="parentSku" sort={sort} onSort={toggle}>
            Parent SKU
          </TableHead>
          <TableHead sortable sortKey="name" sort={sort} onSort={toggle}>
            Name
          </TableHead>
          <TableHead sortable sortKey="status" sort={sort} onSort={toggle}>
            Status
          </TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {sorted.map((bom) => (
          <TableRow
            key={bom.id}
            className={cn('cursor-pointer', selectedBomId === bom.id && 'bg-accent-muted')}
            onClick={() => onSelect(bom.id)}
          >
            <TableCell mono>{bom.parentSku ?? bom.parentVariantId}</TableCell>
            <TableCell>{bom.name}</TableCell>
            <TableCell>
              <span
                className={cn(
                  'rounded-full px-2 py-0.5 text-xs font-medium',
                  bom.isActive
                    ? 'bg-accent-muted text-accent'
                    : 'bg-surface-overlay text-text-muted',
                )}
              >
                {bom.isActive ? 'Active' : 'Inactive'}
              </span>
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

function BomLinesTable({ lines }: { lines: BomLine[] }) {
  const { sort, toggle, sorted } = useClientSort(
    lines,
    {
      sku: (line) => line.componentSku ?? line.componentVariantId,
      name: (line) => line.componentName ?? '',
      qty: (line) => line.quantityRequired,
    },
    { key: 'sku', dir: 'asc' },
  );

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead sortable sortKey="sku" sort={sort} onSort={toggle}>
            SKU
          </TableHead>
          <TableHead sortable sortKey="name" sort={sort} onSort={toggle}>
            Name
          </TableHead>
          <TableHead sortable sortKey="qty" sort={sort} onSort={toggle} align="right">
            Qty required
          </TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {sorted.map((line) => (
          <BomTreeRow key={line.id} line={line} />
        ))}
      </TableBody>
    </Table>
  );
}

function BomTreeRow({ line, depth = 0 }: { line: BomLine; depth?: number }) {
  const [expanded, setExpanded] = useState(depth < 1);
  const hasChildren = (line.children?.length ?? 0) > 0;

  return (
    <>
      <TableRow>
        <TableCell>
          <div
            className="flex items-center gap-2"
            style={{ paddingLeft: `${depth * 1.25}rem` }}
          >
            {hasChildren ? (
              <button
                type="button"
                onClick={() => setExpanded((e) => !e)}
                className="rounded p-0.5 text-text-muted hover:bg-surface-overlay"
              >
                <ChevronRight
                  className={cn('h-4 w-4 transition-transform', expanded && 'rotate-90')}
                />
              </button>
            ) : (
              <span className="w-5" />
            )}
            <span className="font-mono text-sm">{line.componentSku ?? line.componentVariantId}</span>
          </div>
        </TableCell>
        <TableCell>{line.componentName ?? '—'}</TableCell>
        <TableCell align="right" mono>
          {line.quantityRequired}
        </TableCell>
      </TableRow>
      {expanded &&
        line.children?.map((child) => (
          <BomTreeRow key={child.id} line={child} depth={depth + 1} />
        ))}
    </>
  );
}

function CreateBomModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [parentVariantId, setParentVariantId] = useState('');
  const [name, setName] = useState('');
  const [lines, setLines] = useState<DraftLine[]>([
    { componentVariantId: '', quantityRequired: '1' },
  ]);
  const [error, setError] = useState('');

  const { data: variantsPage } = useQuery({
    queryKey: ['variants', 'all'],
    queryFn: async () =>
      (await apiClient.get<PaginatedResponse<ProductVariant>>('/api/v1/variants?limit=200')).data,
    enabled: open,
  });
  const variants = variantsPage?.items ?? [];

  const mutation = useMutation({
    mutationFn: async () => {
      await apiClient.post('/api/v1/manufacturing/boms', {
        parentVariantId,
        name,
        lines: lines
          .filter((line) => line.componentVariantId && Number(line.quantityRequired) > 0)
          .map((line) => ({
            componentVariantId: line.componentVariantId,
            quantityRequired: Number(line.quantityRequired),
          })),
      });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['manufacturing', 'boms'] });
      setParentVariantId('');
      setName('');
      setLines([{ componentVariantId: '', quantityRequired: '1' }]);
      onClose();
    },
    onError: () => setError('Could not create BOM. Ensure the parent variant has no existing BOM.'),
  });

  return (
    <Modal open={open} onClose={onClose} title="New BOM" description="Define components for a finished good">
      <form
        onSubmit={(e) => {
          e.preventDefault();
          setError('');
          mutation.mutate();
        }}
        className="space-y-4"
      >
        <Select
          label="Finished good (parent SKU)"
          value={parentVariantId}
          onChange={(e) => setParentVariantId(e.target.value)}
          required
        >
          <option value="" disabled>
            Select variant…
          </option>
          {variants.map((variant) => (
            <option key={variant.id} value={variant.id}>
              {variant.sku} — {variant.name}
            </option>
          ))}
        </Select>
        <Input
          label="BOM name"
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Smart Gadget Assembly"
          required
        />
        <div className="space-y-2">
          <p className="text-sm font-medium text-text">Components</p>
          {lines.map((line, index) => (
            <div key={index} className="flex items-end gap-2">
              <Select
                label={index === 0 ? 'Component' : undefined}
                value={line.componentVariantId}
                onChange={(e) => {
                  const next = [...lines];
                  next[index] = { ...next[index], componentVariantId: e.target.value };
                  setLines(next);
                }}
                className="flex-1"
                required
              >
                <option value="" disabled>
                  Select component…
                </option>
                {variants
                  .filter((variant) => variant.id !== parentVariantId)
                  .map((variant) => (
                    <option key={variant.id} value={variant.id}>
                      {variant.sku} — {variant.name}
                    </option>
                  ))}
              </Select>
              <Input
                label={index === 0 ? 'Qty' : undefined}
                type="number"
                min="0.01"
                step="0.01"
                value={line.quantityRequired}
                onChange={(e) => {
                  const next = [...lines];
                  next[index] = { ...next[index], quantityRequired: e.target.value };
                  setLines(next);
                }}
                className="w-24"
                required
              />
              {lines.length > 1 && (
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  onClick={() => setLines(lines.filter((_, i) => i !== index))}
                >
                  <Trash2 className="h-4 w-4" />
                </Button>
              )}
            </div>
          ))}
          <Button
            type="button"
            variant="secondary"
            size="sm"
            onClick={() => setLines([...lines, { componentVariantId: '', quantityRequired: '1' }])}
          >
            <Plus className="h-4 w-4" />
            Add component
          </Button>
        </div>
        {error && <p className="text-sm text-danger">{error}</p>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={mutation.isPending} disabled={!parentVariantId || !name}>
            Create BOM
          </Button>
        </div>
      </form>
    </Modal>
  );
}

export function ManufacturingBomsPage() {
  const [selectedBomId, setSelectedBomId] = useState<string | null>(null);
  const [modalOpen, setModalOpen] = useState(false);

  const { data: boms = [], isLoading, isError, refetch } = useQuery({
    queryKey: ['manufacturing', 'boms'],
    queryFn: async () => {
      const res = await apiClient.get<Bom[]>('/api/v1/manufacturing/boms');
      return res.data;
    },
    retry: false,
  });

  const { data: bomDetail, isLoading: detailLoading } = useQuery({
    queryKey: ['manufacturing', 'boms', selectedBomId],
    queryFn: async () => {
      const res = await apiClient.get<Bom>(`/api/v1/manufacturing/boms/${selectedBomId}`);
      return res.data;
    },
    enabled: !!selectedBomId,
    retry: false,
  });

  return (
    <TableDensityScope gridId="manufacturing-boms">
    <div className="mx-auto min-h-0 w-full max-w-7xl overflow-y-auto overscroll-contain p-4 sm:p-6">
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-text">Bill of Materials</h1>
          <p className="mt-1 text-sm text-text-muted">
            Multi-level BOMs for assembly and kitting
          </p>
        </div>
        <Button onClick={() => setModalOpen(true)}>
          <Plus className="h-4 w-4" />
          New BOM
        </Button>
      </div>

      <DataListToolbar gridId="manufacturing-boms" />

      <div className="grid gap-6 lg:grid-cols-2">
        <Card padding="none">
          <div className="border-b border-border p-4">
            <CardHeader title="Active BOMs" description={`${boms.length} configured`} />
          </div>
          <ListPageState
            isLoading={isLoading}
            isError={isError}
            data={boms}
            refetch={() => void refetch()}
            emptyIcon={Factory}
            emptyTitle="No BOMs yet"
            emptyDescription="Create a bill of materials to start production orders."
            emptyAction={
              <Button onClick={() => setModalOpen(true)}>
                <Plus className="h-4 w-4" />
                Create BOM
              </Button>
            }
          >
            {(items) => (
              <ActiveBomsTable
                boms={items}
                selectedBomId={selectedBomId}
                onSelect={setSelectedBomId}
              />
            )}
          </ListPageState>
        </Card>

        <Card>
          <CardHeader
            title="Component tree"
            description={
              bomDetail
                ? `${bomDetail.parentSku ?? bomDetail.parentName ?? 'BOM'} structure`
                : 'Select a BOM to view components'
            }
          />
          {!selectedBomId ? (
            <p className="text-sm text-text-muted">Select a BOM from the list to expand its tree.</p>
          ) : detailLoading ? (
            <TableSkeleton rows={6} cols={3} />
          ) : (bomDetail?.lines?.length ?? 0) === 0 ? (
            <p className="text-sm text-text-muted">No components defined for this BOM.</p>
          ) : (
            <BomLinesTable lines={bomDetail?.lines ?? []} />
          )}
        </Card>
      </div>

      <CreateBomModal open={modalOpen} onClose={() => setModalOpen(false)} />
    </div>
    </TableDensityScope>
  );
}
