import { useState } from 'react';
import { Loader2, PackagePlus, Printer } from 'lucide-react';
import { apiClient } from '@/api/client';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { usePrintStore } from '@/stores/usePrintStore';
import { cn } from '@/lib/utils';

export interface MintedLpn {
  id: string;
  lpnBarcode: string;
  locationId?: string | null;
  status: string;
  zpl: string;
}

export interface PackLpnResult {
  lpnId: string;
  lpnBarcode: string;
  linesPacked: number;
  itemCount: number;
  lines: Array<{
    inventoryLevelId: string;
    variantId: string;
    lotId?: string | null;
    quantity: number;
    locationId: string;
  }>;
}

interface PalletBuilderProps {
  active: boolean;
  activeLpn: MintedLpn | null;
  itemCount: number;
  packing: boolean;
  minting: boolean;
  lastPackedSku?: string | null;
  onMint: () => void;
  onClear: () => void;
}

/**
 * Surface B palletization deck: mint LPN → print ZPL → pack scanned stock.
 */
export function PalletBuilder({
  active,
  activeLpn,
  itemCount,
  packing,
  minting,
  lastPackedSku,
  onMint,
  onClear,
}: PalletBuilderProps) {
  if (!active) return null;

  return (
    <Card
      className="mb-6 border-2 border-accent bg-accent-muted p-4"
      data-testid="pallet-builder"
      padding="lg"
    >
      <div className="flex items-center gap-2">
        <PackagePlus className="h-5 w-5 text-accent" aria-hidden />
        <p className="text-xs font-bold uppercase tracking-wide text-accent">Build pallet</p>
      </div>

      {!activeLpn ? (
        <div className="mt-4 space-y-3">
          <p className="text-sm text-text">
            Mint a license plate anywhere on the floor, print the pallet label, then scan items to
            consolidate.
          </p>
          <Button
            type="button"
            data-testid="mint-new-lpn"
            className="min-h-14 w-full text-base font-bold"
            loading={minting}
            onClick={onMint}
          >
            {minting ? (
              <Loader2 className="h-5 w-5 animate-spin" aria-hidden />
            ) : (
              <Printer className="h-5 w-5" aria-hidden />
            )}
            Mint New LPN
          </Button>
        </div>
      ) : (
        <div className="mt-4 space-y-4">
          <div>
            <p className="text-xs font-medium uppercase tracking-wide text-text-muted">Active LPN</p>
            <p
              className="mt-1 font-mono text-3xl font-black leading-tight text-text sm:text-4xl"
              data-testid="active-lpn-barcode"
            >
              {activeLpn.lpnBarcode}
            </p>
          </div>

          <div
            className={cn(
              'rounded-xl border-4 border-accent bg-accent px-4 py-5 text-center text-text-inverse',
            )}
            data-testid="pallet-item-count"
          >
            <p className="text-xs font-bold uppercase tracking-[0.2em] text-text-inverse/90">
              Items on pallet
            </p>
            <p className="mt-1 text-5xl font-black tabular-nums">{itemCount}</p>
            {packing && (
              <p className="mt-2 inline-flex items-center gap-2 text-sm font-medium">
                <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
                Packing…
              </p>
            )}
            {lastPackedSku && !packing && (
              <p className="mt-2 text-sm font-medium text-text-inverse/90">
                Last packed: {lastPackedSku}
              </p>
            )}
          </div>

          <p className="text-sm text-text">
            Scan item barcodes or allocation IDs to bind stock to this LPN.
          </p>

          <Button
            type="button"
            variant="secondary"
            className="min-h-11 w-full"
            onClick={onClear}
            data-testid="clear-active-lpn"
          >
            Finish / new pallet
          </Button>
        </div>
      )}
    </Card>
  );
}

/** Mint LPN, print ZPL via workstation printer, return the minted plate. */
export async function mintAndPrintLpn(
  locationId: string | undefined,
  executePrint: (payload: string, format: 'ZPL' | 'PDF') => Promise<'hardware' | 'browser'>,
): Promise<MintedLpn> {
  const res = await apiClient.post<MintedLpn>('/api/v1/inventory/lpns/mint', {
    locationId: locationId ?? null,
  });
  const minted = res.data;
  if (minted.zpl) {
    await executePrint(minted.zpl, 'ZPL');
  }
  return minted;
}

/** Pack a scanned barcode / allocation onto the active LPN. */
export async function packScanOntoLpn(
  lpnBarcode: string,
  scan: string,
): Promise<PackLpnResult> {
  const res = await apiClient.post<PackLpnResult>(
    `/api/v1/inventory/lpns/${encodeURIComponent(lpnBarcode)}/pack`,
    { barcodes: [scan] },
  );
  return res.data;
}

/** Hook-friendly mint helper that reads executePrint from the print store. */
export function usePalletMint() {
  const executePrint = usePrintStore((s) => s.executePrint);
  const [minting, setMinting] = useState(false);

  const mint = async (locationId?: string) => {
    setMinting(true);
    try {
      return await mintAndPrintLpn(locationId, executePrint);
    } finally {
      setMinting(false);
    }
  };

  return { mint, minting };
}
