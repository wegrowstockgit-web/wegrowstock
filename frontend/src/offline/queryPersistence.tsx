import { useEffect, type ReactNode } from 'react';
import { QueryClientProvider } from '@tanstack/react-query';
import {
  type PersistedClient,
  PersistQueryClientProvider,
} from '@tanstack/react-query-persist-client';
import { createAsyncStoragePersister } from '@tanstack/query-async-storage-persister';
import { queryClient } from '@/lib/queryClient';
import { useCryptoMemoryKeyStore } from '@/stores/cryptoMemoryKeyStore';
import { createEncryptedIdbStorage, encryptedDel } from '@/offline/encryptedIdb';
import { clearPinVerifier } from '@/offline/pinVault';

const IDB_KEY = 'invsys-query-cache';
const QUEUE_KEY = 'invsys-mutation-queue';

const encryptedStorage = createEncryptedIdbStorage();

/** IndexedDB persister (AES-GCM wrapped idb-keyval) for query cache + paused mutations. */
export const queryPersister = createAsyncStoragePersister({
  storage: encryptedStorage,
  key: IDB_KEY,
  throttleTime: 1000,
});

export { queryClient };

interface QueryProviderProps {
  children: ReactNode;
}

function OnlineResumeEffect() {
  useEffect(() => {
    const onOnline = () => {
      void queryClient.resumePausedMutations();
    };
    window.addEventListener('online', onOnline);
    return () => window.removeEventListener('online', onOnline);
  }, []);
  return null;
}

/**
 * Hydrates the encrypted persist layer only while a PIN-derived AES key is in memory.
 * Login / lock screens still get a live QueryClient without touching ciphertext.
 */
export function QueryProvider({ children }: QueryProviderProps) {
  const memoryKey = useCryptoMemoryKeyStore((s) => s.memoryKey);

  if (!memoryKey) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  }

  return (
    <PersistQueryClientProvider
      client={queryClient}
      persistOptions={{
        persister: queryPersister,
        maxAge: 24 * 60 * 60 * 1000,
        dehydrateOptions: {
          shouldDehydrateQuery: (query) => query.state.status === 'success',
          shouldDehydrateMutation: () => true,
        },
      }}
      onSuccess={() => {
        if (navigator.onLine) {
          void queryClient.resumePausedMutations();
        }
      }}
    >
      <OnlineResumeEffect />
      {children}
    </PersistQueryClientProvider>
  );
}

export async function clearQueryCache(): Promise<void> {
  await encryptedDel(IDB_KEY);
  await encryptedDel(QUEUE_KEY);
  useCryptoMemoryKeyStore.getState().clearKey();
}

/** Full cryptographic wipe used on brute-force PIN failure / hard logout. */
export async function purgeEncryptedOfflineData(): Promise<void> {
  await clearQueryCache();
  await clearPinVerifier();
}

export type { PersistedClient };
