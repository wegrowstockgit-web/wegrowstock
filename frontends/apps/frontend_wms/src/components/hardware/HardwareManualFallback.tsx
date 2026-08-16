import { useState, type FormEvent } from 'react';
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
}

/**
 * Hides Web Bluetooth / Web Serial connect actions when the browser lacks those
 * APIs (Safari, Firefox) and offers a typed weight or scan-string fallback.
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
}: HardwareManualFallbackProps) {
  const [value, setValue] = useState('');
  const showBluetooth = isSupported && bluetoothSupported && Boolean(onConnectBluetooth);
  const showSerial = isSupported && serialSupported && Boolean(onConnectSerial);

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
      <form onSubmit={submit} data-testid="hardware-manual-fallback" data-mode={mode}>
        <Input
          label={mode === 'weight' ? 'Manual weight' : 'Manual scan'}
          value={value}
          onChange={(event) => setValue(event.target.value)}
          placeholder={mode === 'weight' ? 'e.g. 12.50' : 'Type scan string'}
          inputMode={mode === 'weight' ? 'decimal' : 'text'}
          autoComplete="off"
        />
      </form>
    </div>
  );
}
