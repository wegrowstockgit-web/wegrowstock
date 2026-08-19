import { ShieldAlert, ShieldCheck } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import type { AuditLogItem } from '@/api/types';
import { cn } from '@/lib/utils';
import { TableSkeleton } from '@/components/ui/Skeleton';
import {
  actorLabel,
  auditFieldChanges,
  formatActionLabel,
  formatLoginAuditLine,
  isLoginAuditAction,
  summarizeAuditDiff,
  type AuditFieldChange,
} from './auditDiffCopy';

function formatWhen(iso?: string): string {
  if (!iso) return '—';
  try {
    const date = new Date(iso);
    if (Number.isNaN(date.getTime())) return iso;
    const sameDay = date.toDateString() === new Date().toDateString();
    const time = date.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' });
    if (sameDay) return `Today at ${time}`;
    return date.toLocaleString(undefined, {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
      hour: 'numeric',
      minute: '2-digit',
    });
  } catch {
    return iso;
  }
}

function FieldChangeRow({ change }: { change: AuditFieldChange }) {
  return (
    <li className="grid grid-cols-[7.5rem_1fr] gap-2 py-1.5 sm:grid-cols-[9rem_1fr]">
      <span className="text-xs font-medium text-text-muted">{change.field}</span>
      <span className="min-w-0 text-sm text-text">
        {change.from && change.to ? (
          <>
            <span className="text-text-muted line-through">{change.from}</span>
            <span className="mx-1.5 text-text-muted" aria-hidden>
              →
            </span>
            <span>{change.to}</span>
          </>
        ) : (
          (change.to ?? change.from ?? '—')
        )}
      </span>
    </li>
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
        <h3 className="text-sm font-semibold text-text">Activity</h3>
        <p className="mt-0.5 text-xs text-text-muted">Who changed this account, and what they changed.</p>
      </div>
      {isLoading ? (
        <TableSkeleton rows={3} cols={1} />
      ) : isError ? (
        <p className="text-sm text-danger">Could not load this person&apos;s activity. Try closing the drawer and opening it again.</p>
      ) : data.length === 0 ? (
        <p className="text-sm text-text-muted">No changes recorded for this person yet.</p>
      ) : (
        <ol className="relative space-y-0 border-l border-border pl-4">
          {data.map((row, index) => {
            const login = isLoginAuditAction(row.action);
            const changes = login ? [] : auditFieldChanges(row.diff);
            const summary = login ? formatActionLabel(row.action) : summarizeAuditDiff(row.diff, row.action);
            const loginLine = login ? formatLoginAuditLine(row.diff) : '';
            return (
              <li
                key={row.id}
                className="relative pb-5 last:pb-0"
                data-testid={`timeline-event-${row.id}`}
              >
                {row.action === 'LOGIN_SUCCESS' ? (
                  <ShieldCheck
                    className="absolute -left-[1.55rem] top-0.5 h-4 w-4 text-success"
                    aria-hidden
                    data-testid="timeline-login-success-icon"
                  />
                ) : row.action === 'LOGIN_BLOCKED_CIDR' ? (
                  <ShieldAlert
                    className="absolute -left-[1.55rem] top-0.5 h-4 w-4 text-danger"
                    aria-hidden
                    data-testid="timeline-login-blocked-icon"
                  />
                ) : (
                  <span
                    className={cn(
                      'absolute -left-[1.3rem] top-1.5 h-2.5 w-2.5 rounded-full border-2 border-surface bg-accent',
                      index === 0 && 'ring-2 ring-accent/30',
                    )}
                  />
                )}
                <p className="text-sm font-medium text-text" data-testid="timeline-action-badge">
                  {summary}
                </p>
                {loginLine ? (
                  <p className="mt-0.5 text-sm text-text-muted" data-testid="timeline-login-meta">
                    {loginLine}
                  </p>
                ) : null}
                <p className="mt-0.5 text-xs text-text-muted">
                  {formatWhen(row.createdAt)}
                  <span className="mx-1.5 text-border">·</span>
                  {actorLabel(row)}
                </p>
                {changes.length > 0 ? (
                  <ul
                    className="mt-2 divide-y divide-border overflow-hidden rounded-md border border-border bg-surface px-3"
                    data-testid="audit-diff-body"
                  >
                    {changes.slice(0, 6).map((change) => (
                      <FieldChangeRow key={change.field} change={change} />
                    ))}
                    {changes.length > 6 ? (
                      <li className="py-1.5 text-xs text-text-muted">
                        And {changes.length - 6} more {changes.length - 6 === 1 ? 'change' : 'changes'}
                      </li>
                    ) : null}
                  </ul>
                ) : null}
              </li>
            );
          })}
        </ol>
      )}
    </section>
  );
}
