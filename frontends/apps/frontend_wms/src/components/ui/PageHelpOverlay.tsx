import { useEffect, useMemo, useState } from 'react';
import { createPortal } from 'react-dom';
import { useLocation } from 'react-router-dom';
import { BookOpen, Info, Layers, Undo2, Users, Workflow, X } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { DENSITY_STYLES } from '@/stores/preferencesStore';
import { cn } from '@/lib/utils';
import {
  knowledgeContextKey,
  normalizeColumns,
  resolveKnowledgeContext,
  type ResolvedRouteKnowledge,
  type RouteKnowledgeComponent,
} from '@/lib/pageKnowledge';

const density = DENSITY_STYLES.spacious;

function StatusBadges({ statuses }: { statuses: Record<string, string> }) {
  return (
    <ul className="mt-2 space-y-1.5" data-testid="page-help-statuses">
      {Object.entries(statuses).map(([code, meaning]) => (
        <li key={code} className="flex flex-col gap-0.5 sm:flex-row sm:items-baseline sm:gap-2">
          <span
            className={cn(
              'inline-flex w-fit shrink-0 rounded-md px-2 py-0.5 font-mono text-xs font-semibold',
              'bg-muted/50 text-text',
            )}
          >
            {code}
          </span>
          <span className="text-sm text-text-muted">{meaning}</span>
        </li>
      ))}
    </ul>
  );
}

function ComponentCard({ component }: { component: RouteKnowledgeComponent }) {
  const columns = normalizeColumns(component.columns);
  return (
    <div
      className={cn('rounded-md border border-border/70 bg-surface', density.cell)}
      data-testid="page-help-component"
    >
      <p className="font-medium text-text">{component.name}</p>
      <p className="mt-1 text-text-muted">{component.description}</p>
      <p className="mt-2 text-xs text-text-muted">
        <span className="font-semibold text-text">Where this comes from:</span>{' '}
        {component.dataOrigin}
      </p>

      {columns.length > 0 ? (
        <div className="mt-3 border-t border-border/60 pt-3">
          <p className="text-xs font-semibold uppercase tracking-wide text-text-muted">Columns</p>
          <ul className="mt-2 space-y-1.5 pl-3" data-testid="page-help-columns">
            {columns.map((col) => (
              <li key={col.name} className="text-sm">
                <span className="inline-flex rounded-md bg-muted/50 px-1.5 py-0.5 font-medium text-text">
                  {col.name}
                </span>
                <span className="text-text-muted"> — {col.purpose}</span>
              </li>
            ))}
          </ul>
        </div>
      ) : null}

      {component.statuses && Object.keys(component.statuses).length > 0 ? (
        <div className="mt-3 border-t border-border/60 pt-3">
          <p className="text-xs font-semibold uppercase tracking-wide text-text-muted">Statuses</p>
          <StatusBadges statuses={component.statuses} />
        </div>
      ) : null}
    </div>
  );
}

function KnowledgeBody({ knowledge }: { knowledge: ResolvedRouteKnowledge }) {
  const glossaryEntries = knowledge.glossary ? Object.entries(knowledge.glossary) : [];
  return (
    <div className={cn('space-y-6 text-text', density.typography)} data-testid="page-help-body">
      <section>
        <h3 className="text-xs font-semibold uppercase tracking-wide text-text-muted">Purpose</h3>
        <p className={cn('mt-2 leading-relaxed', density.typography)}>{knowledge.purpose}</p>
        <p className="mt-2 text-sm text-text-muted" data-testid="page-help-data-origin">
          <span className="font-semibold text-text">Where this comes from:</span>{' '}
          {knowledge.dataOrigin}
        </p>
        {knowledge.whoCanUse.length > 0 ? (
          <div className="mt-3 flex flex-wrap gap-1.5" data-testid="page-help-roles">
            {knowledge.whoCanUse.map((role) => (
              <span
                key={role}
                className="inline-flex rounded-md bg-muted/50 px-2 py-0.5 text-xs font-semibold text-text"
              >
                {role}
              </span>
            ))}
          </div>
        ) : null}
      </section>

      <section>
        <h3 className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-text-muted">
          <Workflow className="h-3.5 w-3.5" aria-hidden />
          Step-by-step
        </h3>
        <ol className={cn('mt-2 list-decimal space-y-2 pl-5 leading-relaxed', density.typography)}>
          {knowledge.stepByStepFlow.map((step) => (
            <li key={step}>{step}</li>
          ))}
        </ol>
      </section>

      <section>
        <h3 className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-text-muted">
          <Undo2 className="h-3.5 w-3.5" aria-hidden />
          How to undo a mistake
        </h3>
        <ul className={cn('mt-2 list-disc space-y-2 pl-5 leading-relaxed', density.typography)}>
          {knowledge.howToUndo.map((item) => (
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
        <h3 className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-text-muted">
          <Layers className="h-3.5 w-3.5" aria-hidden />
          Components, columns & statuses
        </h3>
        <div className="mt-2 space-y-3">
          {knowledge.components.map((component) => (
            <ComponentCard key={component.name} component={component} />
          ))}
        </div>
      </section>

      {glossaryEntries.length > 0 ? (
        <section data-testid="page-help-glossary">
          <h3 className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-text-muted">
            <BookOpen className="h-3.5 w-3.5" aria-hidden />
            Glossary
          </h3>
          <ul className="mt-2 space-y-1.5">
            {glossaryEntries.map(([term, meaning]) => (
              <li key={term} className="flex flex-col gap-0.5 sm:flex-row sm:items-baseline sm:gap-2">
                <span className="inline-flex w-fit shrink-0 rounded-md bg-muted/50 px-2 py-0.5 font-mono text-xs font-semibold text-text">
                  {term}
                </span>
                <span className="text-sm text-text-muted">{meaning}</span>
              </li>
            ))}
          </ul>
        </section>
      ) : null}
    </div>
  );
}

function FallbackBody({ routeKey }: { routeKey: string }) {
  return (
    <div className={cn('space-y-3 text-text', density.typography)} data-testid="page-help-fallback">
      <p>
        No specialized playbook is registered for <span className="font-mono text-sm">{routeKey}</span>{' '}
        yet.
      </p>
      <p className="text-text-muted">
        Prefer on-screen Undo, Cancel, or Un-allocate actions — never erase inventory history.
      </p>
    </div>
  );
}

/**
 * Global Page Info trigger + responsive help surface.
 * Portaled to document.body so header `backdrop-filter` cannot trap `position: fixed`.
 * Content stays bound to pathname + search while open (settings tabs cross-fade in place).
 */
export function PageHelpOverlay() {
  const location = useLocation();
  const [open, setOpen] = useState(false);

  const routeKey = useMemo(
    () => knowledgeContextKey(location.pathname, location.search),
    [location.pathname, location.search],
  );

  const knowledge = useMemo(
    () => resolveKnowledgeContext(location.pathname, location.search),
    [location.pathname, location.search],
  );

  const title = knowledge?.title ?? 'Page info';

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('keydown', onKey);
    // Non-modal panel: do not lock body scroll so tab navigation remains usable.
    return () => document.removeEventListener('keydown', onKey);
  }, [open]);

  const panel =
    open && typeof document !== 'undefined'
      ? createPortal(
          <div
            className="fixed inset-0 z-[80] pointer-events-none"
            data-testid="page-help-overlay-root"
          >
            {/* Soft dim only — pointer-events none so settings tabs / nav stay clickable while open. */}
            <div
              className="absolute inset-0 bg-text/25 transition-opacity duration-200 md:bg-text/15"
              aria-hidden
            />
            <aside
              role="dialog"
              aria-modal="false"
              aria-labelledby="page-help-title"
              data-testid="page-help-panel"
              className={cn(
                'pointer-events-auto absolute flex flex-col border-border bg-surface-raised shadow-elevated',
                'transition-transform duration-200 ease-[cubic-bezier(0.16,1,0.3,1)]',
                'motion-reduce:transition-none',
                'inset-x-0 bottom-0 max-h-[min(88dvh,40rem)] rounded-t-2xl border',
                'md:inset-y-0 md:right-0 md:left-auto md:bottom-auto md:h-full md:max-h-[100dvh] md:w-full md:max-w-xl md:rounded-none md:border-y-0 md:border-l md:border-r-0',
              )}
            >
              <div className="flex shrink-0 justify-center pt-3 md:hidden" aria-hidden>
                <div className="h-1.5 w-12 rounded-full bg-border" />
              </div>
              <div className="flex shrink-0 items-start justify-between gap-3 border-b border-border px-5 py-3 md:py-4">
                <div className="min-w-0">
                  <h2
                    id="page-help-title"
                    className="text-lg font-semibold text-text"
                    data-testid="page-help-title"
                  >
                    {title}
                  </h2>
                  <p className="mt-0.5 font-mono text-sm text-text-muted" data-testid="page-help-route">
                    {routeKey}
                  </p>
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
                {/* Keyed body cross-fades when pathname/search resolves a new playbook. */}
                <div
                  key={routeKey}
                  className="page-help-fade"
                  data-testid="page-help-context"
                  data-route-key={routeKey}
                >
                  {knowledge ? (
                    <KnowledgeBody knowledge={knowledge} />
                  ) : (
                    <FallbackBody routeKey={routeKey} />
                  )}
                </div>
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
