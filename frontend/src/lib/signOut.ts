import { useActiveWarehouseStore } from '@/stores/activeWarehouse';
import { useSessionStore } from '@/stores/session';
import { clearQueryCache, queryClient } from '@/offline/queryPersistence';

export async function signOut(): Promise<void> {
  useSessionStore.getState().clearSession();
  useActiveWarehouseStore.getState().clearWarehouse();
  queryClient.clear();
  await clearQueryCache();
}
