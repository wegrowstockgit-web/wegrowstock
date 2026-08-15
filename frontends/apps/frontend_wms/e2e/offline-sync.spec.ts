import { expect, test } from '@playwright/test';
import { completeScannerPin, loginAsDemo } from './fixtures/roleFixture';

test.describe('Offline sync conflict UI', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsDemo(page);
    await expect(page).toHaveURL(/\/dashboard/);
  });

  test('sync conflict toast appears when DLQ is populated', async ({ page }) => {
    await page.evaluate(() => {
      localStorage.setItem(
        'invsys-sync-conflicts',
        JSON.stringify({
          state: {
            syncConflicts: [
              {
                id: 'e2e-conflict',
                idempotencyKey: 'e2e-key',
                method: 'POST',
                url: '/api/v1/fulfillment/scan',
                status: 409,
                message: 'Stock no longer available',
                failedAt: Date.now(),
              },
            ],
          },
          version: 0,
        })
      );
    });
    await page.reload();
    await completeScannerPin(page);
    await expect(page.getByTestId('sync-conflict-toast')).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText('Stock no longer available')).toBeVisible();
    await page.getByRole('button', { name: 'Dismiss', exact: true }).click();
    await expect(page.getByTestId('sync-conflict-toast')).toHaveCount(0);
  });
});
