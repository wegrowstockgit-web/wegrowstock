import { useEffect, useRef, useState, type ReactNode } from 'react';
import { AlertTriangle, Bluetooth, Printer, Scale, Usb } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Modal } from '@/components/ui/Modal';
import { useToast } from '@/components/ui/Toast';
import { useDigitalScale } from '@/hooks/useDigitalScale';
import {
  supportsWebBluetooth,
  supportsWebSerial,
  UNSUPPORTED_BROWSER_HARDWARE_MESSAGE,
} from '@/lib/hardwareCapabilities';
import { cn } from '@/lib/utils';
import { isAnyScaleConnected, useHardwareStore } from '@/stores/hardwareStore';
import { usePrintStore } from '@/stores/usePrintStore';

const BROWSER_CONNECT_TOAST =
  'Web Serial is not available in this browser. Please use Google Chrome or Microsoft Edge.';

function isChooserCancelled(err: unknown): boolean {
  if (err instanceof DOMException && err.name === 'NotFoundError') return true;
  return err instanceof Error && /cancel/i.test(err.message);
}

function StatusDot({ connected }: { connected: boolean }) {
  return (
    <span
      className={cn(
        'h-2.5 w-2.5 shrink-0 rounded-full',
        connected ? 'bg-success' : 'bg-text-muted/40',
      )}
      aria-hidden
    />
  );
}

/**
 * Always-mounted Device Manager. The dialog closes, but the Web Serial /
 * Bluetooth session stays alive so pack-station weight can keep streaming.
 */
export function DeviceManagerModal() {
  const { toast } = useToast();
  const scale = useDigitalScale();
  const managerOpen = useHardwareStore((s) => s.managerOpen);
  const closeManager = useHardwareStore((s) => s.closeManager);
  const bluetoothScale = useHardwareStore((s) => s.bluetoothScale);
  const serialScale = useHardwareStore((s) => s.serialScale);
  const qzTray = useHardwareStore((s) => s.qzTray);
  const setBluetoothScale = useHardwareStore((s) => s.setBluetoothScale);
  const setSerialScale = useHardwareStore((s) => s.setSerialScale);
  const setQzTray = useHardwareStore((s) => s.setQzTray);
  const setScaleReading = useHardwareStore((s) => s.setScaleReading);
  const scaleDisconnectToken = useHardwareStore((s) => s.scaleDisconnectToken);
  const agentStatus = usePrintStore((s) => s.agentStatus);
  const connectAgent = usePrintStore((s) => s.connectAgent);
  const [serialBusy, setSerialBusy] = useState(false);
  const [bluetoothBusy, setBluetoothBusy] = useState(false);
  const [qzBusy, setQzBusy] = useState(false);
  const hadScaleLink = useRef(false);

  const serialApi = supportsWebSerial();
  const bluetoothApi = supportsWebBluetooth();
  const scaleApisMissing = !serialApi || !bluetoothApi;

  useEffect(() => {
    const bluetoothConnected = scale.transport === 'bluetooth' && scale.connected;
    const serialConnected = scale.transport === 'serial' && scale.connected;
    setBluetoothScale({
      connected: bluetoothConnected,
      deviceName: bluetoothConnected ? scale.bluetoothDeviceName : null,
    });
    setSerialScale({
      connected: serialConnected,
      portInfo: serialConnected ? 'USB serial · 9600 baud' : null,
    });
    setScaleReading(scale.reading);
  }, [
    scale.bluetoothDeviceName,
    scale.connected,
    scale.reading,
    scale.transport,
    setBluetoothScale,
    setScaleReading,
    setSerialScale,
  ]);

  useEffect(() => {
    setQzTray({
      connected: agentStatus === 'connected',
      version: agentStatus === 'connected' ? 'QZ Tray' : null,
    });
  }, [agentStatus, setQzTray]);

  const disconnectScale = scale.disconnect;
  useEffect(() => {
    if (scaleDisconnectToken === 0) return;
    disconnectScale();
  }, [disconnectScale, scaleDisconnectToken]);

  useEffect(() => {
    const linked = isAnyScaleConnected({ bluetoothScale, serialScale });
    if (hadScaleLink.current && !linked) {
      toast('Scale disconnected. Reopen Device Manager to reconnect.', { tone: 'danger' });
    }
    hadScaleLink.current = linked;
  }, [bluetoothScale, serialScale, toast]);

  const connectSerial = async () => {
    if (!supportsWebSerial()) {
      toast(BROWSER_CONNECT_TOAST, { tone: 'danger' });
      return;
    }
    setSerialBusy(true);
    try {
      const ok = await scale.connectSerial();
      if (ok) {
        setSerialScale({ connected: true, portInfo: 'USB serial · 9600 baud' });
        toast('USB scale connected', { tone: 'success' });
      }
    } catch (err) {
      if (!isChooserCancelled(err)) {
        toast(err instanceof Error ? err.message : 'USB scale connection failed', { tone: 'danger' });
      }
    } finally {
      setSerialBusy(false);
    }
  };

  const connectBluetooth = async () => {
    if (!supportsWebBluetooth()) {
      toast(
        'Web Bluetooth is not available in this browser. Please use Google Chrome or Microsoft Edge.',
        { tone: 'danger' },
      );
      return;
    }
    setBluetoothBusy(true);
    try {
      const ok = await scale.connectBluetooth();
      if (ok) {
        setBluetoothScale({
          connected: true,
          deviceName: scale.bluetoothDeviceName ?? 'Bluetooth scale',
        });
        toast('Bluetooth scale connected', { tone: 'success' });
      }
    } catch (err) {
      if (!isChooserCancelled(err)) {
        toast(err instanceof Error ? err.message : 'Bluetooth scale connection failed', {
          tone: 'danger',
        });
      }
    } finally {
      setBluetoothBusy(false);
    }
  };

  const connectQz = async () => {
    setQzBusy(true);
    try {
      const ok = await connectAgent();
      if (ok) {
        setQzTray({ connected: true, version: 'QZ Tray' });
        toast('QZ Tray connected', { tone: 'success' });
      } else {
        toast('Could not reach QZ Tray. Confirm the agent is running on this workstation.', {
          tone: 'danger',
        });
      }
    } catch (err) {
      toast(err instanceof Error ? err.message : 'QZ Tray connection failed', { tone: 'danger' });
    } finally {
      setQzBusy(false);
    }
  };

  return (
    <Modal
      open={managerOpen}
      onClose={closeManager}
      title="Hardware devices"
      description="Pair the scale and printer this station uses. Barcode scanners keep working as a keyboard — no extra permission needed."
      size="lg"
      data-testid="device-manager-modal"
    >
      {scaleApisMissing ? (
        <div
          className="mb-4 rounded-md border border-warning/50 bg-warning/15 px-3 py-2.5 text-sm text-warning"
          data-testid="device-manager-unsupported-alert"
          role="status"
        >
          <div className="flex gap-2">
            <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
            <p>{UNSUPPORTED_BROWSER_HARDWARE_MESSAGE}</p>
          </div>
        </div>
      ) : null}

      <ul className="divide-y divide-border rounded-lg border border-border bg-surface">
        <DeviceRow
          testId="device-row-bluetooth-scale"
          icon={<Bluetooth className="h-4 w-4" aria-hidden />}
          title="Bluetooth Scale"
          detail={
            bluetoothScale.connected
              ? bluetoothScale.deviceName ?? 'Paired over Web Bluetooth'
              : 'Wireless shipping scale (Web Bluetooth)'
          }
          connected={bluetoothScale.connected}
          action={
            bluetoothScale.connected ? (
              <span className="text-xs font-medium text-success">Connected</span>
            ) : bluetoothApi ? (
              <Button
                type="button"
                size="sm"
                loading={bluetoothBusy || scale.connecting}
                onClick={() => void connectBluetooth()}
                data-testid="device-connect-bluetooth-scale"
              >
                Connect Device
              </Button>
            ) : (
              <span className="text-xs font-medium text-warning">Unavailable in this browser</span>
            )
          }
        />
        <DeviceRow
          testId="device-row-serial-scale"
          icon={<Usb className="h-4 w-4" aria-hidden />}
          title="USB/Serial Scale"
          detail={
            serialScale.connected
              ? serialScale.portInfo ?? 'USB serial · 9600 baud'
              : 'Wired packing scale (Web Serial, 9600 baud)'
          }
          connected={serialScale.connected}
          action={
            serialScale.connected ? (
              <span className="text-xs font-medium text-success">Connected</span>
            ) : serialApi ? (
              <Button
                type="button"
                size="sm"
                loading={serialBusy || scale.connecting}
                onClick={() => void connectSerial()}
                data-testid="device-connect-serial-scale"
              >
                Connect Device
              </Button>
            ) : (
              <span className="text-xs font-medium text-warning">Unavailable in this browser</span>
            )
          }
        />
        <DeviceRow
          testId="device-row-qz-tray"
          icon={<Printer className="h-4 w-4" aria-hidden />}
          title="Thermal Printer (via QZ Tray)"
          detail={
            qzTray.connected
              ? qzTray.version ?? 'QZ Tray'
              : 'Local print agent for ZPL labels'
          }
          connected={qzTray.connected}
          action={
            qzTray.connected ? (
              <span className="text-xs font-medium text-success">Connected</span>
            ) : (
              <Button
                type="button"
                size="sm"
                loading={qzBusy}
                onClick={() => void connectQz()}
                data-testid="device-connect-qz-tray"
              >
                Connect Device
              </Button>
            )
          }
        />
      </ul>

      <p className="mt-3 flex items-start gap-2 text-xs text-text-muted">
        <Scale className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden />
        HID barcode scanners are always available — they type into the focused field like a keyboard.
      </p>
    </Modal>
  );
}

function DeviceRow({
  testId,
  icon,
  title,
  detail,
  connected,
  action,
}: {
  testId: string;
  icon: ReactNode;
  title: string;
  detail: string;
  connected: boolean;
  action: React.ReactNode;
}) {
  return (
    <li
      className="flex flex-wrap items-center gap-3 px-3 py-3 sm:flex-nowrap"
      data-testid={testId}
      data-connected={connected ? 'true' : 'false'}
    >
      <div className="flex min-w-0 flex-1 items-start gap-3">
        <span className="mt-0.5 inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-md border border-border bg-surface-raised text-text">
          {icon}
        </span>
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <StatusDot connected={connected} />
            <p className="text-sm font-semibold text-text">{title}</p>
          </div>
          <p className="mt-0.5 text-xs text-text-muted">{detail}</p>
        </div>
      </div>
      <div className="ml-auto shrink-0">{action}</div>
    </li>
  );
}
