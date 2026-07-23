import { useCallback, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { PackagePlus, ScanLine, Lock } from 'lucide-react';
import { apiClient } from '@/api/client';
import type { PalletManifest, PalletManifestSealResult } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { useHardwareScanner } from '@/hooks/useHardwareScanner';
import { cn } from '@/lib/utils';

export function PalletManifestWorkspace() {
  const queryClient = useQueryClient();
  const [lpnScan, setLpnScan] = useState('');
  const [carrierName, setCarrierName] = useState('');
  const [scanError, setScanError] = useState<string | null>(null);

  const { data: manifest, isLoading } = useQuery({
    queryKey: ['pallet-manifests', 'active'],
    queryFn: async () => {
      const res = await apiClient.get<PalletManifest | null>(
        '/api/v1/pallet-manifests/active',
      );
      return res.data;
    },
    retry: false,
  });

  const createMutation = useMutation({
    mutationFn: async () => {
      const res = await apiClient.post<PalletManifest>('/api/v1/pallet-manifests', {
        carrierName: carrierName.trim() || undefined,
      });
      return res.data;
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['pallet-manifests'] });
      setScanError(null);
    },
  });

  const scanMutation = useMutation({
    mutationFn: async (lpnBarcode: string) => {
      if (!manifest?.id) throw new Error('No active pallet');
      const res = await apiClient.post<PalletManifest>(
        `/api/v1/pallet-manifests/${manifest.id}/lpns`,
        { lpnBarcode },
      );
      return res.data;
    },
    onSuccess: () => {
      setLpnScan('');
      setScanError(null);
      void queryClient.invalidateQueries({ queryKey: ['pallet-manifests'] });
    },
    onError: () => {
      setScanError('LPN not found or already on another pallet.');
    },
  });

  const sealMutation = useMutation({
    mutationFn: async () => {
      if (!manifest?.id) throw new Error('No active pallet');
      const res = await apiClient.post<PalletManifestSealResult>(
        `/api/v1/pallet-manifests/${manifest.id}/seal`,
        {},
      );
      return res.data;
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['pallet-manifests'] });
    },
  });

  const handleLpnScan = useCallback(
    (raw: string) => {
      const barcode = raw.trim();
      if (!barcode || !manifest?.id || manifest.status !== 'BUILDING') return;
      scanMutation.mutate(barcode);
    },
    [manifest, scanMutation],
  );

  useHardwareScanner({
    enabled: manifest?.status === 'BUILDING',
    onScan: (barcode) => handleLpnScan(barcode),
  });

  const items = manifest?.items ?? [];
  const sealed = manifest?.status === 'SEALED' || manifest?.status === 'DISPATCHED';

  return (
    <div className="p-4 sm:p-6" data-testid="pallet-manifest-workspace">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-text">Pallet manifest</h1>
        <p className="mt-1 text-sm text-text-muted">
          Build a pallet, scan LPNs, seal for SSCC-18 and BOL assignment.
        </p>
      </div>

      {!manifest && !isLoading && (
        <Card className="mb-6">
          <CardHeader title="Start new pallet" description="Mint a BUILDING manifest before scanning LPNs" />
          <div className="space-y-4">
            <Input
              label="Carrier (optional)"
              value={carrierName}
              onChange={(e) => setCarrierName(e.target.value)}
              placeholder="e.g. FedEx Freight"
            />
            <Button
              data-testid="create-pallet-manifest"
              className="min-h-12 w-full sm:w-auto"
              loading={createMutation.isPending}
              onClick={() => createMutation.mutate()}
            >
              <PackagePlus className="h-5 w-5" />
              Build pallet
            </Button>
          </div>
        </Card>
      )}

      {manifest && (
        <>
          <Card className="mb-6">
            <CardHeader
              title={sealed ? 'Sealed pallet' : 'Building pallet'}
              description={`Status: ${manifest.status} · ${items.length} LPN(s)`}
              action={
                !sealed ? (
                  <Button
                    data-testid="seal-pallet-manifest"
                    loading={sealMutation.isPending}
                    disabled={items.length === 0}
                    onClick={() => sealMutation.mutate()}
                  >
                    <Lock className="h-4 w-4" />
                    Seal pallet
                  </Button>
                ) : undefined
              }
            />

            {(manifest.sscc18 || sealMutation.data?.sscc18) && (
              <div className="grid gap-4 sm:grid-cols-2">
                <div className="rounded-md border border-border bg-surface-overlay p-4">
                  <p className="text-xs font-medium uppercase tracking-wide text-text-muted">SSCC-18</p>
                  <p
                    className="mt-1 font-mono text-xl font-bold text-text"
                    data-testid="pallet-sscc"
                  >
                    {sealMutation.data?.sscc18 ?? manifest.sscc18}
                  </p>
                </div>
                <div className="rounded-md border border-border bg-surface-overlay p-4">
                  <p className="text-xs font-medium uppercase tracking-wide text-text-muted">BOL</p>
                  <p
                    className="mt-1 font-mono text-xl font-bold text-text"
                    data-testid="pallet-bol"
                  >
                    {sealMutation.data?.bolNumber ?? manifest.bolNumber ?? '—'}
                  </p>
                </div>
              </div>
            )}
          </Card>

          {!sealed && (
            <Card className="mb-6">
              <CardHeader title="Scan LPN" description="Scan license plates to add to this pallet" />
              <form
                className="flex flex-col gap-3 sm:flex-row sm:items-end"
                onSubmit={(e) => {
                  e.preventDefault();
                  handleLpnScan(lpnScan);
                }}
              >
                <Input
                  label="LPN barcode"
                  value={lpnScan}
                  onChange={(e) => setLpnScan(e.target.value)}
                  placeholder="Scan LPN…"
                  autoComplete="off"
                  data-testid="pallet-lpn-scan"
                  disabled={scanMutation.isPending}
                />
                <Button
                  type="submit"
                  className="min-h-11"
                  loading={scanMutation.isPending}
                  disabled={!lpnScan.trim()}
                >
                  <ScanLine className="h-4 w-4" />
                  Add LPN
                </Button>
              </form>
              {scanError && <p className="mt-2 text-sm text-danger">{scanError}</p>}
            </Card>
          )}

          <Card padding="none">
            <div className="px-4 pt-4">
              <CardHeader title="LPN contents" />
            </div>
            {items.length === 0 ? (
              <p className="px-4 pb-4 text-sm text-text-muted">No LPNs scanned yet.</p>
            ) : (
              <ul className="divide-y divide-border">
                {items.map((item) => (
                  <li
                    key={item.id}
                    className={cn(
                      'flex items-center justify-between px-4 py-3 font-mono text-sm',
                    )}
                    data-testid="pallet-lpn-row"
                  >
                    <span>{item.lpnBarcode ?? item.lpnId}</span>
                  </li>
                ))}
              </ul>
            )}
          </Card>
        </>
      )}
    </div>
  );
}
