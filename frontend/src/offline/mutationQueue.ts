import { get, set } from 'idb-keyval';
import axios from 'axios';
import { apiClient, ensureFreshSession } from '@/api/client';
import { useOfflineStore, type QuarantinedMutation } from '@/stores/offlineStore';
import { useSyncConflictStore } from '@/stores/syncConflicts';

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

let replaying = false;
let onlineListenerAttached = false;

async function readQueue(): Promise<QueuedMutation[]> {
  return (await get<QueuedMutation[]>(QUEUE_KEY)) ?? [];
}

async function writeQueue(queue: QueuedMutation[]): Promise<void> {
  await set(QUEUE_KEY, queue);
}

export async function enqueueMutation(
  mutation: Omit<QueuedMutation, 'id' | 'createdAt' | 'attempts'>,
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

export async function updateMutationError(id: string, error: string): Promise<void> {
  const queue = await readQueue();
  const updated = queue.map((m) =>
    m.id === id ? { ...m, attempts: m.attempts + 1, lastError: error } : m,
  );
  await writeQueue(updated);
}

function statusOf(err: unknown): number | undefined {
  if (axios.isAxiosError(err)) {
    return err.response?.status;
  }
  if (typeof err === 'object' && err !== null && 'response' in err) {
    const response = (err as { response?: { status?: number } }).response;
    return response?.status;
  }
  return undefined;
}

/** Parse RFC 7807 Problem Details (title/detail) with legacy message fallbacks. */
export function problemDetailsOf(err: unknown): { title: string; detail: string } {
  if (axios.isAxiosError(err)) {
    const data = err.response?.data as
      | { title?: string; detail?: string; message?: string; error?: string; reason?: string }
      | undefined;
    const detail =
      data?.detail ?? data?.reason ?? data?.message ?? data?.error ?? err.message ?? 'Unknown error';
    const title = data?.title ?? (err.response?.status === 409 ? 'CONFLICT' : 'CLIENT_ERROR');
    return { title, detail };
  }
  if (typeof err === 'object' && err !== null && 'response' in err) {
    const data = (
      err as {
        response?: {
          data?: { title?: string; detail?: string; message?: string; error?: string; reason?: string };
        };
      }
    ).response?.data;
    return {
      title: data?.title ?? 'CLIENT_ERROR',
      detail: data?.detail ?? data?.reason ?? data?.message ?? data?.error ?? 'Unknown error',
    };
  }
  return {
    title: 'CLIENT_ERROR',
    detail: err instanceof Error ? err.message : 'Unknown error',
  };
}

function isBusinessClientError(status?: number): boolean {
  return status !== undefined && status >= 400 && status < 500 && status !== 401;
}

function toQuarantine(
  mutation: QueuedMutation,
  status: number,
  title: string,
  detail: string,
): QuarantinedMutation {
  return {
    id: mutation.id,
    idempotencyKey: mutation.idempotencyKey,
    method: mutation.method,
    url: mutation.url,
    body: mutation.body,
    status,
    title,
    detail,
    failedAt: Date.now(),
  };
}

/**
 * Move a failed mutation into the quarantine store and drop it from the active
 * IndexedDB replay queue so the flush loop never stalls.
 */
export function quarantineFailedMutation(
  mutation: QueuedMutation,
  status: number,
  title: string,
  detail: string,
): void {
  const entry = toQuarantine(mutation, status, title, detail);
  useOfflineStore.getState().quarantineMutation(entry);
  // Keep legacy toast surface in sync for non-fulfillment pages.
  useSyncConflictStore.getState().addConflict({
    id: entry.id,
    idempotencyKey: entry.idempotencyKey,
    method: entry.method,
    url: entry.url,
    body: entry.body,
    status: entry.status,
    message: entry.detail,
    failedAt: entry.failedAt,
  });
}

/**
 * Flush IndexedDB offline mutations. Refreshes JWT before replay so tokens that
 * expired while offline are rotated; HTTP 409 / business 4xx go to quarantine.
 */
export async function replayMutationQueue(): Promise<{
  succeeded: number;
  failed: number;
  deadLettered: number;
}> {
  if (replaying) {
    return { succeeded: 0, failed: 0, deadLettered: 0 };
  }
  replaying = true;

  let succeeded = 0;
  let failed = 0;
  let deadLettered = 0;

  try {
    const refreshed = await ensureFreshSession();
    if (!refreshed) {
      return { succeeded: 0, failed: 0, deadLettered: 0 };
    }

    const queue = await readQueue();
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
        const status = statusOf(err);
        const { title, detail } = problemDetailsOf(err);

        if (isBusinessClientError(status)) {
          // Never stall: drop from active queue, park in quarantine with RFC7807 reason.
          await removeFromQueue(mutation.id);
          quarantineFailedMutation(mutation, status!, title, detail);
          deadLettered++;
          continue;
        }

        await updateMutationError(mutation.id, detail);
        failed++;
      }
    }
  } finally {
    replaying = false;
  }

  return { succeeded, failed, deadLettered };
}

export function startMutationQueueReplay(): void {
  const replay = () => {
    if (navigator.onLine) {
      void replayMutationQueue();
    }
  };

  if (!onlineListenerAttached) {
    window.addEventListener('online', replay);
    onlineListenerAttached = true;
  }

  if (typeof navigator !== 'undefined' && 'serviceWorker' in navigator) {
    navigator.serviceWorker?.addEventListener?.('message', (event: MessageEvent) => {
      if (event.data?.type === 'ONLINE' || event.data?.type === 'SYNC_QUEUE') {
        replay();
      }
    });
  }

  if (navigator.onLine) {
    void replayMutationQueue();
  }
}
