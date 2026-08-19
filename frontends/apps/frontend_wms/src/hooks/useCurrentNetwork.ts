import { useQuery } from '@tanstack/react-query';
import { fetchCurrentNetworkInfo, type CurrentNetworkInfo } from '@/api/settings';

export function useCurrentNetwork() {
  const query = useQuery({
    queryKey: ['current-network'],
    queryFn: fetchCurrentNetworkInfo,
    retry: false,
  });

  return {
    networkInfo: query.data as CurrentNetworkInfo | undefined,
    isLoading: query.isLoading,
    error: query.error,
    refresh: query.refetch,
  };
}
