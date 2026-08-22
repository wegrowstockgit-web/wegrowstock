/**
 * Live Control Plane CRUD for weGrowStock page-knowledge.
 * Requires admin UI :3002 and admin API :8081.
 */
import { test, expect } from '@playwright/test';

const ADMIN_UI = process.env.ADMIN_UI_URL ?? 'http://localhost:3002';

test.describe('Page help manager', () => {
  test.setTimeout(90_000);

  test('lists seeded routes and live-previews purchase orders', async ({ page }) => {
    await page.goto(`${ADMIN_UI}/login`);
    await page.getByTestId('admin-login-email').fill('owner@demo.test');
    await page.getByTestId('admin-login-password').fill('password123');
    await page.getByTestId('admin-login-submit').click();
    await expect(page.getByTestId('admin-layout')).toBeVisible({ timeout: 20_000 });

    await page.getByRole('link', { name: /page help/i }).click();
    await expect(page.getByTestId('page-help-manager')).toBeVisible({ timeout: 20_000 });

    await page.getByTestId('page-help-search').fill('purchase-orders');
    await expect(page.getByTestId('page-help-row').first()).toBeVisible({ timeout: 15_000 });
    await expect(page.getByTestId('page-help-manager')).toContainText('/purchase-orders');
    await expect(page.getByTestId('page-help-manager')).toContainText('Inbound');
    await expect(page.getByTestId('page-help-manager')).not.toContainText(/VetAztek|VetNexus/i);

    await page.getByRole('button', { name: 'Purchase Orders' }).first().click();
    await expect(page.getByTestId('page-help-editor')).toBeVisible();
    await expect(page.getByTestId('page-help-preview')).toBeVisible();
    await expect(page.getByTestId('page-help-preview')).toContainText(/Inbound/i);
    await expect(page.getByTestId('page-help-preview')).toContainText(/Reverse/i);
  });
});
