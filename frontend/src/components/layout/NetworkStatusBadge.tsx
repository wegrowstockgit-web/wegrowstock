import { useEffect, useState } from 'react';
import { CloudOff, Loader2, Wifi } from 'lucide-react';
import { getMutationQueue } from '@/offline/mutationQueue';
import { useNetworkSyncStore, type NetworkBadgePhase } from '@/stores/networkSyncStore';
import { cn } from '@/lib/utils';

function resolvePhase(): NetworkBadgePhase {
  return useNetworkSyncStore.getState().phase();
}

/**
 * Floor-facing connectivity badge: Offline → Syncing → Connected.
 */
export function NetworkStatusBadge({ className }: { className?: string }) {
  const online = useNetworkSyncStore((s) => s.online);
  const syncing = useNetworkSyncStore((s) => s.syncing);
  const pendingCount = useNetworkSyncStore((s) => s.pendingCount);
  const setOnline = useNetworkSyncStore((s) => s.setOnline);
  const setPendingCount = useNetworkSyncStore((s) => s.setPendingCount);
  const [phase, setPhase] = useState<NetworkBadgePhase>(() => resolvePhase());

  useEffect(() => {
    const refreshPending = () => {
      void getMutationQueue().then((q) => setPendingCount(q.length));
    };
    const onOnline = () => {
      setOnline(true);
      refreshPending();
    };
    const onOffline = () => setOnline(false);

    setOnline(navigator.onLine);
    refreshPending();
    window.addEventListener('online', onOnline);
    window.addEventListener('offline', onOffline);
    const timer = window.setInterval(refreshPending, 2_000);
    return () => {
      window.removeEventListener('online', onOnline);
      window.removeEventListener('offline', onOffline);
      window.clearInterval(timer);
    };
  }, [setOnline, setPendingCount]);

  useEffect(() => {
    setPhase(resolvePhase());
  }, [online, syncing, pendingCount]);

  if (phase === 'offline') {
    return (
      <span
        data-testid="network-status-badge"
        data-phase="offline"
        className={cn(
          'inline-flex min-h-9 items-center gap-1.5 rounded-md border border-warning/50',
          'bg-warning/15 px-2.5 py-1 text-xs font-semibold text-warning',
          className,
        )}
        role="status"
        aria-live="polite"
      >
        <CloudOff className="h-3.5 w-3.5 shrink-0" aria-hidden />
        Offline - Caching Scans
        {pendingCount > 0 ? ` (${pendingCount})` : ''}
      </span>
    );
  }

  if (phase === 'syncing') {
    return (
      <span
        data-testid="network-status-badge"
        data-phase="syncing"
        className={cn(
          'inline-flex min-h-9 items-center gap-1.5 rounded-md border border-accent/40',
          'bg-accent/10 px-2.5 py-1 text-xs font-semibold text-accent',
          className,
        )}
        role="status"
        aria-live="polite"
      >
        <Loader2 className="h-3.5 w-3.5 shrink-0 animate-spin" aria-hidden />
        Syncing…
        {pendingCount > 0 ? ` (${pendingCount})` : ''}
      </span>
    );
  }

  return (
    <span
      data-testid="network-status-badge"
      data-phase="online"
      className={cn(
        'inline-flex min-h-9 items-center gap-1.5 rounded-md border border-success/40',
        'bg-success/10 px-2.5 py-1 text-xs font-semibold text-success',
        className,
      )}
      role="status"
      aria-live="polite"
    >
      <Wifi className="h-3.5 w-3.5 shrink-0" aria-hidden />
      Connected
    </span>
  );
}
