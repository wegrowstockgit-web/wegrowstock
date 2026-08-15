import { Link, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ArrowLeft, Cable, RefreshCw } from 'lucide-react';
import { apiClient } from '@/api/client';
import { SettingsSubpageShell } from '@/components/layout/SettingsSubpageShell';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { TableSkeleton } from '@/components/ui/Skeleton';
import { LiveConnectionBadge } from '@/features/settings/LiveConnectionBadge';
import { cn } from '@/lib/utils';

export interface IntegrationHubCard {
  id: string;
  name: string;
  status: string;
  connected: boolean;
}

export interface IntegrationHubCategory {
  id: string;
  label: string;
  integrations: IntegrationHubCard[];
}

export interface IntegrationHubStatus {
  categories: IntegrationHubCategory[];
}

function statusLabel(connected: boolean): string {
  return connected ? 'Connected' : 'Disconnected';
}

/**
 * Dedicated Settings subroute for E-commerce, Accounting, and EDI integrations.
 */
export function IntegrationsHubPage() {
  const navigate = useNavigate();

  const {
    data,
    isLoading,
    isError,
    refetch,
  } = useQuery({
    queryKey: ['integrations', 'hub'],
    queryFn: async () =>
      (await apiClient.get<IntegrationHubStatus>('/api/v1/integrations/hub')).data,
    retry: false,
  });

  const openOptions = (card: IntegrationHubCard) => {
    if (card.id === 'SHOPIFY' || card.id === 'AMAZON') {
      navigate('/settings?tab=integrations');
      return;
    }
    if (card.id === 'XERO' || card.id === 'QUICKBOOKS' || card.id === 'NETSUITE') {
      navigate('/settings?tab=accounting');
      return;
    }
    navigate('/settings?tab=operations');
  };

  return (
    <SettingsSubpageShell testId="integrations-hub-page">
      <div className="space-y-8 p-6">
        <div>
          <Link
            to="/settings"
            className="mb-3 inline-flex items-center gap-1 text-sm font-medium text-text-muted hover:text-text"
          >
            <ArrowLeft className="h-4 w-4" aria-hidden />
            All settings
          </Link>
          <h1 className="text-2xl font-bold text-text">Integrations Hub</h1>
          <p className="mt-1 text-sm text-text-muted">
            Connect e-commerce channels, accounting systems, and AS2 trading partners
          </p>
        </div>

        {isLoading && (
          <div data-testid="integrations-hub-loading">
            <TableSkeleton rows={4} cols={3} />
          </div>
        )}

        {isError && !isLoading && (
          <EmptyState
            icon={Cable}
            title="Unable to load integrations"
            description="Check your connection and try again."
            action={
              <Button onClick={() => void refetch()} data-testid="integrations-hub-retry">
                <RefreshCw className="h-4 w-4" />
                Retry
              </Button>
            }
          />
        )}

        {data?.categories.map((category) => (
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
              {category.integrations.map((card) => (
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
                          card.connected
                            ? 'bg-success/20 text-success'
                            : 'bg-surface-overlay text-text-muted',
                        )}
                      >
                        {statusLabel(card.connected)}
                      </span>
                      {card.connected && <LiveConnectionBadge />}
                    </div>
                    <p className="mt-2 text-sm text-text-muted">
                      {card.connected
                        ? 'Live connection configured for this tenant.'
                        : 'Not connected — configure credentials or trading partners to enable sync.'}
                    </p>
                  </div>
                  <div className="mt-4">
                    <Button
                      variant={card.connected ? 'secondary' : 'primary'}
                      size="sm"
                      data-testid={`integration-action-${card.id}`}
                      onClick={() => openOptions(card)}
                    >
                      {card.connected ? 'Options' : 'Connect'}
                    </Button>
                  </div>
                </article>
              ))}
            </div>
          </section>
        ))}
      </div>
    </SettingsSubpageShell>
  );
}
