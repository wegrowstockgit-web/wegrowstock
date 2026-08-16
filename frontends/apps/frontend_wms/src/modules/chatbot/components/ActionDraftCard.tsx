import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/Button';
import { cn } from '@/lib/utils';
import type { SupportActionDraft } from '../supportChatApi';

const RESTRICTED_KEYS = new Set([
  'supportAction',
  'tenantId',
  'tenant_id',
  'actorUserId',
  'actor_user_id',
]);

type DraftStatus = 'pending' | 'approved' | 'cancelled' | 'failed';

export type ActionDraftCardProps = {
  draft: SupportActionDraft;
  status?: DraftStatus;
  busy?: boolean;
  onApprove: (draft: SupportActionDraft) => void | Promise<void>;
  onCancel?: () => void;
};

function editableEntries(payload: Record<string, unknown> | null | undefined): [string, string][] {
  if (!payload || typeof payload !== 'object') return [];
  return Object.entries(payload)
    .filter(([key, value]) => {
      if (RESTRICTED_KEYS.has(key)) return false;
      const t = typeof value;
      return t === 'string' || t === 'number' || t === 'boolean';
    })
    .map(([key, value]) => [key, String(value)]);
}

/**
 * Interactive inline HITL form for Support Co-Pilot action drafts.
 */
export function ActionDraftCard({
  draft,
  status = 'pending',
  busy = false,
  onApprove,
  onCancel,
}: ActionDraftCardProps) {
  const { t } = useTranslation();
  const initial = useMemo(() => editableEntries(draft.payload ?? undefined), [draft.payload]);
  const [fields, setFields] = useState<Record<string, string>>(() =>
    Object.fromEntries(initial),
  );

  const mergedDraft = (): SupportActionDraft => {
    const nextPayload: Record<string, unknown> = { ...(draft.payload ?? {}) };
    for (const [key, value] of Object.entries(fields)) {
      if (RESTRICTED_KEYS.has(key)) continue;
      const original = draft.payload?.[key];
      if (typeof original === 'number') {
        const n = Number(value);
        nextPayload[key] = Number.isFinite(n) ? n : value;
      } else if (typeof original === 'boolean') {
        nextPayload[key] = value === 'true' || value === '1';
      } else {
        nextPayload[key] = value;
      }
    }
    return { ...draft, payload: nextPayload };
  };

  return (
    <div
      className={cn(
        'ml-2 rounded-lg border border-accent/30 bg-accent/10 p-3',
        status === 'approved' && 'border-success/40 bg-success/10',
      )}
      data-testid="support-action-draft"
    >
      <p className="text-sm font-semibold text-text">{draft.title}</p>
      <p className="mt-1 text-sm text-text-muted">{draft.description}</p>

      {status === 'pending' && initial.length > 0 ? (
        <div className="mt-3 space-y-2" data-testid="support-draft-fields">
          {initial.map(([key]) => (
            <label key={key} className="block text-xs text-text-muted">
              <span className="mb-1 block font-medium text-text">{key}</span>
              <input
                data-testid={`support-draft-field-${key}`}
                className="min-h-10 w-full rounded-md border border-border bg-background px-2 text-sm text-text"
                value={fields[key] ?? ''}
                onChange={(e) => setFields((prev) => ({ ...prev, [key]: e.target.value }))}
                disabled={busy}
              />
            </label>
          ))}
        </div>
      ) : null}

      {status === 'approved' ? (
        <p className="mt-2 text-xs font-semibold text-success" data-testid="support-draft-approved">
          {t('chat.executed')}
        </p>
      ) : status === 'failed' ? (
        <p className="mt-2 text-xs font-semibold text-danger" data-testid="support-draft-failed">
          {t('chat.draftFailed')}
        </p>
      ) : status === 'cancelled' ? null : (
        <div className="mt-3 flex flex-wrap gap-2">
          <Button
            type="button"
            size="sm"
            data-testid="support-draft-approve"
            disabled={busy}
            onClick={() => void onApprove(mergedDraft())}
          >
            {busy ? t('chat.working') : t('chat.approveExecute')}
          </Button>
          {onCancel ? (
            <Button
              type="button"
              size="sm"
              variant="secondary"
              data-testid="support-draft-cancel"
              disabled={busy}
              onClick={onCancel}
            >
              {t('common.cancel')}
            </Button>
          ) : null}
        </div>
      )}
    </div>
  );
}
