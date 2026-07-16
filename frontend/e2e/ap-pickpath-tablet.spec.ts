import { expect, test } from '@playwright/test';

const DEMO_PASSWORD = process.env.E2E_DEMO_PASSWORD ?? 'password123';

async function signInAsOwner(page: import('@playwright/test').Page) {
  await page.goto('/login');
  await page.getByLabel('Email').fill('owner@demo.test');
  await page.getByLabel('Password').fill(DEMO_PASSWORD);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page).toHaveURL(/\/(dashboard|products|fulfillment)/, { timeout: 20_000 });
}

test.describe('AP ingest + pick path + tablet shell', () => {
  test('AP document upload control is available on purchase orders', async ({ page }) => {
    await signInAsOwner(page);
    await page.goto('/purchase-orders');
    await expect(page.getByRole('heading', { name: 'Purchase Orders', exact: true })).toBeVisible({
      timeout: 20_000,
    });
    await expect(page.getByRole('button', { name: /Upload invoice document/i })).toBeVisible();
    await expect(page.getByText(/Document AI upload/i)).toBeVisible();
  });

  test('fulfillment exposes optimize pick path action', async ({ page }) => {
    await signInAsOwner(page);
    await page.goto('/fulfillment');
    await page.getByRole('button', { name: 'Batch', exact: true }).click();
    await expect(page.getByRole('button', { name: /Optimize pick path/i })).toBeVisible({
      timeout: 20_000,
    });
  });

  test('iPad viewport collapses rail into tap drawer with 44px targets', async ({ page }) => {
    await page.setViewportSize({ width: 820, height: 1180 });
    await signInAsOwner(page);
    await page.goto('/products');
    await expect(page.getByRole('heading', { name: 'Products', exact: true })).toBeVisible({
      timeout: 20_000,
    });

    const menu = page.getByRole('button', { name: /open navigation|menu/i }).first();
    await expect(menu).toBeVisible();
    const box = await menu.boundingBox();
    expect(box).toBeTruthy();
    expect(box!.height).toBeGreaterThanOrEqual(44);
    expect(box!.width).toBeGreaterThanOrEqual(44);
  });
});
