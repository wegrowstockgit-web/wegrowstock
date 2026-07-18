import { create } from 'zustand';
import { apiClient } from '@/api/client';
import type { NextBestAction } from '@/api/types';
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

  /** Closest interleaved floor task after a successful pick / putaway / receive. */
  nextBestAction: NextBestAction | null;
  nextBestActionLoading: boolean;
  /** True when a directed next action was just refreshed (UI pulse / auto-surface). */
  nextBestActionFresh: boolean;
  fetchNextBestAction: (currentLocationId: string) => Promise<NextBestAction | null>;
  clearNextBestAction: () => void;
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
  nextBestAction: null,
  nextBestActionLoading: false,
  nextBestActionFresh: false,

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

  fetchNextBestAction: async (currentLocationId: string) => {
    if (!currentLocationId || !navigator.onLine) {
      set({ nextBestAction: null, nextBestActionLoading: false, nextBestActionFresh: false });
      return null;
    }
    set({ nextBestActionLoading: true, nextBestActionFresh: false });
    try {
      const res = await apiClient.get<NextBestAction>('/api/v1/tasks/next-best-action', {
        params: { currentLocationId },
      });
      const action = res.data;
      if (!action?.taskType) {
        set({ nextBestAction: null, nextBestActionLoading: false, nextBestActionFresh: false });
        return null;
      }
      // Surface immediately after scan completion to kill deadhead travel.
      set({ nextBestAction: action, nextBestActionLoading: false, nextBestActionFresh: true });
      return action;
    } catch {
      set({ nextBestAction: null, nextBestActionLoading: false, nextBestActionFresh: false });
      return null;
    }
  },

  clearNextBestAction: () => set({ nextBestAction: null, nextBestActionFresh: false }),
}));
