import { beforeEach, describe, expect, it } from 'vitest';
import {
  clearTerminalPasskey,
  PASSKEY_STORAGE,
  readTerminalPasskey,
  storeTerminalPasskey,
} from '@/lib/terminalPasskey';

describe('terminalPasskey', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('stores and reads bound passkeys', () => {
    storeTerminalPasskey('cred-1', 'secret-1', { userId: 'u1', tenantId: 't1' });
    expect(readTerminalPasskey()).toEqual({
      credentialId: 'cred-1',
      secret: 'secret-1',
      userId: 'u1',
      tenantId: 't1',
    });
  });

  it('discards legacy unbound secrets', () => {
    localStorage.setItem(PASSKEY_STORAGE, JSON.stringify({ credentialId: 'c', secret: 's' }));
    expect(readTerminalPasskey()).toBeNull();
    expect(localStorage.getItem(PASSKEY_STORAGE)).toBeNull();
  });

  it('clears storage', () => {
    storeTerminalPasskey('cred-1', 'secret-1', { userId: 'u1', tenantId: 't1' });
    clearTerminalPasskey();
    expect(readTerminalPasskey()).toBeNull();
  });

  it('ignores incomplete store calls', () => {
    storeTerminalPasskey('', 'secret', { userId: 'u1', tenantId: 't1' });
    expect(readTerminalPasskey()).toBeNull();
  });

  it('discards corrupt JSON', () => {
    localStorage.setItem(PASSKEY_STORAGE, '{not-json');
    expect(readTerminalPasskey()).toBeNull();
    expect(localStorage.getItem(PASSKEY_STORAGE)).toBeNull();
  });
});
