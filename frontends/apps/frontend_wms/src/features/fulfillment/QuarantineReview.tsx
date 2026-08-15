import { AlertTriangle, Trash2 } from 'lucide-react';
import { useOfflineStore } from '@/stores/offlineStore';
import { useSyncConflictStore } from '@/stores/syncConflicts';
import { BigButton } from '@/components/ui/BigButton';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';

function barcodeFromBody(body: unknown): string | null {
  if (body && typeof body === 'object' && 'barcode' in body) {
    const value = (body as { barcode?: unknown }).barcode;
    return typeof value === 'string' ? value : null;
  }
  return null;
}

/** High-contrast Surface B review list for quarantined offline scans. */
export function QuarantineReview({ onClose }: { onClose?: () => void }) {
  const items = useOfflineStore((s) => s.quarantinedMutations);
  const discard = useOfflineStore((s) => s.discardQuarantined);
  const clearAll = useOfflineStore((s) => s.clearQuarantined);
  const dismissConflict = useSyncConflictStore((s) => s.dismissConflict);
  const clearConflicts = useSyncConflictStore((s) => s.clearConflicts);

  const discardOne = (id: string) => {
    discard(id);
    dismissConflict(id);
  };

  const discardAll = () => {
    clearAll();
    clearConflicts();
  };

  return (
    <div className="space-y-4" data-testid="quarantine-review">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h2 className="text-xl font-bold text-text">Quarantined scans</h2>
          <p className="mt-1 text-sm text-text-muted">
            Offline picks the server rejected (409). Discard to clear IndexedDB replay items.
          </p>
        </div>
        {onClose && (
          <Button variant="ghost" size="sm" onClick={onClose}>
            Close
          </Button>
        )}
      </div>

      {items.length === 0 ? (
        <Card padding="lg" className="text-center">
          <p className="text-base text-text-muted">No quarantined scans</p>
        </Card>
      ) : (
        <div className="space-y-3">
          {items.map((item) => {
            const barcode = barcodeFromBody(item.body);
            return (
              <Card
                key={item.id}
                padding="lg"
                className="border-2 border-danger/40 bg-danger/5"
                data-testid="quarantine-item"
              >
                <div className="flex items-start gap-3">
                  <div className="rounded-lg bg-danger/15 p-3 text-danger">
                    <AlertTriangle className="h-6 w-6" aria-hidden />
                  </div>
                  <div className="min-w-0 flex-1 space-y-2">
                    <p className="font-mono text-lg font-bold text-text">
                      {barcode ?? item.url}
                    </p>
                    <p className="text-base font-semibold text-danger">{item.detail}</p>
                    <p className="text-xs uppercase tracking-wide text-text-muted">
                      {item.title} · HTTP {item.status}
                    </p>
                    <p className="font-mono text-xs text-text-muted">
                      {item.method} {item.url}
                    </p>
                    <BigButton
                      variant="danger"
                      className="mt-2 w-full"
                      onClick={() => discardOne(item.id)}
                    >
                      <Trash2 className="h-5 w-5" />
                      Discard Scan
                    </BigButton>
                  </div>
                </div>
              </Card>
            );
          })}
          {items.length > 1 && (
            <Button variant="ghost" className="w-full" onClick={discardAll}>
              Discard all ({items.length})
            </Button>
          )}
        </div>
      )}
    </div>
  );
}
