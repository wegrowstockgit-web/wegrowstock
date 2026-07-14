import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ScanLine, Scale } from 'lucide-react';
import { apiClient } from '@/api/client';
import type { FulfillmentScanResponse, PackLabelResponse, PickingTask, SalesOrder } from '@/api/types';
import { useBluetoothScale } from '@/hooks/useBluetoothScale';
import { useHardwareScanner } from '@/hooks/useHardwareScanner';
import { useScanFeedback } from '@/hooks/useScanFeedback';
import { useScanBufferStore } from '@/stores/scanBuffer';
import { useActiveWarehouseStore } from '@/stores/activeWarehouse';
import { useSessionStore } from '@/stores/session';
import { cn, generateIdempotencyKey } from '@/lib/utils';
import { BigButton } from '@/components/ui/BigButton';
import { Button } from '@/components/ui/Button';
import { ScanFlashOverlay } from '@/components/ui/ScanFlashOverlay';
import { Card } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { LocationBreadcrumb } from '@/components/ui/LocationBreadcrumb';
import { UndoToast } from '@/components/ui/UndoToast';
import { MediaPicker } from '@/components/ui/MediaPicker';
import { VariantThumb } from '@/components/ui/VariantThumb';
import { useWarehouseUXStore } from '@/stores/warehouseUX';

function WaveReleaseControls({ onReleased }: { onReleased: () => void }) {
  const canManage = useSessionStore((s) => s.hasRole('OWNER', 'ADMIN', 'WAREHOUSE_MANAGER'));
  const [draftWaveId, setDraftWaveId] = useState<string | null>(null);

  const generateMutation = useMutation({
    mutationFn: async () => {
      const res = await apiClient.post<{ waveId: string; status: string }>(
        '/api/v1/picking/waves/generate',
        {}
      );
      return res.data;
    },
    onSuccess: (data) => setDraftWaveId(data.waveId),
  });

  const releaseMutation = useMutation({
    mutationFn: async (waveId: string) => {
      await apiClient.post(`/api/v1/picking/waves/${waveId}/release`);
    },
    onSuccess: () => {
      setDraftWaveId(null);
      onReleased();
    },
  });

  if (!canManage) return null;

  return (
    <div className="mt-3 flex flex-wrap gap-2">
      <Button
        size="sm"
        variant="secondary"
        loading={generateMutation.isPending}
        onClick={() => generateMutation.mutate()}
      >
        Generate draft wave
      </Button>
      {draftWaveId && (
        <Button
          size="sm"
          loading={releaseMutation.isPending}
          onClick={() => releaseMutation.mutate(draftWaveId)}
        >
          Release to floor
        </Button>
      )}
    </div>
  );
}

interface ScanResult {
  barcode: string;
  variantId?: string;
  sku?: string;
  name?: string;
  success: boolean;
  message: string;
  putawayTarget?: string;
  primaryMediaUrl?: string | null;
  timestamp: number;
}

interface SerialCaptureState {
  barcode: string;
  sku: string;
  name: string;
  mode: 'pick' | 'receive';
  captured: string[];
  required: number;
}

export function FulfillmentPage() {
  const queryClient = useQueryClient();
  const warehouse = useActiveWarehouseStore((s) => s.warehouse);
  const lastScan = useScanBufferStore((s) => s.lastScan);
  const { flash, triggerSuccess, triggerError } = useScanFeedback();
  const [history, setHistory] = useState<ScanResult[]>([]);
  const [lastThumbUrl, setLastThumbUrl] = useState<string | null>(null);
  const [mode, setMode] = useState<'pick' | 'receive'>('pick');
  const [batchMode, setBatchMode] = useState(false);
  const [serialCapture, setSerialCapture] = useState<SerialCaptureState | null>(null);
  const [packingMode, setPackingMode] = useState(false);
  const [packSalesOrderId, setPackSalesOrderId] = useState('');
  const [labelMessage, setLabelMessage] = useState('');
  const scale = useBluetoothScale();
  const pendingMisScan = useWarehouseUXStore((s) => s.pendingMisScan);
  const bufferMisScan = useWarehouseUXStore((s) => s.bufferMisScan);
  const undoMisScan = useWarehouseUXStore((s) => s.undoMisScan);

  const { data: packOrders = [] } = useQuery({
    queryKey: ['sales-orders', 'pack'],
    queryFn: async () => (await apiClient.get<SalesOrder[]>('/api/v1/sales-orders')).data,
    enabled: packingMode,
    retry: false,
  });

  const packLabelMutation = useMutation({
    mutationFn: async (totalWeightLb: number) => {
      const res = await apiClient.post<PackLabelResponse>('/api/v1/shipments/pack-label', {
        salesOrderId: packSalesOrderId,
        totalWeightLb,
        carrier: 'EASYPOST',
      });
      return res.data;
    },
    onSuccess: (label) => {
      triggerSuccess();
      setLabelMessage(
        `Label ${label.trackingNumber} created · ${label.totalWeight?.toFixed(2)} lb · postage $${label.postageAmount?.toFixed(2)}`
      );
    },
    onError: () => {
      triggerError();
      setLabelMessage('Could not generate label. Select a sales order and try again.');
    },
  });

  const { data: batchTasks = [], refetch: refetchTasks } = useQuery({
    queryKey: ['picking', 'batch-tasks'],
    queryFn: async () => {
      const res = await apiClient.get<PickingTask[]>('/api/v1/picking/batches/current/tasks');
      return res.data;
    },
    enabled: batchMode,
    refetchInterval: batchMode ? 10_000 : false,
    retry: false,
  });

  const nextTask = batchTasks.find((t) => t.status === 'PENDING');

  const pickTaskMutation = useMutation({
    mutationFn: async (taskId: string) => {
      await apiClient.post(`/api/v1/picking/tasks/${taskId}/pick`);
    },
    onSuccess: () => void refetchTasks(),
  });

  const submitScan = async (barcode: string, serialNumber?: string): Promise<FulfillmentScanResponse> => {
    const idempotencyKey = generateIdempotencyKey();
    const payload = {
      barcode,
      warehouseId: warehouse?.id,
      mode: serialCapture?.mode ?? mode,
      serialNumber,
    };

    if (!navigator.onLine) {
      const queuedHistory = {
        sku: barcode,
        name: barcode,
        requiresSerial: false,
        message: 'Queued for sync',
      };
      bufferMisScan({
        barcode,
        message: `Scan queued — undo within 5s`,
        mutation: {
          idempotencyKey,
          method: 'POST',
          url: '/api/v1/fulfillment/scan',
          body: payload,
        },
      });
      return queuedHistory as FulfillmentScanResponse;
    }

    const res = await apiClient.post<FulfillmentScanResponse>('/api/v1/fulfillment/scan', payload, {
      headers: { 'Idempotency-Key': idempotencyKey },
    });
    return res.data;
  };

  const scanMutation = useMutation({
    mutationFn: (barcode: string) => submitScan(barcode),
    onSuccess: (result, barcode) => {
      if (result.requiresSerial && !serialCapture) {
        setSerialCapture({
          barcode,
          sku: result.sku,
          name: result.name,
          mode,
          captured: [],
          required: 1,
        });
        return;
      }
      triggerSuccess();
      if ('vibrate' in navigator) navigator.vibrate([30, 20, 30]);
      if (batchMode && nextTask) {
        pickTaskMutation.mutate(nextTask.id);
      }
      setLastThumbUrl(result.primaryMediaUrl ?? null);
      setHistory((h) => [
        {
          barcode,
          variantId: result.variantId,
          sku: result.sku,
          name: result.name,
          success: true,
          message: result.message,
          putawayTarget: result.putawayTarget ?? undefined,
          primaryMediaUrl: result.primaryMediaUrl ?? null,
          timestamp: Date.now(),
        },
        ...h.slice(0, 19),
      ]);
      setSerialCapture(null);
    },
    onError: (_err, barcode) => {
      triggerError();
      setLastThumbUrl(null);
      setHistory((h) => [
        {
          barcode,
          success: false,
          message: 'Item not found or scan failed',
          timestamp: Date.now(),
        },
        ...h.slice(0, 19),
      ]);
    },
  });

  const serialScanMutation = useMutation({
    mutationFn: async (serialNumber: string) => {
      if (!serialCapture) return;
      return submitScan(serialCapture.barcode, serialNumber);
    },
    onSuccess: (result) => {
      if (!result || !serialCapture) return;
      if (result.requiresSerial) return;
      triggerSuccess();
      if ('vibrate' in navigator) navigator.vibrate([40, 30, 40, 30, 40]);
      const serial = serialCapture.captured.length + 1;
      setLastThumbUrl(result.primaryMediaUrl ?? null);
      setHistory((h) => [
        {
          barcode: serialCapture.barcode,
          sku: result.sku,
          name: result.name,
          success: true,
          message: `Serial captured (${serial})`,
          primaryMediaUrl: result.primaryMediaUrl ?? null,
          timestamp: Date.now(),
        },
        ...h.slice(0, 19),
      ]);
      setSerialCapture(null);
    },
    onError: () => triggerError(),
  });

  useHardwareScanner({
    enabled: true,
    captureAll: true,
    onScan: (code) => {
      if (!code.length) return;
      if (serialCapture) {
        serialScanMutation.mutate(code);
      } else {
        scanMutation.mutate(code);
      }
    },
  });

  return (
    <div className="flex min-h-full flex-col p-4 pb-8" data-theme="warehouse">
      <ScanFlashOverlay flash={flash} />

      <div className="mb-6 text-center">
        <div className="mb-2 flex items-center justify-center gap-2">
          <ScanLine className="h-6 w-6 text-accent" />
          <h1 className="text-2xl font-bold text-text">Fulfillment</h1>
        </div>
        <p className="text-sm text-text-muted">
          {warehouse?.name ?? 'No warehouse selected'} ·{' '}
          {serialCapture
            ? 'Serial capture'
            : batchMode
              ? 'Batch pick'
              : `Scan to ${mode}`}
        </p>
      </div>

      {serialCapture && (
        <Card className="mb-6 border-accent bg-accent-muted p-4">
          <p className="text-xs font-medium uppercase tracking-wide text-accent">Serial capture</p>
          <p className="mt-1 font-semibold text-text">{serialCapture.name}</p>
          <p className="font-mono text-sm text-text-muted">{serialCapture.sku}</p>
          <p className="mt-3 text-sm text-text">
            Scan each serial number — {serialCapture.captured.length} captured
          </p>
          <Input
            className="mt-3"
            placeholder="Scan or type serial..."
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                const value = (e.target as HTMLInputElement).value.trim();
                if (value) {
                  serialScanMutation.mutate(value);
                  (e.target as HTMLInputElement).value = '';
                }
              }
            }}
          />
          <BigButton variant="secondary" className="mt-3 w-full" onClick={() => setSerialCapture(null)}>
            Cancel
          </BigButton>
        </Card>
      )}

      {!serialCapture && (
        <>
          <div className="mb-4 flex gap-3">
            <BigButton
              variant={!batchMode && !packingMode ? 'primary' : 'secondary'}
              onClick={() => {
                setBatchMode(false);
                setPackingMode(false);
              }}
            >
              Single
            </BigButton>
            <BigButton
              variant={batchMode ? 'primary' : 'secondary'}
              onClick={() => {
                setBatchMode(true);
                setPackingMode(false);
                void queryClient.invalidateQueries({ queryKey: ['picking', 'batch-tasks'] });
              }}
            >
              Batch
            </BigButton>
            <BigButton
              variant={packingMode ? 'primary' : 'secondary'}
              onClick={() => {
                setPackingMode(true);
                setBatchMode(false);
                setLabelMessage('');
              }}
            >
              Pack
            </BigButton>
          </div>

          {packingMode && (
            <Card className="mb-6 border-accent bg-accent-muted p-4">
              <div className="flex items-center gap-2">
                <Scale className="h-5 w-5 text-accent" />
                <p className="font-medium text-text">Packing & shipping label</p>
              </div>
              <Select
                className="mt-3"
                label="Sales order"
                value={packSalesOrderId}
                onChange={(e) => setPackSalesOrderId(e.target.value)}
              >
                <option value="">Select order to ship…</option>
                {packOrders
                  .filter((o) => ['CONFIRMED', 'ALLOCATED', 'PARTIALLY_SHIPPED'].includes(o.status))
                  .map((o) => (
                    <option key={o.id} value={o.id}>
                      {o.number} — {o.customerName}
                    </option>
                  ))}
              </Select>
              {!scale.supported ? (
                <p className="mt-2 text-sm text-text-muted">
                  Web Bluetooth is unavailable — enter weight manually or use a supported browser.
                </p>
              ) : (
                <div className="mt-3 space-y-3">
                  <p className="text-sm text-text-muted">
                    {scale.connected
                      ? `Scale connected · ${scale.reading?.rawValue ?? 'Awaiting reading...'}`
                      : 'Connect a shipping scale to auto-read parcel weight.'}
                  </p>
                  {scale.error && <p className="text-sm text-danger">{scale.error}</p>}
                  <div className="flex flex-wrap gap-2">
                    {!scale.connected ? (
                      <Button loading={scale.connecting} onClick={() => void scale.connect()}>
                        Connect scale
                      </Button>
                    ) : (
                      <Button variant="secondary" onClick={scale.disconnect}>
                        Disconnect
                      </Button>
                    )}
                    <Button
                      loading={packLabelMutation.isPending}
                      disabled={!packSalesOrderId}
                      onClick={() => {
                        const weightLb = scale.reading?.weightLb ?? 0;
                        if (weightLb <= 0) {
                          setLabelMessage('No weight reading — step on the scale or connect hardware.');
                          return;
                        }
                        packLabelMutation.mutate(weightLb);
                      }}
                    >
                      Generate label
                    </Button>
                  </div>
                  {labelMessage && <p className="text-sm text-success">{labelMessage}</p>}
                </div>
              )}
            </Card>
          )}

          {!batchMode && !packingMode && (
            <div className="mb-6 flex gap-3" role="radiogroup" aria-label="Scan mode">
              <BigButton
                variant={mode === 'pick' ? 'primary' : 'secondary'}
                role="radio"
                aria-checked={mode === 'pick'}
                onClick={() => setMode('pick')}
              >
                Pick
              </BigButton>
              <BigButton
                variant={mode === 'receive' ? 'success' : 'secondary'}
                role="radio"
                aria-checked={mode === 'receive'}
                onClick={() => setMode('receive')}
              >
                Receive
              </BigButton>
            </div>
          )}

          {batchMode && !packingMode && (
            <Card className="mb-6 border-accent bg-accent-muted p-4">
              {nextTask ? (
                <>
                  <p className="text-xs font-medium uppercase tracking-wide text-accent">Next bin</p>
                  <p className="mt-2 font-mono text-4xl font-bold leading-tight text-text sm:text-5xl">
                    {nextTask.locationPath.split('/').pop()}
                  </p>
                  <LocationBreadcrumb locationPath={nextTask.locationPath} className="mt-3" />
                  {nextTask.zone && (
                    <p className="mt-1 text-sm font-medium text-accent">Zone {nextTask.zone}</p>
                  )}
                  <p className="mt-1 text-sm text-text-muted">
                    Stop {nextTask.sequenceOrder} of {batchTasks.length}
                  </p>
                  <div className="mt-4 space-y-2">
                    <p className="text-xs font-medium uppercase tracking-wide text-text-muted">Optimized route</p>
                    {batchTasks.map((task) => (
                      <div
                        key={task.id}
                        className={cn(
                          'rounded px-2 py-2 text-sm transition-colors duration-200',
                          task.status === 'PICKED' && 'text-text-muted line-through opacity-60',
                          task.id === nextTask.id && 'bg-accent/20'
                        )}
                      >
                        <div className="mb-1 flex items-center justify-between gap-2">
                          <span className="font-mono text-xs text-text-muted">Stop {task.sequenceOrder}</span>
                          <span className="text-xs uppercase tracking-wide">{task.status}</span>
                        </div>
                        <LocationBreadcrumb locationPath={task.locationPath} />
                      </div>
                    ))}
                  </div>
                </>
              ) : (
                <div>
                  <p className="text-sm text-text-muted">
                    No released batch on the floor. Generate a draft wave (path optimizer runs in
                    background), then release it when ready.
                  </p>
                  <WaveReleaseControls onReleased={() => void refetchTasks()} />
                  <p className="mt-2 text-xs text-text-muted">
                    Tip: switch to Single pick if you already know the bin.
                  </p>
                </div>
              )}
            </Card>
          )}
        </>
      )}

      <Card className="mb-6 text-center" padding="lg" data-testid="scan-buffer-card">
        <p className="text-sm text-text-muted">Last scan</p>
        <div className="mt-3 flex items-center justify-center gap-4">
          {(lastThumbUrl || history[0]?.success) && (
            <VariantThumb
              url={lastThumbUrl ?? history[0]?.primaryMediaUrl}
              alt={history[0]?.name ?? history[0]?.sku ?? 'Scanned item'}
              size="lg"
            />
          )}
          <p className="font-mono text-2xl font-bold text-text">
            {lastScan ?? 'Ready to scan'}
          </p>
        </div>
        {(scanMutation.isPending || serialScanMutation.isPending) && (
          <p className="mt-2 text-sm text-accent">Processing...</p>
        )}
        {mode === 'receive' && history[0]?.success && history[0]?.variantId && (
          <div className="mt-4 text-left" data-testid="receive-qc-photo">
            <MediaPicker
              kind="EVIDENCE"
              label="QC / damage photo"
              capture
              webrtc
              presignType="TRANSACTION"
              onUploaded={async (result) => {
                await apiClient.post('/api/v1/media/transactions', {
                  entityType: 'RECEIPT',
                  entityId: history[0]!.variantId,
                  url: result.contentUrl,
                });
                await apiClient.post('/api/v1/media/attachments', {
                  mediaObjectId: result.id,
                  entityType: 'PRODUCT_VARIANT',
                  entityId: history[0]!.variantId,
                  purpose: 'QC_DAMAGE',
                });
              }}
            />
          </div>
        )}
      </Card>

      <div className="flex-1 space-y-2">
        <h2 className="text-sm font-medium text-text-muted">Recent scans</h2>
        {history.length === 0 ? (
          <p className="py-8 text-center text-sm text-text-muted">Scan a barcode to get started</p>
        ) : (
          history.map((item) => (
            <div
              key={item.timestamp}
              className={cn(
                'flex items-center gap-3 rounded-lg border p-4',
                item.success
                  ? 'border-success/30 bg-success/5'
                  : 'border-danger/30 bg-danger/5'
              )}
            >
              {item.success ? (
                <VariantThumb url={item.primaryMediaUrl} alt={item.name ?? item.sku ?? item.barcode} size="md" />
              ) : (
                <div
                  className="h-2.5 w-2.5 shrink-0 rounded-full bg-danger"
                  aria-hidden
                />
              )}
              <div className="min-w-0 flex-1">
                <p className="truncate font-mono font-medium text-text">{item.sku ?? item.barcode}</p>
                {item.name && <p className="truncate text-sm text-text-muted">{item.name}</p>}
                <p className={cn('text-xs', item.success ? 'text-text-muted' : 'text-danger')}>
                  {item.message}
                </p>
                {mode === 'receive' && item.putawayTarget && (
                  <p className="mt-1 text-xs font-medium text-accent">
                    Putaway target: {item.putawayTarget.replace(/\//g, ' / ')}
                  </p>
                )}
              </div>
            </div>
          ))
        )}
      </div>

      <UndoToast
        visible={!!pendingMisScan}
        message={pendingMisScan?.message ?? ''}
        onUndo={undoMisScan}
        onDismiss={() => void useWarehouseUXStore.getState().commitMisScan()}
      />
    </div>
  );
}
