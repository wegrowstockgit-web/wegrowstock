import { create } from 'zustand';

export type NetworkBadgePhase = 'offline' | 'syncing' | 'online';

interface NetworkSyncState {
  online: boolean;
  syncing: boolean;
  pendingCount: number;
  setOnline: (online: boolean) => void;
  setSyncing: (syncing: boolean) => void;
  setPendingCount: (count: number) => void;
  phase: () => NetworkBadgePhase;
}

export const useNetworkSyncStore = create<NetworkSyncState>((set, get) => ({
  online: typeof navigator !== 'undefined' ? navigator.onLine : true,
  syncing: false,
  pendingCount: 0,
  setOnline: (online) => set({ online }),
  setSyncing: (syncing) => set({ syncing }),
  setPendingCount: (pendingCount) => set({ pendingCount }),
  phase: () => {
    const { online, syncing, pendingCount } = get();
    if (!online) return 'offline';
    if (syncing || pendingCount > 0) return 'syncing';
    return 'online';
  },
}));
