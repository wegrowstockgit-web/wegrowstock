import { apiClient } from '@/lib/apiClient';

export type ComplianceBroadcast = {
  id: string;
  category: string;
  title: string;
  payloadJson: string;
  active: boolean;
  createdAt: string;
};

export type CreateBroadcastRequest = {
  category: string;
  title: string;
  payload?: Record<string, unknown>;
};

export async function fetchComplianceBroadcasts(): Promise<ComplianceBroadcast[]> {
  const { data } = await apiClient.get<ComplianceBroadcast[]>(
    '/api/v1/control-plane/compliance/broadcasts',
  );
  return data;
}

export async function createComplianceBroadcast(
  body: CreateBroadcastRequest,
): Promise<ComplianceBroadcast> {
  const { data } = await apiClient.post<ComplianceBroadcast>(
    '/api/v1/control-plane/compliance/broadcasts',
    body,
  );
  return data;
}

export async function activateComplianceBroadcast(id: string): Promise<ComplianceBroadcast> {
  const { data } = await apiClient.post<ComplianceBroadcast>(
    `/api/v1/control-plane/compliance/broadcasts/${id}/activate`,
  );
  return data;
}
