export const PASSKEY_STORAGE = 'invsys.terminalPasskey';

export interface StoredTerminalPasskey {
  credentialId: string;
  secret: string;
  /** Owning operator — required so a later station login cannot reuse the secret. */
  userId: string;
  tenantId: string;
}

export function storeTerminalPasskey(
  credentialId: string,
  secret: string,
  binding: { userId: string; tenantId: string }
) {
  if (!credentialId || !secret || !binding.userId || !binding.tenantId) return;
  const payload: StoredTerminalPasskey = {
    credentialId,
    secret,
    userId: binding.userId,
    tenantId: binding.tenantId,
  };
  localStorage.setItem(PASSKEY_STORAGE, JSON.stringify(payload));
}

export function clearTerminalPasskey() {
  localStorage.removeItem(PASSKEY_STORAGE);
}

export function readTerminalPasskey(): StoredTerminalPasskey | null {
  const raw = localStorage.getItem(PASSKEY_STORAGE);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as Partial<StoredTerminalPasskey>;
    if (!parsed.credentialId || !parsed.secret || !parsed.userId || !parsed.tenantId) {
      // Legacy unbound secrets are unsafe — discard.
      clearTerminalPasskey();
      return null;
    }
    return parsed as StoredTerminalPasskey;
  } catch {
    clearTerminalPasskey();
    return null;
  }
}
