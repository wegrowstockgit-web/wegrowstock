import { expect, test } from '@playwright/test';
import { loginAsDemo } from './fixtures/roleFixture';

test.describe('B2B mesh network hub', () => {
  test('owner can open the mesh hub tabs', async ({ page }) => {
    await loginAsDemo(page, 'owner@demo.test');
    await page.goto('/mesh-network');
    await expect(page.getByTestId('mesh-network-page')).toBeVisible({ timeout: 15_000 });
    await expect(page.getByRole('heading', { name: 'Mesh Network' })).toBeVisible();
    await expect(page.getByTestId('mesh-tab-discover')).toBeVisible();
    await expect(page.getByTestId('mesh-tab-network')).toBeVisible();
    await expect(page.getByTestId('mesh-tab-catalog')).toBeVisible();

    await page.getByTestId('mesh-tab-network').click();
    await expect(page.getByTestId('mesh-network-table')).toBeVisible();

    await page.getByTestId('mesh-tab-catalog').click();
    await expect(page.getByTestId('mesh-shared-catalog')).toBeVisible();
  });

  test('dashboard smart sourcing card is optional and draft PO alias preserves sku', async ({ page }) => {
    await loginAsDemo(page, 'owner@demo.test');
    await page.goto('/dashboard');
    await expect(page.getByRole('heading', { name: /dashboard|command/i }).or(page.getByText('Work queue'))).toBeVisible({
      timeout: 15_000,
    });
    const card = page.getByTestId('smart-sourcing-card');
    if (await card.isVisible()) {
      await card.getByRole('button', { name: 'Draft PO' }).first().click();
      await expect(page).toHaveURL(/\/purchase-orders/);
    } else {
      await page.goto('/purchase-orders/new?meshPartnerSku=MESH-E2E');
      await expect(page).toHaveURL(/\/purchase-orders\?meshPartnerSku=MESH-E2E/);
    }
  });
});
