import { useEffect, useRef, useState } from 'react';
import { ScanLine } from 'lucide-react';
import { useBarcodeScanner } from '@/hooks/useBarcodeScanner';
import { cn } from '@/lib/utils';

/**
 * High-contrast hardware wedge / Bluetooth scanner capture for Zebra & Honeywell.
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
  const [manual, setManual] = useState('');
  const inputRef = useRef<HTMLInputElement>(null);

  useBarcodeScanner({
    enabled,
    captureAll: true,
    onScan: (barcode) => {
      if (!barcode) return;
      onScan(barcode);
      setManual('');
    },
  });

  useEffect(() => {
    if (enabled) {
      inputRef.current?.focus();
    }
  }, [enabled, label]);

  return (
    <div
      className={cn(
        'rounded-2xl border-4 border-accent bg-black px-4 py-6 text-center text-white shadow-elevated',
        className,
      )}
      data-testid="barcode-scanner-input"
    >
      <ScanLine className="mx-auto mb-3 h-12 w-12 text-accent" aria-hidden />
      <p className="text-xs font-bold uppercase tracking-[0.2em] text-accent">{label}</p>
      {hint && <p className="mt-2 text-sm text-white/70">{hint}</p>}
      <p
        className="mt-4 break-all font-mono text-2xl font-black tracking-wide text-white"
        data-testid="scanner-last-value"
      >
        {lastScan || 'Waiting for scan…'}
      </p>
      <form
        className="mt-4"
        onSubmit={(e) => {
          e.preventDefault();
          const value = manual.trim();
          if (!value) return;
          onScan(value);
          setManual('');
        }}
      >
        <input
          ref={inputRef}
          type="text"
          inputMode="none"
          autoComplete="off"
          autoCorrect="off"
          spellCheck={false}
          value={manual}
          onChange={(e) => setManual(e.target.value)}
          aria-label={label}
          data-testid="scanner-manual-input"
          className="h-14 w-full rounded-xl border-2 border-white/40 bg-white/10 px-4 text-center font-mono text-xl text-white placeholder:text-white/40 focus:border-accent focus:outline-none"
          placeholder="Or type barcode + Enter"
        />
      </form>
    </div>
  );
}
