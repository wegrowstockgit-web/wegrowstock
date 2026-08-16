import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Keyboard } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';

export interface HardwareManualFallbackProps {
  isSupported: boolean;
  mode: 'weight' | 'scan';
  bluetoothSupported?: boolean;
  serialSupported?: boolean;
  onConnectBluetooth?: () => void;
  onConnectSerial?: () => void;
  onManualSubmit?: (value: string) => void;
  className?: string;
  /** Inverse labels for the warehouse scanner card. */
  tone?: 'default' | 'inverse';
}

/**
 * Hides Web Bluetooth / Web Serial connect actions when the browser lacks those
 * APIs (Safari, Firefox). Scan mode keeps Keyboard Entry as the last-resort path.
 */
export function HardwareManualFallback({
  isSupported,
  mode,
  bluetoothSupported = false,
  serialSupported = false,
  onConnectBluetooth,
  onConnectSerial,
  onManualSubmit,
  className,
  tone = 'default',
}: HardwareManualFallbackProps) {
  const [value, setValue] = useState('');
  const [manualOpen, setManualOpen] = useState(mode === 'weight');
  const inputRef = useRef<HTMLInputElement>(null);
  const showBluetooth = isSupported && bluetoothSupported && Boolean(onConnectBluetooth);
  const showSerial = isSupported && serialSupported && Boolean(onConnectSerial);

  useEffect(() => {
    if (manualOpen) {
      inputRef.current?.focus();
    }
  }, [manualOpen]);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const trimmed = value.trim();
    if (!trimmed) return;
    onManualSubmit?.(trimmed);
    setValue('');
  };

  return (
    <div className={className} data-testid="hardware-fallback-root">
      {showBluetooth || showSerial ? (
        <div className="mb-2 flex flex-wrap gap-2" data-testid="hardware-connect-actions">
          {showBluetooth ? (
            <Button type="button" size="sm" variant="secondary" onClick={onConnectBluetooth}>
              Connect Bluetooth Scale
            </Button>
          ) : null}
          {showSerial ? (
            <Button type="button" size="sm" variant="secondary" onClick={onConnectSerial}>
              Connect USB Scanner
            </Button>
          ) : null}
        </div>
      ) : null}

      {mode === 'scan' && !manualOpen ? (
        <Button
          type="button"
          size="sm"
          variant="secondary"
          className="w-full active:scale-[0.97]"
          onClick={() => setManualOpen(true)}
          data-testid="scanner-keyboard-entry"
        >
          <Keyboard className="h-4 w-4" />
          Keyboard Entry
        </Button>
      ) : (
        <form onSubmit={submit} data-testid="hardware-manual-fallback" data-mode={mode}>
          <Input
            ref={inputRef}
            label={mode === 'weight' ? 'Manual weight' : 'Manual scan'}
            value={value}
            onChange={(event) => setValue(event.target.value)}
            placeholder={mode === 'weight' ? 'e.g. 12.50' : 'Type SKU or barcode'}
            inputMode={mode === 'weight' ? 'decimal' : 'text'}
            autoComplete="off"
            autoCorrect="off"
            autoFocus={mode === 'scan'}
            spellCheck={false}
            enterKeyHint="done"
            tone={tone}
            data-testid={mode === 'scan' ? 'scanner-manual-input' : undefined}
          />
        </form>
      )}
    </div>
  );
}
