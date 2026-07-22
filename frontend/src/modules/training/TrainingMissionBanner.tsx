import { useTrainingSandboxStore, TRAINING_SCENARIOS } from './trainingSandboxStore';
import { Button } from '@/components/ui/Button';
import { cn } from '@/lib/utils';

/**
 * Interactive training overlay — never mutates production stock.
 */
export function TrainingMissionBanner() {
  const activeScenarioId = useTrainingSandboxStore((s) => s.activeScenarioId);
  const stepIndex = useTrainingSandboxStore((s) => s.stepIndex);
  const lastFeedback = useTrainingSandboxStore((s) => s.lastFeedback);
  const completed = useTrainingSandboxStore((s) => s.completed);
  const blockedCount = useTrainingSandboxStore((s) => s.blockedMutations.length);
  const stopScenario = useTrainingSandboxStore((s) => s.stopScenario);

  if (!activeScenarioId) return null;
  const scenario = TRAINING_SCENARIOS[activeScenarioId];
  const step = scenario.steps[Math.min(stepIndex, scenario.steps.length - 1)];

  return (
    <div
      className={cn(
        'fixed inset-x-0 top-0 z-[80] border-b border-amber-600/50 px-4 py-3 shadow-sm',
        'bg-[repeating-linear-gradient(45deg,#f59e0b_0px,#f59e0b_10px,#111827_10px,#111827_20px)]',
      )}
      data-testid="training-mission-banner"
      role="status"
    >
      <div className="mx-auto flex max-w-5xl flex-col gap-2 rounded-md bg-surface-raised/95 px-3 py-2 sm:flex-row sm:items-center sm:justify-between">
        <div className="min-w-0">
          <p className="text-xs font-semibold uppercase tracking-wide text-amber-700 dark:text-amber-400">
            ⚠️ TRAINING SIMULATOR ACTIVE: NO DATA WILL BE SAVED
          </p>
          <p className="truncate text-sm font-medium text-text">{scenario.title}</p>
          <p className="text-sm text-text-muted">
            {completed
              ? 'Mission complete. Exit training when ready.'
              : `Step ${Math.min(stepIndex + 1, scenario.steps.length)}/${scenario.steps.length}: ${step?.instruction}`}
          </p>
          {lastFeedback ? (
            <p className="mt-1 text-sm text-success" data-testid="training-mission-feedback">
              {lastFeedback}
            </p>
          ) : null}
          {blockedCount > 0 ? (
            <p className="mt-1 text-xs text-text-muted" data-testid="training-blocked-writes">
              {blockedCount} live write{blockedCount === 1 ? '' : 's'} intercepted locally
            </p>
          ) : null}
        </div>
        <Button
          type="button"
          size="sm"
          variant="secondary"
          className="shrink-0"
          data-testid="training-mission-exit"
          onClick={() => stopScenario()}
        >
          Exit training
        </Button>
      </div>
    </div>
  );
}
