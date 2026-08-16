import { useEffect, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useSearchParams } from 'react-router-dom';
import { AlertTriangle, ScanLine, Scale, Settings2 } from 'lucide-react';
import { apiClient } from '@/api/client';
import type {
  FulfillmentScanResponse,
  CartonizePreviewResponse,
  MoveLpnResult,
  PackLabelResponse,
  PaginatedResponse,
  PickingTask,
  ProductVariant,
  SalesOrder,
} from '@/api/types';
import { useDigitalScale } from '@/hooks/useDigitalScale';
import { usePackingScale } from '@/hooks/usePackingScale';
import { useHardwareScanner } from '@/hooks/useHardwareScanner';
import { useScanFeedback } from '@/hooks/useScanFeedback';
import { useScanBufferStore } from '@/stores/scanBuffer';
import { useActiveWarehouseStore } from '@/stores/activeWarehouse';
import { useSessionStore } from '@/stores/session';
import { useOfflineStore } from '@/stores/offlineStore';
import { useVariantCacheStore } from '@/stores/variantCacheStore';
import { cn, generateIdempotencyKey } from '@/lib/utils';
import { evaluateLotGrace, validatePickScan, type ParsedBarcode } from '@/utils/gs1Parser';
import { enqueueMutation } from '@/offline/mutationQueue';
import { createScanEventPayload } from '@/offline/scanEvent';
import { BigButton } from '@/components/ui/BigButton';
import { Button } from '@/components/ui/Button';
import { ScanFlashOverlay } from '@/components/ui/ScanFlashOverlay';
import {
  CrossDockOverlay,
  type CrossDockPrompt,
} from '@/features/fulfillment/CrossDockOverlay';
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
import {
  PalletBuilder,
  mintAndPrintLpn,
  packScanOntoLpn,
  type MintedLpn,
} from '@/features/fulfillment/PalletBuilder';
import { WayfindingMiniMap } from '@/features/fulfillment/WayfindingMiniMap';
import { QuarantineReview } from '@/features/fulfillment/QuarantineReview';
import { RateShoppingWidget } from '@/features/fulfillment/RateShoppingWidget';
import {
  ReplenishmentBadge,
  ReplenishmentQueue,
} from '@/features/fulfillment/ReplenishmentQueue';
import { useWarehouseUXStore } from '@/stores/warehouseUX';
import { usePrintStore } from '@/stores/usePrintStore';
import { ScannerSettings } from '@/features/settings/ScannerSettings';

function isStagingLocationBarcode(code: string, prompt: CrossDockPrompt): boolean {
  const normalized = code.trim().toUpperCase();
  const path = (prompt.stagingPath ?? '').toUpperCase();
  const codePart = path.includes('/') ? path.slice(path.lastIndexOf('/') + 1) : path;
  return (
    normalized === path ||
    normalized === codePart ||
    normalized === 'S-01' ||
    normalized === 'Z-SHIP/S-01' ||
    normalized === 'WH-01/Z-SHIP/S-01' ||
    normalized.endsWith('/S-01')
  );
}

function WaveReleaseControls({ onReleased }: { onReleased: () => void }) {
  const canManage = useSessionStore((s) => s.hasRole('OWNER', 'ADMIN', 'WAREHOUSE_MANAGER'));
  const canClaim = useSessionStore((s) => s.hasRole('OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER'));
  const [draftWaveId, setDraftWaveId] = useState<string | null>(null);
  const [releasedWaveId, setReleasedWaveId] = useState<string | null>(null);

  const [manifestPreview, setManifestPreview] = useState<string[]>([]);

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

  const optimizeMutation = useMutation({
    mutationFn: async () => {
      const res = await apiClient.post<{
        waveId: string;
        status: string;
        manifest: Array<{ sequenceOrder: number; locationPath: string }>;
      }>('/api/v1/picking/waves/optimize', {});
      return res.data;
    },
    onSuccess: (data) => {
      setDraftWaveId(data.waveId);
      setManifestPreview(data.manifest.map((m) => `${m.sequenceOrder}. ${m.locationPath}`));
    },
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
          className="min-h-11 touch-target"
          loading={generateMutation.isPending}
          onClick={() => generateMutation.mutate()}
        >
          Generate draft wave
        </Button>
      )}
      {canManage && (
        <Button
          size="sm"
          variant="secondary"
          className="min-h-11 touch-target"
          loading={optimizeMutation.isPending}
          onClick={() => optimizeMutation.mutate()}
        >
          Optimize pick path
        </Button>
      )}
      {canManage && draftWaveId && (
        <Button
          size="sm"
          className="min-h-11 touch-target"
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
          className="min-h-11 touch-target"
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
      {manifestPreview.length > 0 && (
        <div className="w-full rounded-lg border border-border/60 bg-surface-raised p-3 text-xs text-text-muted">
          <p className="mb-1 font-medium text-text">Optimized path</p>
          <ol className="list-decimal space-y-1 pl-4">
            {manifestPreview.slice(0, 8).map((line) => (
              <li key={line}>{line.replace(/^\d+\.\s*/, '')}</li>
            ))}
          </ol>
        </div>
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

type FloorMode = 'pick' | 'receive' | 'lpn' | 'pallet';

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
  const { flash, triggerSuccess, triggerError, triggerPendingSync, triggerExceptionHaptic } =
    useScanFeedback();
  const [skipFlagPending, setSkipFlagPending] = useState(false);
  const quarantineCount = useOfflineStore((s) => s.quarantinedMutations.length);
  const [history, setHistory] = useState<ScannerHistoryItem[]>([]);
  const [lastThumbUrl, setLastThumbUrl] = useState<string | null>(null);
  const [mode, setMode] = useState<FloorMode>('pick');
  const [lpnBarcodePending, setLpnBarcodePending] = useState<string | null>(null);
  const [activePallet, setActivePallet] = useState<MintedLpn | null>(null);
  const [palletItemCount, setPalletItemCount] = useState(0);
  const [palletMinting, setPalletMinting] = useState(false);
  const [palletPacking, setPalletPacking] = useState(false);
  const [lastPackedSku, setLastPackedSku] = useState<string | null>(null);
  const [crossDockPrompt, setCrossDockPrompt] = useState<CrossDockPrompt | null>(null);
  const crossDockPromptRef = useRef<CrossDockPrompt | null>(null);
  crossDockPromptRef.current = crossDockPrompt;
  const [batchMode, setBatchMode] = useState(false);
  const [serialCapture, setSerialCapture] = useState<SerialCaptureState | null>(null);
  const [packingMode, setPackingMode] = useState(false);
  const [packSalesOrderId, setPackSalesOrderId] = useState('');
  const [manualWeightLb, setManualWeightLb] = useState('');
  const [labelMessage, setLabelMessage] = useState('');
  const [lastPackLabel, setLastPackLabel] = useState<PackLabelResponse | null>(null);
  const [showQuarantine, setShowQuarantine] = useState(false);
  const [showReplenishment, setShowReplenishment] = useState(false);
  const [showScannerSettings, setShowScannerSettings] = useState(false);
  const executePrint = usePrintStore((s) => s.executePrint);
  const setBoundPrinterName = usePrintStore((s) => s.setBoundPrinterName);
  const [mintLotPending, setMintLotPending] = useState(false);
  const [gs1Fields, setGs1Fields] = useState<Gs1FieldState>(EMPTY_GS1);
  const [gs1Active, setGs1Active] = useState(false);
  const [lotLoggedNotTracked, setLotLoggedNotTracked] = useState(false);
  const lastParsedRef = useRef<ParsedBarcode | null>(null);
  /** Survives plain (non-GS1) rescans so minted / typed lots still bind on receive. */
  const activeLotRef = useRef<string>('');
  const gs1FieldsRef = useRef(gs1Fields);
  gs1FieldsRef.current = gs1Fields;
  const gs1FeedbackPendingRef = useRef(false);
  const scale = useDigitalScale();
  const packingScale = usePackingScale();
  const pendingMisScan = useWarehouseUXStore((s) => s.pendingMisScan);
  const bufferMisScan = useWarehouseUXStore((s) => s.bufferMisScan);
  const undoMisScan = useWarehouseUXStore((s) => s.undoMisScan);
  const nextBestAction = useWarehouseUXStore((s) => s.nextBestAction);
  const nextBestActionFresh = useWarehouseUXStore((s) => s.nextBestActionFresh);
  const fetchNextBestAction = useWarehouseUXStore((s) => s.fetchNextBestAction);
  const clearNextBestAction = useWarehouseUXStore((s) => s.clearNextBestAction);
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

  const { data: cartonPreview, isFetching: cartonPreviewLoading } = useQuery({
    queryKey: ['shipments', 'cartonize-preview', packSalesOrderId],
    queryFn: async () =>
      (await apiClient.get<CartonizePreviewResponse>('/api/v1/shipments/cartonize-preview', {
        params: { salesOrderId: packSalesOrderId },
      })).data,
    enabled: packingMode && !!packSalesOrderId,
    retry: false,
  });

  const { data: workstation } = useQuery({
    queryKey: ['users', 'me', 'workstation'],
    queryFn: async () =>
      (await apiClient.get<{ printMode: string; zplPrinterName?: string | null }>(
        '/api/v1/users/me/workstation',
      )).data,
    staleTime: 60_000,
    retry: false,
  });

  useEffect(() => {
    if (workstation?.zplPrinterName) {
      setBoundPrinterName(workstation.zplPrinterName);
    }
  }, [workstation?.zplPrinterName, setBoundPrinterName]);

  const packLabelMutation = useMutation({
    mutationFn: async (totalWeightLb: number) => {
      const res = await apiClient.post<PackLabelResponse>('/api/v1/shipments/pack-label', {
        salesOrderId: packSalesOrderId,
        totalWeightLb,
      });
      return res.data;
    },
    onSuccess: (label) => {
      triggerSuccess();
      setLastPackLabel(label);
      const carton = label.cartonName ? `Use Box: ${label.cartonName}` : 'Carton selected';
      const carrier = [label.carrier, label.serviceLevel].filter(Boolean).join(' ');
      setLabelMessage(
        `${carton} · ${carrier} · Tracking ${label.trackingNumber} · ${label.totalWeight?.toFixed?.(2) ?? label.totalWeight} lb · $${Number(label.postageAmount ?? 0).toFixed(2)}`,
      );
      const format =
        (label.labelFileType ?? workstation?.printMode ?? 'PDF').toUpperCase() === 'ZPL'
          ? 'ZPL'
          : 'PDF';
      if (label.labelRef) {
        void executePrint(label.labelRef, format).then((route) => {
          setLabelMessage((msg) => `${msg} · printed via ${route}`);
        });
      }
    },
    onError: () => {
      triggerError();
      setLastPackLabel(null);
      setLabelMessage('Could not cartonize / purchase label. Check order lines and carton masters.');
    },
  });

  const resolvePackWeightLb = (): number | null => {
    if (packingScale.stableWeightLb != null && packingScale.stableWeightLb > 0) {
      return packingScale.stableWeightLb;
    }
    const scaleLb = scale.reading?.weightLb ?? 0;
    if (scaleLb > 0) return scaleLb;
    const manual = Number(manualWeightLb);
    if (Number.isFinite(manual) && manual > 0) return manual;
    if (cartonPreview?.billableWeightLb && cartonPreview.billableWeightLb > 0) {
      return Number(cartonPreview.billableWeightLb);
    }
    return null;
  };

  const autoLabelKeyRef = useRef<string | null>(null);
  const packLabelMutate = packLabelMutation.mutate;
  const packLabelPending = packLabelMutation.isPending;
  useEffect(() => {
    if (!packingMode || !packSalesOrderId || !cartonPreview || packLabelPending) {
      return;
    }
    const weightLb =
      packingScale.stableWeightLb ??
      (scale.connected && scale.reading?.stable ? scale.reading.weightLb : null);
    if (weightLb == null || !(weightLb > 0)) {
      return;
    }
    const key = `${packSalesOrderId}:${weightLb.toFixed(2)}`;
    if (autoLabelKeyRef.current === key) {
      return;
    }
    autoLabelKeyRef.current = key;
    packLabelMutate(weightLb);
  }, [
    packingMode,
    packSalesOrderId,
    cartonPreview,
    packingScale.stableWeightLb,
    scale.connected,
    scale.reading?.stable,
    scale.reading?.weightLb,
    packLabelPending,
    packLabelMutate,
  ]);

  const {
    data: batchTasks = [],
    isLoading: batchTasksLoading,
    isError: batchTasksError,
    refetch: refetchTasks,
  } = useQuery({
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
  const previousPicked = [...batchTasks]
    .filter((t) => t.status === 'PICKED' && t.locationId)
    .sort((a, b) => b.sequenceOrder - a.sequenceOrder)[0];
  const wayfindingFromId =
    previousPicked?.locationId ?? warehouse?.id ?? nextTask?.locationId ?? null;

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

  const showMissingVendorLot = (() => {
    if (mode !== 'receive') return false;
    const latest = history[0];
    if (!latest?.success || !latest.variantId) return false;
    const cached = lookupVariant(latest.variantId);
    const lotTracked = latest.isLotTracked === true || cached?.isLotTracked === true;
    if (!lotTracked) return false;
    const lotFromFields = gs1Fields.lotNumber.trim();
    const lotFromParse = lastParsedRef.current?.lotNumber?.trim() ?? '';
    return !lotFromFields && !lotFromParse;
  })();

  const pickTaskMutation = useMutation({
    mutationFn: async (input: { taskId: string; locationId?: string | null }) => {
      await apiClient.post(`/api/v1/picking/tasks/${input.taskId}/pick`);
      return input;
    },
    onSuccess: (result) => {
      void refetchTasks();
      if (result.locationId) {
        void fetchNextBestAction(result.locationId);
      }
    },
  });

  const moveLpnMutation = useMutation({
    mutationFn: async (input: { lpnBarcode: string; destinationBarcode: string }) => {
      const res = await apiClient.post<MoveLpnResult>('/api/v1/inventory/lpns/move', {
        lpnBarcode: input.lpnBarcode,
        destinationBarcode: input.destinationBarcode,
      });
      return res.data;
    },
    onSuccess: (result) => {
      triggerSuccess();
      setLpnBarcodePending(null);
      setHistory((h) => [
        {
          barcode: result.lpnBarcode,
          success: true,
          message: `LPN moved · ${result.linesMoved} line${result.linesMoved === 1 ? '' : 's'}`,
          timestamp: Date.now(),
        },
        ...h.slice(0, 19),
      ]);
      if (result.destinationLocationId) {
        void fetchNextBestAction(result.destinationLocationId);
      }
    },
    onError: (_err, vars) => {
      triggerError();
      setHistory((h) => [
        {
          barcode: vars.destinationBarcode,
          success: false,
          message: 'LPN move failed — check LPN and destination bin',
          timestamp: Date.now(),
        },
        ...h.slice(0, 19),
      ]);
    },
  });

  const handleMintPallet = async () => {
    setPalletMinting(true);
    try {
      const minted = await mintAndPrintLpn(warehouse?.id, executePrint);
      setActivePallet(minted);
      setPalletItemCount(0);
      setLastPackedSku(null);
      triggerSuccess();
      setHistory((h) => [
        {
          barcode: minted.lpnBarcode,
          success: true,
          message: 'LPN minted · pallet label sent to printer',
          timestamp: Date.now(),
        },
        ...h.slice(0, 19),
      ]);
    } catch {
      triggerError();
    } finally {
      setPalletMinting(false);
    }
  };

  const handlePackOntoPallet = async (scan: string) => {
    if (!activePallet) return;
    setPalletPacking(true);
    try {
      const result = await packScanOntoLpn(activePallet.lpnBarcode, scan);
      setPalletItemCount(result.itemCount);
      setLastPackedSku(scan);
      triggerSuccess();
      setHistory((h) => [
        {
          barcode: scan,
          success: true,
          message: `Packed onto ${result.lpnBarcode} · ${result.itemCount} line${result.itemCount === 1 ? '' : 's'} on pallet`,
          timestamp: Date.now(),
        },
        ...h.slice(0, 19),
      ]);
    } catch {
      triggerError();
      setHistory((h) => [
        {
          barcode: scan,
          success: false,
          message: 'Pack failed — no loose stock for this scan',
          timestamp: Date.now(),
        },
        ...h.slice(0, 19),
      ]);
    } finally {
      setPalletPacking(false);
    }
  };

  const buildScanPayload = (barcode: string, serialNumber?: string): FulfillmentScanPayload => {
    const parsed = lastParsedRef.current;
    const qtyParsed = gs1Fields.quantity.trim() ? Number(gs1Fields.quantity) : undefined;
    const lot =
      gs1Fields.lotNumber.trim() ||
      activeLotRef.current.trim() ||
      parsed?.lotNumber;
    const scanMode: 'pick' | 'receive' =
      serialCapture?.mode ?? (mode === 'receive' ? 'receive' : 'pick');
    const payload: FulfillmentScanPayload = {
      barcode,
      warehouseId: warehouse?.id,
      mode: scanMode,
      serialNumber,
    };
    // Minted internal lots and GS1 AI(10) both ride on lotNumber for receive binding.
    if (lot) payload.lotNumber = lot;
    if (parsed?.isGs1 || gs1Active) {
      payload.isGs1 = true;
      payload.gtin = parsed?.sku ?? barcode;
      payload.rawBarcode = parsed?.raw;
      const expiry = gs1Fields.expiryDate.trim() || parsed?.expiryDate;
      const quantity =
        qtyParsed != null && Number.isFinite(qtyParsed) ? qtyParsed : parsed?.quantity;
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

  const handleMintInternalLot = async () => {
    const latest = history[0];
    if (!latest?.variantId) return;
    setMintLotPending(true);
    try {
      const res = await apiClient.post<{
        id: string;
        lotNumber: string;
        variantId: string;
        sku: string;
        zpl: string;
      }>('/api/v1/inventory/lots/mint', { variantId: latest.variantId });
      const minted = res.data;
      activeLotRef.current = minted.lotNumber;
      setGs1Active(true);
      setGs1Fields((prev) => ({ ...prev, lotNumber: minted.lotNumber }));
      // Keep the SKU in the scan buffer; lot rides in gs1Fields / activeLotRef for submit.
      if (latest.sku || lastScan) {
        useScanBufferStore.getState().commit(latest.sku ?? lastScan!);
      }
      if (minted.zpl) {
        await executePrint(minted.zpl, 'ZPL');
      }
      triggerSuccess();
    } catch {
      triggerError();
    } finally {
      setMintLotPending(false);
    }
  };

  const submitScan = async (barcode: string, serialNumber?: string): Promise<FulfillmentScanResponse> => {
    const scanEvent = createScanEventPayload(barcode);
    const payload = buildScanPayload(barcode, serialNumber);

    if (!navigator.onLine) {
      const queuedHistory = {
        sku: barcode,
        name: barcode,
        requiresSerial: false,
        message: 'Queued for sync',
      };
      triggerPendingSync();
      bufferMisScan({
        barcode,
        message: `Scan queued — undo within 5s`,
        mutation: {
          idempotencyKey: scanEvent.idempotencyKey,
          scannedAt: scanEvent.scannedAt,
          scanEvent,
          method: 'POST',
          url: '/api/v1/fulfillment/scan',
          body: payload,
        },
      });
      return queuedHistory as FulfillmentScanResponse;
    }

    const res = await apiClient.post<FulfillmentScanResponse>('/api/v1/fulfillment/scan', payload, {
      headers: { 'Idempotency-Key': scanEvent.idempotencyKey },
    });
    return res.data;
  };

  const scanMutation = useMutation({
    mutationFn: (barcode: string) => submitScan(barcode),
    onSuccess: (result, barcode) => {
      if (result.requiresSerial && !serialCapture) {
        const captureMode: 'pick' | 'receive' = mode === 'receive' ? 'receive' : 'pick';
        setSerialCapture({
          barcode,
          sku: result.sku,
          name: result.name,
          mode: captureMode,
          captured: [],
          required: 1,
        });
        return;
      }
      // GS1 path already flashed on parse — avoid double green flash.
      // Success haptic/audio comes solely from triggerSuccess (vibrate(50)).
      if (!gs1FeedbackPendingRef.current) {
        triggerSuccess();
      }
      gs1FeedbackPendingRef.current = false;
      if (batchMode && nextTask) {
        pickTaskMutation.mutate({
          taskId: nextTask.id,
          locationId: nextTask.locationId,
        });
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
      if (result.crossDock && (result.stagingPath || result.crossDockInstruction)) {
        setCrossDockPrompt({
          sku: result.sku,
          stagingPath: result.stagingPath ?? result.putawayTarget ?? 'WH-01/Z-SHIP/S-01',
          stagingLocationId: result.stagingLocationId ?? undefined,
          salesOrderNumber: result.crossDockSalesOrderNumber ?? undefined,
          instruction:
            result.crossDockInstruction ??
            result.message ??
            'Route item directly to Shipping Staging Lane',
        });
      } else if (mode === 'receive') {
        setCrossDockPrompt(null);
        // After putaway / receive commit, interleave the closest floor task.
        if (warehouse?.id) {
          void fetchNextBestAction(warehouse.id);
        }
      }
      setHistory((h) => [
        {
          barcode,
          variantId: result.variantId,
          sku: result.sku,
          name: result.name,
          success: true,
          message: result.message,
          // Cross-dock never shows reserve put-away bins
          putawayTarget: result.crossDock
            ? undefined
            : (result.putawayTarget ?? undefined),
          primaryMediaUrl: result.primaryMediaUrl ?? null,
          lotNumber: gs1Fields.lotNumber || undefined,
          expiryDate: gs1Fields.expiryDate || undefined,
          quantity: gs1Fields.quantity ? Number(gs1Fields.quantity) : undefined,
          lotLoggedNotTracked: logged,
          isLotTracked: !!result.isLotTracked,
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
      const lotNumber = parsed.lotNumber ?? '';
      activeLotRef.current = lotNumber;
      setGs1Fields({
        lotNumber,
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
      // Staging bin confirm while cross-dock overlay is active — do not treat as product scan.
      const activeCrossDock = crossDockPromptRef.current;
      if (activeCrossDock && isStagingLocationBarcode(code, activeCrossDock)) {
        triggerSuccess();
        setHistory((h) => [
          {
            barcode: code,
            sku: activeCrossDock.sku,
            name: 'Cross-dock staging confirmed',
            success: true,
            message: `Drop-off confirmed at ${activeCrossDock.stagingPath}`,
            timestamp: Date.now(),
          },
          ...h.slice(0, 19),
        ]);
        setCrossDockPrompt(null);
        return;
      }
      if (mode === 'lpn') {
        clearNextBestAction();
        if (!lpnBarcodePending) {
          setLpnBarcodePending(code.trim().toUpperCase());
          triggerSuccess();
          useScanBufferStore.getState().commit(code);
          return;
        }
        moveLpnMutation.mutate({
          lpnBarcode: lpnBarcodePending,
          destinationBarcode: code.trim(),
        });
        useScanBufferStore.getState().commit(code);
        return;
      }
      if (mode === 'pallet') {
        useScanBufferStore.getState().commit(code);
        if (!activePallet) {
          triggerError();
          return;
        }
        void handlePackOntoPallet(code.trim());
        return;
      }
      if (parsed && !parsed.isGs1) {
        lastParsedRef.current = null;
        setLotLoggedNotTracked(false);
        gs1FeedbackPendingRef.current = false;
        // Preserve minted/manual lot across SKU rescans (do not wipe INT-* escape hatch).
        const keepLot =
          gs1FieldsRef.current.lotNumber.trim() || activeLotRef.current.trim();
        if (keepLot) {
          activeLotRef.current = keepLot;
          setGs1Active(true);
          setGs1Fields({ lotNumber: keepLot, expiryDate: '', quantity: '' });
        } else {
          activeLotRef.current = '';
          setGs1Active(false);
          setGs1Fields(EMPTY_GS1);
        }
      }
      // Strict client-side GS1 / SKU pre-validation against the expected pick allocation.
      if (batchMode && mode === 'pick' && nextTask && !serialCapture) {
        const expected = {
          sku: nextTask.sku,
          barcode: nextTask.barcode,
          quantity: nextTask.quantity != null ? Number(nextTask.quantity) : null,
        };
        const parsedForCheck =
          parsed ??
          ({
            sku: code,
            isGs1: false,
          } satisfies ParsedBarcode);
        const check = validatePickScan(expected, parsedForCheck, code);
        if (!check.ok) {
          triggerError();
          setHistory((h) => [
            {
              barcode: code,
              sku: parsedForCheck.sku || code,
              name: 'Scan rejected',
              success: false,
              message: check.message ?? 'Does not match expected pick',
              timestamp: Date.now(),
            },
            ...h.slice(0, 19),
          ]);
          useScanBufferStore.getState().commit(code);
          return;
        }
      }
      if (serialCapture) {
        serialScanMutation.mutate(code);
      } else {
        scanMutation.mutate(code);
      }
    },
  });

  return (
    <div
      className="flex min-h-full flex-col p-4 pb-8"
      data-theme="warehouse"
      data-tour="fulfillment-scan"
      data-testid="fulfillment-page"
    >
      <ScanFlashOverlay flash={flash} />

      <div className="mb-6 text-center">
        <div className="mb-2 flex items-center justify-center gap-2">
          <ScanLine className="h-6 w-6 text-accent" />
          <h1 className="text-2xl font-bold text-text">Fulfillment</h1>
          <Button
            type="button"
            variant="secondary"
            size="sm"
            className="ml-1"
            aria-label="Scanner settings"
            data-testid="scanner-settings-open"
            onClick={() => setShowScannerSettings(true)}
          >
            <Settings2 className="h-4 w-4" />
          </Button>
        </div>
        <p className="text-sm text-text-muted">
          {warehouse?.name ?? 'No warehouse selected'} ·{' '}
          {serialCapture
            ? 'Serial capture'
            : batchMode
              ? 'Batch pick'
              : mode === 'lpn'
                ? lpnBarcodePending
                  ? 'Scan destination bin'
                  : 'Scan LPN barcode'
                : mode === 'pallet'
                  ? activePallet
                    ? 'Scan items onto pallet'
                    : 'Mint an LPN to begin'
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
        <div className="mx-auto mt-3 w-full max-w-md">
          <ReplenishmentBadge onOpen={() => setShowReplenishment(true)} />
        </div>
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

      {showReplenishment ? (
        <div className="mb-6">
          <ReplenishmentQueue onClose={() => setShowReplenishment(false)} />
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
                setMode('pick');
                setLpnBarcodePending(null);
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
                setLpnBarcodePending(null);
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
                onChange={(e) => {
                  setPackSalesOrderId(e.target.value);
                  setLabelMessage('');
                  setLastPackLabel(null);
                  setManualWeightLb('');
                }}
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

              {packSalesOrderId && (
                <div className="mt-3 rounded-md border border-border bg-surface px-3 py-2">
                  {cartonPreviewLoading && (
                    <p className="text-sm text-text-muted">Calculating best carton…</p>
                  )}
                  {!cartonPreviewLoading && cartonPreview && (
                    <>
                      <p className="text-sm font-semibold text-text">
                        Use Box: {cartonPreview.cartonName}
                      </p>
                      <p className="mt-1 text-xs text-text-muted">
                        {Number(cartonPreview.lengthIn)}×{Number(cartonPreview.widthIn)}×
                        {Number(cartonPreview.heightIn)} in · billable{' '}
                        {Number(cartonPreview.billableWeightLb).toFixed(2)} lb
                        (actual {Number(cartonPreview.actualWeightLb).toFixed(2)} / dim{' '}
                        {Number(cartonPreview.volumetricWeightLb).toFixed(2)})
                      </p>
                    </>
                  )}
                  {!cartonPreviewLoading && !cartonPreview && (
                    <p className="text-sm text-danger">
                      Could not cartonize this order — check variant dimensions and carton masters.
                    </p>
                  )}
                  {packSalesOrderId && cartonPreview && (
                    <div className="mt-3">
                      <RateShoppingWidget
                        salesOrderId={packSalesOrderId}
                        cartonId={cartonPreview.cartonId}
                        onLabelPurchased={(tracking) => {
                          setLabelMessage(
                            tracking
                              ? `Label purchased via rate shop · ${tracking}`
                              : 'Label purchased via rate shop',
                          );
                          void queryClient.invalidateQueries({ queryKey: ['shipments'] });
                        }}
                      />
                    </div>
                  )}
                </div>
              )}

              <div className="mt-3 space-y-3">
                {!packingScale.isSupported && !scale.isSupported ? (
                  <p className="text-sm text-text-muted">
                    Web Serial / Bluetooth unavailable — using carton billable weight, or enter a
                    manual override.
                  </p>
                ) : (
                  <>
                    <p className="text-sm text-text-muted" data-testid="packing-scale-status">
                      {packingScale.connected
                        ? `Packing scale (Serial)${
                            packingScale.reading?.stable ? ' · stable' : ''
                          } · ${packingScale.reading?.rawValue ?? 'Awaiting reading...'}`
                        : scale.connected
                          ? `Scale connected (${scale.transport ?? 'edge'})${
                              scale.reading?.stable ? ' · stable' : ''
                            } · ${scale.reading?.rawValue ?? 'Awaiting reading...'}`
                          : 'Connect packing scale (Serial 9600). Stable weight auto-buys the label.'}
                    </p>
                    {(packingScale.error || scale.error) && (
                      <p className="text-sm text-danger">{packingScale.error ?? scale.error}</p>
                    )}
                    <div className="flex flex-wrap gap-2">
                      {!packingScale.connected && !scale.connected ? (
                        <>
                          {packingScale.serialSupported && (
                            <Button
                              loading={packingScale.connecting}
                              onClick={() => void packingScale.connect()}
                              data-testid="packing-scale-connect"
                            >
                              Connect packing scale
                            </Button>
                          )}
                          {scale.bluetoothSupported && (
                            <Button
                              variant="secondary"
                              loading={scale.connecting}
                              onClick={() => void scale.connectBluetooth()}
                            >
                              Connect Bluetooth scale
                            </Button>
                          )}
                        </>
                      ) : (
                        <Button
                          variant="secondary"
                          onClick={() => {
                            packingScale.disconnect();
                            scale.disconnect();
                          }}
                        >
                          Disconnect
                        </Button>
                      )}
                    </div>
                  </>
                )}

                <Input
                  type="number"
                  min="0"
                  step="0.01"
                  label="Weight override (lb)"
                  value={manualWeightLb}
                  onChange={(e) => setManualWeightLb(e.target.value)}
                  placeholder={
                    cartonPreview
                      ? String(Number(cartonPreview.billableWeightLb).toFixed(2))
                      : 'Optional'
                  }
                />

                <Button
                  loading={packLabelMutation.isPending}
                  disabled={!packSalesOrderId || !cartonPreview}
                  onClick={() => {
                    const weightLb = resolvePackWeightLb();
                    if (weightLb == null || weightLb <= 0) {
                      setLabelMessage('Enter a weight, connect a scale, or wait for cartonization.');
                      return;
                    }
                    packLabelMutation.mutate(weightLb);
                  }}
                >
                  Complete Pack
                </Button>

                {lastPackLabel && (
                  <div className="rounded-md border border-success/40 bg-surface px-3 py-2 text-sm">
                    <p className="font-semibold text-text">
                      Use Box: {lastPackLabel.cartonName ?? cartonPreview?.cartonName ?? '—'}
                    </p>
                    <p className="mt-1 text-text">
                      {[lastPackLabel.carrier, lastPackLabel.serviceLevel].filter(Boolean).join(' ') ||
                        'Carrier selected'}
                    </p>
                    <p className="mt-1 text-text-muted">
                      Tracking {lastPackLabel.trackingNumber ?? '—'} · postage $
                      {Number(lastPackLabel.postageAmount ?? 0).toFixed(2)}
                    </p>
                  </div>
                )}
                {labelMessage && !lastPackLabel && (
                  <p className="text-sm text-danger">{labelMessage}</p>
                )}
                {labelMessage && lastPackLabel && (
                  <p className="text-sm text-success">{labelMessage}</p>
                )}
              </div>
            </Card>
          )}

          {!batchMode && !packingMode && (
            <div className="mb-6 flex flex-wrap gap-3" role="radiogroup" aria-label="Scan mode">
              <BigButton
                variant={mode === 'pick' ? 'primary' : 'secondary'}
                role="radio"
                aria-checked={mode === 'pick'}
                onClick={() => {
                  setMode('pick');
                  setLpnBarcodePending(null);
                }}
              >
                Pick
              </BigButton>
              <BigButton
                variant={mode === 'receive' ? 'success' : 'secondary'}
                role="radio"
                aria-checked={mode === 'receive'}
                onClick={() => {
                  setMode('receive');
                  setLpnBarcodePending(null);
                }}
              >
                Receive
              </BigButton>
              <BigButton
                variant={mode === 'lpn' ? 'primary' : 'secondary'}
                role="radio"
                aria-checked={mode === 'lpn'}
                data-testid="lpn-move-mode"
                onClick={() => {
                  setMode('lpn');
                  setLpnBarcodePending(null);
                  clearNextBestAction();
                }}
              >
                LPN Move
              </BigButton>
              <BigButton
                variant={mode === 'pallet' ? 'primary' : 'secondary'}
                role="radio"
                aria-checked={mode === 'pallet'}
                data-testid="build-pallet-mode"
                onClick={() => {
                  setMode('pallet');
                  setLpnBarcodePending(null);
                  clearNextBestAction();
                }}
              >
                Build Pallet
              </BigButton>
            </div>
          )}

          <PalletBuilder
            active={mode === 'pallet' && !batchMode && !packingMode}
            activeLpn={activePallet}
            itemCount={palletItemCount}
            packing={palletPacking}
            minting={palletMinting}
            lastPackedSku={lastPackedSku}
            onMint={() => void handleMintPallet()}
            onClear={() => {
              setActivePallet(null);
              setPalletItemCount(0);
              setLastPackedSku(null);
            }}
          />

          {nextBestAction && (
            <Card
              className={cn(
                'mb-6 border-2 border-accent bg-accent-muted p-4',
                nextBestActionFresh && 'ring-2 ring-accent ring-offset-2',
              )}
              data-testid="next-best-action"
            >
              <p className="text-xs font-bold uppercase tracking-wide text-accent">
                Next interleaved task · {nextBestAction.taskType}
                {nextBestActionFresh ? ' · go now' : ''}
              </p>
              <p className="mt-2 text-xl font-bold text-text">
                {nextBestAction.instruction ?? nextBestAction.summary}
              </p>
              {nextBestAction.locationPath && (
                <LocationBreadcrumb
                  locationPath={nextBestAction.locationPath}
                  className="mt-2"
                />
              )}
              {nextBestAction.toteIdentifier && (
                <p className="mt-3 text-lg font-black text-accent">
                  PLACE IN TOTE: {nextBestAction.toteIdentifier}
                </p>
              )}
              <p className="mt-1 text-xs text-text-muted">
                {nextBestAction.summary}
                {nextBestAction.travelScore != null
                  ? ` · score ${Math.round(nextBestAction.travelScore)}`
                  : ''}
              </p>
              <Button
                size="sm"
                variant="secondary"
                className="mt-3 min-h-11"
                onClick={() => clearNextBestAction()}
              >
                Dismiss
              </Button>
            </Card>
          )}

          {batchMode && !packingMode && (
            <Card className="mb-6 border-accent bg-accent-muted p-4">
              {batchTasksLoading && (
                <p className="text-sm text-text-muted" data-testid="list-page-loading">
                  Loading batch tasks…
                </p>
              )}
              {batchTasksError && !batchTasksLoading && (
                <div className="space-y-3" data-testid="list-page-error">
                  <p className="text-sm text-text">Unable to load batch tasks.</p>
                  <Button size="sm" onClick={() => void refetchTasks()} data-testid="list-page-retry">
                    Retry
                  </Button>
                </div>
              )}
              {!batchTasksLoading && !batchTasksError && !nextTask && (
                <p className="text-sm text-text-muted" data-testid="list-page-empty">
                  No pending picks in the current batch.
                </p>
              )}
              {nextTask ? (
                <>
                  <p className="text-xs font-medium uppercase tracking-wide text-accent">Next bin</p>
                  <p className="mt-2 font-mono text-4xl font-bold leading-tight text-text sm:text-5xl">
                    {nextTask.locationPath.split('/').pop()}
                  </p>
                  <LocationBreadcrumb locationPath={nextTask.locationPath} className="mt-3" />
                  {nextTask.toteIdentifier && (
                    <div
                      className="mt-4 rounded-xl border-4 border-accent bg-accent px-3 py-5 text-center"
                      data-testid="batch-place-in-tote"
                    >
                      <p className="text-xs font-bold uppercase tracking-[0.2em] text-text-inverse/90">
                        Place in tote
                      </p>
                      <p className="mt-1 text-4xl font-black text-text-inverse sm:text-5xl">
                        {nextTask.toteIdentifier}
                      </p>
                    </div>
                  )}
                  {nextTask.zone && (
                    <p className="mt-1 text-sm font-medium text-accent">Zone {nextTask.zone}</p>
                  )}
                  <p className="mt-1 text-sm text-text-muted">
                    Stop {nextTask.sequenceOrder} of {batchTasks.length}
                  </p>
                  <WayfindingMiniMap
                    fromLocationId={wayfindingFromId}
                    toLocationId={nextTask.locationId}
                    destinationLabel={nextTask.locationPath.split('/').pop()}
                  />
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

      {crossDockPrompt && (
        <CrossDockOverlay
          prompt={crossDockPrompt}
          awaitingStagingScan
          onDismiss={() => setCrossDockPrompt(null)}
        />
      )}

      <ScannerView
        lastScan={lastScan}
        lastThumbUrl={lastThumbUrl}
        history={history}
        scanning={
          scanMutation.isPending ||
          serialScanMutation.isPending ||
          moveLpnMutation.isPending
        }
        mode={mode}
        feedbackFlash={flash}
        gs1Active={gs1Active}
        gs1Fields={gs1Fields}
        onGs1FieldsChange={(fields) => {
          activeLotRef.current = fields.lotNumber.trim();
          setGs1Fields(fields);
        }}
        lotLoggedNotTracked={lotLoggedNotTracked}
        showSkipFlag={lotMissingForTrackedPick}
        skipFlagPending={skipFlagPending}
        onSkipFlag={() => void handleSkipFlag()}
        showMissingVendorLot={showMissingVendorLot}
        mintLotPending={mintLotPending}
        onMintInternalLot={() => void handleMintInternalLot()}
        lpnBarcodePending={lpnBarcodePending}
        toteIdentifier={
          batchMode && mode === 'pick' ? (nextTask?.toteIdentifier ?? null) : null
        }
        onThumbCaptured={(url, variantId) => {
          setLastThumbUrl(url);
          setHistory((h) =>
            h.map((item) =>
              item.variantId === variantId ? { ...item, primaryMediaUrl: url } : item,
            ),
          );
        }}
        receiveQcSlot={
          mode === 'receive' &&
          !crossDockPrompt &&
          history[0]?.success &&
          history[0]?.variantId ? (
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

      <ScannerSettings
        open={showScannerSettings}
        onClose={() => setShowScannerSettings(false)}
      />
    </div>
  );
}
