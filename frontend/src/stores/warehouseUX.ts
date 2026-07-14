import { create } from 'zustand';
import { enqueueMutation, type QueuedMutation } from '@/offline/mutationQueue';

export interface PendingMisScan {
  id: string;
  barcode: string;
  message: string;
  mutation: Omit<QueuedMutation, 'id' | 'createdAt' | 'attempts' | 'lastError'>;
  expiresAt: number;
}

interface WarehouseUXState {
  pendingMisScan: PendingMisScan | null;
  /** Buffer a mis-scan for 5s before committing to the IndexedDB offline queue. */
  bufferMisScan: (input: {
    barcode: string;
    message?: string;
    mutation: Omit<QueuedMutation, 'id' | 'createdAt' | 'attempts' | 'lastError'>;
    durationMs?: number;
  }) => void;
  undoMisScan: () => void;
  commitMisScan: () => Promise<void>;
  clearMisScan: () => void;
}

let commitTimer: ReturnType<typeof setTimeout> | null = null;

function clearCommitTimer() {
  if (commitTimer) {
    clearTimeout(commitTimer);
    commitTimer = null;
  }
}

export const useWarehouseUXStore = create<WarehouseUXState>((set, get) => ({
  pendingMisScan: null,

  bufferMisScan: ({ barcode, message, mutation, durationMs = 5000 }) => {
    clearCommitTimer();
    const id = crypto.randomUUID();
    const pending: PendingMisScan = {
      id,
      barcode,
      message: message ?? `Scan ${barcode} — undo within 5s`,
      mutation,
      expiresAt: Date.now() + durationMs,
    };
    set({ pendingMisScan: pending });
    commitTimer = setTimeout(() => {
      void get().commitMisScan();
    }, durationMs);
  },

  undoMisScan: () => {
    clearCommitTimer();
    set({ pendingMisScan: null });
  },

  commitMisScan: async () => {
    clearCommitTimer();
    const pending = get().pendingMisScan;
    set({ pendingMisScan: null });
    if (!pending) return;
    await enqueueMutation(pending.mutation);
  },

  clearMisScan: () => {
    clearCommitTimer();
    set({ pendingMisScan: null });
  },
}));
