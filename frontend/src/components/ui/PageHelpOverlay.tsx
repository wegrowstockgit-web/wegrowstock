import { useEffect, useMemo, useState } from 'react';
import { createPortal } from 'react-dom';
import { useLocation } from 'react-router-dom';
import { Info, Undo2, Users, Workflow, X } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { DENSITY_STYLES } from '@/stores/preferencesStore';
import { cn } from '@/lib/utils';
import {
  resolveRouteKnowledge,
  type RouteKnowledge,
} from '@/features/support/RouteKnowledgeRegistry';

const density = DENSITY_STYLES.spacious;

function KnowledgeBody({ knowledge }: { knowledge: RouteKnowledge }) {
  return (
    <div className={cn('space-y-6 text-text', density.typography)} data-testid="page-help-body">
      <section>
        <h3 className="text-xs font-semibold uppercase tracking-wide text-text-muted">Purpose</h3>
        <p className={cn('mt-2 leading-relaxed', density.typography)}>{knowledge.purpose}</p>
      </section>

      <section>
        <h3 className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-text-muted">
          <Workflow className="h-3.5 w-3.5" aria-hidden />
          How to use this page
        </h3>
        <ol className={cn('mt-2 list-decimal space-y-2 pl-5 leading-relaxed', density.typography)}>
          {knowledge.flow.map((step) => (
            <li key={step}>{step}</li>
          ))}
        </ol>
      </section>

      <section>
        <h3 className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-text-muted">
          <Undo2 className="h-3.5 w-3.5" aria-hidden />
          Reversals & undo
        </h3>
        <ul className={cn('mt-2 list-disc space-y-2 pl-5 leading-relaxed', density.typography)}>
          {knowledge.reversals.map((item) => (
            <li key={item}>{item}</li>
          ))}
        </ul>
      </section>

      <section>
        <h3 className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-text-muted">
          <Users className="h-3.5 w-3.5" aria-hidden />
          Who else this affects
        </h3>
        <ul className={cn('mt-2 list-disc space-y-2 pl-5 leading-relaxed', density.typography)}>
          {knowledge.correlations.map((item) => (
            <li key={item}>{item}</li>
          ))}
        </ul>
      </section>

      <section>
        <h3 className="text-xs font-semibold uppercase tracking-wide text-text-muted">Key elements</h3>
        <dl className="mt-2 space-y-3">
          {Object.entries(knowledge.components).map(([name, def]) => (
            <div key={name} className={cn('rounded-md border border-border/70 bg-surface', density.cell)}>
              <dt className="font-medium text-text">{name}</dt>
              <dd className="mt-1 text-text-muted">{def}</dd>
            </div>
          ))}
        </dl>
      </section>
    </div>
  );
}

function FallbackBody({ pathname }: { pathname: string }) {
  return (
    <div className={cn('space-y-3 text-text', density.typography)} data-testid="page-help-fallback">
      <p>
        No specialized playbook is registered for <span className="font-mono text-sm">{pathname}</span>{' '}
        yet.
      </p>
      <p className="text-text-muted">
        Ask the support copilot for help, and prefer reversals that append compensating ledger rows
        (ERROR_CORRECTION / OFFLINE_CONFLICT_OVERRIDE) instead of deleting history.
      </p>
    </div>
  );
}

/**
 * Global Page Info trigger + responsive help surface.
 * Portaled to document.body so header `backdrop-filter` cannot trap `position: fixed`.
 * Mobile (&lt;md): bottom sheet. Desktop (md+): right drawer.
 */
export function PageHelpOverlay() {
  const location = useLocation();
  const [open, setOpen] = useState(false);

  const knowledge = useMemo(
    () => resolveRouteKnowledge(location.pathname),
    [location.pathname],
  );

  const title = knowledge?.title ?? 'Page info';
  const description = location.pathname;

  useEffect(() => {
    setOpen(false);
  }, [location.pathname]);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('keydown', onKey);
    const prevOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = prevOverflow;
    };
  }, [open]);

  const panel =
    open && typeof document !== 'undefined'
      ? createPortal(
          <div
            className="fixed inset-0 z-[80] pointer-events-auto"
            data-testid="page-help-overlay-root"
          >
            <button
              type="button"
              className="absolute inset-0 bg-text/40 transition-opacity duration-200"
              onClick={() => setOpen(false)}
              aria-label="Close page help"
            />
            <aside
              role="dialog"
              aria-modal="true"
              aria-labelledby="page-help-title"
              data-testid="page-help-panel"
              className={cn(
                'absolute flex flex-col border-border bg-surface-raised shadow-elevated',
                'transition-transform duration-200 ease-[cubic-bezier(0.16,1,0.3,1)]',
                'motion-reduce:transition-none',
                // Mobile: bottom sheet
                'inset-x-0 bottom-0 max-h-[min(88dvh,40rem)] rounded-t-2xl border',
                // Desktop: full-height right drawer
                'md:inset-y-0 md:right-0 md:left-auto md:bottom-auto md:h-full md:max-h-[100dvh] md:w-full md:max-w-xl md:rounded-none md:border-y-0 md:border-l md:border-r-0',
              )}
            >
              <div className="flex shrink-0 justify-center pt-3 md:hidden" aria-hidden>
                <div className="h-1.5 w-12 rounded-full bg-border" />
              </div>
              <div className="flex shrink-0 items-start justify-between gap-3 border-b border-border px-5 py-3 md:py-4">
                <div className="min-w-0">
                  <h2 id="page-help-title" className="text-lg font-semibold text-text">
                    {title}
                  </h2>
                  <p className="mt-0.5 text-sm text-text-muted">{description}</p>
                </div>
                <Button
                  variant="ghost"
                  size="sm"
                  className="min-h-11 min-w-11"
                  onClick={() => setOpen(false)}
                  aria-label="Close"
                >
                  <X className="h-4 w-4" />
                </Button>
              </div>
              <div
                className="min-h-0 flex-1 overflow-y-auto px-5 py-4 pb-8"
                data-testid="page-help-drawer"
              >
                {knowledge ? (
                  <KnowledgeBody knowledge={knowledge} />
                ) : (
                  <FallbackBody pathname={location.pathname} />
                )}
              </div>
            </aside>
          </div>,
          document.body,
        )
      : null;

  return (
    <>
      <Button
        type="button"
        variant="ghost"
        size="sm"
        className="min-h-11 min-w-11 touch-target"
        aria-label="Page info"
        title="What is this page?"
        data-testid="page-help-trigger"
        onClick={() => setOpen(true)}
      >
        <Info className="h-4 w-4" aria-hidden />
      </Button>
      {panel}
    </>
  );
}
