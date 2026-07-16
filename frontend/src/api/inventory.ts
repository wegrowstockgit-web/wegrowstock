import { apiClient } from '@/api/client';

export interface InventoryLedgerEntry {
  id: string;
  variantId: string;
  locationId: string;
  lotId: string | null;
  movementType: string;
  quantityDelta: number | string;
  reasonCode: string | null;
  referenceType: string | null;
  referenceId: string | null;
  reversalOfLedgerId: string | null;
  unitCost: number | string | null;
  createdAt: string;
}

export async function listLedgerTransactions(limit = 50): Promise<InventoryLedgerEntry[]> {
  const { data } = await apiClient.get<InventoryLedgerEntry[]>('/api/v1/inventory/ledger', {
    params: { limit },
  });
  return data;
}

export async function reverseLedgerTransaction(id: string): Promise<InventoryLedgerEntry> {
  const { data } = await apiClient.post<InventoryLedgerEntry>(
    `/api/v1/inventory/ledger/${id}/reverse`,
  );
  return data;
}
