import { MutationCache, QueryClient } from '@tanstack/react-query';
import axios from 'axios';
import {
  problemDetailsOf,
  quarantineFailedMutation,
  type QueuedMutation,
} from '@/offline/mutationQueue';

function mutationMetaAsQueued(mutation: {
  options: { meta?: Record<string, unknown> };
  state: { variables?: unknown };
}): QueuedMutation | null {
  const meta = mutation.options.meta;
  if (!meta || typeof meta.url !== 'string' || typeof meta.idempotencyKey !== 'string') {
    return null;
  }
  const createdAt = Date.now();
  return {
    id: typeof meta.queueId === 'string' ? meta.queueId : crypto.randomUUID(),
    idempotencyKey: meta.idempotencyKey,
    method: (typeof meta.method === 'string' ? meta.method : 'POST') as QueuedMutation['method'],
    url: meta.url,
    body: mutation.state.variables,
    createdAt,
    scannedAt: typeof meta.scannedAt === 'number' ? meta.scannedAt : createdAt,
    attempts: 0,
  };
}

/**
 * Shared TanStack Query client — offlineFirst by default for floor scanners.
 * Persisted via PersistQueryClientProvider (IndexedDB / idb-keyval).
 */
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
