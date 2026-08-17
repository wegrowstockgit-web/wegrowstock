import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { AlertTriangle } from 'lucide-react';
import { apiClient } from '@/api/client';
import { refetchIntervalWhileAuthenticated } from '@/lib/queryClient';
import { Button } from '@/components/ui/Button';
import type { ServerSyncConflict } from '@/features/offline/syncConflictTypes';

/** Glanceable dashboard alert — full resolution lives on /exceptions?tab=sync. */
export function SyncConflictAlertBanner() {
  const navigate = useNavigate();

  const { data = [] } = useQuery({
    queryKey: ['offline_sync_conflicts'],
    queryFn: async () =>
      (
        await apiClient.get<ServerSyncConflict[]>('/api/v1/offline-sync-conflicts', {
          params: { status: 'PENDING' },
        })
      ).data,
    retry: false,
    refetchInterval: refetchIntervalWhileAuthenticated(60_000),
  });

  if (data.length === 0) return null;

  const count = data.length;

  return (
    <section
      role="status"
      data-testid="sync-conflict-alert-banner"
      className="flex flex-col gap-3 rounded-lg border border-warning/40 bg-warning/5 px-4 py-3 sm:flex-row sm:items-center sm:justify-between"
    >
      <div className="flex min-w-0 items-start gap-3">
        <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-warning" aria-hidden />
        <div className="min-w-0">
          <p className="text-sm font-semibold text-text">
            {count} offline {count === 1 ? 'scan' : 'scans'} failed to sync
          </p>
          <p className="text-xs text-text-muted">
            Resolve parked mutations so the floor queue stays accurate.
          </p>
        </div>
      </div>
      <Button
        size="sm"
        className="min-h-11 shrink-0 sm:min-h-10"
        data-testid="sync-conflict-resolve-now"
        onClick={() => navigate('/exceptions?tab=sync')}
      >
        Resolve Now
      </Button>
    </section>
  );
}
