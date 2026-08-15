import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { ChevronDown, ChevronRight } from 'lucide-react';
import { apiClient } from '@/api/client';
import type { AuditLogItem } from '@/api/types';
import { cn } from '@/lib/utils';
import { TableSkeleton } from '@/components/ui/Skeleton';

function formatWhen(iso?: string): string {
  if (!iso) return '—';
  try {
    return new Date(iso).toLocaleString();
  } catch {
    return iso;
  }
}

function actorLabel(row: AuditLogItem): string {
  if (row.actorDisplayName && row.actorEmail) {
    return `${row.actorDisplayName} (${row.actorEmail})`;
  }
  return row.actorDisplayName || row.actorEmail || row.actorUserId?.slice(0, 8) || 'System';
}

function DiffBlock({ diff }: { diff: Record<string, unknown> }) {
  const [open, setOpen] = useState(false);
  const before = diff.old ?? diff.from ?? diff.previous;
  const after = diff.new ?? diff.to ?? diff.next;
  const hasBeforeAfter = before !== undefined || after !== undefined;

  return (
    <div className="mt-2">
      <button
        type="button"
        className="inline-flex items-center gap-1 text-xs font-medium text-accent hover:underline"
        onClick={() => setOpen((v) => !v)}
        data-testid="audit-diff-toggle"
      >
        {open ? <ChevronDown className="h-3.5 w-3.5" /> : <ChevronRight className="h-3.5 w-3.5" />}
        {open ? 'Hide changes' : 'Show changes'}
      </button>
      {open && (
        <div
          className="mt-2 space-y-2 rounded-md border border-border bg-surface-overlay/40 p-3 font-mono text-xs text-text"
          data-testid="audit-diff-body"
        >
          {hasBeforeAfter ? (
            <>
              <div>
                <div className="mb-1 text-[10px] font-semibold uppercase tracking-wide text-text-muted">
                  Before
                </div>
                <pre className="whitespace-pre-wrap break-all">{JSON.stringify(before ?? null, null, 2)}</pre>
              </div>
              <div>
                <div className="mb-1 text-[10px] font-semibold uppercase tracking-wide text-text-muted">
                  After
                </div>
                <pre className="whitespace-pre-wrap break-all">{JSON.stringify(after ?? null, null, 2)}</pre>
              </div>
            </>
          ) : (
            <pre className="whitespace-pre-wrap break-all">{JSON.stringify(diff, null, 2)}</pre>
          )}
        </div>
      )}
    </div>
  );
}

export function ActivityTimeline({
  entityType,
  entityId,
  enabled = true,
}: {
  entityType: string;
  entityId: string;
  enabled?: boolean;
}) {
  const { data = [], isLoading, isError } = useQuery({
    queryKey: ['audit', 'entity', entityType, entityId],
    queryFn: async () =>
      (
        await apiClient.get<AuditLogItem[]>(
          `/api/v1/audit/entity/${encodeURIComponent(entityType)}/${entityId}`,
          { params: { limit: 40 } },
        )
      ).data,
    enabled: enabled && !!entityId,
    retry: false,
  });

  return (
    <section className="space-y-3" data-testid="activity-timeline">
      <div>
        <h3 className="text-sm font-semibold text-text">Activity timeline</h3>
        <p className="text-xs text-text-muted">Compliance events for this user (OWNER / ADMIN)</p>
      </div>
      {isLoading ? (
        <TableSkeleton rows={3} cols={1} />
      ) : isError ? (
        <p className="text-sm text-danger">Could not load audit timeline.</p>
      ) : data.length === 0 ? (
        <p className="text-sm text-text-muted">No audit events for this user yet.</p>
      ) : (
        <ol className="relative space-y-0 border-l border-border pl-4">
          {data.map((row, index) => (
            <li
              key={row.id}
              className="relative pb-5 last:pb-0"
              data-testid={`timeline-event-${row.id}`}
            >
              <span
                className={cn(
                  'absolute -left-[1.3rem] top-1.5 h-2.5 w-2.5 rounded-full border-2 border-surface bg-accent',
                  index === 0 && 'ring-2 ring-accent/30',
                )}
              />
              <div className="flex flex-wrap items-center gap-2">
                <span
                  className="rounded bg-accent-muted px-2 py-0.5 font-mono text-[11px] font-semibold text-accent"
                  data-testid="timeline-action-badge"
                >
                  {row.action}
                </span>
                <span className="text-xs text-text-muted">{formatWhen(row.createdAt)}</span>
              </div>
              <p className="mt-1 text-sm text-text">
                <span className="text-text-muted">by </span>
                {actorLabel(row)}
              </p>
              {row.diff && Object.keys(row.diff).length > 0 && <DiffBlock diff={row.diff} />}
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}
