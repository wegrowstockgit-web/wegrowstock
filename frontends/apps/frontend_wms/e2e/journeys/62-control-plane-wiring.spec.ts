import { expect, test } from '../fixtures/roleFixture';

const ADMIN_UI = process.env.ADMIN_UI_URL ?? 'http://localhost:3002';
const ADMIN_API = process.env.ADMIN_API_URL ?? 'http://localhost:8081';
const WMS_API = process.env.WMS_API_URL ?? 'http://localhost:8080';

function isSameOriginAdminApi(url: string, path: string): boolean {
  try {
    const parsed = new URL(url);
    return parsed.origin === new URL(ADMIN_UI).origin && parsed.pathname.includes(path);
  } catch {
    return false;
  }
}

/**
 * Journey 62 — Prove frontend_admin is wired to invsys-admin-api through the
 * same-origin /api proxy (admin-web → api-gateway:8081 → backend-admin).
 */
test.describe('Journey 62: Control Plane UI ↔ admin-api wiring', () => {
  test.setTimeout(180_000);

  test('login, tenants, packaging, billing, and reports hit admin-api via :3002', async ({
    page,
  }) => {
    await page.goto(`${ADMIN_UI}/login`);
    await expect(page.getByTestId('admin-login-page')).toBeVisible({ timeout: 20_000 });

    await page.getByTestId('admin-login-email').fill('owner@demo.test');
    await page.getByTestId('admin-login-password').fill('wrong-password');
    await page.getByTestId('admin-login-submit').click();
    await expect(page.getByTestId('admin-login-error')).toBeVisible({ timeout: 15_000 });

    const tenantsResponse = page.waitForResponse(
      (res) => isSameOriginAdminApi(res.url(), '/api/v1/control-plane/tenants') && res.ok(),
      { timeout: 20_000 },
    );
    await page.getByTestId('admin-login-password').fill('password123');
    await page.getByTestId('admin-login-submit').click();

    const tenants = await tenantsResponse;
    const tenantRows = (await tenants.json()) as Array<{ slug: string }>;
    expect(tenantRows.some((row) => row.slug === 'demo-corp')).toBeTruthy();

    await expect(page.getByTestId('admin-layout')).toBeVisible({ timeout: 20_000 });
    await expect(page.getByTestId('tenant-manager')).toBeVisible();
    await expect(page.getByTestId('tenant-row-demo-corp')).toBeVisible();
    await expect(page.getByTestId('admin-session-email')).toHaveText('owner@demo.test');

    const packagingResponse = page.waitForResponse(
      (res) => isSameOriginAdminApi(res.url(), '/api/v1/control-plane/packaging/tiers') && res.ok(),
    );
    await page.getByRole('link', { name: /pricing & packaging/i }).click();
    const packaging = await packagingResponse;
    const tiers = (await packaging.json()) as Array<{ tierCode: string }>;
    expect(tiers.map((t) => t.tierCode).sort()).toEqual(['BASIC', 'ENTERPRISE', 'INTERMEDIATE']);
    await expect(page.getByTestId('platform-packaging')).toBeVisible({ timeout: 20_000 });
    await expect(page.getByTestId('packaging-card-BASIC')).toBeVisible();

    const billingResponse = page.waitForResponse(
      (res) => isSameOriginAdminApi(res.url(), '/api/v1/control-plane/billing/overview') && res.ok(),
    );
    await page.getByRole('link', { name: /platform billing/i }).click();
    const billing = await billingResponse;
    const bill = (await billing.json()) as { estimatedMrr?: unknown };
    expect(bill.estimatedMrr).toBeDefined();
    await expect(page.getByTestId('platform-billing')).toBeVisible({ timeout: 20_000 });

    const commercialResponse = page.waitForResponse(
      (res) =>
        isSameOriginAdminApi(res.url(), '/api/v1/control-plane/reports/commercial') && res.ok(),
    );
    await page.getByRole('link', { name: /commercial reports/i }).click();
    expect((await commercialResponse).ok()).toBeTruthy();
    await expect(page.getByTestId('commercial-reports')).toBeVisible({ timeout: 20_000 });

    const extraSurfaces = [
      { name: /audit trail/i, testId: 'platform-audit' },
      { name: /webhooks & integrations/i, testId: 'integrations-hub' },
      { name: /copilot knowledge/i, testId: 'copilot-knowledge' },
    ] as const;
    for (const dest of extraSurfaces) {
      await page.getByRole('link', { name: dest.name }).click();
      await expect(page.getByTestId(dest.testId)).toBeVisible({ timeout: 20_000 });
    }

    const blocked = await page.request.get(`${WMS_API}/api/v1/control-plane/tenants`);
    expect(blocked.status(), 'data-plane edge must not expose control-plane').toBe(404);

    const gatewayHealth = await page.request.get(`${ADMIN_API}/actuator/health`);
    expect(gatewayHealth.ok()).toBeTruthy();
  });
});
