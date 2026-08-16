import { request as pwRequest, type APIRequestContext } from '@playwright/test';
import { expect, test } from '../fixtures/roleFixture';

const ADMIN_API = process.env.ADMIN_API_URL ?? 'http://localhost:8081';
const WMS_API = process.env.WMS_API_URL ?? 'http://localhost:8080';

async function csrfHeaders(ctx: APIRequestContext): Promise<Record<string, string>> {
  await ctx.get('/api/v1/control-plane/auth/csrf');
  const state = await ctx.storageState();
  const token = state.cookies.find((c) => c.name === 'XSRF-TOKEN')?.value;
  return token ? { 'X-XSRF-TOKEN': decodeURIComponent(token) } : {};
}

async function loginAdminApi() {
  const ctx = await pwRequest.newContext({ baseURL: ADMIN_API });
  expect((await ctx.get('/api/v1/control-plane/auth/csrf')).ok()).toBeTruthy();
  const login = await ctx.post('/api/v1/control-plane/auth/login', {
    data: { email: 'owner@demo.test', password: 'password123' },
  });
  expect(login.ok(), await login.text()).toBeTruthy();
  expect((await ctx.get('/api/v1/control-plane/auth/csrf')).ok()).toBeTruthy();
  return ctx;
}

test.describe('Journey 63: Control Plane ops + admin-api', () => {
  test.setTimeout(180_000);

  test('API: impersonate, packaging, billing, kill-switch, audit, suspend', async () => {
    const admin = await loginAdminApi();
    const wms = await pwRequest.newContext({ baseURL: WMS_API });
    try {
      const tenants = await admin.get('/api/v1/control-plane/tenants');
      expect(tenants.ok(), await tenants.text()).toBeTruthy();
      const list = (await tenants.json()) as Array<{ slug?: string; tenantId: string }>;
      const demo = list.find((t) => t.slug === 'demo-corp' || t.slug === 'demo') ?? list[0];
      const tenantId = demo.tenantId;

      const imp = await admin.post(`/api/v1/control-plane/tenants/${tenantId}/impersonate`, {
        headers: await csrfHeaders(admin),
      });
      expect(imp.ok(), await imp.text()).toBeTruthy();
      const impBody = (await imp.json()) as { accessToken?: string; expiresInSeconds?: number };
      expect(impBody.accessToken).toBeTruthy();
      expect(impBody.expiresInSeconds).toBe(900);

      const packaging = await admin.get('/api/v1/control-plane/packaging/tiers');
      expect(packaging.ok()).toBeTruthy();
      const tiers = (await packaging.json()) as Array<{ tierCode: string; defaultModules: string[] }>;
      expect(tiers.some((t) => t.tierCode === 'BASIC' && t.defaultModules.includes('CORE'))).toBeTruthy();

      const billing = await admin.get('/api/v1/control-plane/billing/overview');
      expect(billing.ok()).toBeTruthy();
      expect((await billing.json()).estimatedMrr).toBeDefined();

      const audit = await admin.get('/api/v1/control-plane/audit-logs?limit=20');
      expect(audit.ok()).toBeTruthy();
      expect(Array.isArray(await audit.json())).toBeTruthy();

      const traffic = await admin.get('/api/v1/control-plane/integrations/traffic');
      expect(traffic.ok(), await traffic.text()).toBeTruthy();

      const kill = await admin.post(
        `/api/v1/control-plane/integrations/tenants/${tenantId}/kill-switch`,
        { headers: await csrfHeaders(admin), data: { paused: true, reason: 'e2e' } },
      );
      expect(kill.ok(), `${kill.status()} ${await kill.text()}`).toBeTruthy();

      const suspend = await admin.patch(`/api/v1/control-plane/tenants/${tenantId}/status`, {
        headers: await csrfHeaders(admin),
        data: { status: 'SUSPENDED' },
      });
      expect(suspend.ok(), `${suspend.status()} ${await suspend.text()}`).toBeTruthy();

      const accept = await wms.post('/api/v1/auth/impersonation/accept', {
        data: { token: impBody.accessToken },
      });
      if (accept.ok()) {
        expect((await wms.get('/api/v1/auth/me')).status()).toBe(403);
      }

      expect(
        (
          await admin.patch(`/api/v1/control-plane/tenants/${tenantId}/status`, {
            headers: await csrfHeaders(admin),
            data: { status: 'ACTIVE' },
          })
        ).ok(),
      ).toBeTruthy();
      expect(
        (
          await admin.post(`/api/v1/control-plane/integrations/tenants/${tenantId}/kill-switch`, {
            headers: await csrfHeaders(admin),
            data: { paused: false },
          })
        ).ok(),
      ).toBeTruthy();
    } finally {
      await admin.dispose();
      await wms.dispose();
    }
  });
});
