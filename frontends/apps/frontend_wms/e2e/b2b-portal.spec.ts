import { expect, test } from '@playwright/test';
import { completeScannerPin, loginAsDemo } from './fixtures/roleFixture';

test.describe('B2B portal', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsDemo(page, 'b2b@demo.test');
    await expect(page).toHaveURL(/\/showroom/, { timeout: 15_000 });
  });

  test('catalog loads and cannot reach office settings', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'Catalog' })).toBeVisible({ timeout: 15_000 });

    await page.goto('/settings');
    await expect(page).not.toHaveURL(/\/settings/);
    await expect(page).toHaveURL(/\/showroom/);
  });

  test('submits a draft portal order from catalog', async ({ page }) => {
    await page.goto('/showroom/catalog');
    await completeScannerPin(page);
    await expect(page.getByRole('heading', { name: 'Catalog' })).toBeVisible({ timeout: 15_000 });

    const firstCard = page.locator('.grid > div').first();
    await firstCard.getByRole('button').last().click();

    await page.getByLabel(/Open cart/i).click();
    await expect(page.getByRole('dialog')).toBeVisible();
    await page.getByRole('button', { name: 'Proceed to checkout' }).click();
    await expect(page.getByLabel('Your PO number')).toBeVisible();
    await page.getByLabel('Your PO number').fill('PO-E2E-1');
    await page.getByRole('button', { name: 'Continue' }).click();
    await page.getByRole('button', { name: 'Instant Checkout' }).click();

    await expect(page.getByText('Order submitted')).toBeVisible({ timeout: 15_000 });
    await page.getByRole('button', { name: 'View orders' }).click();
    await expect(page).toHaveURL(/\/showroom\/orders/);
    await expect(page.getByRole('heading', { name: 'Order history' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Reorder' }).first()).toBeVisible();
  });

  test('persistent cart FAB opens global drawer', async ({ page }) => {
    await page.goto('/showroom/catalog');
    await completeScannerPin(page);
    await expect(page.getByRole('heading', { name: 'Catalog' })).toBeVisible({ timeout: 15_000 });
    await page.getByLabel(/Open cart/i).click();
    await expect(page.getByRole('dialog')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Your cart' })).toBeVisible();
  });
});
