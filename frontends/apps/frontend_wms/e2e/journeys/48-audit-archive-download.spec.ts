import { expect, test } from '../fixtures/roleFixture';
import { contextForRole } from './helpers';

/**
 * Journey 48 — Historical audit archive download (OWNER/ADMIN cold storage).
 * Download against real S3 can hang in local Docker — assert UI + RBAC only.
 */
test.describe('Journey 48: Audit archive download', () => {
  test.setTimeout(90_000);

  test('owner can open historical archives panel and set a date range', async ({ browser }) => {
    const owner = await contextForRole(browser, 'owner');
    try {
      await owner.page.goto('/settings?tab=operations');
      await expect(owner.page.getByTestId('historical-archives-panel')).toBeVisible({ timeout: 20_000 });
      await expect(owner.page.getByTestId('archive-start-date')).toBeVisible();
      await expect(owner.page.getByTestId('archive-end-date')).toBeVisible();
      await expect(owner.page.getByTestId('archive-download-button')).toBeEnabled();

      const today = new Date();
      const end = today.toISOString().slice(0, 10);
      const startDate = new Date(today);
      startDate.setUTCMonth(startDate.getUTCMonth() - 1);
      const start = startDate.toISOString().slice(0, 10);

      await owner.page.getByTestId('archive-start-date').fill(start);
      await owner.page.getByTestId('archive-end-date').fill(end);
      await expect(owner.page.getByTestId('archive-start-date')).toHaveValue(start);
      await expect(owner.page.getByTestId('archive-end-date')).toHaveValue(end);
      await expect(owner.page.getByTestId('archive-download-button')).toBeEnabled();
    } finally {
      await owner.close();
    }
  });

  test('warehouse manager cannot download archives', async ({ browser }) => {
    const manager = await contextForRole(browser, 'manager');
    try {
      const denied = await manager.page.request.get(
        '/api/v1/office/audit/archives/download?startDate=2026-01-01&endDate=2026-01-31',
        { timeout: 15_000 },
      );
      expect(denied.status()).toBe(403);

      await manager.page.goto('/settings?tab=operations');
      await expect(manager.page).not.toHaveURL(/\/settings/, { timeout: 15_000 });
      await expect(manager.page.getByTestId('historical-archives-panel')).toHaveCount(0);
    } finally {
      await manager.close();
    }
  });
});
