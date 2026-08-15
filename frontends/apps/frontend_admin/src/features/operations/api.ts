import { apiClient } from '@/lib/apiClient';

export type DeadLetterGroup = {
  tenantId: string;
  count: number;
  latestAt: string | null;
};

export type DeadLetterDetail = {
  id: string;
  tenantId: string;
  aggregateType: string;
  aggregateId: string;
  eventType: string;
  status: string;
  retryCount: number;
  lastError: string | null;
  createdAt: string;
  nextAttemptAt: string | null;
};

export async function fetchDeadLetterGroups(): Promise<DeadLetterGroup[]> {
  const { data } = await apiClient.get<DeadLetterGroup[]>(
    '/api/v1/control-plane/queues/dead-letters',
  );
  return data;
}

export async function fetchDeadLetter(id: string): Promise<DeadLetterDetail> {
  const { data } = await apiClient.get<DeadLetterDetail>(
    `/api/v1/control-plane/queues/dead-letters/${id}`,
  );
  return data;
}

export async function retryDeadLetter(id: string): Promise<DeadLetterDetail> {
  const { data } = await apiClient.post<DeadLetterDetail>(
    `/api/v1/control-plane/queues/dead-letters/${id}/retry`,
  );
  return data;
}
