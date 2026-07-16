import { test } from '@playwright/test';
import { contextForRole, expect } from './helpers';

/**
 * Track 8 — Owner-gated fintech / billing boundaries.
 * Fintech cockpit is OWNER-only; billing remains ADMIN+OWNER; capital drawdown is OWNER-only.
 */
test.describe.serial('Journey 08: Strict owner-gated financial boundaries', () => {
  test('admin masked from fintech; owner sees financing cockpit', async ({ browser }) => {
    const admin = await contextForRole(browser, 'admin');
    const owner = await contextForRole(browser, 'owner');
    const manager = await contextForRole(browser, 'manager');

    try {
      // --- Admin: Cash Flow nav masked; direct fintech URL blocked ---
      await admin.page.goto('/settings');
      await expect(admin.page.getByRole('heading', { name: 'Settings', exact: true })).toBeVisible({
        timeout: 15_000,
      });
      await expect(admin.page.getByRole('link', { name: 'Cash Flow & Financing' })).toHaveCount(0);
      // Billing still available to ADMIN (Stripe + carriers)
      await expect(admin.page.getByRole('link', { name: 'Billing' })).toBeVisible();

      await admin.page.goto('/settings/fintech');
      await expect(admin.page).not.toHaveURL(/\/settings\/fintech/, { timeout: 15_000 });
      await expect(admin.page.getByTestId('fintech-settings-page')).toHaveCount(0);

      const fintechApi = await admin.page.request.get('/api/v1/fintech/dashboard');
      expect([401, 403]).toContain(fintechApi.status());

      const drawdown = await admin.page.request.post('/api/v1/fintech/drawdown', {
        headers: {
          'Content-Type': 'application/json',
          'Idempotency-Key': `j8-admin-${Date.now()}`,
        },
        data: { amount: 100 },
      });
      expect([401, 403]).toContain(drawdown.status());

      // Manager cannot enter billing / fintech surfaces
      await manager.page.goto('/settings/billing');
      await expect(manager.page).not.toHaveURL(/\/settings\/billing/, { timeout: 15_000 });
      const billingApi = await manager.page.request.get('/api/v1/billing/stripe/status');
      expect([401, 403]).toContain(billingApi.status());

      // --- Owner: fintech cockpit + Stripe-connected billing ---
      await owner.page.goto('/settings/fintech');
      await expect(owner.page.getByTestId('fintech-settings-page')).toBeVisible({ timeout: 20_000 });
      await expect(owner.page.getByRole('heading', { name: /Cash flow & financing/i })).toBeVisible();
      await expect(owner.page.getByText('Financing Cockpit')).toBeVisible({ timeout: 20_000 });
      await expect(owner.page.getByText(/GMV|Credit limit|Capital drawdown/i).first()).toBeVisible();

      const ownerDash = await owner.page.request.get('/api/v1/fintech/dashboard');
      expect(ownerDash.ok()).toBeTruthy();
      const dash = (await ownerDash.json()) as { creditLine?: { creditLimit?: number } };
      expect(dash.creditLine).toBeTruthy();

      await owner.page.goto('/settings/billing');
      await expect(owner.page.getByTestId('billing-settings-page')).toBeVisible({ timeout: 15_000 });
      await expect(
        owner.page.getByText(/Billing|Connect Stripe|Stripe/i).first(),
      ).toBeVisible();
    } finally {
      await manager.close();
      await admin.close();
      await owner.close();
    }
  });
});
