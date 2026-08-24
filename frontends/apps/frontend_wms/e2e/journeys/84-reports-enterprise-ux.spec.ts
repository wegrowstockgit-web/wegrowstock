import { test } from '@playwright/test';
import { contextForRole, expect } from './helpers';

test.describe('Journey 84: Enterprise reports UX, export, and RBAC', () => {
  test.setTimeout(180_000);

  test('owner uses presets, export, and SKU drill-down', async ({ browser }) => {
    const owner = await contextForRole(browser, 'owner');
    try {
      await owner.page.goto('/reports');
      await expect(owner.page.getByRole('heading', { name: 'Reports' })).toBeVisible({
        timeout: 20_000,
      });
      const skipTour = owner.page.getByRole('button', { name: 'Not now' });
      if (await skipTour.isVisible({ timeout: 3_000 }).catch(() => false)) {
        await skipTour.click();
      }

      await expect(owner.page.getByTestId('report-date-presets')).toBeVisible();
      await owner.page.getByTestId('report-preset-7d').click();
      await expect(owner.page.getByTestId('report-range-error')).toHaveCount(0);
      await expect(owner.page.getByTestId('report-virtualized-table')).toBeVisible({
        timeout: 20_000,
      });

      await owner.page.getByTestId('report-export-menu').click();
      await expect(owner.page.getByTestId('report-export-csv')).toBeVisible();
      await expect(owner.page.getByTestId('report-export-print')).toBeVisible();
      await owner.page.keyboard.press('Escape');

      const skuLink = owner.page.getByTestId('report-sku-link').first();
      if (await skuLink.isVisible().catch(() => false)) {
        await expect(skuLink).toHaveAttribute('href', /\/products/);
      }
    } finally {
      await owner.close();
    }
  });

  test('warehouse manager sees operational reports only', async ({ browser }) => {
    const manager = await contextForRole(browser, 'manager');
    try {
      await manager.page.goto('/reports');
      await expect(manager.page.getByRole('heading', { name: 'Reports' })).toBeVisible({
        timeout: 20_000,
      });
      const skipTour = manager.page.getByRole('button', { name: 'Not now' });
      if (await skipTour.isVisible({ timeout: 3_000 }).catch(() => false)) {
        await skipTour.click();
      }

      await expect(manager.page.getByTestId('reports-tab-fulfillment')).toBeVisible();
      await expect(manager.page.getByTestId('reports-tab-profit')).toHaveCount(0);
      await expect(manager.page.getByTestId('reports-tab-cogs')).toHaveCount(0);
    } finally {
      await manager.close();
    }
  });
});
