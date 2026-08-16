import { request as pwRequest, type APIRequestContext } from '@playwright/test';
import { expect, test } from '../fixtures/roleFixture';

const ADMIN_API = process.env.ADMIN_API_URL ?? 'http://localhost:8081';
const ADMIN_UI = process.env.ADMIN_UI_URL ?? 'http://localhost:3002';

async function csrfHeaders(ctx: APIRequestContext): Promise<Record<string, string>> {
  const state = await ctx.storageState();
  const token = state.cookies.find((c) => c.name === 'XSRF-TOKEN')?.value;
  return token ? { 'X-XSRF-TOKEN': decodeURIComponent(token) } : {};
}

/**
 * Journey 61 — Dynamic SaaS packaging: live tier bundles from Control Plane.
 */
test.describe('Journey 61: Control Plane packaging studio', () => {
  test.setTimeout(180_000);

  test('lists live tiers, toggles BASIC Shopify, restores CORE-only', async ({ page }) => {
    const admin = await pwRequest.newContext({ baseURL: ADMIN_API });
    try {
      const csrf = await admin.get('/api/v1/control-plane/auth/csrf');
      expect(csrf.ok()).toBeTruthy();
      const login = await admin.post('/api/v1/control-plane/auth/login', {
        data: { email: 'owner@demo.test', password: 'password123' },
      });
      expect(login.ok(), await login.text()).toBeTruthy();
      expect((await admin.get('/api/v1/control-plane/auth/csrf')).ok()).toBeTruthy();

      const listed = await admin.get('/api/v1/control-plane/packaging/tiers');
      expect(listed.ok(), await listed.text()).toBeTruthy();
      const tiers = (await listed.json()) as Array<{ tierCode: string; defaultModules: string[] }>;
      expect(tiers.map((t) => t.tierCode).sort()).toEqual(['BASIC', 'ENTERPRISE', 'INTERMEDIATE']);

      const put = await admin.put('/api/v1/control-plane/packaging/tiers/BASIC', {
        headers: await csrfHeaders(admin),
        data: { defaultModules: ['CORE', 'SHOPIFY'] },
      });
      expect(put.ok(), `${put.status()} ${await put.text()}`).toBeTruthy();
      const updated = (await put.json()) as { defaultModules: string[] };
      expect(updated.defaultModules).toEqual(expect.arrayContaining(['CORE', 'SHOPIFY']));

      await page.goto(`${ADMIN_UI}/login`);
      await page.getByLabel(/email/i).fill('owner@demo.test');
      await page.getByLabel(/password/i).fill('password123');
      await page.getByRole('button', { name: /sign in|log in/i }).click();
      await expect(page.getByTestId('admin-layout')).toBeVisible({ timeout: 20_000 });
      await page.getByRole('link', { name: /pricing & packaging/i }).click();
      await expect(page.getByTestId('platform-packaging')).toBeVisible({ timeout: 20_000 });
      await expect(page.getByTestId('packaging-load-error')).toHaveCount(0);
      await expect(page.getByTestId('packaging-card-BASIC')).toBeVisible({ timeout: 20_000 });
      await expect(page.getByTestId('packaging-toggle-BASIC-SHOPIFY')).toBeChecked();

      const restore = await admin.put('/api/v1/control-plane/packaging/tiers/BASIC', {
        headers: await csrfHeaders(admin),
        data: { defaultModules: ['CORE'] },
      });
      expect(restore.ok(), `${restore.status()} ${await restore.text()}`).toBeTruthy();
    } finally {
      await admin.dispose();
    }
  });
});
