import { test } from '@playwright/test';
import { contextForRole, expect } from './helpers';

async function dismissTour(page: import('@playwright/test').Page) {
  const skipTour = page.getByRole('button', { name: 'Not now' });
  if (await skipTour.isVisible({ timeout: 3_000 }).catch(() => false)) {
    await skipTour.click();
  }
}

async function overflowY(locator: import('@playwright/test').Locator) {
  return locator.evaluate((el) => getComputedStyle(el).overflowY);
}

test.describe('Journey 80: Global scrollbar layout', () => {
  test.setTimeout(180_000);

  test('list pages scroll on AppShell main; grids keep their own port; sidebar stays independent', async ({
    browser,
  }) => {
    const owner = await contextForRole(browser, 'owner');
    try {
      await owner.page.setViewportSize({ width: 1440, height: 720 });

      await owner.page.goto('/suppliers');
      await expect(owner.page.getByTestId('suppliers-page')).toBeVisible({ timeout: 20_000 });
      await dismissTour(owner.page);

      const suppliersPage = owner.page.getByTestId('suppliers-page');
      const main = owner.page.getByTestId('app-shell-main');
      expect(await overflowY(suppliersPage)).not.toMatch(/auto|scroll/);
      expect(await overflowY(main)).toMatch(/auto|scroll/);

      const [mainBox, pageBox] = await Promise.all([
        main.boundingBox(),
        suppliersPage.boundingBox(),
      ]);
      expect(mainBox).toBeTruthy();
      expect(pageBox).toBeTruthy();
      expect(mainBox!.width).toBeGreaterThan(pageBox!.width + 24);
      expect(mainBox!.x + mainBox!.width).toBeGreaterThan(1400);

      await owner.page.goto('/customers');
      await expect(owner.page.getByTestId('customers-page')).toBeVisible({ timeout: 20_000 });
      expect(await overflowY(owner.page.getByTestId('customers-page'))).not.toMatch(/auto|scroll/);
      expect(await overflowY(owner.page.getByTestId('app-shell-main'))).toMatch(/auto|scroll/);

      await owner.page.goto('/invoices');
      await expect(owner.page.getByTestId('invoices-page')).toBeVisible({ timeout: 20_000 });
      expect(await overflowY(owner.page.getByTestId('invoices-page'))).not.toMatch(/auto|scroll/);
      expect(await overflowY(owner.page.getByTestId('app-shell-main'))).toMatch(/auto|scroll/);

      await owner.page.goto('/returns');
      await expect(owner.page.getByRole('heading', { name: 'Returns (RMA)' })).toBeVisible({
        timeout: 20_000,
      });
      expect(await overflowY(owner.page.getByTestId('app-shell-main'))).toMatch(/auto|scroll/);

      await owner.page.goto('/purchase-orders');
      await expect(owner.page.getByRole('heading', { name: 'Purchase Orders' })).toBeVisible({
        timeout: 20_000,
      });
      expect(await overflowY(owner.page.getByTestId('app-shell-main'))).toBe('hidden');
      const poPort = owner.page.locator('[data-list-scrollport="true"]');
      await expect(poPort).toBeVisible();
      expect(await overflowY(poPort)).toMatch(/auto|scroll/);
      const poBox = await poPort.boundingBox();
      expect(poBox).toBeTruthy();
      expect(poBox!.x + poBox!.width).toBeGreaterThan(1400);

      await owner.page.goto('/sales-orders');
      await expect(owner.page.getByRole('heading', { name: 'Sales Orders' })).toBeVisible({
        timeout: 20_000,
      });
      expect(await overflowY(owner.page.getByTestId('app-shell-main'))).toBe('hidden');
      expect(await overflowY(owner.page.locator('[data-list-scrollport="true"]'))).toMatch(
        /auto|scroll/,
      );

      await owner.page.setViewportSize({ width: 1280, height: 420 });
      await owner.page.goto('/suppliers');
      await expect(owner.page.getByTestId('suppliers-page')).toBeVisible({ timeout: 20_000 });
      const nav = owner.page.getByRole('navigation', { name: 'Primary' });
      await expect(nav).toBeVisible();
      const navClass = await nav.getAttribute('class');
      expect(navClass).toMatch(/scrollbar-none/);
      expect(await overflowY(nav)).toMatch(/auto|scroll/);

      await owner.page.setViewportSize({ width: 1440, height: 900 });
      await owner.page.goto('/settings?tab=warehouses');
      await expect(owner.page.getByRole('heading', { name: 'Settings' })).toBeVisible({
        timeout: 20_000,
      });
      await expect(owner.page.locator('.settings-shell')).toBeVisible();
      expect(await overflowY(owner.page.getByTestId('app-shell-main'))).toBe('hidden');
    } finally {
      await owner.close();
    }
  });
});
