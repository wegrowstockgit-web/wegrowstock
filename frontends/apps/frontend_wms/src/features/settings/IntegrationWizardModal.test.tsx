import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { IntegrationWizardModal } from './IntegrationWizardModal';

vi.mock('@/api/client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
  },
}));

vi.mock('@/api/integrations', () => ({
  fetchLedgerAccounts: vi.fn(),
  autoProvisionAccounts: vi.fn(),
  fetchIntegrationAuthUrl: vi.fn(),
  fetchIntegrationStatus: vi.fn(),
  testIntegrationSync: vi.fn(),
  saveAccountMappings: vi.fn(),
  saveIntegrationApiKey: vi.fn(),
}));

import { apiClient } from '@/api/client';
import {
  autoProvisionAccounts,
  fetchIntegrationAuthUrl,
  fetchLedgerAccounts,
  saveAccountMappings,
  testIntegrationSync,
} from '@/api/integrations';

function renderWizard(provider = 'QUICKBOOKS') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <IntegrationWizardModal provider={provider} open onClose={vi.fn()} />
    </QueryClientProvider>,
  );
}

describe('IntegrationWizardModal', () => {
  beforeEach(() => {
    vi.mocked(fetchLedgerAccounts).mockResolvedValue([
      { accountId: 'inv-1', name: 'Inventory Asset', type: 'Other Current Asset', classification: 'Asset', code: '12000' },
      { accountId: 'cogs-1', name: 'Cost of Goods Sold', type: 'COGS', classification: 'Expense', code: '50000' },
      { accountId: 'rev-1', name: 'Sales Revenue', type: 'Income', classification: 'Revenue', code: '40000' },
      { accountId: 'tax-1', name: 'Sales Tax Payable', type: 'Other Current Liability', classification: 'Liability', code: '22000' },
    ]);
    vi.mocked(fetchIntegrationAuthUrl).mockResolvedValue({
      authorizationUrl: 'https://appcenter.intuit.com/connect/oauth2?state=abc',
      state: 'abc',
      provider: 'QUICKBOOKS',
    });
    vi.mocked(autoProvisionAccounts).mockResolvedValue([
      { accountId: 'std-12000', name: '12000 - Inventory Asset', type: 'Other Current Asset', classification: 'ASSET', code: '12000' },
    ]);
    vi.mocked(saveAccountMappings).mockResolvedValue();
    vi.mocked(testIntegrationSync).mockResolvedValue({
      ok: true,
      readOk: true,
      writeOk: true,
      message: 'QuickBooks read/write permissions verified',
    });
    vi.mocked(apiClient.put).mockResolvedValue({ data: {} } as never);
    vi.stubGlobal('location', { ...window.location, assign: vi.fn() });
  });

  it('walks through oauth, mapping recommendations, and test sync', async () => {
    const user = userEvent.setup();
    renderWizard();

    expect(screen.getByTestId('wizard-step-auth')).toBeInTheDocument();
    expect(screen.getByTestId('wizard-no-account-banner')).toHaveTextContent('$35');
    expect(screen.getByTestId('wizard-signup-link')).toHaveAttribute(
      'href',
      'https://quickbooks.intuit.com/signup/',
    );

    await user.click(screen.getByTestId('wizard-manual-toggle'));
    expect(screen.getByTestId('wizard-manual-setup')).toBeInTheDocument();

    await user.click(screen.getByTestId('wizard-oauth-connect'));
    await waitFor(() => {
      expect(fetchIntegrationAuthUrl).toHaveBeenCalledWith('QUICKBOOKS');
      expect(window.location.assign).toHaveBeenCalledWith(
        'https://appcenter.intuit.com/connect/oauth2?state=abc',
      );
    });

    await user.click(screen.getByTestId('wizard-continue-mapping'));
    expect(await screen.findByTestId('wizard-step-mapping')).toBeInTheDocument();
    await waitFor(() => expect(fetchLedgerAccounts).toHaveBeenCalledWith('QUICKBOOKS'));
    expect(await screen.findByLabelText('Inventory Asset')).toHaveValue('inv-1');

    await user.click(screen.getByTestId('wizard-create-standard-accounts'));
    await waitFor(() => expect(autoProvisionAccounts).toHaveBeenCalledWith('QUICKBOOKS'));

    await user.click(screen.getByTestId('wizard-confirm-mappings'));
    await waitFor(() => expect(saveAccountMappings).toHaveBeenCalled());
    expect(await screen.findByTestId('wizard-step-health')).toBeInTheDocument();

    await user.click(screen.getByTestId('wizard-test-sync'));
    expect(await screen.findByTestId('wizard-message')).toHaveTextContent('permissions verified');
  });
});
