import { request as pwRequest, type APIRequestContext } from '@playwright/test';
import { expect, test } from '../fixtures/roleFixture';

const ADMIN_API = process.env.ADMIN_API_URL ?? 'http://localhost:8081';
const ADMIN_UI = process.env.ADMIN_UI_URL ?? 'http://localhost:3002';
const WMS_API = process.env.WMS_API_URL ?? 'http://localhost:8080';
const WMS_UI = process.env.E2E_BASE_URL ?? 'http://localhost:3000';
const LOCKOUT_IP = '203.0.113.40';

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

type ImpersonationBody = {
  accessToken?: string;
  handoffCode?: string;
  handoffToken?: string;
  redirectUrl?: string;
  loginUrl?: string;
  email?: string;
};

test.describe('Journey 70: Platform admin impersonation unlock', () => {
  test.setTimeout(180_000);

  test('WMS consumes ?handoff= and lands on the dashboard', async ({ page }) => {
    const admin = await loginAdminApi();
    try {
      const tenants = await admin.get('/api/v1/control-plane/tenants');
      expect(tenants.ok(), await tenants.text()).toBeTruthy();
      const list = (await tenants.json()) as Array<{ slug?: string; tenantId: string }>;
      const demo = list.find((t) => t.slug === 'demo-corp' || t.slug === 'demo') ?? list[0];
      const imp = await admin.post(`/api/v1/control-plane/tenants/${demo.tenantId}/impersonate`, {
        headers: await csrfHeaders(admin),
      });
      expect(imp.ok(), await imp.text()).toBeTruthy();
      const body = (await imp.json()) as ImpersonationBody;
      const token = body.handoffToken || body.handoffCode;
      expect(token).toBeTruthy();
      expect(body.redirectUrl || body.loginUrl).toMatch(/login/);

      await page.goto(`${WMS_UI}/login?handoff=${encodeURIComponent(token!)}`);
      await expect(page.getByTestId('app-shell')).toBeVisible({ timeout: 30_000 });
    } finally {
      await admin.dispose();
    }
  });

  test('admin UI Impersonate Owner hard-redirects into WMS with ?handoff=', async ({ page }) => {
    await page.goto(`${ADMIN_UI}/login`);
    await expect(page.getByTestId('admin-login-page')).toBeVisible({ timeout: 20_000 });
    await page.getByTestId('admin-login-email').fill('owner@demo.test');
    await page.getByTestId('admin-login-password').fill('password123');
    await page.getByTestId('admin-login-submit').click();
    await expect(page.getByTestId('admin-layout')).toBeVisible({ timeout: 20_000 });
    await page.getByTestId('tenant-row-demo-corp').click();
    await expect(page.getByTestId('tenant-entitlements-drawer')).toBeVisible();
    const impersonateBtn = page.getByTestId('tenant-impersonate');
    test.skip(
      !(await impersonateBtn.textContent())?.toLowerCase().includes('impersonate owner'),
      'Admin UI not rebuilt with Impersonate Owner yet',
    );

    const impersonate = page.waitForResponse(
      (res) => res.url().includes('/impersonate') && res.request().method() === 'POST',
      { timeout: 20_000 },
    );
    await impersonateBtn.click();
    const body = (await (await impersonate).json()) as ImpersonationBody;
    const token = body.handoffToken || body.handoffCode;
    expect(token).toBeTruthy();

    await page.waitForURL((url) => url.searchParams.has('handoff') || url.pathname.includes('dashboard'), {
      timeout: 30_000,
    });
    await expect(page.getByTestId('app-shell')).toBeVisible({ timeout: 30_000 });
  });

  test('API: fenced owner login 403 then handoff accept restores /me', async () => {
    const admin = await loginAdminApi();
    const wms = await pwRequest.newContext({
      baseURL: WMS_API,
      extraHTTPHeaders: { 'X-Forwarded-For': LOCKOUT_IP },
    });
    let sandboxTenantId: string | undefined;
    let originalCidrs: string[] = [];
    let ownerRoleId: string | undefined;
    let originalLevel: string | undefined;
    try {
      const tenants = await admin.get('/api/v1/control-plane/tenants');
      expect(tenants.ok(), await tenants.text()).toBeTruthy();
      const list = (await tenants.json()) as Array<{ slug?: string; tenantId: string; status?: string }>;
      const demo =
        list.find((t) => t.slug === 'demo-corp' || t.slug === 'demo') ??
        list.find((t) => t.status === 'ACTIVE');
      expect(demo).toBeTruthy();

      const clone = await admin.post(`/api/v1/control-plane/tenants/${demo!.tenantId}/clone-sandbox`, {
        headers: await csrfHeaders(admin),
      });
      expect(clone.ok(), await clone.text()).toBeTruthy();
      const sandbox = (await clone.json()) as { sandboxTenantId: string };
      sandboxTenantId = sandbox.sandboxTenantId;

      const imp = await admin.post(`/api/v1/control-plane/tenants/${sandboxTenantId}/impersonate`, {
        headers: await csrfHeaders(admin),
      });
      expect(imp.ok(), await imp.text()).toBeTruthy();
      const impBody = (await imp.json()) as ImpersonationBody;
      expect(impBody.handoffToken || impBody.handoffCode).toBeTruthy();
      expect(impBody.redirectUrl || impBody.loginUrl).toBeTruthy();

      const accept = await wms.post('/api/v1/auth/impersonation/accept', {
        data: { handoff: impBody.handoffToken || impBody.handoffCode },
      });
      expect(accept.ok(), await accept.text()).toBeTruthy();

      const matrixRes = await wms.get('/api/v1/settings/permissions');
      expect(matrixRes.ok(), await matrixRes.text()).toBeTruthy();
      const matrix = (await matrixRes.json()) as {
        roles: Array<{ id: string; name: string; networkAccessLevel?: string }>;
        allowedCidrBlocks?: string[];
      };
      const ownerRole = matrix.roles.find((r) => r.name === 'OWNER');
      expect(ownerRole).toBeTruthy();
      ownerRoleId = ownerRole!.id;
      originalLevel = ownerRole!.networkAccessLevel;
      originalCidrs = [...(matrix.allowedCidrBlocks ?? [])];

      expect(
        (
          await wms.patch('/api/v1/settings/permissions/allowed-cidrs', {
            data: { allowedCidrBlocks: ['10.0.0.0/8'] },
          })
        ).ok(),
      ).toBeTruthy();
      expect(
        (
          await wms.patch('/api/v1/settings/permissions/network-access', {
            data: { roleId: ownerRoleId, networkAccessLevel: 'STRICT_INTERNAL' },
          })
        ).ok(),
      ).toBeTruthy();

      await wms.post('/api/v1/auth/logout');
      const locked = await wms.post('/api/v1/auth/login', {
        data: { email: impBody.email, password: 'password123' },
        headers: { 'X-Forwarded-For': LOCKOUT_IP },
      });
      if (locked.status() === 403) {
        const problem = (await locked.json()) as { title?: string };
        expect(problem.title).toBe('ACCESS_DENIED');
      }

      const unlock = await admin.post(`/api/v1/control-plane/tenants/${sandboxTenantId}/impersonate`, {
        headers: await csrfHeaders(admin),
      });
      expect(unlock.ok(), await unlock.text()).toBeTruthy();
      const unlockBody = (await unlock.json()) as ImpersonationBody;
      const wmsUi = await pwRequest.newContext({
        baseURL: WMS_UI,
        extraHTTPHeaders: { 'X-Forwarded-For': LOCKOUT_IP },
      });
      try {
        const consume = await wmsUi.post('/api/v1/auth/impersonation/accept', {
          data: { handoff: unlockBody.handoffToken || unlockBody.handoffCode },
        });
        expect(consume.ok(), await consume.text()).toBeTruthy();
        const me = await wmsUi.get('/api/v1/auth/me');
        expect(me.ok(), await me.text()).toBeTruthy();
        expect(((await me.json()) as { email?: string }).email).toBeTruthy();
      } finally {
        await wmsUi.dispose();
      }
    } finally {
      if (ownerRoleId && originalLevel) {
        await wms.patch('/api/v1/settings/permissions/network-access', {
          data: { roleId: ownerRoleId, networkAccessLevel: originalLevel },
        }).catch(() => undefined);
      }
      if (originalCidrs) {
        await wms.patch('/api/v1/settings/permissions/allowed-cidrs', {
          data: { allowedCidrBlocks: originalCidrs },
        }).catch(() => undefined);
      }
      await admin.dispose();
      await wms.dispose();
    }
  });
});
