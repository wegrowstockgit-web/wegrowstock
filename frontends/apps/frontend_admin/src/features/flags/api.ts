import type { FeatureFlagDto, TenantFeatureFlagOverrideDto } from '@invsys/shared-types';
import { apiClient } from '@/lib/apiClient';

export async function fetchFeatureFlags(): Promise<FeatureFlagDto[]> {
  const { data } = await apiClient.get<FeatureFlagDto[]>('/api/v1/control-plane/flags');
  return data;
}

export async function createFeatureFlag(payload: {
  flagKey: string;
  description?: string;
  isGlobal?: boolean;
}): Promise<FeatureFlagDto> {
  const { data } = await apiClient.post<FeatureFlagDto>('/api/v1/control-plane/flags', payload);
  return data;
}

export async function putFeatureFlagTenants(
  flagId: string,
  payload: { isGlobal?: boolean; overrides: TenantFeatureFlagOverrideDto[] },
): Promise<FeatureFlagDto> {
  const { data } = await apiClient.put<FeatureFlagDto>(
    `/api/v1/control-plane/flags/${flagId}/tenants`,
    payload,
  );
  return data;
}
