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
 * Journey 64 — Control Plane clone-sandbox (UAT tenant + one-time API key).
 */
test.describe('Journey 64: Control Plane sandbox clone', () => {
  test.setTimeout(180_000);

  test('API clone-sandbox returns a UAT slug and key; Tenants Sandbox button succeeds', async ({
    page,
  }) => {
    const admin = await pwRequest.newContext({ baseURL: ADMIN_API });
    try {
      expect((await admin.get('/api/v1/control-plane/auth/csrf')).ok()).toBeTruthy();
      const login = await admin.post('/api/v1/control-plane/auth/login', {
        data: { email: 'owner@demo.test', password: 'password123' },
      });
      expect(login.ok(), await login.text()).toBeTruthy();

      const listed = await admin.get('/api/v1/control-plane/tenants');
      expect(listed.ok(), await listed.text()).toBeTruthy();
      const tenants = (await listed.json()) as Array<{ tenantId: string; slug: string; status: string }>;
      const demo =
        tenants.find((t) => t.slug === 'demo-corp' || t.slug === 'demo') ??
        tenants.find((t) => t.status === 'ACTIVE');
      expect(demo, 'expected an ACTIVE tenant to clone').toBeTruthy();

      const clone = await admin.post(`/api/v1/control-plane/tenants/${demo!.tenantId}/clone-sandbox`, {
        headers: await csrfHeaders(admin),
      });
      expect(clone.ok(), `${clone.status()} ${await clone.text()}`).toBeTruthy();
      const body = (await clone.json()) as {
        sandboxTenantId: string;
        sandboxSlug: string;
        apiKey: string;
        apiKeyHint: string;
      };
      expect(body.sandboxSlug).toMatch(/^uat-/);
      expect(body.apiKey).toMatch(/^sk_uat_/);
      expect(body.apiKeyHint).toBeTruthy();

      await page.goto(`${ADMIN_UI}/login`);
      await page.getByLabel(/email/i).fill('owner@demo.test');
      await page.getByLabel(/password/i).fill('password123');
      await page.getByRole('button', { name: /sign in|log in/i }).click();
      await expect(page.getByTestId('admin-layout')).toBeVisible({ timeout: 20_000 });
      await page.getByRole('link', { name: /^tenants$/i }).click();
      await expect(page.getByTestId('tenant-manager')).toBeVisible({ timeout: 20_000 });

      page.once('dialog', (dialog) => void dialog.accept());
      const sandboxWait = page.waitForResponse(
        (res) => res.url().includes('/clone-sandbox') && res.request().method() === 'POST',
        { timeout: 30_000 },
      );
      await page.getByTestId(`sandbox-btn-${demo!.slug}`).click();
      expect((await sandboxWait).ok()).toBeTruthy();
    } finally {
      await admin.dispose();
    }
  });
});
