import { apiClient } from '@/api/client';
import { useActiveWarehouseStore } from '@/stores/activeWarehouse';
import { useSessionStore } from '@/stores/session';
import { clearQueryCache, queryClient } from '@/offline/queryPersistence';
import { clearTerminalPasskey } from '@/lib/terminalPasskey';

export async function signOut(): Promise<void> {
  try {
    await apiClient.post('/api/v1/auth/logout');
  } catch {
    // still clear local session
  }
  // Drop shared-terminal passkey material so the next station login cannot
  // impersonate the previous operator via biometric assert.
  clearTerminalPasskey();
  useSessionStore.getState().clearSession();
  useActiveWarehouseStore.getState().clearWarehouse();
  queryClient.clear();
  await clearQueryCache();
}
