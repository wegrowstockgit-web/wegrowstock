import { AlertTriangle, X } from 'lucide-react';
import { useSyncConflictStore } from '@/stores/syncConflicts';
import { Button } from '@/components/ui/Button';
import { cn } from '@/lib/utils';

/**
 * Global toast for offline mutation dead-letter conflicts (4xx business failures).
 */
export function SyncConflictToast() {
  const conflicts = useSyncConflictStore((s) => s.syncConflicts);
  const dismissConflict = useSyncConflictStore((s) => s.dismissConflict);
  const clearConflicts = useSyncConflictStore((s) => s.clearConflicts);

  if (conflicts.length === 0) return null;

  const latest = conflicts[0];
  const remaining = conflicts.length - 1;

  return (
    <div
      role="alertdialog"
      aria-labelledby="sync-conflict-title"
      aria-describedby="sync-conflict-desc"
      data-testid="sync-conflict-toast"
      className={cn(
        'fixed bottom-4 right-4 z-[70] w-full max-w-sm rounded-xl border border-warning/40',
        'bg-surface-raised p-4 shadow-elevated',
        'motion-safe:animate-in motion-safe:fade-in motion-safe:slide-in-from-bottom-2'
      )}
    >
      <div className="flex items-start gap-3">
        <div className="mt-0.5 rounded-lg bg-warning/15 p-2 text-warning">
          <AlertTriangle className="h-4 w-4" aria-hidden />
        </div>
        <div className="min-w-0 flex-1">
          <p id="sync-conflict-title" className="text-sm font-semibold text-text">
            Offline sync conflict
          </p>
          <p id="sync-conflict-desc" className="mt-1 text-sm text-text-muted">
            {latest.message || `Request failed (${latest.status})`}
          </p>
          <p className="mt-1 truncate font-mono text-xs text-text-muted">
            {latest.method} {latest.url}
          </p>
          {remaining > 0 && (
            <p className="mt-2 text-xs text-text-muted">
              +{remaining} more failed action{remaining === 1 ? '' : 's'} to review
            </p>
          )}
          <div className="mt-3 flex flex-wrap gap-2">
            <Button size="sm" variant="secondary" onClick={() => dismissConflict(latest.id)}>
              Dismiss
            </Button>
            {conflicts.length > 1 && (
              <Button size="sm" variant="ghost" onClick={() => clearConflicts()}>
                Clear all
              </Button>
            )}
          </div>
        </div>
        <button
          type="button"
          onClick={() => dismissConflict(latest.id)}
          className="rounded p-1 text-text-muted hover:bg-surface-overlay hover:text-text"
          aria-label="Dismiss sync conflict"
        >
          <X className="h-4 w-4" />
        </button>
      </div>
    </div>
  );
}
