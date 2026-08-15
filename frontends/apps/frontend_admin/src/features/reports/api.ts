import { apiClient } from '@/lib/apiClient';
import type { AppModule, CommercialTier } from '@invsys/shared-types';

export type TierDistributionRow = {
  tier: CommercialTier;
  count: number;
};

export type ModuleAdoptionRow = {
  module: AppModule;
  tenantCount: number;
};

export type GmvRow = {
  month: string;
  gmv: number;
};

export type CommercialReport = {
  tierDistribution: TierDistributionRow[];
  moduleAdoption: ModuleAdoptionRow[];
  gmvByMonth: GmvRow[];
};

export type WebhookFailureRow = {
  tenantSlug: string;
  endpoint: string;
  failures24h: number;
  lastError: string;
};

export type RateLimitRow = {
  tenantSlug: string;
  route: string;
  hits24h: number;
};

export type LedgerGrowthRow = {
  tenantSlug: string;
  entries30d: number;
  totalEntries: number;
};

export type HealthReport = {
  webhookFailures: WebhookFailureRow[];
  rateLimitHits: RateLimitRow[];
  ledgerGrowth: LedgerGrowthRow[];
};

export async function fetchCommercialReport(): Promise<CommercialReport> {
  const { data } = await apiClient.get<CommercialReport>('/api/v1/control-plane/reports/commercial');
  return data;
}

export async function fetchHealthReport(): Promise<HealthReport> {
  const { data } = await apiClient.get<HealthReport>('/api/v1/control-plane/reports/health');
  return data;
}
