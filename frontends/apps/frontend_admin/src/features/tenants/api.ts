import { apiClient } from '@/lib/apiClient';
import type { AppModule, CommercialTier, ControlPlaneTenant } from '@invsys/shared-types';

export async function fetchTenants(): Promise<ControlPlaneTenant[]> {
  const { data } = await apiClient.get<ControlPlaneTenant[]>('/api/v1/control-plane/tenants');
  return data;
}

export async function patchTenantModules(
  tenantId: string,
  enabledModules: AppModule[],
): Promise<ControlPlaneTenant> {
  const { data } = await apiClient.patch<ControlPlaneTenant>(
    `/api/v1/control-plane/tenants/${tenantId}/modules`,
    { enabledModules },
  );
  return data;
}

export async function patchTenantTier(
  tenantId: string,
  tier: CommercialTier,
): Promise<ControlPlaneTenant> {
  const { data } = await apiClient.patch<ControlPlaneTenant>(
    `/api/v1/control-plane/tenants/${tenantId}/tier`,
    { tier },
  );
  return data;
}

export type ImpersonationResponse = {
  accessToken: string;
  expiresInSeconds: number;
  loginUrl: string;
  email: string;
};

export async function impersonateTenant(tenantId: string): Promise<ImpersonationResponse> {
  const { data } = await apiClient.post<ImpersonationResponse>(
    `/api/v1/control-plane/tenants/${tenantId}/impersonate`,
  );
  return data;
}

export type TenantStatus = 'ACTIVE' | 'SUSPENDED';

export type TenantStatusView = {
  tenantId: string;
  name: string;
  slug: string;
  status: TenantStatus;
};

export async function patchTenantStatus(
  tenantId: string,
  status: TenantStatus,
): Promise<TenantStatusView> {
  const { data } = await apiClient.patch<TenantStatusView>(
    `/api/v1/control-plane/tenants/${tenantId}/status`,
    { status },
  );
  return data;
}

export type SandboxCredentials = {
  sourceTenantId: string;
  sandboxTenantId: string;
  sandboxSlug: string;
  apiKey: string;
  apiKeyHint: string;
};

export async function cloneSandbox(tenantId: string): Promise<SandboxCredentials> {
  const { data } = await apiClient.post<SandboxCredentials>(
    `/api/v1/control-plane/tenants/${tenantId}/clone-sandbox`,
  );
  return data;
}

export function buildWmsImpersonationUrl(accessToken: string, loginUrl?: string): string {
  const base = (import.meta.env.VITE_WMS_APP_URL as string | undefined)?.replace(/\/$/, '')
    || 'http://localhost:3000';
  if (base.includes('localhost') || base.includes('127.0.0.1')) {
    return `${base}/login?impersonateToken=${encodeURIComponent(accessToken)}`;
  }
  if (loginUrl) return loginUrl;
  return `${base}/login?impersonateToken=${encodeURIComponent(accessToken)}`;
}

export type AdminLoginResponse = {
  email: string;
};

export async function adminLogin(email: string, password: string): Promise<AdminLoginResponse> {
  const { data } = await apiClient.post<AdminLoginResponse>('/api/v1/control-plane/auth/login', {
    email,
    password,
  });
  return data;
}

export async function adminLogout(): Promise<void> {
  await apiClient.post('/api/v1/control-plane/auth/logout');
}
