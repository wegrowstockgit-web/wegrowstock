import { apiClient } from '@/lib/apiClient';

export type TrafficRow = {
  tenantId: string;
  status: string;
  eventCount: number;
};

export type KillSwitchView = {
  tenantId: string;
  paused: boolean;
  reason: string | null;
  updatedAt: string;
};

export async function fetchIntegrationTraffic(): Promise<TrafficRow[]> {
  const { data } = await apiClient.get<TrafficRow[]>('/api/v1/control-plane/integrations/traffic');
  return data;
}

export async function setIntegrationKillSwitch(
  tenantId: string,
  paused: boolean,
  reason?: string,
): Promise<KillSwitchView> {
  const { data } = await apiClient.post<KillSwitchView>(
    `/api/v1/control-plane/integrations/tenants/${tenantId}/kill-switch`,
    { paused, reason },
  );
  return data;
}
