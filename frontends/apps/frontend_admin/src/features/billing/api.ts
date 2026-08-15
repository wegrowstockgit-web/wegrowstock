import { apiClient } from '@/lib/apiClient';

export type UsageLimits = {
  shippedLinesPerMonth: number;
  warehouses: number;
};

export type TenantBillingCard = {
  tenantId: string;
  slug: string;
  tier: string;
  cardStatus: string;
  shippedLinesUsage: number;
  usageLimits: UsageLimits;
};

export type BillingOverview = {
  estimatedMrr: number;
  tenants: TenantBillingCard[];
};

export async function fetchBillingOverview(): Promise<BillingOverview> {
  const { data } = await apiClient.get<BillingOverview>('/api/v1/control-plane/billing/overview');
  return data;
}
