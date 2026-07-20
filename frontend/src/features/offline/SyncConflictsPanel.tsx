import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { AlertTriangle, CheckCircle2, Trash2 } from 'lucide-react';
import { apiClient } from '@/api/client';
import { Button } from '@/components/ui/Button';
import { Modal } from '@/components/ui/Modal';
import { useToast } from '@/components/ui/Toast';
import { DENSITY_STYLES } from '@/stores/preferencesStore';
import { cn } from '@/lib/utils';
import {
  actionBadgeLabel,
  fieldValue,
  formatConflictSummary,
  payloadBody,
} from './conflictSummary';
import type { ConflictFieldDescriptor, ServerSyncConflict } from './syncConflictTypes';

export type { ServerSyncConflict, ConflictFieldDescriptor } from './syncConflictTypes';

const density = DENSITY_STYLES.cozy;

function draftsFromConflict(row: ServerSyncConflict): Record<string, string | number> {
  const next: Record<string, string | number> = {};
  for (const field of row.schemaMetadata ?? []) {
    if (field.mutable) {
      next[field.key] = fieldValue(row, field);
    }
  }
  return next;
}

export function SyncConflictsPanel() {
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const [activeId, setActiveId] = useState<string | null>(null);
  const [drafts, setDrafts] = useState<Record<string, string | number>>({});
  const [confirmDiscardId, setConfirmDiscardId] = useState<string | null>(null);
  const [confirmApproveId, setConfirmApproveId] = useState<string | null>(null);

  const { data = [], isLoading, isError } = useQuery({
    queryKey: ['offline_sync_conflicts'],
    queryFn: async () =>
      (
        await apiClient.get<ServerSyncConflict[]>('/api/v1/offline-sync-conflicts', {
          params: { status: 'PENDING' },
        })
      ).data,
    retry: false,
  });

  const active = useMemo(
    () => data.find((row) => row.id === activeId) ?? null,
    [activeId, data],
  );

  const openConflict = (row: ServerSyncConflict) => {
    setActiveId(row.id);
    setDrafts(draftsFromConflict(row));
  };

  useEffect(() => {
    if (data.length === 0) {
      setActiveId(null);
      return;
    }
    if (activeId && data.some((row) => row.id === activeId)) {
      return;
    }
    openConflict(data[0]);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- only re-seed when list identity changes
  }, [data, activeId]);

  const discardMutation = useMutation({
    mutationFn: async (id: string) =>
      (await apiClient.post(`/api/v1/offline-sync-conflicts/${id}/dismiss`)).data,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['offline_sync_conflicts'] });
      setConfirmDiscardId(null);
      setActiveId(null);
      toast('Transaction discarded', { tone: 'success' });
    },
    onError: () => toast('Could not discard transaction', { tone: 'danger' }),
  });

  const resolveMutation = useMutation({
    mutationFn: async ({
      id,
      corrections,
    }: {
      id: string;
      corrections: Record<string, unknown>;
    }) =>
      (
        await apiClient.post(`/api/v1/offline-sync-conflicts/${id}/resolve`, {
          corrections,
        })
      ).data,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['offline_sync_conflicts'] });
      setConfirmApproveId(null);
      setActiveId(null);
      toast('Approved and re-processed with manager override', { tone: 'success' });
    },
    onError: () => toast('Could not re-process conflict', { tone: 'danger' }),
  });

  const buildCorrections = (row: ServerSyncConflict): Record<string, unknown> => {
    const corrections: Record<string, unknown> = {};
    for (const field of row.schemaMetadata ?? []) {
      if (!field.mutable) continue;
      const value = drafts[field.key];
      if (field.type === 'number') {
        const n = typeof value === 'number' ? value : Number(value);
        if (!Number.isFinite(n)) {
          throw new Error(`${field.label} must be a number`);
        }
        const min = field.constraints?.min;
        if (min != null && n < min) {
          throw new Error(`${field.label} must be at least ${min}`);
        }
        corrections[field.key] = n;
      } else if (value !== undefined && value !== '') {
        corrections[field.key] = value;
      }
    }
    return corrections;
  };

  return (
    <section
      className="rounded-2xl bg-surface-raised p-5 shadow-card"
      data-testid="sync-conflicts-panel"
    >
      <div className="mb-4 flex items-start gap-3 rounded-lg border border-warning/40 bg-warning/5 px-4 py-3">
        <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-warning" aria-hidden />
        <div>
          <h2 className={cn('font-semibold text-text', density.typography)}>Sync conflicts</h2>
          <p className="text-sm text-text-muted">
            Floor scans that failed business rules while offline. Correct the values below — never edit
            raw JSON — then approve as the responsible manager.
          </p>
        </div>
      </div>

      {isLoading ? (
        <p className="text-sm text-text-muted">Loading conflicts…</p>
      ) : isError ? (
        <p className="text-sm text-danger">Could not load sync conflicts.</p>
      ) : data.length === 0 ? (
        <p className="text-sm text-text-muted" data-testid="sync-conflicts-empty">
          No pending sync conflicts.
        </p>
      ) : (
        <div className="grid gap-4 lg:grid-cols-[minmax(0,14rem)_minmax(0,1fr)]">
          <ul className="flex flex-col gap-2" data-testid="sync-conflicts-list">
            {data.map((row) => {
              const selected = active?.id === row.id;
              return (
                <li key={row.id}>
                  <button
                    type="button"
                    data-testid={`sync-conflict-${row.id}`}
                    onClick={() => openConflict(row)}
                    className={cn(
                      'w-full rounded-lg border px-3 text-left transition-colors',
                      density.cell,
                      density.typography,
                      selected
                        ? 'border-accent bg-accent/10 text-text'
                        : 'border-border bg-surface hover:bg-surface-overlay',
                    )}
                  >
                    <span className="block text-xs font-medium uppercase tracking-wide text-warning">
                      {actionBadgeLabel(row)}
                    </span>
                    <span className="mt-1 line-clamp-3 text-sm text-text">
                      {formatConflictSummary(row)}
                    </span>
                    <span className="mt-1 block text-xs text-text-muted">
                      {row.createdAt
                        ? new Date(row.createdAt).toLocaleString(undefined, {
                            month: 'short',
                            day: 'numeric',
                            hour: '2-digit',
                            minute: '2-digit',
                          })
                        : '—'}
                    </span>
                  </button>
                </li>
              );
            })}
          </ul>

          {active ? (
            <ConflictResolutionForm
              conflict={active}
              drafts={drafts}
              onDraftChange={(key, value) => setDrafts((prev) => ({ ...prev, [key]: value }))}
              onDiscard={() => setConfirmDiscardId(active.id)}
              onApprove={() => {
                try {
                  buildCorrections(active);
                  setConfirmApproveId(active.id);
                } catch (err) {
                  toast(err instanceof Error ? err.message : 'Invalid corrections', {
                    tone: 'danger',
                  });
                }
              }}
              discarding={discardMutation.isPending}
              approving={resolveMutation.isPending}
            />
          ) : null}
        </div>
      )}

      <Modal
        open={confirmDiscardId != null}
        onClose={() => setConfirmDiscardId(null)}
        title="Discard transaction?"
        description="This permanently removes the parked floor scan. Inventory will not change."
      >
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={() => setConfirmDiscardId(null)}>
            Cancel
          </Button>
          <Button
            type="button"
            variant="danger"
            data-testid="confirm-discard-conflict"
            loading={discardMutation.isPending}
            onClick={() => confirmDiscardId && discardMutation.mutate(confirmDiscardId)}
          >
            Discard Transaction
          </Button>
        </div>
      </Modal>

      <Modal
        open={confirmApproveId != null}
        onClose={() => setConfirmApproveId(null)}
        title="Approve & re-process?"
        description="Your corrections will post to the inventory ledger as a manager override (OFFLINE_CONFLICT_OVERRIDE)."
      >
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={() => setConfirmApproveId(null)}>
            Cancel
          </Button>
          <Button
            type="button"
            variant="primary"
            data-testid="confirm-approve-conflict"
            loading={resolveMutation.isPending}
            onClick={() => {
              if (!confirmApproveId || !active) return;
              try {
                const corrections = buildCorrections(active);
                resolveMutation.mutate({ id: confirmApproveId, corrections });
              } catch (err) {
                toast(err instanceof Error ? err.message : 'Invalid corrections', {
                  tone: 'danger',
                });
              }
            }}
          >
            Approve & Re-process
          </Button>
        </div>
      </Modal>
    </section>
  );
}

function ConflictResolutionForm({
  conflict,
  drafts,
  onDraftChange,
  onDiscard,
  onApprove,
  discarding,
  approving,
}: {
  conflict: ServerSyncConflict;
  drafts: Record<string, string | number>;
  onDraftChange: (key: string, value: string | number) => void;
  onDiscard: () => void;
  onApprove: () => void;
  discarding: boolean;
  approving: boolean;
}) {
  const fields = conflict.schemaMetadata?.length
    ? conflict.schemaMetadata
    : fallbackSchema(conflict);

  return (
    <div
      className="rounded-xl border border-border bg-surface p-4"
      data-testid="sync-conflict-resolution-form"
    >
      <p className="text-sm font-medium text-text" data-testid="sync-conflict-human-summary">
        {formatConflictSummary(conflict)}
      </p>
      <p className="mt-1 text-xs text-text-muted">
        Status: Pending review · Action: {actionBadgeLabel(conflict)}
      </p>

      <div className="mt-4 space-y-3">
        {fields.map((field) => (
          <FieldBlock
            key={field.key}
            field={field}
            readValue={String(fieldValue(conflict, field) ?? '')}
            draftValue={drafts[field.key]}
            onChange={(value) => onDraftChange(field.key, value)}
          />
        ))}
      </div>

      <div className="mt-6 flex flex-wrap justify-end gap-2">
        <Button
          type="button"
          variant="ghost"
          className="text-danger hover:text-danger"
          data-testid={`discard-conflict-${conflict.id}`}
          loading={discarding}
          onClick={onDiscard}
        >
          <Trash2 className="h-3.5 w-3.5" />
          Discard Transaction
        </Button>
        <Button
          type="button"
          variant="primary"
          data-testid={`approve-conflict-${conflict.id}`}
          loading={approving}
          onClick={onApprove}
        >
          <CheckCircle2 className="h-3.5 w-3.5" />
          Approve & Re-process
        </Button>
      </div>
    </div>
  );
}

function FieldBlock({
  field,
  readValue,
  draftValue,
  onChange,
}: {
  field: ConflictFieldDescriptor;
  readValue: string;
  draftValue: string | number | undefined;
  onChange: (value: string | number) => void;
}) {
  if (!field.mutable) {
    return (
      <div
        className={cn('rounded-md border border-border/70 bg-surface-raised', density.cell)}
        data-testid={`conflict-field-readonly-${field.key}`}
      >
        <p className="text-xs font-medium uppercase tracking-wide text-text-muted">{field.label}</p>
        <p className={cn('mt-1 font-mono text-text', density.typography)}>{readValue || '—'}</p>
      </div>
    );
  }

  const min = field.constraints?.min;
  return (
    <label
      className="block"
      data-testid={`conflict-field-mutable-${field.key}`}
    >
      <span className="text-xs font-medium uppercase tracking-wide text-text-muted">
        {field.label}
      </span>
      <input
        type={field.type === 'number' ? 'number' : 'text'}
        min={min}
        className={cn(
          'mt-1 w-full rounded-md border border-border bg-surface-raised text-text outline-none ring-accent focus:ring-2',
          density.cell,
          density.typography,
        )}
        value={
          field.type === 'number' && typeof draftValue === 'number' && Number.isNaN(draftValue)
            ? ''
            : (draftValue ?? '')
        }
        onChange={(e) => {
          if (field.type === 'number') {
            const n = e.target.valueAsNumber;
            onChange(Number.isNaN(n) ? '' : n);
          } else {
            onChange(e.target.value);
          }
        }}
        data-testid={`conflict-input-${field.key}`}
      />
    </label>
  );
}

function fallbackSchema(conflict: ServerSyncConflict): ConflictFieldDescriptor[] {
  const body = payloadBody(conflict);
  const fields: ConflictFieldDescriptor[] = [
    {
      key: 'quantity',
      label: 'Corrected Quantity Count',
      type: 'number',
      mutable: true,
      constraints: { min: 1 },
    },
  ];
  if (body.barcode != null) {
    fields.push({
      key: 'barcode',
      label: 'Scanned Item Master GTIN',
      type: 'string',
      mutable: false,
    });
  }
  return fields;
}
