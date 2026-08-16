import { apiClient } from '@/lib/apiClient';
import type { AppModule, PlatformTierDefinition } from '@invsys/shared-types';

export async function fetchTierDefinitions(): Promise<PlatformTierDefinition[]> {
  const { data } = await apiClient.get<PlatformTierDefinition[]>(
    '/api/v1/control-plane/packaging/tiers',
  );
  return data;
}

export async function putTierDefinition(
  tierCode: string,
  defaultModules: AppModule[],
): Promise<PlatformTierDefinition> {
  const { data } = await apiClient.put<PlatformTierDefinition>(
    `/api/v1/control-plane/packaging/tiers/${tierCode}`,
    { defaultModules },
  );
  return data;
}
