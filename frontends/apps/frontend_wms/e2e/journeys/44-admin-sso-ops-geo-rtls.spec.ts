import { expect, test } from '../fixtures/roleFixture';
import { contextForRole } from './helpers';

/**
 * Journey 44 — SSO cards, ops settings cache flush, warehouse geo, RTLS workspace.
 */
test.describe('Journey 44: Admin SSO / ops / geo / RTLS', () => {
  test.setTimeout(240_000);

  test('security IdP cards, ops policies, warehouse geocode, RTLS inject', async ({ browser }) => {
    const baseURL = process.env.E2E_BASE_URL ?? 'http://localhost:3000';
    const anon = await browser.newContext({ baseURL });
    try {
      const anonPage = await anon.newPage();
      await anonPage.goto('/login');
      await expect(anonPage.getByTestId('login-google-sso')).toBeVisible({ timeout: 15_000 });
    } finally {
      await anon.close();
    }

    const owner = await contextForRole(browser, 'owner');
    try {
      await owner.page.goto('/settings?tab=security');
      await expect(owner.page.getByTestId('security-sso-tab')).toBeVisible({ timeout: 20_000 });
      await expect(owner.page.getByTestId('sso-card-GOOGLE')).toBeVisible();
      await expect(owner.page.getByTestId('sso-card-ENTRA')).toBeVisible();
      await expect(owner.page.getByTestId('sso-card-OKTA')).toBeVisible();

      await owner.page.goto('/settings?tab=operations');
      await expect(owner.page.getByTestId('operations-settings-panel')).toBeVisible({ timeout: 20_000 });
      await owner.page.getByTestId('ops-wave-max-lines').fill('48');
      await owner.page.getByTestId('ops-over-receive-pct').fill('3');
      const saveWait = owner.page.waitForResponse(
        (res) => res.url().includes('/api/v1/settings') && res.request().method() === 'PATCH',
        { timeout: 30_000 },
      );
      const flushWait = owner.page.waitForResponse(
        (res) => res.url().includes('/api/v1/settings/cache/flush') && res.request().method() === 'POST',
        { timeout: 30_000 },
      );
      await owner.page.getByTestId('ops-settings-save').click();
      expect((await saveWait).ok()).toBeTruthy();
      expect((await flushWait).ok()).toBeTruthy();

      await owner.page.goto('/warehouses/add');
      await expect(owner.page.getByTestId('places-address-input')).toBeVisible({ timeout: 15_000 });
      await owner.page.getByRole('textbox', { name: 'Name', exact: true }).fill(`RTLS WH ${Date.now().toString(36)}`);
      await owner.page.getByRole('textbox', { name: 'Code', exact: true }).fill(
        `RT${Date.now().toString(36).slice(-4).toUpperCase()}`,
      );
      await owner.page.getByRole('textbox', { name: 'City', exact: true }).fill('Austin');
      await owner.page.getByTestId('geocode-address').click();
      await expect(owner.page.getByTestId('warehouse-latitude')).not.toHaveValue('');
      await expect(owner.page.getByTestId('warehouse-longitude')).not.toHaveValue('');

      await owner.page.goto('/rtls');
      await expect(owner.page.getByTestId('rtls-workspace-page')).toBeVisible({ timeout: 20_000 });
      const telemWait = owner.page.waitForResponse(
        (res) => res.url().includes('/api/v1/rtls/telemetry') && res.request().method() === 'POST',
        { timeout: 30_000 },
      );
      await owner.page.getByTestId('rtls-inject-sample').click();
      expect((await telemWait).ok()).toBeTruthy();
      await expect(owner.page.getByTestId('rtls-vector-canvas')).toBeVisible();
    } finally {
      await owner.close();
    }
  });
});
