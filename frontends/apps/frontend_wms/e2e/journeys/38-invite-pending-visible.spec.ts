import { expect, test } from '../fixtures/roleFixture';
import { contextForRole, selectInviteRoles } from './helpers';

/**
 * Journey 38 — Invite user shows pending invitation in Settings → Users
 * (root cause: API succeeded but UI never listed open invites).
 */
test.describe('Journey 38: Invite pending visibility', () => {
  test.setTimeout(180_000);

  test('send invitation → toast path + pending row; duplicate blocked', async ({ browser }) => {
    const owner = await contextForRole(browser, 'owner');
    const email = `invite.ui.${Date.now()}@demo.test`;
    try {
      await owner.page.goto('/settings?tab=users');
      await expect(owner.page.getByTestId('invite-user-button')).toBeVisible({ timeout: 20_000 });
      await owner.page.getByTestId('invite-user-button').click();
      await expect(owner.page.getByTestId('invite-user-modal')).toBeVisible();

      await owner.page.locator('#invite-email').fill(email);
      await selectInviteRoles(owner.page, 'PICKER');

      const inviteWait = owner.page.waitForResponse(
        (r) =>
          r.url().includes('/api/v1/users/invitations') &&
          r.request().method() === 'POST',
        { timeout: 30_000 },
      );
      await owner.page.getByTestId('invite-submit').click();
      const inviteRes = await inviteWait;
      expect(inviteRes.ok(), await inviteRes.text()).toBeTruthy();
      const body = (await inviteRes.json()) as { email: string; role: string; token: string };
      expect(body.email).toBe(email);
      expect(body.role).toBe('PICKER');
      expect(body.token).toBeTruthy();

      await expect(owner.page.getByTestId('invite-user-modal')).toBeHidden({ timeout: 10_000 });
      await expect(owner.page.getByTestId('pending-invitations')).toBeVisible({ timeout: 15_000 });
      await expect(owner.page.getByTestId(`pending-invite-${email}`)).toBeVisible();
      await expect(owner.page.getByTestId(`pending-invite-${email}`)).toContainText('PICKER');
      await expect(owner.page.getByTestId(`pending-invite-${email}`)).toContainText(/PENDING/i);

      // Duplicate invite surfaces API detail
      await owner.page.getByTestId('invite-user-button').click();
      await owner.page.locator('#invite-email').fill(email);
      await selectInviteRoles(owner.page, 'VIEWER');
      const dupWait = owner.page.waitForResponse(
        (r) =>
          r.url().includes('/api/v1/users/invitations') &&
          r.request().method() === 'POST',
        { timeout: 30_000 },
      );
      await owner.page.getByTestId('invite-submit').click();
      expect((await dupWait).status()).toBe(409);
      await expect(owner.page.getByTestId('invite-error')).toContainText(/already exists|open invitation/i);
    } finally {
      await owner.close();
    }
  });
});
