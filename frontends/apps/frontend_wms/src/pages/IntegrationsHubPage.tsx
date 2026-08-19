import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ArrowLeft, Cable, RefreshCw } from 'lucide-react';
import { apiClient } from '@/api/client';
import { SettingsSubpageShell } from '@/components/layout/SettingsSubpageShell';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { TableSkeleton } from '@/components/ui/Skeleton';
import { IntegrationWizardModal } from '@/features/settings/IntegrationWizardModal';
import {
  IntegrationConnectionCards,
  type IntegrationHubCard,
  type IntegrationHubCategory,
} from '@/features/settings/IntegrationConnectionCards';

export type { IntegrationHubCard, IntegrationHubCategory };

export interface IntegrationHubStatus {
  categories: IntegrationHubCategory[];
}

/**
 * Dedicated Settings subroute for E-commerce, Accounting, and EDI integrations.
 */
export function IntegrationsHubPage() {
  const [wizardProvider, setWizardProvider] = useState<string | null>(null);

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['integrations', 'hub'],
    queryFn: async () =>
      (await apiClient.get<IntegrationHubStatus>('/api/v1/integrations/hub')).data,
    retry: false,
  });

  const openWizard = (card: IntegrationHubCard) => {
    setWizardProvider(card.id);
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

        {data?.categories && (
          <div className="space-y-8">
            <IntegrationConnectionCards categories={data.categories} onOpen={openWizard} />
          </div>
        )}
      </div>
      <IntegrationWizardModal
        provider={wizardProvider}
        open={wizardProvider !== null}
        onClose={() => setWizardProvider(null)}
      />
    </SettingsSubpageShell>
  );
}
