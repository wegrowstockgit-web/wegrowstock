import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@/api/client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
  },
}));

import { apiClient } from '@/api/client';
import {
  autoProvisionAccounts,
  fetchIntegrationAuthUrl,
  fetchIntegrationStatus,
  fetchLedgerAccounts,
  saveAccountMappings,
  saveIntegrationApiKey,
  testIntegrationSync,
} from './integrations';

describe('integrations api', () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: [] } as never);
    vi.mocked(apiClient.post).mockResolvedValue({ data: { ok: true } } as never);
    vi.mocked(apiClient.put).mockResolvedValue({ data: {} } as never);
  });

  it('calls accounting and oauth endpoints', async () => {
    await fetchLedgerAccounts('QUICKBOOKS');
    await autoProvisionAccounts('XERO');
    await fetchIntegrationAuthUrl('QUICKBOOKS');
    await fetchIntegrationStatus('XERO');
    await testIntegrationSync('QUICKBOOKS');
    await saveAccountMappings([{ system: 'QUICKBOOKS', accountType: 'COGS', externalAccountId: '1' }]);
    await saveIntegrationApiKey('XERO', 'secret');

    expect(apiClient.get).toHaveBeenCalledWith('/api/v1/integrations/accounting/accounts', {
      params: { provider: 'QUICKBOOKS' },
    });
    expect(apiClient.post).toHaveBeenCalledWith(
      '/api/v1/integrations/accounting/accounts/auto-provision',
      { provider: 'XERO' },
    );
    expect(apiClient.get).toHaveBeenCalledWith('/api/v1/integrations/QUICKBOOKS/auth-url');
    expect(apiClient.get).toHaveBeenCalledWith('/api/v1/integrations/XERO/status');
    expect(apiClient.post).toHaveBeenCalledWith('/api/v1/integrations/QUICKBOOKS/test-sync');
    expect(apiClient.put).toHaveBeenCalledWith('/api/v1/integrations/accounting/mappings/bulk', {
      mappings: [{ system: 'QUICKBOOKS', accountType: 'COGS', externalAccountId: '1' }],
    });
    expect(apiClient.post).toHaveBeenCalledWith('/api/v1/settings/integration-credentials', {
      system: 'XERO',
      apiKey: 'secret',
    });
  });
});
