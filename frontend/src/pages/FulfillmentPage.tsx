import { useEffect, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useSearchParams } from 'react-router-dom';
import { AlertTriangle, ScanLine, Scale } from 'lucide-react';
import { apiClient } from '@/api/client';
import type {
  FulfillmentScanResponse,
  PackLabelResponse,
  PaginatedResponse,
  PickingTask,
  ProductVariant,
  SalesOrder,
} from '@/api/types';
import { useBluetoothScale } from '@/hooks/useBluetoothScale';
import { useHardwareScanner } from '@/hooks/useHardwareScanner';
import { useScanFeedback } from '@/hooks/useScanFeedback';
import { useScanBufferStore } from '@/stores/scanBuffer';
import { useActiveWarehouseStore } from '@/stores/activeWarehouse';
import { useSessionStore } from '@/stores/session';
import { useOfflineStore } from '@/stores/offlineStore';
import { useVariantCacheStore } from '@/stores/variantCacheStore';
import { cn, generateIdempotencyKey } from '@/lib/utils';
import { evaluateLotGrace, type ParsedBarcode } from '@/utils/gs1Parser';
import { enqueueMutation } from '@/offline/mutationQueue';
import { BigButton } from '@/components/ui/BigButton';
import { Button } from '@/components/ui/Button';
import { ScanFlashOverlay } from '@/components/ui/ScanFlashOverlay';
import { Card } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { LocationBreadcrumb } from '@/components/ui/LocationBreadcrumb';
import { UndoToast } from '@/components/ui/UndoToast';
import {
  ReceiveQcPhotoSlot,
  ScannerView,
  type Gs1FieldState,
  type ScannerHistoryItem,
} from '@/features/fulfillment/ScannerView';
import { QuarantineReview } from '@/features/fulfillment/QuarantineReview';
import { useWarehouseUXStore } from '@/stores/warehouseUX';

function WaveReleaseControls({ onReleased }: { onReleased: () => void }) {
  const canManage = useSessionStore((s) => s.hasRole('OWNER', 'ADMIN', 'WAREHOUSE_MANAGER'));
  const canClaim = useSessionStore((s) => s.hasRole('OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER'));
  const [draftWaveId, setDraftWaveId] = useState<string | null>(null);
  const [releasedWaveId, setReleasedWaveId] = useState<string | null>(null);

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
      return waveId;
    },
    onSuccess: (waveId) => {
      setDraftWaveId(null);
      setReleasedWaveId(waveId);
      onReleased();
    },
  });

  const claimMutation = useMutation({
    mutationFn: async (waveId: string) => {
      const res = await apiClient.post<{ waveId: string; allocationsClaimed: number }>(
        `/api/v1/picking/waves/${waveId}/claim`,
      );
      return res.data;
    },
  });

  if (!canManage && !canClaim) return null;

  return (
    <div className="mt-3 flex flex-wrap gap-2">
      {canManage && (
        <Button
          size="sm"
          variant="secondary"
          loading={generateMutation.isPending}
          onClick={() => generateMutation.mutate()}
        >
          Generate draft wave
        </Button>
      )}
      {canManage && draftWaveId && (
        <Button
          size="sm"
          loading={releaseMutation.isPending}
          onClick={() => releaseMutation.mutate(draftWaveId)}
        >
          Release to floor
        </Button>
      )}
      {canClaim && releasedWaveId && (
        <Button
          size="sm"
          variant="secondary"
          loading={claimMutation.isPending}
          onClick={() => claimMutation.mutate(releasedWaveId)}
        >
          Claim wave (device lock)
        </Button>
      )}
      {claimMutation.isSuccess && (
        <p className="w-full text-xs text-success">
          Locked {claimMutation.data.allocationsClaimed} allocation
          {claimMutation.data.allocationsClaimed === 1 ? '' : 's'} to this device
        </p>
      )}
    </div>
  );
}

interface SerialCaptureState {
  barcode: string;
  sku: string;
  name: string;
  mode: 'pick' | 'receive';
  captured: string[];
  required: number;
}

const EMPTY_GS1: Gs1FieldState = { lotNumber: '', expiryDate: '', quantity: '' };

/** Offline / online scan body with client-decoded GS1 fields so the API need not re-parse. */
export interface FulfillmentScanPayload {
  barcode: string;
  warehouseId: string | undefined;
  mode: 'pick' | 'receive';
  serialNumber?: string;
  gtin?: string;
  lotNumber?: string;
  expiryDate?: string;
  quantity?: number;
  isGs1?: boolean;
  rawBarcode?: string;
  metadata?: Record<string, string>;
}

export function FulfillmentPage() {
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const warehouse = useActiveWarehouseStore((s) => s.warehouse);
  const lastScan = useScanBufferStore((s) => s.lastScan);
  const clearScanCard = useScanBufferStore((s) => s.clearScanCard);
  const { flash, triggerSuccess, triggerError, triggerExceptionHaptic } = useScanFeedback();
  const [skipFlagPending, setSkipFlagPending] = useState(false);
  const quarantineCount = useOfflineStore((s) => s.quarantinedMutations.length);
  const [history, setHistory] = useState<ScannerHistoryItem[]>([]);
  const [lastThumbUrl, setLastThumbUrl] = useState<string | null>(null);
  const [mode, setMode] = useState<'pick' | 'receive'>('pick');
  const [batchMode, setBatchMode] = useState(false);
  const [serialCapture, setSerialCapture] = useState<SerialCaptureState | null>(null);
  const [packingMode, setPackingMode] = useState(false);
  const [packSalesOrderId, setPackSalesOrderId] = useState('');
  const [labelMessage, setLabelMessage] = useState('');
  const [showQuarantine, setShowQuarantine] = useState(false);
  const [gs1Fields, setGs1Fields] = useState<Gs1FieldState>(EMPTY_GS1);
  const [gs1Active, setGs1Active] = useState(false);
  const [lotLoggedNotTracked, setLotLoggedNotTracked] = useState(false);
  const lastParsedRef = useRef<ParsedBarcode | null>(null);
  const gs1FeedbackPendingRef = useRef(false);
  const scale = useBluetoothScale();
  const pendingMisScan = useWarehouseUXStore((s) => s.pendingMisScan);
  const bufferMisScan = useWarehouseUXStore((s) => s.bufferMisScan);
  const undoMisScan = useWarehouseUXStore((s) => s.undoMisScan);
  const upsertVariants = useVariantCacheStore((s) => s.upsertMany);
  const upsertVariant = useVariantCacheStore((s) => s.upsert);
  const lookupVariant = useVariantCacheStore((s) => s.lookup);

  useQuery({
    queryKey: ['variants', 'lot-cache'],
    queryFn: async () => {
      const res = await apiClient.get<PaginatedResponse<ProductVariant>>('/api/v1/variants?limit=200');
      upsertVariants(
        (res.data.items ?? []).map((v) => ({
          id: v.id,
          sku: v.sku,
          barcode: v.barcode,
          isLotTracked: !!v.isLotTracked,
        })),
      );
      return res.data;
    },
    staleTime: 60_000,
  });

  useEffect(() => {
    if (searchParams.get('quarantine') === '1') {
      setShowQuarantine(true);
    }
  }, [searchParams]);

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

  const lotMissingForTrackedPick = (() => {
    if (mode !== 'pick' || !batchMode || !nextTask?.allocationId) return false;
    const cached = nextTask.variantId ? lookupVariant(nextTask.variantId) : undefined;
    const requiresLot = nextTask.isLotTracked === true || cached?.isLotTracked === true;
    if (!requiresLot) return false;
    const lotFromFields = gs1Fields.lotNumber.trim();
    const lotFromParse = lastParsedRef.current?.lotNumber?.trim() ?? '';
    // Show after any scan attempt on this stop, or when GS1 decoded without AI 10.
    const attempted =
      !!lastScan || gs1Active || (lastParsedRef.current != null && lastParsedRef.current.isGs1);
    return attempted && !lotFromFields && !lotFromParse;
  })();

  const pickTaskMutation = useMutation({
    mutationFn: async (taskId: string) => {
      await apiClient.post(`/api/v1/picking/tasks/${taskId}/pick`);
    },
    onSuccess: () => void refetchTasks(),
  });

  const buildScanPayload = (barcode: string, serialNumber?: string): FulfillmentScanPayload => {
    const parsed = lastParsedRef.current;
    const qtyParsed = gs1Fields.quantity.trim() ? Number(gs1Fields.quantity) : undefined;
    const payload: FulfillmentScanPayload = {
      barcode,
      warehouseId: warehouse?.id,
      mode: serialCapture?.mode ?? mode,
      serialNumber,
    };
    if (parsed?.isGs1 || gs1Active) {
      payload.isGs1 = true;
      payload.gtin = parsed?.sku ?? barcode;
      payload.rawBarcode = parsed?.raw;
      const lot = gs1Fields.lotNumber.trim() || parsed?.lotNumber;
      const expiry = gs1Fields.expiryDate.trim() || parsed?.expiryDate;
      const quantity =
        qtyParsed != null && Number.isFinite(qtyParsed) ? qtyParsed : parsed?.quantity;
      if (lot) payload.lotNumber = lot;
      if (expiry) payload.expiryDate = expiry;
      if (quantity != null) payload.quantity = quantity;

      const cached =
        lookupVariant(parsed?.sku) ?? lookupVariant(barcode) ?? lookupVariant(payload.gtin);
      const grace = evaluateLotGrace(
        {
          sku: parsed?.sku ?? barcode,
          lotNumber: lot,
          isGs1: true,
        },
        cached?.isLotTracked,
      );
      if (grace.metadata) {
        payload.metadata = { ...payload.metadata, ...grace.metadata };
      }
    }
    return payload;
  };

  const submitScan = async (barcode: string, serialNumber?: string): Promise<FulfillmentScanResponse> => {
    const idempotencyKey = generateIdempotencyKey();
    const payload = buildScanPayload(barcode, serialNumber);

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
      // GS1 path already flashed on parse — avoid double green flash.
      if (!gs1FeedbackPendingRef.current) {
        triggerSuccess();
        if ('vibrate' in navigator) navigator.vibrate([30, 20, 30]);
      }
      gs1FeedbackPendingRef.current = false;
      if (batchMode && nextTask) {
        pickTaskMutation.mutate(nextTask.id);
      }
      if (result.variantId) {
        upsertVariant({
          id: result.variantId,
          sku: result.sku,
          barcode,
          isLotTracked: !!result.isLotTracked,
        });
      }
      const logged = !!result.lotLoggedNotTracked;
      if (logged) setLotLoggedNotTracked(true);
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
          lotNumber: gs1Fields.lotNumber || undefined,
          expiryDate: gs1Fields.expiryDate || undefined,
          quantity: gs1Fields.quantity ? Number(gs1Fields.quantity) : undefined,
          lotLoggedNotTracked: logged,
          timestamp: Date.now(),
        },
        ...h.slice(0, 19),
      ]);
      setSerialCapture(null);
    },
    onError: (_err, barcode) => {
      gs1FeedbackPendingRef.current = false;
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

  const handleSkipFlag = async () => {
    if (!nextTask?.allocationId || skipFlagPending) return;
    const allocationId = nextTask.allocationId;
    const taskId = nextTask.id;
    setSkipFlagPending(true);

    // Optimistic: advance pick path immediately.
    void queryClient.setQueryData<PickingTask[]>(['picking', 'batch-tasks'], (prev) =>
      (prev ?? []).map((t) => (t.id === taskId ? { ...t, status: 'SKIPPED' } : t)),
    );
    clearScanCard();
    setGs1Active(false);
    setGs1Fields(EMPTY_GS1);
    setLotLoggedNotTracked(false);
    lastParsedRef.current = null;
    triggerExceptionHaptic();
    setHistory((h) => [
      {
        barcode: lastScan ?? 'EXCEPTION',
        success: false,
        message: 'Skipped — damaged barcode flagged for office',
        timestamp: Date.now(),
      },
      ...h.slice(0, 19),
    ]);

    const body = {
      allocationId,
      metadata: {
        reason: 'DAMAGED_BARCODE',
        taskId,
        locationPath: nextTask.locationPath,
      },
    };
    const idempotencyKey = generateIdempotencyKey();

    try {
      if (!navigator.onLine) {
        await enqueueMutation({
          idempotencyKey,
          method: 'POST',
          url: '/api/v1/fulfillment/exceptions/report',
          body,
        });
      } else {
        await apiClient.post('/api/v1/fulfillment/exceptions/report', body, {
          headers: { 'Idempotency-Key': idempotencyKey },
        });
      }
      void refetchTasks();
    } catch {
      void refetchTasks();
    } finally {
      setSkipFlagPending(false);
    }
  };

  useHardwareScanner({
    enabled: true,
    captureAll: true,
    onGs1Scan: (parsed) => {
      lastParsedRef.current = parsed;
      setGs1Active(true);
      setGs1Fields({
        lotNumber: parsed.lotNumber ?? '',
        expiryDate: parsed.expiryDate ?? '',
        quantity: parsed.quantity != null ? String(parsed.quantity) : '',
      });
      const cached = lookupVariant(parsed.sku);
      const grace = evaluateLotGrace(parsed, cached?.isLotTracked);
      setLotLoggedNotTracked(grace.lotLoggedNotTracked);
      // Instant offline-capable feedback: never block velocity for unexpected lot AI.
      gs1FeedbackPendingRef.current = true;
      triggerSuccess();
    },
    onScan: (code, parsed) => {
      if (!code.length) return;
      if (parsed && !parsed.isGs1) {
        lastParsedRef.current = null;
        setGs1Active(false);
        setGs1Fields(EMPTY_GS1);
        setLotLoggedNotTracked(false);
        gs1FeedbackPendingRef.current = false;
      }
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
        {quarantineCount > 0 && (
          <button
            type="button"
            data-testid="fulfillment-quarantine-badge"
            className="mt-3 inline-flex min-h-12 items-center gap-2 rounded-lg border-2 border-danger bg-danger px-4 py-2 text-base font-bold text-white"
            onClick={() => setShowQuarantine(true)}
          >
            <AlertTriangle className="h-5 w-5" aria-hidden />
            {quarantineCount} quarantined scan{quarantineCount === 1 ? '' : 's'}
          </button>
        )}
      </div>

      {showQuarantine ? (
        <div className="mb-6">
          <QuarantineReview
            onClose={() => {
              setShowQuarantine(false);
              if (searchParams.get('quarantine')) {
                searchParams.delete('quarantine');
                setSearchParams(searchParams, { replace: true });
              }
            }}
          />
        </div>
      ) : null}

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

      <ScannerView
        lastScan={lastScan}
        lastThumbUrl={lastThumbUrl}
        history={history}
        scanning={scanMutation.isPending || serialScanMutation.isPending}
        mode={mode}
        feedbackFlash={flash}
        gs1Active={gs1Active}
        gs1Fields={gs1Fields}
        onGs1FieldsChange={setGs1Fields}
        lotLoggedNotTracked={lotLoggedNotTracked}
        showSkipFlag={lotMissingForTrackedPick}
        skipFlagPending={skipFlagPending}
        onSkipFlag={() => void handleSkipFlag()}
        onThumbCaptured={(url, variantId) => {
          setLastThumbUrl(url);
          setHistory((h) =>
            h.map((item) =>
              item.variantId === variantId ? { ...item, primaryMediaUrl: url } : item,
            ),
          );
        }}
        receiveQcSlot={
          mode === 'receive' && history[0]?.success && history[0]?.variantId ? (
            <ReceiveQcPhotoSlot variantId={history[0].variantId} />
          ) : undefined
        }
      />

      <UndoToast
        visible={!!pendingMisScan}
        message={pendingMisScan?.message ?? ''}
        onUndo={undoMisScan}
        onDismiss={() => void useWarehouseUXStore.getState().commitMisScan()}
      />
    </div>
  );
}
