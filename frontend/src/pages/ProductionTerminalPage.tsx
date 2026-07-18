import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { CheckCircle, Clock, Factory, ScanLine, Square } from 'lucide-react';
import { apiClient } from '@/api/client';
import type { ManufacturingOperation, ProductionOrder, ProductionTimesheet } from '@/api/types';
import { useBarcodeScanner } from '@/hooks/useBarcodeScanner';
import { useScanFeedback } from '@/hooks/useScanFeedback';
import { useScanBufferStore } from '@/stores/scanBuffer';
import { ListPageState } from '@/components/layout/ListPageState';
import { BigButton } from '@/components/ui/BigButton';
import { ScanFlashOverlay } from '@/components/ui/ScanFlashOverlay';
import { Card } from '@/components/ui/Card';
import { Select } from '@/components/ui/Select';
import { cn } from '@/lib/utils';

const ACTIVE_STATUSES = ['COMPONENTS_ALLOCATED', 'WIP'];

export function ProductionTerminalPage() {
  const queryClient = useQueryClient();
  const lastScan = useScanBufferStore((s) => s.lastScan);
  const { flash, triggerSuccess, triggerError } = useScanFeedback();
  const [selectedOrderId, setSelectedOrderId] = useState<string | null>(null);
  const [verifiedScans, setVerifiedScans] = useState<string[]>([]);
  const [selectedOperationId, setSelectedOperationId] = useState('');
  const [activeTimesheetId, setActiveTimesheetId] = useState<string | null>(null);

  const {
    data: orders = [],
    isLoading,
    isError,
    error,
    refetch,
  } = useQuery({
    queryKey: ['manufacturing', 'terminal', 'orders'],
    queryFn: async () => {
      const res = await apiClient.get<ProductionOrder[]>('/api/v1/manufacturing/orders');
      return res.data.filter((o) => ACTIVE_STATUSES.includes(o.status));
    },
    retry: false,
  });

  const selectedOrder = orders.find((o) => o.id === selectedOrderId) ?? orders[0] ?? null;

  const { data: operations = [] } = useQuery({
    queryKey: ['manufacturing', 'operations', selectedOrder?.id],
    queryFn: async () => {
      const res = await apiClient.get<ManufacturingOperation[]>(
        `/api/v1/manufacturing/orders/${selectedOrder!.id}/operations`
      );
      return res.data;
    },
    enabled: !!selectedOrder,
    retry: false,
  });

  const { data: timesheets = [] } = useQuery({
    queryKey: ['manufacturing', 'timesheets', selectedOrder?.id],
    queryFn: async () => {
      const res = await apiClient.get<ProductionTimesheet[]>(
        `/api/v1/manufacturing/orders/${selectedOrder!.id}/timesheets`
      );
      return res.data;
    },
    enabled: !!selectedOrder,
    retry: false,
  });

  const openTimesheet = timesheets.find((t) => !t.endTime);

  const startTimesheetMutation = useMutation({
    mutationFn: async () => {
      const res = await apiClient.post<ProductionTimesheet>(
        `/api/v1/manufacturing/orders/${selectedOrder!.id}/timesheets/start`,
        { operationId: selectedOperationId }
      );
      return res.data;
    },
    onSuccess: (sheet) => {
      triggerSuccess();
      setActiveTimesheetId(sheet.id);
      void queryClient.invalidateQueries({ queryKey: ['manufacturing', 'timesheets'] });
    },
    onError: () => triggerError(),
  });

  const stopTimesheetMutation = useMutation({
    mutationFn: async (timesheetId: string) => {
      const res = await apiClient.post<ProductionTimesheet>(
        `/api/v1/manufacturing/timesheets/${timesheetId}/stop`
      );
      return res.data;
    },
    onSuccess: () => {
      triggerSuccess();
      setActiveTimesheetId(null);
      void queryClient.invalidateQueries({ queryKey: ['manufacturing', 'timesheets'] });
    },
    onError: () => triggerError(),
  });

  const completeMutation = useMutation({
    mutationFn: async (orderId: string) => {
      await apiClient.post(`/api/v1/manufacturing/orders/${orderId}/assemble`, {
        qtyToProduce: 1,
      });
    },
    onSuccess: () => {
      triggerSuccess();
      setVerifiedScans([]);
      void queryClient.invalidateQueries({ queryKey: ['manufacturing'] });
    },
    onError: () => triggerError(),
  });

  useBarcodeScanner({
    enabled: !!selectedOrder,
    captureAll: true,
    onScan: (barcode) => {
      if (!selectedOrder || barcode.length === 0) return;
      setVerifiedScans((prev) => {
        if (prev.includes(barcode)) return prev;
        triggerSuccess();
        return [barcode, ...prev].slice(0, 20);
      });
    },
  });

  const currentTimesheetId = activeTimesheetId ?? openTimesheet?.id ?? null;

  return (
    <div className="flex min-h-full flex-col p-4 pb-8" data-theme="warehouse">
      <ScanFlashOverlay flash={flash} />

      <div className="mb-6 text-center">
        <div className="mb-2 flex items-center justify-center gap-2">
          <Factory className="h-6 w-6 text-accent" />
          <h1 className="text-2xl font-bold text-text">Production Terminal</h1>
        </div>
        <p className="text-sm text-text-muted">Track labor, scan components, complete builds</p>
      </div>

      <ListPageState
        isLoading={isLoading}
        isError={isError}
        error={error}
        data={orders}
        refetch={() => void refetch()}
        emptyIcon={Factory}
        emptyTitle="No active production orders"
        emptyDescription="Orders in COMPONENTS_ALLOCATED or WIP will appear here for the floor terminal."
      >
        {(activeOrders) => (
        <>
          <div className="mb-4 space-y-2">
            {activeOrders.map((order) => (
              <button
                key={order.id}
                type="button"
                onClick={() => {
                  setSelectedOrderId(order.id);
                  setVerifiedScans([]);
                  setSelectedOperationId('');
                  setActiveTimesheetId(null);
                }}
                className={cn(
                  'w-full rounded-lg border p-4 text-left transition-colors',
                  selectedOrder?.id === order.id
                    ? 'border-accent bg-accent-muted'
                    : 'border-border bg-surface-raised hover:bg-surface-overlay'
                )}
              >
                <p className="font-mono font-bold text-text">{order.number}</p>
                <p className="text-sm text-text-muted">
                  {order.parentSku ?? order.parentName} · {order.qtyProduced}/{order.qtyTarget}
                </p>
              </button>
            ))}
          </div>

          {selectedOrder && (
            <Card className="mb-4" padding="md">
              <div className="mb-3 flex items-center gap-2">
                <Clock className="h-5 w-5 text-accent" />
                <p className="font-medium text-text">Labor timesheet</p>
              </div>
              {!currentTimesheetId ? (
                <>
                  <Select
                    label="Operation"
                    value={selectedOperationId}
                    onChange={(e) => setSelectedOperationId(e.target.value)}
                  >
                    <option value="">Select operation…</option>
                    {operations.map((op) => (
                      <option key={op.id} value={op.id}>
                        {op.name} (${op.defaultHourlyRate}/hr)
                      </option>
                    ))}
                  </Select>
                  <BigButton
                    variant="primary"
                    className="mt-3 w-full"
                    disabled={!selectedOperationId}
                    loading={startTimesheetMutation.isPending}
                    onClick={() => startTimesheetMutation.mutate()}
                  >
                    Start timesheet
                  </BigButton>
                </>
              ) : (
                <>
                  <p className="text-sm text-accent">Timesheet running…</p>
                  <BigButton
                    variant="secondary"
                    className="mt-3 w-full"
                    loading={stopTimesheetMutation.isPending}
                    onClick={() => stopTimesheetMutation.mutate(currentTimesheetId)}
                  >
                    <Square className="h-4 w-4" />
                    Stop timesheet
                  </BigButton>
                </>
              )}
              {timesheets.filter((t) => t.endTime).length > 0 && (
                <p className="mt-3 text-xs text-text-muted">
                  Recorded labor: $
                  {timesheets
                    .filter((t) => t.endTime)
                    .reduce((sum, t) => sum + t.totalCost, 0)
                    .toFixed(2)}
                </p>
              )}
            </Card>
          )}

          <Card className="mb-6 text-center" padding="lg">
            <ScanLine className="mx-auto mb-3 h-10 w-10 text-accent" />
            <p className="text-sm text-text-muted">Last scan</p>
            <p className="mt-1 font-mono text-2xl font-bold text-text">
              {lastScan ?? 'Ready to scan'}
            </p>
          </Card>

          {verifiedScans.length > 0 && (
            <div className="mb-6 space-y-2">
              <h2 className="text-sm font-medium text-text-muted">Verified components</h2>
              {verifiedScans.map((scan) => (
                <div
                  key={scan}
                  className="flex items-center gap-3 rounded-lg border border-border bg-surface-raised p-3"
                >
                  <CheckCircle className="h-5 w-5 text-success" />
                  <span className="font-mono text-text">{scan}</span>
                </div>
              ))}
            </div>
          )}

          <BigButton
            variant="success"
            loading={completeMutation.isPending}
            disabled={!selectedOrder}
            onClick={() => selectedOrder && completeMutation.mutate(selectedOrder.id)}
          >
            Complete build
          </BigButton>
        </>
        )}
      </ListPageState>
    </div>
  );
}
