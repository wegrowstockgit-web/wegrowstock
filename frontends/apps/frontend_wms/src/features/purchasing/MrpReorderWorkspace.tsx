import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Calculator, RefreshCw } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { apiClient } from '@/api/client';
import type { MrpCalculateResult, MrpSuggestionLine } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { ListPageState, useListQuery } from '@/components/layout/ListPageState';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/Table';
import { formatCurrency } from '@/lib/utils';
import { useClientSort } from '@/hooks/useClientSort';
import { useToast } from '@/components/ui/Toast';

function asNumber(value: unknown): number {
  if (typeof value === 'number') return value;
  if (typeof value === 'string') return Number(value) || 0;
  return 0;
}

export function MrpReorderWorkspace() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { toast } = useToast();

  const { data, isLoading, isError, error, refetch } = useListQuery<MrpSuggestionLine>(
    ['purchasing', 'mrp', 'suggestions'],
    '/api/v1/purchasing/mrp/suggestions',
  );

  const { sort, toggle, sorted } = useClientSort(
    data ?? [],
    {
      sku: (line) => line.sku,
      supplier: (line) => line.defaultSupplierName ?? '',
      qty: (line) => asNumber(line.suggestedOrderQty),
      lead: (line) => line.leadTimeDays,
      capital: (line) => asNumber(line.capitalEstimate),
    },
    { key: 'capital', dir: 'desc' },
  );

  const totalCapital = (data ?? []).reduce(
    (sum, line) => sum + asNumber(line.capitalEstimate),
    0,
  );

  const consolidateMutation = useMutation({
    mutationFn: async () => {
      const res = await apiClient.post<MrpCalculateResult>('/api/v1/purchasing/mrp/calculate', {});
      return res.data;
    },
    onSuccess: (result) => {
      const ids = result.createdPurchaseOrders?.map((po) => po.id) ?? [];
      const groups = new Set(result.createdPurchaseOrders?.map((po) => po.supplierId) ?? []).size;
      toast(`Created ${ids.length} draft PO(s) across ${groups} supplier group(s).`, {
        tone: 'success',
      });
      void queryClient.invalidateQueries({ queryKey: ['purchasing', 'mrp'] });
      void queryClient.invalidateQueries({ queryKey: ['purchase-orders'] });
      if (ids.length === 1) {
        navigate(`/purchase-orders?peek=${ids[0]}`);
      }
    },
    onError: () => {
      toast('Could not consolidate MRP suggestions into draft POs.', { tone: 'danger' });
    },
  });

  return (
    <div className="p-6" data-testid="mrp-reorder-workspace">
      <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold text-text">MRP reorder</h1>
          <p className="mt-1 text-sm text-text-muted">
            Safety-stock and lead-time driven purchase suggestions
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button variant="secondary" onClick={() => void refetch()}>
            <RefreshCw className="h-4 w-4" />
            Refresh
          </Button>
          <Button
            data-testid="mrp-consolidate-button"
            loading={consolidateMutation.isPending}
            disabled={(data?.length ?? 0) === 0}
            onClick={() => consolidateMutation.mutate()}
          >
            <Calculator className="h-4 w-4" />
            Consolidate & Create Draft POs
          </Button>
        </div>
      </div>

      <Card className="mb-6">
        <CardHeader
          title="Capital exposure"
          description={`${data?.length ?? 0} SKU line(s) with net requirement`}
        />
        <p className="px-6 pb-4 text-2xl font-bold tabular-nums text-text">
          {formatCurrency(totalCapital)}
        </p>
      </Card>

      <ListPageState
        isLoading={isLoading}
        isError={isError}
        error={error}
        data={data}
        refetch={refetch}
        emptyTitle="No reorder suggestions"
        emptyDescription="Variants at or below safety stock with open demand will appear here."
      >
        {() => (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead sortable sortKey="sku" sort={sort} onSort={toggle}>
                  SKU
                </TableHead>
                <TableHead sortable sortKey="supplier" sort={sort} onSort={toggle}>
                  Supplier
                </TableHead>
                <TableHead sortable sortKey="qty" sort={sort} onSort={toggle} align="right">
                  Suggested qty
                </TableHead>
                <TableHead sortable sortKey="lead" sort={sort} onSort={toggle} align="right">
                  Lead time (days)
                </TableHead>
                <TableHead align="right">Unit cost</TableHead>
                <TableHead sortable sortKey="capital" sort={sort} onSort={toggle} align="right">
                  Capital
                </TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {sorted.map((line) => (
                <TableRow key={line.variantId} data-testid={`mrp-line-${line.sku}`}>
                  <TableCell mono>{line.sku}</TableCell>
                  <TableCell>{line.defaultSupplierName ?? '—'}</TableCell>
                  <TableCell mono align="right">
                    {asNumber(line.suggestedOrderQty)}
                  </TableCell>
                  <TableCell mono align="right">
                    {line.leadTimeDays}
                  </TableCell>
                  <TableCell mono align="right">
                    {formatCurrency(asNumber(line.unitCost))}
                  </TableCell>
                  <TableCell mono align="right">
                    {formatCurrency(asNumber(line.capitalEstimate))}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </ListPageState>
    </div>
  );
}
