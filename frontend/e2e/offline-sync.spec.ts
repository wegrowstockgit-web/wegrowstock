import { expect, test } from '@playwright/test';

const DEMO_PASSWORD = process.env.E2E_DEMO_PASSWORD ?? 'password123';

test.describe('Offline sync conflict UI', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Email').fill('owner@demo.test');
    await page.getByLabel('Password').fill(DEMO_PASSWORD);
    await page.getByRole('button', { name: 'Sign in' }).click();
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
    await expect(page.getByTestId('sync-conflict-toast')).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText('Stock no longer available')).toBeVisible();
    await page.getByRole('button', { name: 'Dismiss', exact: true }).click();
    await expect(page.getByTestId('sync-conflict-toast')).toHaveCount(0);
  });
});
