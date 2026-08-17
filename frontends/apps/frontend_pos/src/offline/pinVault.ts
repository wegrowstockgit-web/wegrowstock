import { sha256Hex } from './sha256';

export const POS_PIN_VAULT_KEY = 'pos.managerPins.v1';
export const DEMO_TENANT_ID = 'a0000000-0000-4000-8000-000000000001';
export const DEMO_MANAGER_ID = 'a0000000-0000-4000-8000-000000000203';
export const DEMO_MANAGER_PIN = '1234';

export type ManagerPinRecord = {
  managerId: string;
  pinHash: string;
};

export type ManagerPinVault = {
  tenantId: string;
  managers: ManagerPinRecord[];
};

let memoryVault: ManagerPinVault | null = null;

export function hashManagerPin(tenantId: string, pin: string): string {
  return sha256Hex(`${tenantId}:${pin}`);
}

export function cacheManagerPins(tenantId: string, managers: ManagerPinRecord[]): void {
  memoryVault = { tenantId, managers: managers.filter((row) => row.managerId && row.pinHash) };
  if (typeof localStorage === 'undefined') return;
  localStorage.setItem(POS_PIN_VAULT_KEY, JSON.stringify(memoryVault));
}

export function readPinVault(): ManagerPinVault | null {
  if (memoryVault) return memoryVault;
  if (typeof localStorage === 'undefined') return null;
  try {
    const raw = localStorage.getItem(POS_PIN_VAULT_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as ManagerPinVault;
    if (!parsed || typeof parsed !== 'object' || !Array.isArray(parsed.managers)) return null;
    memoryVault = parsed;
    return parsed;
  } catch {
    return null;
  }
}

export function clearPinVault(): void {
  memoryVault = null;
  if (typeof localStorage === 'undefined') return;
  localStorage.removeItem(POS_PIN_VAULT_KEY);
}

export function seedDemoManagerPinsIfEmpty(): ManagerPinVault {
  const existing = readPinVault();
  if (existing && existing.managers.length > 0) return existing;
  cacheManagerPins(DEMO_TENANT_ID, [
    { managerId: DEMO_MANAGER_ID, pinHash: hashManagerPin(DEMO_TENANT_ID, DEMO_MANAGER_PIN) },
  ]);
  return readPinVault()!;
}

/** Hashes the entered PIN and returns the matching managerId, or null. Works offline. */
export function validateManagerPin(pin: string): string | null {
  if (!/^\d{4}$/.test(pin)) return null;
  const vault = readPinVault();
  if (!vault || vault.managers.length === 0) return null;
  const digest = hashManagerPin(vault.tenantId, pin);
  return vault.managers.find((row) => row.pinHash === digest)?.managerId ?? null;
}

export async function syncManagerPinVault(
  fetchImpl: typeof fetch = fetch,
  fallbackTenantId?: string,
): Promise<void> {
  try {
    const response = await fetchImpl('/api/v1/pos/managers/sync-pins', {
      credentials: 'include',
      headers: { Accept: 'application/json' },
    });
    if (!response.ok) return;
    const body = (await response.json()) as {
      tenantId?: string;
      managers?: ManagerPinRecord[];
    };
    if (!body || !Array.isArray(body.managers)) return;
    cacheManagerPins(body.tenantId || fallbackTenantId || DEMO_TENANT_ID, body.managers);
  } catch {
    /* Keep the last morning-sync vault while the register is offline. */
  }
}
