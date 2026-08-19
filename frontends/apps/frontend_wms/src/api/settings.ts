import { apiClient } from '@/api/client';

export interface CurrentNetworkInfo {
  clientIp: string;
  suggestedCidr: string;
  isPrivateNetwork: boolean;
  networkHint: string;
}

export async function fetchCurrentNetworkInfo(): Promise<CurrentNetworkInfo> {
  return (await apiClient.get<CurrentNetworkInfo>('/api/v1/settings/network/current-ip')).data;
}
