import { useMemo, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Calculator, RefreshCw } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { apiClient } from '@/api/client';
import { listSuppliers } from '@/api/operational';
import type { MrpConsolidateJob, MrpSuggestionLine, MrpSuggestionSummary, Supplier } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { InlineEditableCell } from '@/components/ui/InlineEditableCell';
import { Select } from '@/components/ui/Select';
import { DebouncedSearchInput } from '@/components/ui/DebouncedSearchInput';
import { DataListToolbar } from '@/components/ui/DensityToggle';
import { Pagination } from '@/components/ui/Pagination';
import { EmptyState } from '@/components/ui/EmptyState';
import { TableSkeleton } from '@/components/ui/Skeleton';
import { DataListWorkspace } from '@/components/layout/DataListWorkspace';
import { EntityMobileCard } from '@/components/layout/EntityMobileCard';
import {
  VirtualizedTable,
  type VirtualizedColumnDef,
} from '@/components/ui/primitives/VirtualizedTable';
import { TableDensityScope } from '@/hooks/useDensity';
import { useCapMobilePageSize } from '@/hooks/useCapMobilePageSize';
import { useMediaQuery } from '@/hooks/useMediaQuery';
import { useServerTableQuery } from '@/hooks/useServerTable';
import { cn, formatCurrency } from '@/lib/utils';
import { useToast } from '@/components/ui/Toast';

type UrgencyFilter = '' | 'STOCKOUT' | 'BELOW_MINIMUM' | 'FORECASTED';

function asNumber(value: unknown): number {
  if (typeof value === 'number') return value;
  if (typeof value === 'string') return Number(value) || 0;
  return 0;
}

async function waitForMrpJob(jobId: string): Promise<MrpConsolidateJob> {
  for (let attempt = 0; attempt < 40; attempt += 1) {
    const { data } = await apiClient.get<MrpConsolidateJob>(
      `/api/v1/purchasing/mrp/calculate/jobs/${jobId}`,
    );
    if (data.status === 'COMPLETED') return data;
    if (data.status === 'FAILED') {
      throw new Error(data.error || 'MRP consolidate failed');
    }
    await new Promise((resolve) => window.setTimeout(resolve, 400));
  }
  throw new Error('MRP consolidate is still running. Refresh Purchase Orders in a moment.');
}

export function MrpReorderWorkspace() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const isMobile = useMediaQuery('(max-width: 767px)');
  const [supplierId, setSupplierId] = useState('');
  const [urgency, setUrgency] = useState<UrgencyFilter>('');
  const [overrides, setOverrides] = useState<Record<string, number>>({});

  const table = useServerTableQuery<MrpSuggestionLine>({
    queryKey: ['purchasing', 'mrp', 'suggestions'],
    path: '/api/v1/purchasing/mrp/suggestions',
    defaultSort: 'capital,desc',
    extraParams: {
      supplierId: supplierId || undefined,
      urgency: urgency || undefined,
    },
  });
  useCapMobilePageSize(isMobile, table.size, table.setSize);

  const summary = useQuery({
    queryKey: ['purchasing', 'mrp', 'summary', table.search, supplierId, urgency],
    queryFn: async () =>
      (
        await apiClient.get<MrpSuggestionSummary>('/api/v1/purchasing/mrp/suggestions/summary', {
          params: {
            ...(table.search ? { search: table.search } : {}),
            ...(supplierId ? { supplierId } : {}),
            ...(urgency ? { urgency } : {}),
          },
        })
      ).data,
  });

  const suppliers = useQuery({
    queryKey: ['suppliers', 'mrp-filter'],
    queryFn: () => listSuppliers({ page: 1, size: 100, sort: 'name,asc' }),
  });

  const itemsRef = useRef(table.items);
  itemsRef.current = table.items;

  const rows = useMemo(
    () =>
      table.items.map((line) => {
        const override = overrides[line.variantId];
        if (override == null) return line;
        return {
          ...line,
          suggestedOrderQty: override,
          capitalEstimate: override * asNumber(line.unitCost),
        };
      }),
    [overrides, table.items],
  );

  const overrideCount = Object.keys(overrides).length;
  const lineCount = summary.data?.qualifyingLineCount ?? table.totalElements;
  const totalCapital = summary.data?.totalCapital ?? rows.reduce((sum, line) => sum + asNumber(line.capitalEstimate), 0);

  const consolidateMutation = useMutation({
    mutationFn: async () => {
      const payload = {
        supplierId: supplierId || undefined,
        urgency: urgency || undefined,
        overrides: Object.entries(overrides).map(([variantId, suggestedOrderQty]) => ({
          variantId,
          suggestedOrderQty,
        })),
      };
      const queued = (await apiClient.post<MrpConsolidateJob>('/api/v1/purchasing/mrp/calculate/jobs', payload)).data;
      return waitForMrpJob(queued.jobId);
    },
    onSuccess: (job) => {
      const result = job.result;
      const ids = result?.createdPurchaseOrders?.map((po) => po.id) ?? [];
      const groups = new Set(result?.createdPurchaseOrders?.map((po) => po.supplierId) ?? []).size;
      toast(`Created ${ids.length} draft PO(s) across ${groups} supplier group(s).`, {
        tone: 'success',
      });
      setOverrides({});
      void queryClient.invalidateQueries({ queryKey: ['purchasing', 'mrp'] });
      void queryClient.invalidateQueries({ queryKey: ['purchase-orders'] });
      if (ids.length === 1) {
        navigate(`/purchase-orders?peek=${ids[0]}`);
      }
    },
    onError: (error: Error) => {
      toast(error.message || 'Could not consolidate MRP suggestions into draft POs.', { tone: 'danger' });
    },
  });

  const columns = useMemo<VirtualizedColumnDef<MrpSuggestionLine>[]>(
    () => [
      {
        id: 'sku',
        header: 'SKU',
        width: 140,
        maxWidth: 180,
        sortable: true,
        sortValue: (line) => line.sku,
        cell: (line) => (
          <span className="font-mono" data-testid={`mrp-line-${line.sku}`}>
            {line.sku}
          </span>
        ),
      },
      {
        id: 'supplier',
        header: 'Supplier',
        width: 160,
        flexGrow: true,
        sortable: true,
        sortValue: (line) => line.defaultSupplierName ?? '',
        cell: (line) => line.defaultSupplierName ?? '—',
      },
      {
        id: 'onHand',
        header: 'On-Hand',
        width: 96,
        align: 'right',
        sortable: true,
        sortValue: (line) => asNumber(line.onHand),
        cell: (line) => <span className="font-mono tabular-nums">{asNumber(line.onHand)}</span>,
      },
      {
        id: 'allocated',
        header: 'Allocated',
        width: 96,
        align: 'right',
        sortable: true,
        sortValue: (line) => asNumber(line.allocated),
        cell: (line) => <span className="font-mono tabular-nums">{asNumber(line.allocated)}</span>,
      },
      {
        id: 'inbound',
        header: 'Inbound',
        width: 96,
        align: 'right',
        sortable: true,
        sortValue: (line) => asNumber(line.inboundOpenPoQty),
        cell: (line) => <span className="font-mono tabular-nums">{asNumber(line.inboundOpenPoQty)}</span>,
      },
      {
        id: 'minmax',
        header: 'Min / Max',
        width: 110,
        align: 'right',
        cell: (line) => (
          <span className="font-mono tabular-nums" data-testid="mrp-minmax">
            {asNumber(line.minStock ?? line.safetyStock)} / {asNumber(line.maxStock ?? line.safetyStock)}
          </span>
        ),
      },
      {
        id: 'qty',
        header: 'Suggested qty',
        width: 140,
        align: 'right',
        sortable: true,
        sortValue: (line) => asNumber(line.suggestedOrderQty),
        cell: (line) => {
          const overridden = overrides[line.variantId] != null;
          return (
            <InlineEditableCell
              value={asNumber(line.suggestedOrderQty)}
              inputType="number"
              testId={`mrp-qty-${line.sku}`}
              className={cn(overridden && 'bg-warning/20')}
              onSave={(next) => {
                const qty = Number(next);
                if (!Number.isFinite(qty) || qty < 0) return;
                setOverrides((prev) => {
                  const original = asNumber(
                    itemsRef.current.find((row) => row.variantId === line.variantId)?.suggestedOrderQty,
                  );
                  if (qty === original) {
                    const { [line.variantId]: _removed, ...rest } = prev;
                    return rest;
                  }
                  return { ...prev, [line.variantId]: qty };
                });
              }}
            />
          );
        },
      },
      {
        id: 'lead',
        header: 'Lead time (days)',
        width: 120,
        align: 'right',
        sortable: true,
        sortValue: (line) => line.leadTimeDays,
        cell: (line) => <span className="font-mono tabular-nums">{line.leadTimeDays}</span>,
      },
      {
        id: 'unitCost',
        header: 'Unit cost',
        width: 110,
        align: 'right',
        cell: (line) => formatCurrency(asNumber(line.unitCost)),
      },
      {
        id: 'capital',
        header: 'Capital',
        width: 120,
        align: 'right',
        sortable: true,
        sortValue: (line) => asNumber(line.capitalEstimate),
        cell: (line) => formatCurrency(asNumber(line.capitalEstimate)),
      },
    ],
    [overrides],
  );

  return (
    <TableDensityScope gridId="mrp-suggestions">
      <div className="flex h-full min-h-0 flex-col" data-testid="mrp-reorder-workspace">
        <div className="flex shrink-0 flex-col gap-4 px-6 pt-6 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h1 className="text-2xl font-bold text-text">MRP reorder</h1>
            <p className="mt-1 text-sm text-text-muted">
              Available = (On-Hand + Inbound) − Allocated. Buy up to Max when Available is below Min.
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <Button variant="secondary" onClick={() => void table.refetch()}>
              <RefreshCw className="h-4 w-4" />
              Refresh
            </Button>
            <Button
              data-testid="mrp-consolidate-button"
              loading={consolidateMutation.isPending}
              disabled={lineCount === 0}
              onClick={() => consolidateMutation.mutate()}
            >
              <Calculator className="h-4 w-4" />
              Consolidate & Create Draft POs
            </Button>
          </div>
        </div>

        <div className="shrink-0 px-6 pt-4">
          <Card>
            <CardHeader
              title="Capital exposure"
              description={`${lineCount} SKU line(s) with net requirement${
                overrideCount ? ` · ${overrideCount} manual override(s)` : ''
              }`}
            />
            <p className="px-6 pb-4 text-2xl font-bold tabular-nums text-text" data-testid="mrp-capital-total">
              {formatCurrency(asNumber(totalCapital))}
            </p>
          </Card>
        </div>

        <div className="shrink-0 px-6 pt-4" data-testid="mrp-filter-bar">
          <DataListToolbar gridId="mrp-suggestions">
            <div className="flex flex-wrap items-end gap-3">
              <DebouncedSearchInput
                value={table.search}
                onDebouncedChange={table.setSearch}
                placeholder="Search SKU or supplier…"
              />
              <Select
                label="Supplier"
                value={supplierId}
                data-testid="mrp-supplier-filter"
                onChange={(e) => {
                  setSupplierId(e.target.value);
                  table.setPage(1);
                }}
              >
                <option value="">All suppliers</option>
                {(suppliers.data?.items ?? []).map((supplier: Supplier) => (
                  <option key={supplier.id} value={supplier.id}>
                    {supplier.name}
                  </option>
                ))}
              </Select>
              <Select
                label="Urgency"
                value={urgency}
                data-testid="mrp-urgency-filter"
                onChange={(e) => {
                  setUrgency(e.target.value as UrgencyFilter);
                  table.setPage(1);
                }}
              >
                <option value="">All suggestions</option>
                <option value="STOCKOUT">Show Stockouts Only</option>
                <option value="BELOW_MINIMUM">Below Minimum</option>
                <option value="FORECASTED">Forecasted Demand</option>
              </Select>
            </div>
          </DataListToolbar>
        </div>

        <DataListWorkspace className="px-6 pb-4 pt-2" testId="mrp-grid-shell">
          {table.isLoading && rows.length === 0 ? (
            <div className="p-6" data-testid="list-page-loading">
              <TableSkeleton rows={8} cols={8} />
            </div>
          ) : table.isError ? (
            <EmptyState
              title="Could not load MRP suggestions"
              description={table.error instanceof Error ? table.error.message : 'Try again.'}
              action={
                <Button variant="secondary" onClick={() => void table.refetch()}>
                  Retry
                </Button>
              }
            />
          ) : rows.length === 0 ? (
            <EmptyState
              title="No reorder suggestions"
              description="Variants at or below safety stock with open demand will appear here."
            />
          ) : (
            <>
              {isMobile ? (
                <div
                  className="flex flex-col gap-3 overflow-y-auto md:hidden"
                  data-testid="mrp-mobile-list"
                >
                  {rows.map((line) => (
                    <EntityMobileCard
                      key={line.variantId}
                      testId={`mrp-line-${line.sku}`}
                      identity={line.sku}
                      title={line.defaultSupplierName ?? 'No default supplier'}
                      status={
                        <span className="inline-flex rounded-full bg-accent-muted px-2.5 py-0.5 text-xs font-medium text-accent">
                          Qty {asNumber(line.suggestedOrderQty)}
                        </span>
                      }
                      amount={formatCurrency(asNumber(line.capitalEstimate))}
                      date={`On hand ${asNumber(line.onHand)}`}
                    />
                  ))}
                </div>
              ) : (
                <div
                  className="hidden min-h-0 flex-1 md:flex md:flex-col"
                  data-testid="mrp-table-view"
                >
                  <VirtualizedTable
                    gridId="mrp-suggestions"
                    columns={columns}
                    rows={rows}
                    getRowId={(row) => row.variantId}
                  />
                </div>
              )}
            </>
          )}
        </DataListWorkspace>

        <div className="shrink-0 px-6 pb-6">
          <Pagination
            page={table.page}
            totalPages={table.totalPages}
            totalElements={table.totalElements}
            size={table.size}
            onPageChange={table.setPage}
            onSizeChange={table.setSize}
          />
        </div>
      </div>
    </TableDensityScope>
  );
}
