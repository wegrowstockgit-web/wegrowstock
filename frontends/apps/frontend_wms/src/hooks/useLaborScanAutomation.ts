import { useEffect, useRef } from 'react';
import { useLocation } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { extractIntentBarcode } from '@/hooks/useBarcodeScanner';
import { applyLaborFromScan } from '@/lib/laborScanAutomation';
import { useActiveWarehouseStore } from '@/stores/activeWarehouse';

/**
 * Auto-clock / switch labor from floor scans (tote, LPN, PO, dock).
 * Hardware events cover inbound; barcode+mode cover fulfillment ScannerView.
 */
export function useLaborScanAutomation(options?: {
  barcode?: string | null;
  mode?: string | null;
  listenHardware?: boolean;
  enabled?: boolean;
}): void {
  const enabled = options?.enabled !== false;
  const listenHardware = options?.listenHardware === true;
  const location = useLocation();
  const warehouseId = useActiveWarehouseStore((s) => s.warehouseId);
  const queryClient = useQueryClient();
  const lastBarcodeRef = useRef('');

  useEffect(() => {
    if (!enabled) return;
    const barcode = options?.barcode?.trim();
    if (!barcode || barcode === lastBarcodeRef.current) return;
    lastBarcodeRef.current = barcode;
    void applyLaborFromScan({
      barcode,
      warehouseId,
      mode: options?.mode,
      pathname: location.pathname,
    }).then(() => {
      void queryClient.invalidateQueries({ queryKey: ['labor'] });
    });
  }, [enabled, options?.barcode, options?.mode, location.pathname, warehouseId, queryClient]);

  useEffect(() => {
    if (!enabled || !listenHardware) return;
    const onScan = (event: Event) => {
      const barcode = extractIntentBarcode((event as CustomEvent).detail);
      if (!barcode) return;
      void applyLaborFromScan({
        barcode,
        warehouseId,
        pathname: location.pathname,
      }).then(() => {
        void queryClient.invalidateQueries({ queryKey: ['labor'] });
      });
    };
    window.addEventListener('hardwareScan', onScan);
    return () => window.removeEventListener('hardwareScan', onScan);
  }, [enabled, listenHardware, location.pathname, warehouseId, queryClient]);
}
