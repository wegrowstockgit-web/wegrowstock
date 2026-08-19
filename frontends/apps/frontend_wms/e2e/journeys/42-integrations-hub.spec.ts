import { expect, test } from '../fixtures/roleFixture';
import { contextForRole } from './helpers';

/**
 * Journey 42 — Integrations Hub subroute: catalog cards, RBAC, settings nav link.
 */
test.describe('Journey 42: Integrations Hub', () => {
  test.setTimeout(180_000);

  test('owner sees hub cards; manager blocked; connect opens the wizard', async ({
    browser,
  }) => {
    const owner = await contextForRole(browser, 'owner');
    const manager = await contextForRole(browser, 'manager');
    try {
      await owner.page.goto('/settings');
      await expect(owner.page.getByRole('heading', { name: 'Settings', exact: true })).toBeVisible({
        timeout: 20_000,
      });
      const hubNav = owner.page.getByTestId('settings-nav-integrations-hub');
      await expect(hubNav).toBeVisible({ timeout: 15_000 });
      await hubNav.click();
      await expect(owner.page).toHaveURL(/\/settings\/integrations/, { timeout: 15_000 });
      await expect(owner.page.getByTestId('integrations-hub-page')).toBeVisible({ timeout: 20_000 });

      const hubApi = await owner.page.request.get('/api/v1/integrations/hub');
      expect(hubApi.ok()).toBeTruthy();
      const hub = (await hubApi.json()) as {
        categories: Array<{ id: string; integrations: Array<{ id: string; connected: boolean }> }>;
      };
      expect(hub.categories.map((c) => c.id)).toEqual(['ECOMMERCE', 'ACCOUNTING', 'EDI']);

      await expect(owner.page.getByTestId('integrations-hub-category-ECOMMERCE')).toBeVisible();
      await expect(owner.page.getByTestId('integration-card-SHOPIFY')).toBeVisible();
      await expect(owner.page.getByTestId('integration-card-AMAZON')).toBeVisible();
      await expect(owner.page.getByTestId('integration-card-NETSUITE')).toBeVisible();
      await expect(owner.page.getByTestId('integration-card-XERO')).toBeVisible();
      await expect(owner.page.getByTestId('integration-card-QUICKBOOKS')).toBeVisible();
      await expect(owner.page.getByTestId('integration-card-AS2')).toBeVisible();

      await expect(owner.page.getByTestId('integration-status-SHOPIFY')).toHaveText(
        /LIVE|DISCONNECTED|ACTION REQUIRED/,
      );

      await owner.page.getByTestId('integration-action-QUICKBOOKS').click();
      await expect(owner.page.getByTestId('integration-wizard')).toBeVisible({ timeout: 15_000 });
      await expect(owner.page.getByTestId('integration-wizard')).toHaveAttribute(
        'data-provider',
        'QUICKBOOKS',
      );

      await manager.page.goto('/settings/integrations');
      await expect(manager.page).not.toHaveURL(/\/settings\/integrations/, { timeout: 15_000 });
      await expect(manager.page.getByTestId('integrations-hub-page')).toHaveCount(0);
      const managerHub = await manager.page.request.get('/api/v1/integrations/hub');
      expect([401, 403]).toContain(managerHub.status());
    } finally {
      await manager.close();
      await owner.close();
    }
  });
});
