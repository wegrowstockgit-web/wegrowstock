import { request as pwRequest, type APIRequestContext, type Page } from '@playwright/test';
import {
  completeScannerPin,
  dismissOnboardingTourIfPresent,
  expect,
  test,
} from '../fixtures/roleFixture';
import { contextForRole } from './helpers';

const ADMIN_API = process.env.ADMIN_API_URL ?? 'http://localhost:8081';
const ADMIN_UI = process.env.ADMIN_UI_URL ?? 'http://localhost:3002';
const ADMIN_PASSWORD = process.env.E2E_DEMO_PASSWORD ?? 'password123';

/** Same order as `@invsys/shared-types` APP_MODULES and backend `AppModule`. */
const APP_MODULES = [
  'CORE',
  'SHOPIFY',
  'ACCOUNTING',
  'ADVANCED_FULFILLMENT',
  'MANUFACTURING',
  'DOCUMENTS',
  'MRP',
  'B2B_SHOWROOM',
  'FINTECH',
  'MESH_NETWORK',
  'RTLS_TELEMETRY',
  'AI_COPILOT',
  'RETAIL_POS',
] as const;

type ControlPlaneTenant = {
  tenantId: string;
  name: string;
  slug: string;
  status: string;
  tier: string;
  enabledModules: string[];
};

type MeEntitlements = {
  enabledModules?: string[];
  tier?: string | null;
};

function sorted(values: string[] | undefined): string[] {
  return [...(values ?? [])].map((value) => value.toUpperCase()).sort();
}

async function csrfHeaders(ctx: APIRequestContext): Promise<Record<string, string>> {
  await ctx.get('/api/v1/control-plane/auth/csrf');
  const state = await ctx.storageState();
  const token = state.cookies.find((c) => c.name === 'XSRF-TOKEN')?.value;
  return token ? { 'X-XSRF-TOKEN': decodeURIComponent(token) } : {};
}

async function loginAdminApi(): Promise<APIRequestContext> {
  const ctx = await pwRequest.newContext({ baseURL: ADMIN_API });
  expect((await ctx.get('/api/v1/control-plane/auth/csrf')).ok()).toBeTruthy();
  const login = await ctx.post('/api/v1/control-plane/auth/login', {
    data: { email: 'owner@demo.test', password: ADMIN_PASSWORD },
  });
  expect(login.ok(), await login.text()).toBeTruthy();
  expect((await ctx.get('/api/v1/control-plane/auth/csrf')).ok()).toBeTruthy();
  return ctx;
}

async function fetchDemo(admin: APIRequestContext): Promise<ControlPlaneTenant> {
  const tenants = await admin.get('/api/v1/control-plane/tenants');
  expect(tenants.ok(), await tenants.text()).toBeTruthy();
  const list = (await tenants.json()) as ControlPlaneTenant[];
  const demo = list.find((row) => row.slug === 'demo-corp' || row.slug === 'demo');
  expect(demo, 'expected demo-corp in control-plane tenant list').toBeTruthy();
  return demo!;
}

async function restoreDemo(admin: APIRequestContext, snapshot: ControlPlaneTenant): Promise<void> {
  const csrf = await admin.get('/api/v1/control-plane/auth/csrf');
  if (!csrf.ok()) {
    const login = await admin.post('/api/v1/control-plane/auth/login', {
      data: { email: 'owner@demo.test', password: ADMIN_PASSWORD },
    });
    expect(login.ok(), `re-login for restore failed: ${await login.text()}`).toBeTruthy();
  }
  const tier = await admin.patch(`/api/v1/control-plane/tenants/${snapshot.tenantId}/tier`, {
    headers: await csrfHeaders(admin),
    data: { tier: snapshot.tier },
  });
  expect(tier.ok(), `restore tier failed: ${tier.status()} ${await tier.text()}`).toBeTruthy();
  const modules = await admin.patch(`/api/v1/control-plane/tenants/${snapshot.tenantId}/modules`, {
    headers: await csrfHeaders(admin),
    data: { enabledModules: snapshot.enabledModules },
  });
  expect(modules.ok(), `restore modules failed: ${modules.status()} ${await modules.text()}`).toBeTruthy();
}

async function loginAdminUi(page: Page): Promise<void> {
  await page.goto(`${ADMIN_UI}/login`);
  await expect(page.getByTestId('admin-login-page')).toBeVisible({ timeout: 20_000 });
  await page.getByTestId('admin-login-email').fill('owner@demo.test');
  await page.getByTestId('admin-login-password').fill(ADMIN_PASSWORD);
  await page.getByTestId('admin-login-submit').click();
  await expect(page.getByTestId('admin-layout')).toBeVisible({ timeout: 20_000 });
  await expect(page.getByTestId('tenant-manager')).toBeVisible();
}

async function pinOfficeRail(page: Page): Promise<void> {
  await page.evaluate(() => {
    localStorage.setItem('invsys-icon-rail', JSON.stringify({ state: { pinned: true }, version: 0 }));
  });
  await page.reload();
  await completeScannerPin(page);
  await dismissOnboardingTourIfPresent(page);
}

async function closeDrawer(page: Page): Promise<void> {
  const close = page.getByRole('button', { name: /^close( drawer)?$/i }).first();
  if (await close.isVisible().catch(() => false)) {
    await close.click();
  }
  await expect(page.getByTestId('tenant-entitlements-drawer')).toHaveCount(0);
}

async function waitForMeModules(page: Page, expected: string[]): Promise<MeEntitlements> {
  let latest: MeEntitlements = {};
  await expect
    .poll(
      async () => {
        const res = await page.request.get('/api/v1/auth/me');
        if (!res.ok()) return [];
        latest = (await res.json()) as MeEntitlements;
        return sorted(latest.enabledModules);
      },
      { timeout: 20_000, message: 'WMS /me enabledModules did not catch up to control plane' },
    )
    .toEqual(sorted(expected));
  return latest;
}

/**
 * Journey 66 — Admin entitlements drawer is the same catalog as WMS session + API gates.
 */
test.describe('Journey 66: Control Plane entitlements ↔ WMS', () => {
  test.setTimeout(180_000);

  test('admin drawer catalog matches Demo Corp /me, sidebar, and Fintech API', async ({
    browser,
    page,
  }) => {
    const admin = await loginAdminApi();
    try {
      const demo = await fetchDemo(admin);

      await loginAdminUi(page);
      await page.getByTestId('tenant-row-demo-corp').click();
      await expect(page.getByTestId('tenant-entitlements-drawer')).toBeVisible({ timeout: 15_000 });
      await expect(page.getByText(new RegExp(`demo-corp · ${demo.tier} ·`, 'i'))).toBeVisible();

      for (const moduleName of APP_MODULES) {
        const toggle = page.getByTestId(`module-toggle-${moduleName}`);
        await expect(toggle, `missing admin toggle ${moduleName}`).toBeVisible();
        if (moduleName === 'CORE') {
          await expect(toggle).toBeDisabled();
        }
        expect(
          await toggle.isChecked(),
          `admin ${moduleName} should match control-plane enabledModules`,
        ).toBe(demo.enabledModules.map((value) => value.toUpperCase()).includes(moduleName));
      }

      const wms = await contextForRole(browser, 'owner');
      try {
        await wms.page.setViewportSize({ width: 1280, height: 800 });
        await dismissOnboardingTourIfPresent(wms.page);

        const me = await waitForMeModules(wms.page, demo.enabledModules);
        expect((me.tier ?? '').toUpperCase()).toBe(demo.tier.toUpperCase());

        await wms.page.goto('/dashboard');
        await completeScannerPin(wms.page);
        await pinOfficeRail(wms.page);
        await expect(wms.page.getByTestId('tier-badge')).toHaveText(new RegExp(demo.tier, 'i'), {
          timeout: 15_000,
        });

        const entitled = new Set(sorted(demo.enabledModules));
        if (entitled.has('MESH_NETWORK')) {
          await expect(wms.page.getByTestId('nav-mesh-network')).toBeVisible({ timeout: 15_000 });
          await wms.page.goto('/mesh-network');
          await completeScannerPin(wms.page);
          await expect(wms.page.getByTestId('mesh-network-page')).toBeVisible({ timeout: 15_000 });
        } else {
          await expect(wms.page.getByTestId('nav-mesh-network')).toHaveCount(0);
          await wms.page.goto('/mesh-network');
          await completeScannerPin(wms.page);
          await expect(wms.page).toHaveURL(/\/upgrade/, { timeout: 15_000 });
        }

        await wms.page.goto('/manufacturing/orders');
        await completeScannerPin(wms.page);
        if (entitled.has('MANUFACTURING')) {
          await expect(
            wms.page.getByRole('heading', { name: 'Production Orders', exact: true }),
          ).toBeVisible({ timeout: 15_000 });
        } else {
          await expect(wms.page).toHaveURL(/\/upgrade/, { timeout: 15_000 });
          await expect(wms.page.getByTestId('upgrade-page')).toBeVisible();
        }

        const fintech = await wms.page.request.get('/api/v1/fintech/dashboard');
        if (entitled.has('FINTECH')) {
          expect(fintech.ok(), await fintech.text()).toBeTruthy();
        } else {
          expect(fintech.status()).toBe(402);
          expect(((await fintech.json()) as { code?: string }).code).toBe('MODULE_LOCKED');
        }
      } finally {
        await wms.close();
      }
    } finally {
      await admin.dispose();
    }
  });

  test('admin disable/enable is reflected in WMS /me, nav, /upgrade, and Fintech 402', async ({
    browser,
    page,
  }) => {
    const admin = await loginAdminApi();
    const snapshot = await fetchDemo(admin);
    try {
      const stripped = snapshot.enabledModules.filter(
        (moduleName) =>
          !['MESH_NETWORK', 'FINTECH', 'MANUFACTURING'].includes(moduleName.toUpperCase()),
      );
      if (!stripped.includes('CORE')) stripped.unshift('CORE');

      const patched = await admin.patch(
        `/api/v1/control-plane/tenants/${snapshot.tenantId}/modules`,
        { headers: await csrfHeaders(admin), data: { enabledModules: stripped } },
      );
      expect(patched.ok(), await patched.text()).toBeTruthy();
      const after = (await patched.json()) as ControlPlaneTenant;
      expect(sorted(after.enabledModules).includes('MESH_NETWORK')).toBe(false);
      expect(sorted(after.enabledModules).includes('FINTECH')).toBe(false);
      expect(sorted(after.enabledModules).includes('MANUFACTURING')).toBe(false);

      await loginAdminUi(page);
      await page.getByTestId('tenant-row-demo-corp').click();
      await expect(page.getByTestId('tenant-entitlements-drawer')).toBeVisible({ timeout: 15_000 });
      await expect(page.getByTestId('module-toggle-MESH_NETWORK')).not.toBeChecked();
      await expect(page.getByTestId('module-toggle-FINTECH')).not.toBeChecked();
      await expect(page.getByTestId('module-toggle-MANUFACTURING')).not.toBeChecked();
      await closeDrawer(page);

      const wms = await contextForRole(browser, 'owner');
      try {
        await wms.page.setViewportSize({ width: 1280, height: 800 });
        await dismissOnboardingTourIfPresent(wms.page);
        await waitForMeModules(wms.page, after.enabledModules);

        await wms.page.goto('/dashboard');
        await completeScannerPin(wms.page);
        await expect(wms.page.getByTestId('nav-mesh-network')).toHaveCount(0);

        await wms.page.goto('/mesh-network');
        await completeScannerPin(wms.page);
        await expect(wms.page).toHaveURL(/\/upgrade/, { timeout: 15_000 });
        await expect(wms.page.getByTestId('upgrade-page')).toBeVisible();

        await wms.page.goto('/manufacturing/orders');
        await completeScannerPin(wms.page);
        await expect(wms.page).toHaveURL(/\/upgrade/, { timeout: 15_000 });

        const locked = await wms.page.request.get('/api/v1/fintech/dashboard');
        expect(locked.status()).toBe(402);
        expect(((await locked.json()) as { code?: string }).code).toBe('MODULE_LOCKED');
      } finally {
        await wms.close();
      }

      const restored = await admin.patch(
        `/api/v1/control-plane/tenants/${snapshot.tenantId}/modules`,
        { headers: await csrfHeaders(admin), data: { enabledModules: snapshot.enabledModules } },
      );
      expect(restored.ok(), await restored.text()).toBeTruthy();

      const wmsOn = await contextForRole(browser, 'owner');
      try {
        await waitForMeModules(wmsOn.page, snapshot.enabledModules);
        if (sorted(snapshot.enabledModules).includes('MESH_NETWORK')) {
          await wmsOn.page.setViewportSize({ width: 1280, height: 800 });
          await wmsOn.page.goto('/dashboard');
          await completeScannerPin(wmsOn.page);
          await pinOfficeRail(wmsOn.page);
          await expect(wmsOn.page.getByTestId('nav-mesh-network')).toBeVisible({ timeout: 15_000 });
        }
        if (sorted(snapshot.enabledModules).includes('FINTECH')) {
          expect((await wmsOn.page.request.get('/api/v1/fintech/dashboard')).ok()).toBeTruthy();
        }
      } finally {
        await wmsOn.close();
      }
    } finally {
      try {
        await restoreDemo(admin, snapshot);
      } finally {
        await admin.dispose();
      }
    }
  });
});
