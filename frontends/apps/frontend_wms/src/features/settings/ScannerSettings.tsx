import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Printer, Wifi, WifiOff } from 'lucide-react';
import { apiClient } from '@/api/client';
import type { WorkstationSettings } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { Select } from '@/components/ui/Select';
import { SlideOutDrawer } from '@/components/ui/SlideOutDrawer';
import { usePrintStore } from '@/stores/usePrintStore';
import { cn } from '@/lib/utils';

interface ScannerSettingsProps {
  open: boolean;
  onClose: () => void;
}

export function ScannerSettings({ open, onClose }: ScannerSettingsProps) {
  const queryClient = useQueryClient();
  const agentStatus = usePrintStore((s) => s.agentStatus);
  const agentError = usePrintStore((s) => s.agentError);
  const printers = usePrintStore((s) => s.printers);
  const printersLoading = usePrintStore((s) => s.printersLoading);
  const refreshPrinters = usePrintStore((s) => s.refreshPrinters);
  const connectAgent = usePrintStore((s) => s.connectAgent);
  const setBoundPrinterName = usePrintStore((s) => s.setBoundPrinterName);

  const [printMode, setPrintMode] = useState<'PDF' | 'ZPL'>('PDF');
  const [zplPrinterName, setZplPrinterName] = useState('');
  const [labelFormat, setLabelFormat] = useState('4x6');
  const [saveMessage, setSaveMessage] = useState('');

  const { data: workstation, isLoading } = useQuery({
    queryKey: ['users', 'me', 'workstation'],
    queryFn: async () =>
      (await apiClient.get<WorkstationSettings>('/api/v1/users/me/workstation')).data,
    enabled: open,
    retry: false,
  });

  useEffect(() => {
    if (!workstation) return;
    setPrintMode(workstation.printMode === 'ZPL' ? 'ZPL' : 'PDF');
    setZplPrinterName(workstation.zplPrinterName ?? '');
    setLabelFormat(workstation.labelFormat || '4x6');
    setBoundPrinterName(workstation.zplPrinterName ?? null);
  }, [workstation, setBoundPrinterName]);

  useEffect(() => {
    if (!open || printMode !== 'ZPL') return;
    void (async () => {
      const ok = await connectAgent();
      if (ok) await refreshPrinters();
    })();
  }, [open, printMode, connectAgent, refreshPrinters]);

  const saveMutation = useMutation({
    mutationFn: async () => {
      const res = await apiClient.patch<WorkstationSettings>('/api/v1/users/me/workstation', {
        printMode,
        zplPrinterName: printMode === 'ZPL' ? zplPrinterName || null : null,
        labelFormat,
      });
      return res.data;
    },
    onSuccess: (data) => {
      setBoundPrinterName(data.zplPrinterName ?? null);
      void queryClient.invalidateQueries({ queryKey: ['users', 'me', 'workstation'] });
      setSaveMessage('Workstation print settings saved.');
    },
    onError: () => setSaveMessage('Could not save workstation settings.'),
  });

  const agentConnected = agentStatus === 'connected';

  return (
    <SlideOutDrawer
      open={open}
      onClose={onClose}
      title="Scanner Settings"
      description="Hardware scanner preferences and packing-station print routing"
      width="md"
    >
      <div className="space-y-6 px-4 py-4" data-testid="scanner-settings">
        <section className="space-y-3">
          <div className="flex items-center gap-2">
            <Printer className="h-4 w-4 text-accent" />
            <h3 className="text-sm font-semibold text-text">Hardware Printing</h3>
          </div>
          <p className="text-xs text-text-muted">
            Choose browser PDF dialogs or silent ZPL via a local QZ Tray agent
            (wss://localhost:8181).
          </p>

          <fieldset className="space-y-2" disabled={isLoading}>
            <legend className="sr-only">Print mode</legend>
            <label
              className={cn(
                'flex cursor-pointer items-start gap-3 rounded-md border px-3 py-3',
                printMode === 'PDF' ? 'border-accent bg-accent-muted' : 'border-border',
              )}
            >
              <input
                type="radio"
                name="print-mode"
                className="mt-1"
                checked={printMode === 'PDF'}
                onChange={() => setPrintMode('PDF')}
              />
              <span>
                <span className="block text-sm font-medium text-text">Browser Print (PDF)</span>
                <span className="text-xs text-text-muted">
                  Carrier returns PDF; opens the system print dialog.
                </span>
              </span>
            </label>
            <label
              className={cn(
                'flex cursor-pointer items-start gap-3 rounded-md border px-3 py-3',
                printMode === 'ZPL' ? 'border-accent bg-accent-muted' : 'border-border',
              )}
            >
              <input
                type="radio"
                name="print-mode"
                className="mt-1"
                checked={printMode === 'ZPL'}
                onChange={() => setPrintMode('ZPL')}
              />
              <span>
                <span className="block text-sm font-medium text-text">
                  Direct Hardware Print (ZPL)
                </span>
                <span className="text-xs text-text-muted">
                  Silent raw socket print to a Zebra (or compatible) printer.
                </span>
              </span>
            </label>
          </fieldset>

          <Select
            label="Label size"
            value={labelFormat}
            onChange={(e) => setLabelFormat(e.target.value)}
          >
            <option value="4x6">4×6</option>
            <option value="4x4">4×4</option>
            <option value="4x8">4×8</option>
            <option value="8.5x11">8.5×11</option>
          </Select>

          {printMode === 'ZPL' && (
            <div className="space-y-3 rounded-md border border-border bg-surface-raised p-3">
              <div className="flex items-center justify-between gap-2">
                <p className="flex items-center gap-1.5 text-xs text-text-muted">
                  {agentConnected ? (
                    <Wifi className="h-3.5 w-3.5 text-success" />
                  ) : (
                    <WifiOff className="h-3.5 w-3.5 text-danger" />
                  )}
                  Print agent:{' '}
                  <span className="font-medium text-text">{agentStatus}</span>
                </p>
                <Button
                  size="sm"
                  variant="secondary"
                  loading={printersLoading || agentStatus === 'connecting'}
                  onClick={() => void refreshPrinters()}
                >
                  Refresh printers
                </Button>
              </div>
              {agentError && <p className="text-xs text-danger">{agentError}</p>}
              <Select
                label="Packing station printer"
                value={zplPrinterName}
                onChange={(e) => {
                  setZplPrinterName(e.target.value);
                  setBoundPrinterName(e.target.value || null);
                }}
              >
                <option value="">Select USB / network printer…</option>
                {zplPrinterName && !printers.includes(zplPrinterName) && (
                  <option value={zplPrinterName}>{zplPrinterName} (saved)</option>
                )}
                {printers.map((name) => (
                  <option key={name} value={name}>
                    {name}
                  </option>
                ))}
              </Select>
              {!agentConnected && (
                <p className="text-xs text-text-muted">
                  Start QZ Tray on this tablet, allow this site, then refresh. Until connected,
                  labels fall back to the browser print dialog.
                </p>
              )}
            </div>
          )}
        </section>

        <div className="flex flex-wrap gap-2">
          <Button loading={saveMutation.isPending} onClick={() => saveMutation.mutate()}>
            Save workstation
          </Button>
          <Button variant="secondary" onClick={onClose}>
            Close
          </Button>
        </div>
        {saveMessage && (
          <p
            className={cn(
              'text-sm',
              saveMutation.isError ? 'text-danger' : 'text-success',
            )}
          >
            {saveMessage}
          </p>
        )}
      </div>
    </SlideOutDrawer>
  );
}
