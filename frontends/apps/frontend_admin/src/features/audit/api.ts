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
  actorType?: string | null;
};

export function isImpersonationAudit(row: AuditLogRow): boolean {
  return (
    row.actorType === 'PLATFORM_ADMIN_IMPERSONATION' || row.action === 'TENANT_IMPERSONATE'
  );
}

export async function fetchAuditLogs(
  limit = 50,
  impersonationOnly = false,
): Promise<AuditLogRow[]> {
  const { data } = await apiClient.get<AuditLogRow[]>('/api/v1/control-plane/audit-logs', {
    params: { limit, impersonationOnly },
  });
  return data;
}
