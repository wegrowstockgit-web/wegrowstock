import { AlertTriangle, Check, Lightbulb, Shield } from 'lucide-react';
import type { PageHelpWritePayload } from './pageHelpApi';

type Props = {
  draft: PageHelpWritePayload;
};

/** Live preview that mirrors the WMS PageHelpOverlay dynamic body. */
export function PageHelpPreview({ draft }: Props) {
  return (
    <div
      className="flex h-full min-h-[28rem] flex-col overflow-hidden rounded-xl border border-border bg-surface-raised shadow-elevated"
      data-testid="page-help-preview"
    >
      <div className="border-b border-border px-4 py-3">
        <p className="text-[11px] font-semibold uppercase tracking-wide text-accent">
          {draft.category || 'Category'}
        </p>
        <h3 className="mt-0.5 text-lg font-semibold text-text">{draft.title || 'Untitled page'}</h3>
        <p className="mt-0.5 font-mono text-xs text-text-muted">{draft.routePattern || '/route'}</p>
      </div>
      <div className="min-h-0 flex-1 space-y-5 overflow-y-auto px-4 py-4 text-sm text-text">
        <p className="leading-relaxed text-text-muted">
          {draft.summary || 'Summary appears here as operators will see it in weGrowStock.'}
        </p>
        <section className="rounded-lg border border-border bg-surface px-3 py-2.5">
          <p className="flex items-center gap-2 text-[11px] font-semibold uppercase tracking-wide text-text-muted">
            <Shield className="h-3.5 w-3.5" aria-hidden />
            Required Privileges / RBAC
          </p>
          <p className="mt-1.5 leading-relaxed">{draft.rolePrivileges || 'Who can use this page…'}</p>
        </section>
        <section>
          <p className="flex items-center gap-2 text-[11px] font-semibold uppercase tracking-wide text-text-muted">
            <Check className="h-3.5 w-3.5" aria-hidden />
            Key Actions
          </p>
          <ul className="mt-2 space-y-1.5">
            {(draft.keyActions.length ? draft.keyActions : ['Add a key action']).map((action, index) => (
              <li key={`${action}-${index}`} className="flex items-start gap-2">
                <Check className="mt-0.5 h-4 w-4 shrink-0 text-success" aria-hidden />
                <span>{action || 'Empty action'}</span>
              </li>
            ))}
          </ul>
        </section>
        <section>
          <p className="flex items-center gap-2 text-[11px] font-semibold uppercase tracking-wide text-text-muted">
            <AlertTriangle className="h-3.5 w-3.5" aria-hidden />
            Common Mistakes & How to Recover
          </p>
          <ul className="mt-2 space-y-2">
            {(draft.commonMistakes.length
              ? draft.commonMistakes
              : [{ mistake: 'Example fat-finger', solution: 'Post a reversing ledger entry.', requiredRole: 'WAREHOUSE_MANAGER' }]
            ).map((item, index) => (
              <li key={`${item.mistake}-${index}`} className="rounded-lg border border-warning/30 bg-warning/10 px-3 py-2">
                <p className="font-medium">⚠️ {item.mistake || 'Mistake'}</p>
                <p className="mt-1 text-xs uppercase tracking-wide text-text-muted">{item.requiredRole}</p>
                <p className="mt-1 text-text-muted">{item.solution || 'How to recover…'}</p>
              </li>
            ))}
          </ul>
        </section>
        {draft.proTip ? (
          <section className="rounded-lg border border-accent/25 bg-accent/10 px-3 py-2.5">
            <p className="flex items-center gap-2 text-[11px] font-semibold uppercase tracking-wide text-text-muted">
              <Lightbulb className="h-3.5 w-3.5" aria-hidden />
              Pro Tip
            </p>
            <p className="mt-1.5">💡 {draft.proTip}</p>
          </section>
        ) : null}
      </div>
    </div>
  );
}
