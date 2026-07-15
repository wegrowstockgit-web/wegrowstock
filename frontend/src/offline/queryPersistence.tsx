import { get, set, del } from 'idb-keyval';
import {
  type PersistedClient,
  PersistQueryClientProvider,
} from '@tanstack/react-query-persist-client';
import { createAsyncStoragePersister } from '@tanstack/query-async-storage-persister';
import { MutationCache, QueryClient } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import axios from 'axios';
import {
  problemDetailsOf,
  quarantineFailedMutation,
  type QueuedMutation,
} from '@/offline/mutationQueue';

const IDB_KEY = 'invsys-query-cache';

const idbStorage = {
  getItem: async (key: string) => (await get(key)) ?? null,
  setItem: async (key: string, value: string) => {
    await set(key, value);
  },
  removeItem: async (key: string) => {
    await del(key);
  },
};

export const queryPersister = createAsyncStoragePersister({
  storage: idbStorage,
  key: IDB_KEY,
  throttleTime: 1000,
});

function mutationMetaAsQueued(mutation: {
  options: { meta?: Record<string, unknown> };
  state: { variables?: unknown };
}): QueuedMutation | null {
  const meta = mutation.options.meta;
  if (!meta || typeof meta.url !== 'string' || typeof meta.idempotencyKey !== 'string') {
    return null;
  }
  return {
    id: typeof meta.queueId === 'string' ? meta.queueId : crypto.randomUUID(),
    idempotencyKey: meta.idempotencyKey,
    method: (typeof meta.method === 'string' ? meta.method : 'POST') as QueuedMutation['method'],
    url: meta.url,
    body: mutation.state.variables,
    createdAt: Date.now(),
    attempts: 0,
  };
}

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      networkMode: 'offlineFirst',
      staleTime: 60_000,
      gcTime: 24 * 60 * 60 * 1000,
      retry: 1,
      refetchOnWindowFocus: true,
    },
    mutations: {
      networkMode: 'offlineFirst',
      retry: 0,
    },
  },
  mutationCache: new MutationCache({
    onError: (error, _variables, _context, mutation) => {
      if (!axios.isAxiosError(error) || error.response?.status !== 409) {
        return;
      }
      const queued = mutationMetaAsQueued(mutation);
      if (!queued) {
        return;
      }
      const { title, detail } = problemDetailsOf(error);
      quarantineFailedMutation(queued, 409, title, detail);
    },
  }),
});

interface QueryProviderProps {
  children: ReactNode;
}

export function QueryProvider({ children }: QueryProviderProps) {
  return (
    <PersistQueryClientProvider
      client={queryClient}
      persistOptions={{
        persister: queryPersister,
        maxAge: 24 * 60 * 60 * 1000,
        dehydrateOptions: {
          shouldDehydrateQuery: (query) => query.state.status === 'success',
        },
      }}
    >
      {children}
    </PersistQueryClientProvider>
  );
}

export async function clearQueryCache(): Promise<void> {
  await del(IDB_KEY);
}

export type { PersistedClient };
