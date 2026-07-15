import { useEffect, useRef, useState, type ReactNode } from 'react';
import { AlertTriangle, Camera, Loader2 } from 'lucide-react';
import { apiClient } from '@/api/client';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { MediaPicker } from '@/components/ui/MediaPicker';
import { VariantThumb } from '@/components/ui/VariantThumb';
import { uploadViaPresign } from '@/lib/mediaPresign';
import { compressImageForUpload } from '@/utils/imageCompression';
import { useScanFeedback, type ScanFeedbackType } from '@/hooks/useScanFeedback';
import { cn } from '@/lib/utils';

export interface ScannerHistoryItem {
  barcode: string;
  variantId?: string;
  sku?: string;
  name?: string;
  success: boolean;
  message: string;
  putawayTarget?: string;
  primaryMediaUrl?: string | null;
  lotNumber?: string;
  expiryDate?: string;
  quantity?: number;
  /** GS1 lot present but variant is not lot-tracked — logged to ledger metadata. */
  lotLoggedNotTracked?: boolean;
  timestamp: number;
}

export interface Gs1FieldState {
  lotNumber: string;
  expiryDate: string;
  quantity: string;
}

interface ScannerViewProps {
  lastScan: string | null;
  lastThumbUrl: string | null;
  history: ScannerHistoryItem[];
  scanning: boolean;
  mode: 'pick' | 'receive';
  onThumbCaptured: (url: string, variantId: string) => void;
  receiveQcSlot?: ReactNode;
  /** Auto-filled from client-side GS1 parse; editable for operator override. */
  gs1Fields?: Gs1FieldState;
  onGs1FieldsChange?: (fields: Gs1FieldState) => void;
  gs1Active?: boolean;
  /** Transient non-blocking warning when lot AI is sunk (not tracked). */
  lotLoggedNotTracked?: boolean;
  /**
   * Lot-tracked pick where GS1 failed to yield a lot — show Skip & Flag (56px+ glove target).
   */
  showSkipFlag?: boolean;
  skipFlagPending?: boolean;
  onSkipFlag?: () => void;
  /** Parent-driven acoustic/haptic flash — drives success enter / error shake. */
  feedbackFlash?: ScanFeedbackType;
}

/**
 * Floor scanner panel: last-scan buffer + GS1 Lot/Expiry/Qty card + HTML5
 * environment camera capture when the variant has no primary catalog image.
 */
export function ScannerView({
  lastScan,
  lastThumbUrl,
  history,
  scanning,
  mode,
  onThumbCaptured,
  receiveQcSlot,
  gs1Fields,
  onGs1FieldsChange,
  gs1Active = false,
  lotLoggedNotTracked = false,
  showSkipFlag = false,
  skipFlagPending = false,
  onSkipFlag,
  feedbackFlash = null,
}: ScannerViewProps) {
  const captureRef = useRef<HTMLInputElement>(null);
  const { triggerSuccess, triggerError } = useScanFeedback();
  const [capturing, setCapturing] = useState(false);
  const [phase, setPhase] = useState<'compressing' | 'uploading' | null>(null);
  const [motion, setMotion] = useState<'success' | 'error' | null>(null);
  const motionKeyRef = useRef(0);
  const latest = history[0];
  const needsCapture =
    !!latest?.success &&
    !!latest.variantId &&
    !(lastThumbUrl ?? latest.primaryMediaUrl);

  useEffect(() => {
    if (!feedbackFlash) return;
    motionKeyRef.current += 1;
    setMotion(feedbackFlash);
    const clear = window.setTimeout(() => setMotion(null), 320);
    return () => window.clearTimeout(clear);
  }, [feedbackFlash, latest?.timestamp]);

  const handleCapture = async (file: File | undefined) => {
    if (!file || !latest?.variantId) return;
    setCapturing(true);
    setPhase('compressing');
    try {
      // Local WebP resize before any network I/O — critical on weak floor Wi-Fi.
      const compressed = await compressImageForUpload(file);
      setPhase('uploading');
      const completed = await uploadViaPresign(compressed, 'PRODUCT', { compress: false });
      await apiClient.post(`/api/v1/products/variants/${latest.variantId}/media`, {
        url: completed.contentUrl,
        isPrimary: true,
      });
      triggerSuccess();
      onThumbCaptured(completed.contentUrl, latest.variantId);
    } catch {
      triggerError();
    } finally {
      setCapturing(false);
      setPhase(null);
      if (captureRef.current) captureRef.current.value = '';
    }
  };

  const patchGs1 = (patch: Partial<Gs1FieldState>) => {
    if (!gs1Fields || !onGs1FieldsChange) return;
    onGs1FieldsChange({ ...gs1Fields, ...patch });
  };

  const deckMotionClass =
    motion === 'success'
      ? 'scan-success-enter'
      : motion === 'error'
        ? 'scan-error-shake'
        : undefined;

  return (
    <Card className="mb-6 text-center" padding="lg" data-testid="scan-buffer-card" data-motion={motion ?? undefined}>
      <div
        key={motion ? `${motion}-${motionKeyRef.current}` : 'deck'}
        className={cn(deckMotionClass)}
        data-testid="scan-verification-deck"
      >
      <p className="text-sm text-text-muted">Last scan</p>
      <div
        className="mt-3 flex items-center justify-center gap-4"
        data-testid="scan-detail-target"
      >
        {(lastThumbUrl || latest?.success) && (
          <VariantThumb
            url={lastThumbUrl ?? latest?.primaryMediaUrl}
            alt={latest?.name ?? latest?.sku ?? 'Scanned item'}
            size="lg"
          />
        )}
        <p className="font-mono text-2xl font-bold text-text">{lastScan ?? 'Ready to scan'}</p>
      </div>
      {scanning && <p className="mt-2 text-sm text-accent">Processing...</p>}

      {gs1Active && gs1Fields && (
        <div
          className="mt-5 rounded-lg border-2 border-success/40 bg-success/10 p-4 text-left"
          data-testid="gs1-fields-card"
        >
          <div className="flex flex-wrap items-center gap-2">
            <p className="text-xs font-bold uppercase tracking-wide text-success">
              GS1 composite decoded
            </p>
            {lotLoggedNotTracked && (
              <span
                data-testid="lot-logged-badge"
                className="inline-flex items-center rounded-md border border-warning/50 bg-warning/15 px-2 py-0.5 text-xs font-semibold text-warning"
              >
                Lot Data Logged (Not Tracked)
              </span>
            )}
          </div>
          <div className="mt-3 grid gap-3 sm:grid-cols-3">
            <label className="block">
              <span className="mb-1 block text-sm font-medium text-text">Lot</span>
              <Input
                data-testid="gs1-lot"
                className="min-h-12 font-mono text-base"
                value={gs1Fields.lotNumber}
                onChange={(e) => patchGs1({ lotNumber: e.target.value })}
                placeholder="Batch / lot"
                autoComplete="off"
              />
            </label>
            <label className="block">
              <span className="mb-1 block text-sm font-medium text-text">Expiry</span>
              <Input
                data-testid="gs1-expiry"
                type="date"
                className="min-h-12 text-base"
                value={gs1Fields.expiryDate}
                onChange={(e) => patchGs1({ expiryDate: e.target.value })}
              />
            </label>
            <label className="block">
              <span className="mb-1 block text-sm font-medium text-text">Qty</span>
              <Input
                data-testid="gs1-qty"
                type="number"
                inputMode="numeric"
                min={0}
                step={1}
                className="min-h-12 font-mono text-base"
                value={gs1Fields.quantity}
                onChange={(e) => patchGs1({ quantity: e.target.value })}
                placeholder="Count"
              />
            </label>
          </div>
        </div>
      )}

      {needsCapture && (
        <div className="mt-4" data-testid="capture-product-image">
          <Button
            type="button"
            size="lg"
            className="w-full border-2 border-text bg-accent text-text-inverse hover:bg-accent-hover"
            disabled={capturing}
            onClick={() => captureRef.current?.click()}
          >
            {capturing ? (
              <Loader2 className="h-5 w-5 animate-spin" />
            ) : (
              <Camera className="h-5 w-5" />
            )}
            {phase === 'compressing'
              ? 'Compressing…'
              : phase === 'uploading'
                ? 'Uploading…'
                : 'Capture Product Image'}
          </Button>
          <input
            ref={captureRef}
            type="file"
            accept="image/*"
            capture="environment"
            className="sr-only"
            aria-hidden
            tabIndex={-1}
            onChange={(e) => void handleCapture(e.target.files?.[0])}
          />
        </div>
      )}

      {showSkipFlag && onSkipFlag && (
        <div className="mt-4">
          <button
            type="button"
            data-testid="skip-flag-barcode"
            disabled={skipFlagPending}
            onClick={onSkipFlag}
            className={cn(
              'flex w-full min-h-14 items-center justify-center gap-2 rounded-lg border-2 border-danger',
              'bg-danger px-4 py-3 text-base font-bold text-white shadow-sm',
              'active:scale-[0.98] disabled:opacity-60',
            )}
          >
            {skipFlagPending ? (
              <Loader2 className="h-5 w-5 animate-spin" aria-hidden />
            ) : (
              <AlertTriangle className="h-5 w-5" aria-hidden />
            )}
            Skip &amp; Flag Barcode
          </button>
          <p className="mt-1.5 text-xs text-text-muted">
            Damaged or unreadable lot label — shunt to office queue without dropping inventory.
          </p>
        </div>
      )}

      {mode === 'receive' && receiveQcSlot}

      <div className="mt-6 flex-1 space-y-2 text-left">
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
                  : 'border-danger/30 bg-danger/5',
              )}
            >
              {item.success ? (
                <VariantThumb
                  url={item.primaryMediaUrl}
                  alt={item.name ?? item.sku ?? item.barcode}
                  size="md"
                />
              ) : (
                <div className="h-2.5 w-2.5 shrink-0 rounded-full bg-danger" aria-hidden />
              )}
              <div className="min-w-0 flex-1">
                <p className="truncate font-mono font-medium text-text">{item.sku ?? item.barcode}</p>
                {item.name && <p className="truncate text-sm text-text-muted">{item.name}</p>}
                {item.lotLoggedNotTracked && (
                  <span
                    data-testid="history-lot-logged-badge"
                    className="mt-1 inline-flex rounded-md border border-warning/40 bg-warning/10 px-1.5 py-0.5 text-[11px] font-semibold text-warning"
                  >
                    Lot Data Logged (Not Tracked)
                  </span>
                )}
                {(item.lotNumber || item.expiryDate || item.quantity != null) && (
                  <p className="mt-0.5 font-mono text-xs text-text-muted">
                    {[
                      item.lotNumber && `Lot ${item.lotNumber}`,
                      item.expiryDate && `Exp ${item.expiryDate}`,
                      item.quantity != null && `Qty ${item.quantity}`,
                    ]
                      .filter(Boolean)
                      .join(' · ')}
                  </p>
                )}
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
      </div>
    </Card>
  );
}

/** Optional QC photo slot helper for receive mode (keeps MediaPicker wiring local). */
export function ReceiveQcPhotoSlot({
  variantId,
  onDone,
}: {
  variantId: string;
  onDone?: () => void;
}) {
  return (
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
            entityId: variantId,
            url: result.contentUrl,
          });
          await apiClient.post('/api/v1/media/attachments', {
            mediaObjectId: result.id,
            entityType: 'PRODUCT_VARIANT',
            entityId: variantId,
            purpose: 'QC_DAMAGE',
          });
          onDone?.();
        }}
      />
    </div>
  );
}
