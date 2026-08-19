import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ChevronDown, ExternalLink } from 'lucide-react';
import type { LedgerAccount } from '@/api/types';
import {
  autoProvisionAccounts,
  fetchIntegrationAuthUrl,
  fetchLedgerAccounts,
  saveAccountMappings,
  saveIntegrationApiKey,
  testIntegrationSync,
} from '@/api/integrations';
import { apiClient } from '@/api/client';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Modal } from '@/components/ui/Modal';
import { providerMeta } from '@/features/settings/integrationProviders';
import { REQUIRED_ACCOUNT_TYPES, suggestAccountMappings } from '@/utils/accountAutoMatcher';

const MAPPING_LABELS: Record<string, string> = {
  INVENTORY_ASSET: 'Inventory Asset',
  COGS: 'Cost of Goods Sold',
  SALES_REVENUE: 'Sales Revenue',
  TAX: 'Sales Tax Payable',
};

function SearchableAccountSelect({
  label,
  accounts,
  value,
  onChange,
}: {
  label: string;
  accounts: LedgerAccount[];
  value: string;
  onChange: (accountId: string) => void;
}) {
  const [query, setQuery] = useState('');
  const selected = accounts.find((account) => account.accountId === value);
  const filtered = accounts.filter((account) => {
    const hay = `${account.name} ${account.code} ${account.type}`.toLowerCase();
    return hay.includes(query.trim().toLowerCase());
  });

  return (
    <div className="space-y-1.5" data-testid={`mapping-select-${label}`}>
      <label className="text-sm font-medium text-text">{label}</label>
      <Input
        value={query || selected?.name || ''}
        onChange={(e) => setQuery(e.target.value)}
        placeholder="Search accounts"
        aria-label={`Search ${label}`}
      />
      <select
        className="h-10 w-full rounded-md border border-border bg-surface-raised px-3 text-sm"
        value={value}
        onChange={(e) => {
          onChange(e.target.value);
          setQuery('');
        }}
        aria-label={label}
        data-testid={`mapping-account-${label}`}
      >
        <option value="">Select an account</option>
        {filtered.map((account) => (
          <option key={account.accountId} value={account.accountId}>
            {account.code ? `${account.code} — ${account.name}` : account.name}
          </option>
        ))}
      </select>
    </div>
  );
}

export function IntegrationWizardModal({
  provider,
  open,
  onClose,
}: {
  provider: string | null;
  open: boolean;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const meta = provider ? providerMeta(provider) : null;
  const [step, setStep] = useState(1);
  const [manualOpen, setManualOpen] = useState(false);
  const [apiKey, setApiKey] = useState('');
  const [mappings, setMappings] = useState<Record<string, string>>({});
  const [alertEmail, setAlertEmail] = useState('');
  const [slackWebhookUrl, setSlackWebhookUrl] = useState('');
  const [message, setMessage] = useState('');

  useEffect(() => {
    if (open) {
      setStep(1);
      setManualOpen(false);
      setApiKey('');
      setMappings({});
      setMessage('');
    }
  }, [open, provider]);

  const accountsQuery = useQuery({
    queryKey: ['integrations', 'accounting', 'accounts', provider],
    queryFn: () => fetchLedgerAccounts(provider!),
    enabled: open && !!provider && meta?.mapping === true && step >= 2,
    retry: false,
  });

  useEffect(() => {
    if (!accountsQuery.data?.length) return;
    setMappings((prev) => {
      if (Object.values(prev).some(Boolean)) return prev;
      return suggestAccountMappings(accountsQuery.data ?? []);
    });
  }, [accountsQuery.data]);

  const connectMutation = useMutation({
    mutationFn: async () => fetchIntegrationAuthUrl(provider!),
    onSuccess: (data) => {
      window.location.assign(data.authorizationUrl);
    },
    onError: () => setMessage('Could not start OAuth. Try the manual API key option.'),
  });

  const vaultMutation = useMutation({
    mutationFn: async () => saveIntegrationApiKey(provider!, apiKey.trim()),
    onSuccess: () => {
      setMessage('API key saved to the vault.');
      setApiKey('');
      void queryClient.invalidateQueries({ queryKey: ['integrations'] });
    },
    onError: () => setMessage('Could not save the API key.'),
  });

  const provisionMutation = useMutation({
    mutationFn: async () => autoProvisionAccounts(provider!),
    onSuccess: (accounts) => {
      void queryClient.setQueryData(['integrations', 'accounting', 'accounts', provider], accounts);
      setMappings(suggestAccountMappings(accounts));
      setMessage('Standard accounts created in ' + (meta?.label ?? provider));
    },
    onError: () => setMessage('Could not create standard accounts.'),
  });

  const saveMappingsMutation = useMutation({
    mutationFn: async () => {
      const payload = REQUIRED_ACCOUNT_TYPES.filter((type) => mappings[type]).map((accountType) => ({
        system: provider!,
        accountType,
        externalAccountId: mappings[accountType],
      }));
      if (payload.length) {
        await saveAccountMappings(payload);
      }
    },
    onSuccess: () => setStep(3),
  });

  const testMutation = useMutation({
    mutationFn: async () => testIntegrationSync(provider!),
    onSuccess: (result) => {
      setMessage(result.message || (result.ok ? 'Sync permissions look healthy.' : 'Sync test failed.'));
    },
    onError: () => setMessage('Test sync failed.'),
  });

  const alertsMutation = useMutation({
    mutationFn: async () => {
      const body: { alertEmail: string; slackWebhookUrl?: string } = { alertEmail: alertEmail.trim() };
      if (slackWebhookUrl.trim()) body.slackWebhookUrl = slackWebhookUrl.trim();
      await apiClient.put('/api/v1/settings/alert-preferences', body);
    },
    onSuccess: () => setMessage('Failure alerts saved.'),
    onError: () => setMessage('Could not save alert preferences.'),
  });

  const accounts = accountsQuery.data ?? [];
  const stepTitle = useMemo(() => {
    if (!meta) return 'Connect integration';
    if (step === 1) return `Connect ${meta.label}`;
    if (step === 2) return `Map ${meta.label} accounts`;
    return `Test ${meta.label}`;
  }, [meta, step]);

  if (!provider || !meta) {
    return null;
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      size="lg"
      title={stepTitle}
      description={`Step ${step} of 3 — ${meta.label}`}
    >
      <div className="space-y-4" data-testid="integration-wizard" data-provider={provider} data-step={step}>
        <ol className="flex gap-2 text-xs font-medium text-text-muted" aria-label="Wizard steps">
          {['Authentication', 'Mapping', 'Health check'].map((label, index) => (
            <li
              key={label}
              className={step === index + 1 ? 'text-accent' : undefined}
              data-testid={`wizard-step-label-${index + 1}`}
            >
              {index + 1}. {label}
            </li>
          ))}
        </ol>

        {step === 1 && (
          <div className="space-y-4" data-testid="wizard-step-auth">
            {meta.oauth && (
              <Button
                data-testid="wizard-oauth-connect"
                loading={connectMutation.isPending}
                onClick={() => connectMutation.mutate()}
              >
                Connect with {meta.label}
              </Button>
            )}
            <div
              className="rounded-md border border-accent/30 bg-accent/5 p-3 text-sm"
              data-testid="wizard-no-account-banner"
            >
              <p className="font-medium text-text">Don&apos;t have an account?</p>
              <p className="mt-1 text-text-muted">{meta.pricing}</p>
              <a
                href={meta.signupUrl}
                target="_blank"
                rel="noreferrer"
                className="mt-2 inline-flex items-center gap-1 text-sm font-medium text-accent"
                data-testid="wizard-signup-link"
              >
                Create a {meta.label} account
                <ExternalLink className="h-3.5 w-3.5" aria-hidden />
              </a>
            </div>
            <div>
              <button
                type="button"
                className="inline-flex items-center gap-1 text-sm text-text-muted"
                data-testid="wizard-manual-toggle"
                onClick={() => setManualOpen((openManual) => !openManual)}
              >
                <ChevronDown className="h-4 w-4" aria-hidden />
                Manual API Key / Advanced IT Setup
              </button>
              {manualOpen && (
                <div className="mt-3 space-y-3" data-testid="wizard-manual-setup">
                  <Input
                    label="API key / client secret"
                    type="password"
                    value={apiKey}
                    onChange={(e) => setApiKey(e.target.value)}
                    autoComplete="off"
                  />
                  <Button
                    variant="secondary"
                    disabled={!apiKey.trim()}
                    loading={vaultMutation.isPending}
                    onClick={() => vaultMutation.mutate()}
                    data-testid="wizard-save-api-key"
                  >
                    Save to vault
                  </Button>
                </div>
              )}
            </div>
            <div className="flex justify-end gap-2">
              <Button variant="secondary" onClick={() => setStep(2)} data-testid="wizard-continue-mapping">
                Continue to mapping
              </Button>
            </div>
          </div>
        )}

        {step === 2 && (
          <div className="space-y-4" data-testid="wizard-step-mapping">
            {meta.mapping ? (
              <>
                <div className="flex flex-wrap gap-2">
                  <Button
                    variant="secondary"
                    size="sm"
                    loading={provisionMutation.isPending}
                    onClick={() => provisionMutation.mutate()}
                    data-testid="wizard-create-standard-accounts"
                  >
                    + Create standard accounts in {meta.label}
                  </Button>
                </div>
                {accountsQuery.isLoading && <p className="text-sm text-text-muted">Loading chart of accounts…</p>}
                {REQUIRED_ACCOUNT_TYPES.map((type) => (
                  <SearchableAccountSelect
                    key={type}
                    label={MAPPING_LABELS[type]}
                    accounts={accounts}
                    value={mappings[type] ?? ''}
                    onChange={(accountId) => setMappings((prev) => ({ ...prev, [type]: accountId }))}
                  />
                ))}
              </>
            ) : (
              <p className="text-sm text-text-muted">
                {meta.label} does not require ledger mapping. Continue to run a health check.
              </p>
            )}
            <div className="flex justify-between gap-2">
              <Button variant="ghost" onClick={() => setStep(1)}>
                Back
              </Button>
              <Button
                loading={saveMappingsMutation.isPending}
                onClick={() => (meta.mapping ? saveMappingsMutation.mutate() : setStep(3))}
                data-testid="wizard-confirm-mappings"
              >
                Confirm
              </Button>
            </div>
          </div>
        )}

        {step === 3 && (
          <div className="space-y-4" data-testid="wizard-step-health">
            <Button
              loading={testMutation.isPending}
              onClick={() => testMutation.mutate()}
              data-testid="wizard-test-sync"
            >
              Test Sync
            </Button>
            <div className="grid gap-3 sm:grid-cols-2">
              <Input
                label="Failure alert email"
                type="email"
                value={alertEmail}
                onChange={(e) => setAlertEmail(e.target.value)}
                placeholder="ops@yourcompany.com"
              />
              <Input
                label="Slack webhook"
                type="url"
                value={slackWebhookUrl}
                onChange={(e) => setSlackWebhookUrl(e.target.value)}
                placeholder="https://hooks.slack.com/services/…"
              />
            </div>
            <Button
              variant="secondary"
              loading={alertsMutation.isPending}
              onClick={() => alertsMutation.mutate()}
              data-testid="wizard-save-alerts"
            >
              Save diagnostic alerts
            </Button>
            <div className="flex justify-between gap-2">
              <Button variant="ghost" onClick={() => setStep(2)}>
                Back
              </Button>
              <Button onClick={onClose} data-testid="wizard-done">
                Done
              </Button>
            </div>
          </div>
        )}

        {message && (
          <p className="text-sm text-text-muted" data-testid="wizard-message">
            {message}
          </p>
        )}
      </div>
    </Modal>
  );
}
