import { Camera, ScanLine } from 'lucide-react';
import { HardwareManualFallback } from '@/components/hardware/HardwareManualFallback';
import { Button } from '@/components/ui/Button';
import { WebRtcCamera } from '@/components/ui/WebRtcCamera';
import { useBarcodeScanner } from '@/hooks/useBarcodeScanner';
import { useScanFeedback } from '@/hooks/useScanFeedback';
import { cn } from '@/lib/utils';

/**
 * Universal floor scanner: hardware wedge → device camera → keyboard entry.
 */
export function BarcodeScannerInput({
  label,
  hint,
  enabled = true,
  onScan,
  lastScan,
  className,
}: {
  label: string;
  hint?: string;
  enabled?: boolean;
  onScan: (barcode: string) => void;
  lastScan?: string | null;
  className?: string;
}) {
  const { triggerSuccess } = useScanFeedback();
  const { hardwareStatus, triggerCamera, setTriggerCamera, ingestScan } = useBarcodeScanner({
    enabled,
    captureAll: true,
    onScan: (barcode) => {
      if (!barcode) return;
      triggerSuccess();
      onScan(barcode);
    },
  });

  const hardwareReady = hardwareStatus === 'CONNECTED';
  const showCameraCta = !hardwareReady && !triggerCamera;

  const commit = (barcode: string, source: 'camera' | 'manual') => {
    ingestScan(barcode, source);
  };

  return (
    <div
      className={cn(
        'rounded-2xl border-4 border-accent bg-black px-4 py-6 text-center text-white shadow-elevated',
        className,
      )}
      data-testid="barcode-scanner-input"
      data-tour="tour-inbound-scanner"
      data-hardware-status={hardwareStatus}
    >
      <div className="mb-3 flex justify-center">
        {hardwareReady ? (
          <span
            className="inline-flex items-center gap-1.5 rounded-full bg-success/20 px-3 py-1 text-xs font-semibold text-success ring-1 ring-success/40"
            data-testid="scanner-status-badge"
          >
            <span className="h-1.5 w-1.5 rounded-full bg-success" aria-hidden />
            Scanner Ready
          </span>
        ) : (
          <span
            className="inline-flex items-center gap-1.5 rounded-full bg-warning/20 px-3 py-1 text-xs font-semibold text-warning ring-1 ring-warning/40"
            data-testid="scanner-status-badge"
          >
            <span className="h-1.5 w-1.5 rounded-full bg-warning" aria-hidden />
            {hardwareStatus === 'UNSUPPORTED' ? 'No hardware scanner' : 'Scanner disconnected'}
          </span>
        )}
      </div>
      <ScanLine className="mx-auto mb-3 h-12 w-12 text-accent" aria-hidden />
      <p className="text-xs font-bold uppercase tracking-[0.2em] text-accent">{label}</p>
      {hint && <p className="mt-2 text-sm text-white/70">{hint}</p>}
      <p
        className="mt-4 break-all font-mono text-2xl font-black tracking-wide text-white"
        data-testid="scanner-last-value"
      >
        {lastScan || 'Waiting for scan…'}
      </p>

      {showCameraCta && (
        <Button
          type="button"
          size="lg"
          className="mt-5 w-full active:scale-[0.97]"
          onClick={() => setTriggerCamera(true)}
          data-testid="scanner-camera-trigger"
        >
          <Camera className="h-5 w-5" />
          Tap to Use Device Camera
        </Button>
      )}

      {triggerCamera && (
        <div className="mt-5">
          <WebRtcCamera
            autoStart
            onBarcode={(barcode) => {
              commit(barcode, 'camera');
              setTriggerCamera(false);
            }}
            onCancel={() => setTriggerCamera(false)}
          />
        </div>
      )}

      <HardwareManualFallback
        isSupported={false}
        mode="scan"
        className="mt-5 text-left"
        tone="inverse"
        onManualSubmit={(value) => commit(value, 'manual')}
      />
    </div>
  );
}
