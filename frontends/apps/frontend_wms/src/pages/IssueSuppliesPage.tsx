import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { PackageCheck, ScanLine } from 'lucide-react';
import { apiClient } from '@/api/client';
import type { InternalRequisition } from '@/api/types';
import { useHardwareScanner } from '@/hooks/useHardwareScanner';
import { useScanFeedback } from '@/hooks/useScanFeedback';
import { useScanBufferStore } from '@/stores/scanBuffer';
import { useActiveWarehouseStore } from '@/stores/activeWarehouse';
import { ListPageState } from '@/components/layout/ListPageState';
import { BigButton } from '@/components/ui/BigButton';
import { ScanFlashOverlay } from '@/components/ui/ScanFlashOverlay';
import { Card } from '@/components/ui/Card';
import { cn } from '@/lib/utils';

export function IssueSuppliesPage() {
  const queryClient = useQueryClient();
  const lastScan = useScanBufferStore((s) => s.lastScan);
  const { flash, triggerSuccess, triggerError } = useScanFeedback();
  const warehouse = useActiveWarehouseStore((s) => s.warehouse);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [confirmedSkus, setConfirmedSkus] = useState<Set<string>>(new Set());

  const {
    data: requisitions = [],
    isLoading,
    isError,
    error,
    refetch,
  } = useQuery({
    queryKey: ['internal-requisitions', 'APPROVED'],
    queryFn: async () =>
      (await apiClient.get<InternalRequisition[]>('/api/v1/internal-requisitions', {
        params: { status: 'APPROVED' },
      })).data,
    retry: false,
  });

  const selected = useMemo(
    () => requisitions.find((r) => r.id === selectedId) ?? null,
    [requisitions, selectedId]
  );

  const issueMutation = useMutation({
    mutationFn: async (requisitionId: string) => {
      if (!warehouse?.id) throw new Error('No warehouse selected');
      const res = await apiClient.post<InternalRequisition>(
        `/api/v1/internal-requisitions/${requisitionId}/issue`,
        { locationId: warehouse.id }
      );
      return res.data;
    },
    onSuccess: () => {
      triggerSuccess();
      setSelectedId(null);
      setConfirmedSkus(new Set());
      void queryClient.invalidateQueries({ queryKey: ['internal-requisitions'] });
    },
    onError: () => triggerError(),
  });

  useHardwareScanner({
    enabled: true,
    captureAll: true,
    onScan: (code) => {
      if (!code.length || !selected) return;
      const match = selected.lines?.find(
        (l) => l.sku && l.sku.toLowerCase() === code.toLowerCase()
      );
      if (match?.sku) {
        triggerSuccess();
        setConfirmedSkus((prev) => new Set(prev).add(match.sku!.toLowerCase()));
      } else {
        triggerError();
      }
    },
  });

  const lines = selected?.lines ?? [];
  const allConfirmed =
    lines.length > 0 &&
    lines.every((l) => l.sku && confirmedSkus.has(l.sku.toLowerCase()));

  return (
    <div className="flex min-h-full flex-col p-4 pb-8" data-theme="warehouse">
      <ScanFlashOverlay flash={flash} />

      <div className="mb-6 text-center">
        <div className="mb-2 flex items-center justify-center gap-2">
          <PackageCheck className="h-6 w-6 text-accent" />
          <h1 className="text-2xl font-bold text-text">Issue Supplies</h1>
        </div>
        <p className="text-sm text-text-muted">
          {warehouse?.name ?? 'No warehouse selected'} · Scan SKUs then Issue Fact
        </p>
      </div>

      <Card className="mb-6 text-center" padding="lg">
        <ScanLine className="mx-auto mb-3 h-10 w-10 text-accent" />
        <p className="text-sm text-text-muted">Last scan</p>
        <p className="mt-1 font-mono text-2xl font-bold text-text">{lastScan ?? 'Scan line SKU'}</p>
      </Card>

      {!selected ? (
        <div className="space-y-3">
          <p className="text-sm font-medium text-text-muted">Approved requisitions</p>
          <ListPageState
            isLoading={isLoading}
            isError={isError}
            error={error}
            data={requisitions}
            refetch={() => void refetch()}
            emptyIcon={PackageCheck}
            emptyTitle="No approved requisitions"
            emptyDescription="Approved internal requisitions will appear here for floor issue."
          >
            {(items) => (
              <div className="space-y-3">
                {items.map((req) => (
                  <button
                    key={req.id}
                    type="button"
                    onClick={() => {
                      setSelectedId(req.id);
                      setConfirmedSkus(new Set());
                    }}
                    className="min-h-14 w-full rounded-xl border border-border bg-surface-raised p-4 text-left transition-colors hover:border-accent"
                  >
                    <p className="font-mono text-lg font-bold text-text">{req.requisitionNumber}</p>
                    <p className="text-sm text-text-muted">
                      {req.costCenterCode ?? 'Cost center'} · {req.lines?.length ?? 0} lines
                    </p>
                  </button>
                ))}
              </div>
            )}
          </ListPageState>
        </div>
      ) : (
        <>
          <Card className="mb-4" padding="md">
            <p className="font-mono text-lg font-bold text-text">{selected.requisitionNumber}</p>
            <p className="text-sm text-text-muted">{selected.costCenterCode}</p>
          </Card>

          <div className="mb-6 space-y-3">
            {lines.map((line) => {
              const confirmed = !!(line.sku && confirmedSkus.has(line.sku.toLowerCase()));
              return (
                <div
                  key={line.id}
                  className={cn(
                    'rounded-xl border p-4',
                    confirmed
                      ? 'border-success/30 bg-success/5'
                      : 'border-border bg-surface-raised'
                  )}
                >
                  <p className="font-mono text-base font-bold text-text">{line.sku ?? line.variantId}</p>
                  <p className="text-sm text-text-muted">
                    Qty {line.qtyRequested} · issued {line.qtyIssued}
                  </p>
                </div>
              );
            })}
          </div>

          <div className="mt-auto space-y-3">
            <BigButton
              variant="success"
              disabled={!allConfirmed || issueMutation.isPending || !warehouse?.id}
              onClick={() => issueMutation.mutate(selected.id)}
            >
              Issue Fact
            </BigButton>
            <BigButton
              variant="secondary"
              onClick={() => {
                setSelectedId(null);
                setConfirmedSkus(new Set());
              }}
            >
              Back to list
            </BigButton>
          </div>
        </>
      )}
    </div>
  );
}
