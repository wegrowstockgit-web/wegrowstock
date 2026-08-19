/**
 * Phase 4/5 Control Plane — real functional checks against a running stack.
 * Requires: deploy.bat (admin :3002 / api :8081) and seed credentials.
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

test.describe('Control Plane Phase 4/5 functional', () => {
  test('admin UI surfaces new ops nav destinations', async ({ page }) => {
    await page.goto(`${ADMIN_UI}/login`);
    await page.getByLabel(/email/i).fill('owner@demo.test');
    await page.getByLabel(/password/i).fill('password123');
    await page.getByRole('button', { name: /sign in|log in/i }).click();
    await expect(page.getByTestId('admin-layout')).toBeVisible({ timeout: 20_000 });

    const destinations = [
      { name: /platform billing/i, testId: 'platform-billing' },
      { name: /pricing & packaging/i, testId: 'platform-packaging' },
      { name: /copilot knowledge/i, testId: 'copilot-knowledge' },
      { name: /webhooks & integrations/i, testId: 'integrations-hub' },
      { name: /audit trail/i, testId: 'platform-audit' },
      { name: /shard routing/i, testId: 'shard-routing' },
      { name: /dead letter queue/i, testId: 'dead-letter-queue' },
      { name: /concurrency/i, testId: 'concurrency-dashboard' },
      { name: /global compliance/i, testId: 'global-compliance' },
    ] as const;

    for (const dest of destinations) {
      await page.getByRole('link', { name: dest.name }).click();
      await expect(page.getByTestId('admin-layout')).toBeVisible();
      await expect(page.getByTestId(dest.testId)).toBeVisible({ timeout: 20_000 });
    }
  });

  test('API: impersonate, suspend 403 on WMS, billing, kill-switch, audit', async () => {
    const admin = await loginAdminApi();
    const tenants = await admin.get('/api/v1/control-plane/tenants');
    expect(tenants.ok()).toBeTruthy();
    const list = await tenants.json();
    expect(Array.isArray(list) && list.length > 0).toBeTruthy();
    const demo = list.find((t: { slug?: string }) => t.slug === 'demo-corp' || t.slug === 'demo') ?? list[0];
    const tenantId = demo.tenantId as string;

    const headers = await csrfHeaders(admin);
    const imp = await admin.post(`/api/v1/control-plane/tenants/${tenantId}/impersonate`, {
      headers,
    });
    expect(imp.ok(), await imp.text()).toBeTruthy();
    const impBody = await imp.json();
    expect(impBody.accessToken).toBeTruthy();
    expect(impBody.handoffToken || impBody.handoffCode).toBeTruthy();
    expect(impBody.redirectUrl || impBody.loginUrl).toBeTruthy();
    expect(impBody.expiresInSeconds).toBe(900);

    const packaging = await admin.get('/api/v1/control-plane/packaging/tiers');
    expect(packaging.ok()).toBeTruthy();
    const tiers = (await packaging.json()) as Array<{ tierCode: string; defaultModules: string[] }>;
    expect(tiers.some((t) => t.tierCode === 'BASIC' && t.defaultModules.includes('CORE'))).toBeTruthy();

    const billing = await admin.get('/api/v1/control-plane/billing/overview');
    expect(billing.ok()).toBeTruthy();
    const bill = await billing.json();
    expect(bill.estimatedMrr).toBeDefined();

    await admin.post(`/api/v1/control-plane/integrations/tenants/${tenantId}/kill-switch`, {
      headers,
      data: { paused: true, reason: 'e2e' },
    });

    const audit = await admin.get('/api/v1/control-plane/audit-logs?limit=20');
    expect(audit.ok()).toBeTruthy();
    const logs = await audit.json();
    expect(Array.isArray(logs)).toBeTruthy();

    // Suspend → WMS must 403 authenticated calls
    await admin.patch(`/api/v1/control-plane/tenants/${tenantId}/status`, {
      headers,
      data: { status: 'SUSPENDED' },
    });

    const wms = await pwRequest.newContext({ baseURL: WMS_API });
    const accept = await wms.post('/api/v1/auth/impersonation/accept', {
      data: { token: impBody.accessToken },
    });
    // Token may still mint session, but subsequent API should 403 if suspended
    if (accept.ok()) {
      const me = await wms.get('/api/v1/auth/me');
      expect(me.status()).toBe(403);
    }

    // Restore
    await admin.patch(`/api/v1/control-plane/tenants/${tenantId}/status`, {
      headers,
      data: { status: 'ACTIVE' },
    });
    await admin.post(`/api/v1/control-plane/integrations/tenants/${tenantId}/kill-switch`, {
      headers,
      data: { paused: false },
    });

    await admin.dispose();
    await wms.dispose();
  });

  test('API: clone-sandbox provisions a UAT tenant and one-time key', async () => {
    const admin = await loginAdminApi();
    const tenants = await admin.get('/api/v1/control-plane/tenants');
    expect(tenants.ok()).toBeTruthy();
    const list = (await tenants.json()) as Array<{ tenantId: string; slug: string; status: string }>;
    const demo =
      list.find((t) => t.slug === 'demo-corp' || t.slug === 'demo') ??
      list.find((t) => t.status === 'ACTIVE');
    expect(demo).toBeTruthy();

    const headers = await csrfHeaders(admin);
    const clone = await admin.post(`/api/v1/control-plane/tenants/${demo!.tenantId}/clone-sandbox`, {
      headers,
    });
    expect(clone.ok(), await clone.text()).toBeTruthy();
    const body = await clone.json();
    expect(body.sandboxSlug).toMatch(/^uat-/);
    expect(body.apiKey).toMatch(/^sk_uat_/);
    await admin.dispose();
  });
});
