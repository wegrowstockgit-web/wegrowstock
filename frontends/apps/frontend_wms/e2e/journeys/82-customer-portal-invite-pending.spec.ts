import { test } from '@playwright/test';
import { apiJson, contextForRole, expect } from './helpers';

test.describe('Journey 82: B2B portal invite pending state and error toasts', () => {
  test.setTimeout(180_000);

  test('workspace invite becomes Invite Pending and 409 detail is toasted', async ({ browser }) => {
    const admin = await contextForRole(browser, 'admin');
    try {
      const suffix = Date.now().toString(36);
      const pendingCustomer = await apiJson<{ id: string; portalStatus?: string }>(
        admin.page,
        '/api/v1/customers',
        {
          method: 'POST',
          body: JSON.stringify({
            name: `Portal Pending ${suffix}`,
            email: `pending-${suffix}@demo.test`,
          }),
        },
      );

      await admin.page.goto(`/sales/customers/${pendingCustomer.id}`);
      await expect(admin.page.getByTestId('customer-workspace-page')).toBeVisible({ timeout: 20_000 });
      const invite = admin.page.getByRole('button', { name: 'Send B2B Portal Invite' });
      await expect(invite).toBeEnabled();

      const inviteWait = admin.page.waitForResponse(
        (res) =>
          res.url().includes(`/api/v1/customers/${pendingCustomer.id}/portal-invite`) &&
          res.request().method() === 'POST',
      );
      await invite.click();
      expect((await inviteWait).ok()).toBeTruthy();
      const pending = admin.page.getByRole('button', { name: 'Invite Pending' });
      await expect(pending).toBeVisible({ timeout: 15_000 });
      await expect(pending).toBeDisabled();

      const toastCustomer = await apiJson<{ id: string }>(admin.page, '/api/v1/customers', {
        method: 'POST',
        body: JSON.stringify({
          name: `Portal Toast ${suffix}`,
          email: `toast-${suffix}@demo.test`,
        }),
      });

      await admin.page.route(`**/api/v1/customers/${toastCustomer.id}/portal-invite`, async (route) => {
        await route.fulfill({
          status: 409,
          contentType: 'application/problem+json',
          body: JSON.stringify({
            title: 'INVITE_PENDING',
            detail: 'An open invitation already exists for this email',
            status: 409,
          }),
        });
      });
      await admin.page.route(`**/api/v1/customers/${toastCustomer.id}/credit-hold`, async (route) => {
        await route.fulfill({
          status: 409,
          contentType: 'application/problem+json',
          body: JSON.stringify({
            title: 'CREDIT_HOLD_FAILED',
            detail: 'Credit hold is not available for this account',
            status: 409,
          }),
        });
      });

      await admin.page.goto(`/sales/customers/${toastCustomer.id}`);
      await expect(admin.page.getByTestId('customer-workspace-page')).toBeVisible({ timeout: 20_000 });

      await admin.page.getByRole('button', { name: 'Send B2B Portal Invite' }).click();
      await expect(
        admin.page.getByTestId('app-toast').filter({
          hasText: 'An open invitation already exists for this email',
        }),
      ).toBeVisible();

      await admin.page.getByRole('button', { name: 'Place on Credit Hold' }).click();
      await expect(
        admin.page.getByTestId('app-toast').filter({
          hasText: 'Credit hold is not available for this account',
        }),
      ).toBeVisible();
    } finally {
      await admin.close();
    }
  });
});
