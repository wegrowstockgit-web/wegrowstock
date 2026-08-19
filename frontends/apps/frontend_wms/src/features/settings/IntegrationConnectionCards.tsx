import { Button } from '@/components/ui/Button';
import { LiveConnectionBadge } from '@/features/settings/LiveConnectionBadge';
import { hubStatusLabel } from '@/features/settings/integrationProviders';
import { cn } from '@/lib/utils';

export interface IntegrationHubCard {
  id: string;
  name: string;
  status: string;
  connected: boolean;
  lastSyncAt?: string;
  errorCount?: number;
  tokenExpiringSoon?: boolean;
}

export interface IntegrationHubCategory {
  id: string;
  label: string;
  integrations: IntegrationHubCard[];
}

function formatSync(value?: string): string {
  if (!value) return 'Never synced';
  const parsed = Date.parse(value);
  if (Number.isNaN(parsed)) return 'Never synced';
  return `Last sync ${new Date(parsed).toLocaleString()}`;
}

function pillClass(status: string): string {
  if (status === 'LIVE') return 'bg-success/20 text-success';
  if (status === 'ACTION_REQUIRED') return 'bg-warning/20 text-warning';
  return 'bg-surface-overlay text-text-muted';
}

export function IntegrationConnectionCards({
  categories,
  onOpen,
}: {
  categories: IntegrationHubCategory[];
  onOpen: (card: IntegrationHubCard) => void;
}) {
  return (
    <>
      {categories.map((category) => (
        <section
          key={category.id}
          data-testid={`integrations-hub-category-${category.id}`}
          aria-labelledby={`hub-cat-${category.id}`}
        >
          <h2
            id={`hub-cat-${category.id}`}
            className="mb-3 text-xs font-semibold uppercase tracking-wider text-text-muted"
          >
            {category.label}
          </h2>
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
            {category.integrations.map((card) => {
              const label = hubStatusLabel(card.status, card.tokenExpiringSoon);
              return (
                <article
                  key={card.id}
                  data-testid={`integration-card-${card.id}`}
                  className="flex flex-col justify-between rounded-lg border border-border bg-surface-raised p-4"
                >
                  <div>
                    <div className="flex flex-wrap items-center gap-2">
                      <h3 className="text-base font-semibold text-text">{card.name}</h3>
                      <span
                        data-testid={`integration-status-${card.id}`}
                        className={cn(
                          'rounded-full px-2 py-0.5 text-xs font-medium',
                          pillClass(card.status),
                        )}
                      >
                        {label}
                      </span>
                      {card.connected && card.status === 'LIVE' && <LiveConnectionBadge />}
                      {(card.errorCount ?? 0) > 0 && (
                        <span
                          data-testid={`integration-errors-${card.id}`}
                          className="rounded-full bg-danger/15 px-2 py-0.5 text-xs font-medium text-danger"
                        >
                          {card.errorCount} errors
                        </span>
                      )}
                    </div>
                    <p className="mt-2 text-sm text-text-muted" data-testid={`integration-sync-${card.id}`}>
                      {formatSync(card.lastSyncAt)}
                    </p>
                  </div>
                  <div className="mt-4">
                    <Button
                      variant={card.connected ? 'secondary' : 'primary'}
                      size="sm"
                      data-testid={`integration-action-${card.id}`}
                      onClick={() => onOpen(card)}
                    >
                      {card.connected ? 'Configure / Options' : 'Connect'}
                    </Button>
                  </div>
                </article>
              );
            })}
          </div>
        </section>
      ))}
    </>
  );
}
