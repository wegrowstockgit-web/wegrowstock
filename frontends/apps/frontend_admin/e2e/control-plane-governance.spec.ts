/**
 * Control-plane governance: throttle, feature flags, impersonation audit.
 * Requires: deploy.bat (admin :3002 / api :8081). Skips until V127 is on the live image.
 */
import { test, expect, request as pwRequest } from '@playwright/test';

const ADMIN_API = process.env.ADMIN_API_URL ?? 'http://localhost:8081';
const ADMIN_UI = process.env.ADMIN_UI_URL ?? 'http://localhost:3002';
const WMS_API = process.env.WMS_API_URL ?? 'http://localhost:8080';

async function csrfHeaders(ctx: Awaited<ReturnType<typeof pwRequest.newContext>>) {
  const token = (await ctx.storageState()).cookies.find((c) => c.name === 'XSRF-TOKEN')?.value;
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

test.describe('Control Plane governance', () => {
  test.beforeAll(async () => {
    const admin = await loginAdminApi();
    try {
      const flagsProbe = await admin.get('/api/v1/control-plane/flags');
      test.skip(!flagsProbe.ok(), 'Feature-flag control plane API not deployed');
    } finally {
      await admin.dispose();
    }
  });

  test('API: flags, throttle, impersonation filter', async () => {
    const admin = await loginAdminApi();

    const tenants = await admin.get('/api/v1/control-plane/tenants');
    expect(tenants.ok()).toBeTruthy();
    const list = (await tenants.json()) as Array<{ tenantId: string; slug?: string }>;
    const demo = list.find((t) => t.slug === 'demo-corp' || t.slug === 'demo') ?? list[0];
    const tenantId = demo.tenantId;
    const headers = await csrfHeaders(admin);

    const created = await admin.post('/api/v1/control-plane/flags', {
      headers,
      data: { flagKey: `e2e-flag-${Date.now()}`, description: 'e2e', isGlobal: false },
    });
    expect(created.ok(), await created.text()).toBeTruthy();
    const flag = await created.json();
    expect(flag.flagKey).toBeTruthy();

    const target = await admin.put(`/api/v1/control-plane/flags/${flag.id}/tenants`, {
      headers,
      data: { isGlobal: false, overrides: [{ tenantId, enabled: true }] },
    });
    expect(target.ok(), await target.text()).toBeTruthy();

    const throttle = await admin.patch(
      `/api/v1/control-plane/telemetry/tenants/${tenantId}/throttle`,
      { headers, data: { tenantId, customRateLimit: 120, isThrottled: false } },
    );
    expect(throttle.ok(), await throttle.text()).toBeTruthy();
    const body = await throttle.json();
    expect(body.isThrottled).toBe(false);
    expect(body.customRateLimit).toBe(120);

    await admin.post(`/api/v1/control-plane/tenants/${tenantId}/impersonate`, { headers });
    const audit = await admin.get('/api/v1/control-plane/audit-logs?impersonationOnly=true&limit=20');
    expect(audit.ok()).toBeTruthy();
    const logs = (await audit.json()) as Array<{ action: string; actorType?: string }>;
    expect(logs.some((row) => row.action === 'TENANT_IMPERSONATE')).toBeTruthy();
    expect(logs.every((row) => row.actorType === 'PLATFORM_ADMIN_IMPERSONATION' || row.action === 'TENANT_IMPERSONATE')).toBeTruthy();

    const wms = await pwRequest.newContext({ baseURL: WMS_API });
    const wmsFlags = await wms.get('/api/v1/feature-flags');
    expect([200, 401, 403]).toContain(wmsFlags.status());
    await wms.dispose();
    await admin.dispose();
  });

  test('admin UI: feature flags + traffic controller + impersonation filter', async ({ page }) => {
    await page.goto(`${ADMIN_UI}/login`);
    await page.getByLabel(/email/i).fill('owner@demo.test');
    await page.getByLabel(/password/i).fill('password123');
    await page.getByRole('button', { name: /sign in|log in/i }).click();
    await expect(page.getByTestId('admin-layout')).toBeVisible({ timeout: 20_000 });

    const flagsLink = page.getByRole('link', { name: /feature flags/i });
    test.skip((await flagsLink.count()) === 0, 'Feature Flags nav not deployed');
    await flagsLink.click();
    await expect(page.getByTestId('feature-flags')).toBeVisible({ timeout: 20_000 });

    await page.getByRole('link', { name: /concurrency/i }).click();
    await expect(page.getByTestId('live-traffic-controller')).toBeVisible({ timeout: 20_000 });

    await page.getByRole('link', { name: /audit trail/i }).click();
    await expect(page.getByTestId('audit-impersonation-filter')).toBeVisible({ timeout: 20_000 });
    await page.getByTestId('audit-impersonation-filter').click();
  });
});
