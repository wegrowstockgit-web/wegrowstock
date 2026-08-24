import { test } from '@playwright/test';
import { contextForRole, expect } from './helpers';

test.describe('Journey 83: Reports date range and virtualized tables', () => {
  test.setTimeout(180_000);

  test('blocks a 90+ day window and virtualizes profit rows', async ({ browser }) => {
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

      await expect(owner.page.getByTestId('report-date-range')).toBeVisible();
      await owner.page.getByTestId('report-start-date').fill('2025-01-01');
      await owner.page.getByTestId('report-end-date').fill('2025-12-31');
      await expect(owner.page.getByTestId('report-range-error')).toHaveText(
        'Date range cannot exceed 90 days. Narrow the search.',
      );

      const today = new Date();
      const start = new Date(today);
      start.setUTCDate(start.getUTCDate() - 30);
      await owner.page.getByTestId('report-start-date').fill(start.toISOString().slice(0, 10));
      await owner.page.getByTestId('report-end-date').fill(today.toISOString().slice(0, 10));
      await expect(owner.page.getByTestId('report-range-error')).toHaveCount(0);

      await expect(owner.page.getByTestId('report-virtualized-table')).toBeVisible({
        timeout: 20_000,
      });
      await expect(owner.page.getByTestId('virtualized-table-scrollport')).toBeVisible();
    } finally {
      await owner.close();
    }
  });
});
