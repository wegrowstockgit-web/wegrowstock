import { apiClient } from '@/api/client';
import type {
  IntegrationAuthUrl,
  IntegrationConnectionStatus,
  IntegrationConnectionTest,
  LedgerAccount,
  UpdateAccountMapping,
} from '@/api/types';

export async function fetchLedgerAccounts(provider: string): Promise<LedgerAccount[]> {
  return (
    await apiClient.get<LedgerAccount[]>('/api/v1/integrations/accounting/accounts', {
      params: { provider },
    })
  ).data;
}

export async function autoProvisionAccounts(provider: string): Promise<LedgerAccount[]> {
  return (
    await apiClient.post<LedgerAccount[]>('/api/v1/integrations/accounting/accounts/auto-provision', {
      provider,
    })
  ).data;
}

export async function fetchIntegrationAuthUrl(provider: string): Promise<IntegrationAuthUrl> {
  return (await apiClient.get<IntegrationAuthUrl>(`/api/v1/integrations/${provider}/auth-url`)).data;
}

export async function fetchIntegrationStatus(provider: string): Promise<IntegrationConnectionStatus> {
  return (await apiClient.get<IntegrationConnectionStatus>(`/api/v1/integrations/${provider}/status`))
    .data;
}

export async function testIntegrationSync(provider: string): Promise<IntegrationConnectionTest> {
  return (await apiClient.post<IntegrationConnectionTest>(`/api/v1/integrations/${provider}/test-sync`))
    .data;
}

export async function saveAccountMappings(mappings: UpdateAccountMapping[]): Promise<void> {
  await apiClient.put('/api/v1/integrations/accounting/mappings/bulk', { mappings });
}

export async function saveIntegrationApiKey(system: string, apiKey: string): Promise<void> {
  await apiClient.post('/api/v1/settings/integration-credentials', { system, apiKey });
}
