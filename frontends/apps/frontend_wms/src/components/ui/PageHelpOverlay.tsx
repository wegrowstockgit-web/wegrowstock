import { useEffect, useMemo, useState } from 'react';
import { createPortal } from 'react-dom';
import { useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
  AlertTriangle,
  BookOpen,
  Check,
  Info,
  Layers,
  Lightbulb,
  MessageCircle,
  Shield,
  Undo2,
  Users,
  Workflow,
  Wrench,
  X,
  Zap,
} from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { DENSITY_STYLES } from '@/stores/preferencesStore';
import { cn } from '@/lib/utils';
import {
  knowledgeContextKey,
  normalizeColumns,
  resolveKnowledgeContext,
  usePageKnowledge,
  type DynamicPageKnowledge,
  type PageAction,
  type ResolvedRouteKnowledge,
  type RouteKnowledgeComponent,
  type TroubleshootingStep,
} from '@/lib/pageKnowledge';
import { resolvePageActionIcon } from '@/lib/pageKnowledge/pageHelpIcons';

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
          <span className="text-sm text-text">{meaning}</span>
        </li>
      ))}
    </ul>
  );
}

function ComponentCard({ component }: { component: RouteKnowledgeComponent }) {
  const { t } = useTranslation();
  const columns = normalizeColumns(component.columns);
  return (
    <div
      className={cn('rounded-md border border-border/70 bg-surface', density.cell)}
      data-testid="page-help-component"
    >
      <p className="font-medium text-text">{component.name}</p>
      <p className="mt-1 text-text">{component.description}</p>
      <p className="mt-2 text-sm text-text">
        <span className="font-semibold">{t('pageHelp.whereFrom')}</span> {component.dataOrigin}
      </p>

      {columns.length > 0 ? (
        <div className="mt-3 border-t border-border/60 pt-3">
          <p className="text-xs font-semibold uppercase tracking-wide text-text">{t('pageHelp.columns')}</p>
          <ul className="mt-2 space-y-1.5 pl-3" data-testid="page-help-columns">
            {columns.map((col) => (
              <li key={col.name} className="text-sm">
                <span className="inline-flex rounded-md bg-muted/50 px-1.5 py-0.5 font-medium text-text">
                  {col.name}
                </span>
                <span className="text-text"> — {col.purpose}</span>
              </li>
            ))}
          </ul>
        </div>
      ) : null}

      {component.statuses && Object.keys(component.statuses).length > 0 ? (
        <div className="mt-3 border-t border-border/60 pt-3">
          <p className="text-xs font-semibold uppercase tracking-wide text-text">{t('pageHelp.statuses')}</p>
          <StatusBadges statuses={component.statuses} />
        </div>
      ) : null}
    </div>
  );
}

function ActionButton({
  action,
  onRun,
}: {
  action: PageAction;
  onRun: (route: string) => void;
}) {
  const Icon = resolvePageActionIcon(action.icon);
  const variant =
    action.variant === 'destructive'
      ? 'danger'
      : action.variant === 'primary'
        ? 'primary'
        : 'secondary';
  return (
    <Button
      type="button"
      variant={variant}
      className={cn(
        'h-auto min-h-11 w-full justify-start px-3 py-2.5 text-left active:scale-[0.98]',
        action.variant === 'primary' && 'bg-accent text-text-inverse',
      )}
      data-testid="page-help-quick-action"
      data-route={action.route}
      onClick={() => onRun(action.route)}
    >
      <Icon className="h-4 w-4 shrink-0" aria-hidden />
      <span className="min-w-0 flex-1 text-wrap">{action.label}</span>
    </Button>
  );
}

function KnowledgeBody({
  knowledge,
  onRunAction,
}: {
  knowledge: ResolvedRouteKnowledge;
  onRunAction: (route: string) => void;
}) {
  const { t } = useTranslation();
  const ns = knowledge.i18nKey;
  const field = (suffix: string, fallback: string) =>
    ns ? String(t(`pageHelp.playbooks.${ns}.${suffix}`, { defaultValue: fallback })) : fallback;

  const description = field('description', knowledge.description);
  const markdown = field('markdown', knowledge.markdown);
  const purpose = field('purpose', knowledge.purpose);
  const dataOrigin = field('dataOrigin', knowledge.dataOrigin);
  const processParagraphs = markdown.split('\n').map((line) => line.trim()).filter(Boolean);
  const quickActions = knowledge.quickActions.map((action, index) => ({
    ...action,
    label: field(`actions.${index}`, action.label),
  }));
  const troubleshooting: TroubleshootingStep[] = (knowledge.troubleshooting ?? []).map((step, index) => ({
    ...step,
    issue: field(`troubleshooting.${index}.issue`, step.issue),
    solution: field(`troubleshooting.${index}.solution`, step.solution),
    action: {
      ...step.action,
      label: field(`troubleshooting.${index}.action`, step.action.label),
    },
  }));
  const glossaryEntries = knowledge.glossary ? Object.entries(knowledge.glossary) : [];
  const roleLabels = knowledge.rolePermissions.map((code) =>
    String(t(`roles.${code}`, { defaultValue: code })),
  );
  const stepByStepFlow = knowledge.stepByStepFlow.map((step, index) =>
    field(`flow.${index}`, step),
  );
  const howToUndo = knowledge.howToUndo.map((item, index) => field(`reversals.${index}`, item));
  const correlations = knowledge.correlations.map((item, index) =>
    field(`correlations.${index}`, item),
  );

  return (
    <div className={cn('space-y-8 text-text', density.typography)}>
      <section>
        <h3 className="text-xs font-semibold uppercase tracking-wide text-text">{t('pageHelp.overview')}</h3>
        <p className={cn('mt-2 leading-relaxed', density.typography)}>{description}</p>
        <p className="mt-3 text-sm" data-testid="page-help-data-origin">
          <span className="font-semibold">{t('pageHelp.whereFrom')}</span> {dataOrigin}
        </p>
        {roleLabels.length > 0 ? (
          <div className="mt-3 flex flex-wrap gap-1.5" data-testid="page-help-roles">
            {roleLabels.map((role) => (
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
        <h3 className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-text">
          <Workflow className="h-3.5 w-3.5" aria-hidden />
          {t('pageHelp.process')}
        </h3>
        <div className={cn('mt-2 space-y-2 leading-relaxed', density.typography)} data-testid="page-help-markdown">
          {processParagraphs.map((paragraph) => (
            <p key={paragraph}>{paragraph}</p>
          ))}
        </div>
        <p className="mt-3 text-sm leading-relaxed" data-testid="page-help-purpose">
          <span className="font-semibold">{t('pageHelp.purpose')}</span> {purpose}
        </p>
      </section>

      {quickActions.length > 0 ? (
        <section data-testid="page-help-quick-actions">
          <h3 className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-text">
            <Zap className="h-3.5 w-3.5" aria-hidden />
            {t('pageHelp.takeAction')}
          </h3>
          <div className="mt-3 grid grid-cols-1 gap-2 sm:grid-cols-2">
            {quickActions.map((action) => (
              <ActionButton key={`${action.route}-${action.label}`} action={action} onRun={onRunAction} />
            ))}
          </div>
        </section>
      ) : null}

      {troubleshooting.length > 0 ? (
        <section
          className="rounded-xl bg-warning/15 px-4 py-4"
          data-testid="page-help-troubleshooting"
        >
          <h3 className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-text">
            <Wrench className="h-3.5 w-3.5" aria-hidden />
            {t('pageHelp.troubleshooting')}
          </h3>
          <ul className="mt-3 space-y-4">
            {troubleshooting.map((step) => (
              <li key={step.issue} className="space-y-2">
                <p className="font-semibold leading-snug text-text">{step.issue}</p>
                <p className="text-sm leading-relaxed text-text">{step.solution}</p>
                <ActionButton action={step.action} onRun={onRunAction} />
              </li>
            ))}
          </ul>
        </section>
      ) : null}

      <section>
        <h3 className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-text">
          <Workflow className="h-3.5 w-3.5" aria-hidden />
          {t('pageHelp.stepByStep')}
        </h3>
        <ol className={cn('mt-2 list-decimal space-y-2 pl-5 leading-relaxed', density.typography)}>
          {stepByStepFlow.map((step) => (
            <li key={step}>{step}</li>
          ))}
        </ol>
      </section>

      <section>
        <h3 className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-text">
          <Undo2 className="h-3.5 w-3.5" aria-hidden />
          {t('pageHelp.howToUndo')}
        </h3>
        <ul className={cn('mt-2 list-disc space-y-2 pl-5 leading-relaxed', density.typography)}>
          {howToUndo.map((item) => (
            <li key={item}>{item}</li>
          ))}
        </ul>
      </section>

      <section>
        <h3 className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-text">
          <Users className="h-3.5 w-3.5" aria-hidden />
          {t('pageHelp.whoElse')}
        </h3>
        <ul className={cn('mt-2 list-disc space-y-2 pl-5 leading-relaxed', density.typography)}>
          {correlations.map((item) => (
            <li key={item}>{item}</li>
          ))}
        </ul>
      </section>

      <section>
        <h3 className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-text">
          <Layers className="h-3.5 w-3.5" aria-hidden />
          {t('pageHelp.components')}
        </h3>
        <div className="mt-2 space-y-3">
          {knowledge.components.map((component) => (
            <ComponentCard key={component.name} component={component} />
          ))}
        </div>
      </section>

      {glossaryEntries.length > 0 ? (
        <section data-testid="page-help-glossary">
          <h3 className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-text">
            <BookOpen className="h-3.5 w-3.5" aria-hidden />
            {t('pageHelp.glossary')}
          </h3>
          <ul className="mt-2 space-y-1.5">
            {glossaryEntries.map(([term, meaning]) => (
              <li key={term} className="flex flex-col gap-0.5 sm:flex-row sm:items-baseline sm:gap-2">
                <span className="inline-flex w-fit shrink-0 rounded-md bg-muted/50 px-2 py-0.5 font-mono text-xs font-semibold text-text">
                  {term}
                </span>
                <span className="text-sm text-text">{meaning}</span>
              </li>
            ))}
          </ul>
        </section>
      ) : null}
    </div>
  );
}

function DynamicKnowledgeBody({ knowledge }: { knowledge: DynamicPageKnowledge }) {
  const [openMistake, setOpenMistake] = useState<string | null>(knowledge.commonMistakes[0]?.mistake ?? null);

  return (
    <div className={cn('space-y-6 text-text', density.typography)} data-testid="page-help-dynamic">
      <div className="flex flex-wrap items-center gap-2" data-testid="page-help-category-row">
        <span className="inline-flex rounded-full bg-accent/15 px-2.5 py-0.5 text-xs font-semibold text-accent">
          {knowledge.category}
        </span>
        <span className="font-mono text-xs text-text-muted">{knowledge.routePattern}</span>
      </div>

      <section>
        <p className="leading-relaxed" data-testid="page-help-summary">
          {knowledge.summary}
        </p>
      </section>

      <section
        className="rounded-xl border border-border/70 bg-muted/20 px-4 py-3"
        data-testid="page-help-privileges"
      >
        <h3 className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-text">
          <Shield className="h-3.5 w-3.5" aria-hidden />
          Required Privileges / RBAC
        </h3>
        <p className="mt-2 text-sm leading-relaxed">{knowledge.rolePrivileges}</p>
      </section>

      {knowledge.keyActions.length > 0 ? (
        <section data-testid="page-help-key-actions">
          <h3 className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-text">
            <Check className="h-3.5 w-3.5" aria-hidden />
            Key Actions
          </h3>
          <ul className="mt-3 space-y-2">
            {knowledge.keyActions.map((action) => (
              <li key={action} className="flex items-start gap-2 text-sm leading-relaxed">
                <Check className="mt-0.5 h-4 w-4 shrink-0 text-success" aria-hidden />
                <span>{action}</span>
              </li>
            ))}
          </ul>
        </section>
      ) : null}

      {knowledge.commonMistakes.length > 0 ? (
        <section data-testid="page-help-mistakes">
          <h3 className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-text">
            <AlertTriangle className="h-3.5 w-3.5" aria-hidden />
            Common Mistakes & How to Recover
          </h3>
          <ul className="mt-3 space-y-2">
            {knowledge.commonMistakes.map((item) => {
              const open = openMistake === item.mistake;
              return (
                <li key={item.mistake} className="rounded-xl border border-warning/30 bg-warning/10">
                  <button
                    type="button"
                    className="flex w-full items-start justify-between gap-3 px-3 py-2.5 text-left"
                    aria-expanded={open}
                    onClick={() => setOpenMistake(open ? null : item.mistake)}
                    data-testid="page-help-mistake-toggle"
                  >
                    <span className="font-semibold leading-snug">⚠️ {item.mistake}</span>
                    <span className="shrink-0 rounded-md bg-surface px-1.5 py-0.5 font-mono text-[10px] uppercase text-text-muted">
                      {item.requiredRole}
                    </span>
                  </button>
                  {open ? (
                    <p className="border-t border-warning/20 px-3 py-2.5 text-sm leading-relaxed">
                      {item.solution}
                    </p>
                  ) : null}
                </li>
              );
            })}
          </ul>
        </section>
      ) : null}

      {knowledge.proTip ? (
        <section
          className="rounded-xl border border-accent/25 bg-accent/10 px-4 py-3"
          data-testid="page-help-pro-tip"
        >
          <h3 className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-text">
            <Lightbulb className="h-3.5 w-3.5" aria-hidden />
            Pro Tip
          </h3>
          <p className="mt-2 text-sm leading-relaxed">💡 {knowledge.proTip}</p>
        </section>
      ) : null}
    </div>
  );
}

function FallbackBody({ routeKey }: { routeKey: string }) {
  const { t } = useTranslation();
  return (
    <div className={cn('space-y-3 text-text', density.typography)} data-testid="page-help-fallback">
      <p>
        {t('pageHelp.fallbackBody', { route: routeKey })}{' '}
        <span className="font-mono text-sm">{routeKey}</span>
      </p>
      <p>{t('pageHelp.fallbackHint')}</p>
      <Button
        type="button"
        variant="secondary"
        className="mt-2"
        data-testid="page-help-open-copilot"
        onClick={() => {
          window.dispatchEvent(new CustomEvent('invsys:open-copilot'));
        }}
      >
        <MessageCircle className="h-4 w-4" aria-hidden />
        Ask the AI Copilot
      </Button>
    </div>
  );
}

/**
 * Global Page Info trigger + responsive help surface.
 * Portaled to document.body so header `backdrop-filter` cannot trap `position: fixed`.
 * Content stays bound to pathname + search while open (settings tabs cross-fade in place).
 */
export function PageHelpOverlay() {
  const { t } = useTranslation();
  const location = useLocation();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);

  const routeKey = useMemo(
    () => knowledgeContextKey(location.pathname, location.search),
    [location.pathname, location.search],
  );

  const knowledge = useMemo(
    () => resolveKnowledgeContext(location.pathname, location.search),
    [location.pathname, location.search],
  );
  const dynamic = usePageKnowledge(location.pathname, location.search);

  const title = dynamic?.title
    ? dynamic.title
    : knowledge
      ? knowledge.i18nKey
        ? String(t(`pageHelp.playbooks.${knowledge.i18nKey}.title`, { defaultValue: knowledge.title }))
        : knowledge.title
      : t('pageHelp.fallbackTitle');

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [open]);

  const runAction = (route: string) => {
    setOpen(false);
    navigate(route);
  };

  const panel =
    open && typeof document !== 'undefined'
      ? createPortal(
          <div
            className="fixed inset-0 z-[80] pointer-events-none"
            data-testid="page-help-overlay-root"
          >
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
                  {dynamic ? (
                    <p className="mb-1 text-[11px] font-semibold uppercase tracking-wide text-accent">
                      {dynamic.category}
                    </p>
                  ) : null}
                  <h2
                    id="page-help-title"
                    className="text-lg font-semibold text-text"
                    data-testid="page-help-title"
                  >
                    {title}
                  </h2>
                  <p className="mt-0.5 font-mono text-sm text-text" data-testid="page-help-route">
                    {routeKey}
                  </p>
                </div>
                <Button
                  variant="ghost"
                  size="sm"
                  className="min-h-11 min-w-11"
                  onClick={() => setOpen(false)}
                  aria-label={t('common.close')}
                >
                  <X className="h-4 w-4" />
                </Button>
              </div>
              <div
                className="min-h-0 flex-1 overflow-y-auto px-5 py-4 pb-8"
                data-testid="page-help-drawer"
              >
                <div
                  key={routeKey}
                  className="page-help-fade"
                  data-testid="page-help-context"
                  data-route-key={routeKey}
                >
                  <div className="space-y-8" data-testid="page-help-body">
                    {dynamic ? (
                      <DynamicKnowledgeBody knowledge={dynamic} />
                    ) : knowledge ? (
                      <KnowledgeBody knowledge={knowledge} onRunAction={runAction} />
                    ) : (
                      <FallbackBody routeKey={routeKey} />
                    )}
                  </div>
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
        aria-label={t('pageHelp.trigger')}
        title={t('pageHelp.triggerTitle')}
        data-testid="page-help-trigger"
        onClick={() => setOpen(true)}
      >
        <Info className="h-4 w-4" aria-hidden />
      </Button>
      {panel}
    </>
  );
}
