import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowRight, PackageOpen, X } from 'lucide-react';
import { apiClient } from '@/api/client';
import { refetchIntervalWhileAuthenticated } from '@/lib/queryClient';
import type { ReplenishmentTask } from '@/api/types';
import { BigButton } from '@/components/ui/BigButton';
import { Button } from '@/components/ui/Button';
import { cn } from '@/lib/utils';

export function ReplenishmentBadge({ onOpen }: { onOpen: () => void }) {
  const { data: tasks = [] } = useQuery({
    queryKey: ['warehouse', 'replenishments'],
    queryFn: async () =>
      (await apiClient.get<ReplenishmentTask[]>('/api/v1/warehouse/replenishments')).data,
    refetchInterval: refetchIntervalWhileAuthenticated(30_000),
  });

  if (tasks.length === 0) return null;

  return (
    <button
      type="button"
      data-testid="replenishments-needed"
      onClick={onOpen}
      className={cn(
        'flex min-h-12 w-full items-center justify-between gap-3 rounded-lg border-2 border-warning/50',
        'bg-warning/15 px-4 py-3 text-left text-base font-bold text-text',
        'active:scale-[0.99] transition-transform',
      )}
    >
      <span className="inline-flex items-center gap-2">
        <PackageOpen className="h-5 w-5 text-warning" aria-hidden />
        Replenishments Needed
      </span>
      <span
        className="inline-flex min-h-8 min-w-8 items-center justify-center rounded-md bg-warning px-2 font-mono text-sm font-bold text-text"
        data-testid="replenishments-count"
      >
        {tasks.length}
      </span>
    </button>
  );
}

export function ReplenishmentQueue({ onClose }: { onClose: () => void }) {
  const queryClient = useQueryClient();
  const { data: tasks = [], isLoading } = useQuery({
    queryKey: ['warehouse', 'replenishments'],
    queryFn: async () =>
      (await apiClient.get<ReplenishmentTask[]>('/api/v1/warehouse/replenishments')).data,
  });

  const confirmMutation = useMutation({
    mutationFn: async (task: ReplenishmentTask) => {
      await apiClient.post('/api/v1/warehouse/replenishments/confirm', {
        variantId: task.variantId,
        fromLocationId: task.fromLocationId,
        toLocationId: task.toLocationId,
        lotId: task.lotId,
        quantity: task.suggestedQuantity,
      });
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['warehouse', 'replenishments'] });
    },
  });

  return (
    <div className="space-y-4" data-testid="replenishment-queue">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h2 className="text-xl font-bold text-text">Replenishment queue</h2>
          <p className="mt-1 text-sm text-text-muted">
            Move reserve stock to pick faces below min. Swipe confirm posts a ledger transfer.
          </p>
        </div>
        <Button variant="ghost" size="sm" onClick={onClose} aria-label="Close replenishment queue">
          <X className="h-5 w-5" />
        </Button>
      </div>

      {isLoading ? (
        <p className="text-sm text-text-muted">Loading suggestions…</p>
      ) : tasks.length === 0 ? (
        <p className="rounded-lg border border-border bg-surface-raised px-4 py-8 text-center text-base text-text-muted">
          Pick faces are stocked — no replenishments needed.
        </p>
      ) : (
        <ul className="space-y-3">
          {tasks.map((task) => (
            <li
              key={`${task.ruleId}-${task.fromLocationId}`}
              className="rounded-lg border-2 border-border bg-surface-raised p-4"
              data-testid="replenishment-task"
            >
              <p className="font-mono text-lg font-bold text-text">{task.sku}</p>
              <p className="mt-0.5 text-sm text-text-muted">{task.variantName}</p>
              <p className="mt-3 text-base font-semibold text-text">{task.instruction}</p>
              <div className="mt-2 flex flex-wrap items-center gap-2 font-mono text-xs text-text-muted">
                <span>{task.fromLocationPath}</span>
                <ArrowRight className="h-3.5 w-3.5" aria-hidden />
                <span>{task.toLocationPath}</span>
                <span className="text-text">
                  · face {task.pickFaceOnHand}/{task.minQuantity} min
                </span>
              </div>
              <BigButton
                className="mt-4 w-full"
                disabled={confirmMutation.isPending}
                onClick={() => confirmMutation.mutate(task)}
                data-testid="confirm-replenishment"
              >
                Confirm transfer
              </BigButton>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
