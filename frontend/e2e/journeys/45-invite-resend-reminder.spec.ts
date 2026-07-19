import { expect, test } from '../fixtures/roleFixture';
import { contextForRole } from './helpers';

/**
 * Journey 45 — Send Reminder remints invitation expiry and shows success toast.
 */
test.describe('Journey 45: Invitation reminder resend', () => {
  test.setTimeout(180_000);

  test('pending row Send Reminder → toast + refresh expires', async ({ browser }) => {
    const owner = await contextForRole(browser, 'owner');
    const email = `remind.ui.${Date.now()}@demo.test`;
    try {
      await owner.page.goto('/settings?tab=users');
      await expect(owner.page.getByTestId('invite-user-button')).toBeVisible({ timeout: 20_000 });
      await owner.page.getByTestId('invite-user-button').click();
      await owner.page.locator('#invite-email').fill(email);
      await owner.page.locator('#invite-role').selectOption('PICKER');

      const inviteWait = owner.page.waitForResponse(
        (r) =>
          r.url().includes('/api/v1/users/invitations') &&
          r.request().method() === 'POST' &&
          !r.url().includes('/resend'),
        { timeout: 30_000 },
      );
      await owner.page.getByTestId('invite-submit').click();
      const inviteRes = await inviteWait;
      expect(inviteRes.ok(), await inviteRes.text()).toBeTruthy();
      const inviteBody = (await inviteRes.json()) as { id: string; email: string };
      expect(inviteBody.email).toBe(email);

      await expect(owner.page.getByTestId(`pending-invite-${email}`)).toBeVisible({ timeout: 15_000 });
      const resendBtn = owner.page.getByTestId(`resend-invite-${inviteBody.id}`);
      await expect(resendBtn).toBeVisible();

      const resendWait = owner.page.waitForResponse(
        (r) =>
          r.url().includes(`/api/v1/office/invitations/${inviteBody.id}/resend`) &&
          r.request().method() === 'POST',
        { timeout: 30_000 },
      );
      await resendBtn.click();
      const resendRes = await resendWait;
      expect(resendRes.ok(), await resendRes.text()).toBeTruthy();
      const resent = (await resendRes.json()) as { expiresAt: string; emailDispatched: boolean };
      expect(resent.emailDispatched).toBeTruthy();
      expect(resent.expiresAt).toBeTruthy();

      await expect(
        owner.page.getByRole('status').filter({ hasText: /Reminder email dispatched successfully/i }),
      ).toBeVisible({ timeout: 10_000 });
      await expect(owner.page.getByTestId(`pending-invite-expires-${inviteBody.id}`)).toBeVisible();
    } finally {
      await owner.close();
    }
  });
});
