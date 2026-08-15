/**
 * Platform Super Admin Control Plane — hosted at admin.invsys.com.
 * Isolated from the tenant WMS (frontend_wms / app.invsys.com).
 */
import { test, expect } from '@playwright/test';

test.describe('Control Plane admin portal', () => {
  test('login page renders and rejects non-admin credentials', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByTestId('admin-login-page')).toBeVisible();
    await page.getByLabel(/email/i).fill('owner@demo.test');
    await page.getByLabel(/password/i).fill('wrong-password');
    await page.getByRole('button', { name: /sign in|log in/i }).click();
    await expect(page.getByText(/invalid|unauthorized|failed/i).first()).toBeVisible({
      timeout: 15_000,
    });
  });
});
