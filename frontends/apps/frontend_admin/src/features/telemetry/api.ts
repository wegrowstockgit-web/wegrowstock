import { apiClient } from '@/lib/apiClient';

export type TenantTelemetry = {
  tenantId: string;
  slug: string;
  status: string;
  p50LatencyMs: number;
  p95LatencyMs: number;
  capacityMultiplier: number;
};

export async function fetchTenantTelemetry(): Promise<TenantTelemetry[]> {
  const { data } = await apiClient.get<TenantTelemetry[]>(
    '/api/v1/control-plane/telemetry/tenants',
  );
  return data;
}

export async function putTenantRateLimit(
  tenantId: string,
  capacityMultiplier: number,
): Promise<TenantTelemetry> {
  const { data } = await apiClient.put<TenantTelemetry>(
    `/api/v1/control-plane/telemetry/tenants/${tenantId}/rate-limit`,
    { capacityMultiplier },
  );
  return data;
}
