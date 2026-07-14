import { get, set } from 'idb-keyval';
import { apiClient } from '@/api/client';

const QUEUE_KEY = 'invsys-mutation-queue';

export interface QueuedMutation {
  id: string;
  idempotencyKey: string;
  method: 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  url: string;
  body?: unknown;
  createdAt: number;
  attempts: number;
  lastError?: string;
}

async function readQueue(): Promise<QueuedMutation[]> {
  return (await get<QueuedMutation[]>(QUEUE_KEY)) ?? [];
}

async function writeQueue(queue: QueuedMutation[]): Promise<void> {
  await set(QUEUE_KEY, queue);
}

export async function enqueueMutation(
  mutation: Omit<QueuedMutation, 'id' | 'createdAt' | 'attempts'>
): Promise<QueuedMutation> {
  const queue = await readQueue();
  const entry: QueuedMutation = {
    ...mutation,
    id: crypto.randomUUID(),
    createdAt: Date.now(),
    attempts: 0,
  };
  queue.push(entry);
  await writeQueue(queue);
  return entry;
}

export async function getMutationQueue(): Promise<QueuedMutation[]> {
  return readQueue();
}

export async function removeFromQueue(id: string): Promise<void> {
  const queue = await readQueue();
  await writeQueue(queue.filter((m) => m.id !== id));
}

export async function updateMutationError(
  id: string,
  error: string
): Promise<void> {
  const queue = await readQueue();
  const updated = queue.map((m) =>
    m.id === id ? { ...m, attempts: m.attempts + 1, lastError: error } : m
  );
  await writeQueue(updated);
}

export async function replayMutationQueue(): Promise<{
  succeeded: number;
  failed: number;
}> {
  const queue = await readQueue();
  let succeeded = 0;
  let failed = 0;

  for (const mutation of queue) {
    try {
      await apiClient.request({
        method: mutation.method,
        url: mutation.url,
        data: mutation.body,
        headers: {
          'Idempotency-Key': mutation.idempotencyKey,
        },
      });
      await removeFromQueue(mutation.id);
      succeeded++;
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Unknown error';
      await updateMutationError(mutation.id, message);
      failed++;
    }
  }

  return { succeeded, failed };
}

export function startMutationQueueReplay(): void {
  const replay = () => {
    if (navigator.onLine) {
      void replayMutationQueue();
    }
  };

  window.addEventListener('online', replay);
  if (navigator.onLine) {
    void replayMutationQueue();
  }
}
