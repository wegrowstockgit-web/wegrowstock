import axios from 'axios';
import { apiClient, ensureFreshSession } from '@/api/client';
import { encryptedGetJson, encryptedSetJson } from '@/offline/encryptedIdb';
import { useOfflineStore, type QuarantinedMutation } from '@/stores/offlineStore';
import { useNetworkSyncStore } from '@/stores/networkSyncStore';
import { useSyncConflictStore } from '@/stores/syncConflicts';
import type { ScanEventPayload } from '@/offline/scanEvent';

const QUEUE_KEY = 'invsys-mutation-queue';

export interface QueuedMutation {
  id: string;
  idempotencyKey: string;
  method: 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  url: string;
  body?: unknown;
  createdAt: number;
  /** Chronological drain key — prefers ScanEventPayload.scannedAt. */
  scannedAt: number;
  /** Full client scan envelope when the mutation originated from a hardware scan. */
  scanEvent?: ScanEventPayload;
  attempts: number;
  lastError?: string;
}

export type EnqueueMutationInput = Omit<QueuedMutation, 'id' | 'createdAt' | 'attempts' | 'scannedAt'> & {
  scannedAt?: number;
};

let replaying = false;
let onlineListenerAttached = false;

function chronoKey(m: QueuedMutation): number {
  return m.scannedAt ?? m.scanEvent?.scannedAt ?? m.createdAt;
}

function sortChronological(queue: QueuedMutation[]): QueuedMutation[] {
  return [...queue].sort((a, b) => chronoKey(a) - chronoKey(b));
}

async function readQueue(): Promise<QueuedMutation[]> {
  return (await encryptedGetJson<QueuedMutation[]>(QUEUE_KEY)) ?? [];
}

/** Test / E2E seam — decrypts the AES-GCM queue for Playwright assertions. */
export function installMutationQueueTestHook(): void {
  if (typeof window === 'undefined') return;
  (
    window as Window & {
      __INVSYS_MUTATION_QUEUE__?: {
        peek: () => Promise<QueuedMutation[]>;
      };
    }
  ).__INVSYS_MUTATION_QUEUE__ = {
    peek: () => readQueue(),
  };
}

async function writeQueue(queue: QueuedMutation[]): Promise<void> {
  await encryptedSetJson(QUEUE_KEY, queue);
  useNetworkSyncStore.getState().setPendingCount(queue.length);
}

async function syncPendingCount(): Promise<void> {
  const queue = await readQueue();
  useNetworkSyncStore.getState().setPendingCount(queue.length);
}

export async function enqueueMutation(mutation: EnqueueMutationInput): Promise<QueuedMutation> {
  const queue = await readQueue();
  const scannedAt = mutation.scannedAt ?? mutation.scanEvent?.scannedAt ?? Date.now();
  const entry: QueuedMutation = {
    ...mutation,
    id: crypto.randomUUID(),
    createdAt: Date.now(),
    scannedAt,
    attempts: 0,
  };
  queue.push(entry);
  await writeQueue(queue);
  useNetworkSyncStore.getState().setOnline(navigator.onLine);
  return entry;
}

/** Serialize a ScanEventPayload-backed mutation into the IndexedDB queue. */
export async function enqueueScanMutation(
  event: ScanEventPayload,
  mutation: Pick<QueuedMutation, 'method' | 'url' | 'body'>,
): Promise<QueuedMutation> {
  return enqueueMutation({
    idempotencyKey: event.idempotencyKey,
    scannedAt: event.scannedAt,
    scanEvent: event,
    method: mutation.method,
    url: mutation.url,
    body: mutation.body,
  });
}

export async function getMutationQueue(): Promise<QueuedMutation[]> {
  return sortChronological(await readQueue());
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

async function dispatchMutation(mutation: QueuedMutation): Promise<void> {
  await apiClient.request({
    method: mutation.method,
    url: mutation.url,
    data: mutation.body,
    headers: {
      'Idempotency-Key': mutation.idempotencyKey,
      'X-Offline-Replay': 'true',
    },
  });
}

/**
 * Flush IndexedDB offline mutations chronologically by `scannedAt`.
 * On 401: silent token refresh, retry once, then hold remaining queue (no drops).
 */
export async function replayMutationQueue(): Promise<{
  succeeded: number;
  failed: number;
  deadLettered: number;
  heldForAuth: number;
}> {
  if (replaying) {
    return { succeeded: 0, failed: 0, deadLettered: 0, heldForAuth: 0 };
  }
  replaying = true;
  useNetworkSyncStore.getState().setSyncing(true);
  useNetworkSyncStore.getState().setOnline(true);

  let succeeded = 0;
  let failed = 0;
  let deadLettered = 0;
  let heldForAuth = 0;

  try {
    const refreshed = await ensureFreshSession();
    if (!refreshed) {
      const pending = await readQueue();
      return { succeeded: 0, failed: 0, deadLettered: 0, heldForAuth: pending.length };
    }

    const queue = sortChronological(await readQueue());
    useNetworkSyncStore.getState().setPendingCount(queue.length);

    for (const mutation of queue) {
      try {
        await dispatchMutation(mutation);
        await removeFromQueue(mutation.id);
        succeeded++;
      } catch (err) {
        const status = statusOf(err);
        const { title, detail } = problemDetailsOf(err);

        if (status === 401) {
          // Hold remaining queue, silent refresh, retry this mutation, then resume.
          const ok = await ensureFreshSession();
          if (ok) {
            try {
              await dispatchMutation(mutation);
              await removeFromQueue(mutation.id);
              succeeded++;
              continue;
            } catch (retryErr) {
              const retryStatus = statusOf(retryErr);
              if (retryStatus === 401) {
                const remaining = await readQueue();
                heldForAuth = remaining.length;
                failed++;
                break;
              }
              const retryDetails = problemDetailsOf(retryErr);
              if (isBusinessClientError(retryStatus)) {
                await removeFromQueue(mutation.id);
                quarantineFailedMutation(mutation, retryStatus!, retryDetails.title, retryDetails.detail);
                deadLettered++;
                continue;
              }
              await updateMutationError(mutation.id, retryDetails.detail);
              failed++;
              break;
            }
          }
          const remaining = await readQueue();
          heldForAuth = remaining.length;
          failed++;
          break;
        }

        if (isBusinessClientError(status)) {
          // Fallback for endpoints that do not yet emit 202 — keep local quarantine.
          await removeFromQueue(mutation.id);
          quarantineFailedMutation(mutation, status!, title, detail);
          deadLettered++;
          continue;
        }

        await updateMutationError(mutation.id, detail);
        failed++;
        // Transient network / 5xx — stop so chronological order is preserved.
        break;
      }
    }
  } finally {
    replaying = false;
    await syncPendingCount();
    useNetworkSyncStore.getState().setSyncing(false);
  }

  return { succeeded, failed, deadLettered, heldForAuth };
}

export function startMutationQueueReplay(): void {
  const replay = () => {
    if (navigator.onLine) {
      useNetworkSyncStore.getState().setOnline(true);
      // Lazy import avoids circular dependency with queryClient → mutationQueue.
      void import('@/lib/queryClient').then(({ queryClient }) => {
        void queryClient.resumePausedMutations();
      });
      void replayMutationQueue();
    } else {
      useNetworkSyncStore.getState().setOnline(false);
    }
  };

  if (!onlineListenerAttached) {
    window.addEventListener('online', replay);
    window.addEventListener('offline', () => {
      useNetworkSyncStore.getState().setOnline(false);
    });
    onlineListenerAttached = true;
  }

  if (typeof navigator !== 'undefined' && 'serviceWorker' in navigator) {
    navigator.serviceWorker?.addEventListener?.('message', (event: MessageEvent) => {
      if (event.data?.type === 'ONLINE' || event.data?.type === 'SYNC_QUEUE') {
        replay();
      }
    });
  }

  void syncPendingCount();
  if (navigator.onLine) {
    void replayMutationQueue();
  } else {
    useNetworkSyncStore.getState().setOnline(false);
  }
}
