import { apiClient } from '@/lib/apiClient';

export type ShardRoute = {
  tenantId: string;
  shardKey: string;
  jdbcUrl: string | null;
  auroraCluster: string | null;
  region: string;
  notes: string | null;
  updatedAt: string | null;
};

export type ShardUpsertRequest = {
  shardKey?: string;
  jdbcUrl?: string | null;
  auroraCluster?: string | null;
  region?: string;
  notes?: string | null;
};

export async function fetchShards(): Promise<ShardRoute[]> {
  const { data } = await apiClient.get<ShardRoute[]>('/api/v1/control-plane/shards');
  return data;
}

export async function fetchShard(tenantId: string): Promise<ShardRoute> {
  const { data } = await apiClient.get<ShardRoute>(`/api/v1/control-plane/shards/${tenantId}`);
  return data;
}

export async function putShard(tenantId: string, body: ShardUpsertRequest): Promise<ShardRoute> {
  const { data } = await apiClient.put<ShardRoute>(
    `/api/v1/control-plane/shards/${tenantId}`,
    body,
  );
  return data;
}
