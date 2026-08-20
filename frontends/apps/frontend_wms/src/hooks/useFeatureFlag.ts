import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/api/client';

export type FeatureFlagsResponse = {
  flags: string[];
};

export async function fetchEnabledFeatureFlags(): Promise<string[]> {
  const { data } = await apiClient.get<FeatureFlagsResponse>('/api/v1/feature-flags');
  return data.flags ?? [];
}

/**
 * Bootstrap hook for the signed-in tenant's progressive-delivery flags.
 */
export function useFeatureFlag(flagKey?: string) {
  const query = useQuery({
    queryKey: ['feature-flags'],
    queryFn: fetchEnabledFeatureFlags,
    staleTime: 60_000,
  });
  const flags = query.data ?? [];
  const isEnabled = (key: string) => flags.includes(key);
  return {
    flags,
    isEnabled: flagKey ? isEnabled(flagKey) : false,
    hasFlag: isEnabled,
    isLoading: query.isLoading,
    isError: query.isError,
  };
}
