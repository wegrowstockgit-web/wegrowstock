import { apiClient } from '@/lib/apiClient';

export type AuditLogRow = {
  id: string;
  adminId: string;
  adminEmail: string;
  action: string;
  targetTenantId: string | null;
  diffJson: string | null;
  ipAddress: string | null;
  createdAt: string;
};

export async function fetchAuditLogs(limit = 50): Promise<AuditLogRow[]> {
  const { data } = await apiClient.get<AuditLogRow[]>('/api/v1/control-plane/audit-logs', {
    params: { limit },
  });
  return data;
}
