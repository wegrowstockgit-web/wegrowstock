import type { TenantThrottleRequestDto } from '@invsys/shared-types';
import { apiClient } from '@/lib/apiClient';

export type TenantTelemetry = {
  tenantId: string;
  slug: string;
  status: string;
  p50LatencyMs: number;
  p95LatencyMs: number;
  capacityMultiplier: number;
  customRateLimit?: number | null;
  isThrottled?: boolean;
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

export async function patchTenantThrottle(
  tenantId: string,
  payload: TenantThrottleRequestDto,
): Promise<TenantTelemetry> {
  const { data } = await apiClient.patch<TenantTelemetry>(
    `/api/v1/control-plane/telemetry/tenants/${tenantId}/throttle`,
    payload,
  );
  return data;
}
