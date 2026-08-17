import { expect, test } from '../fixtures/roleFixture';
import { contextForRole, selectInviteRoles } from './helpers';

/**
 * Journey 46 — Settings change + invite produce SOC 2 audit rows
 * (DB trigger TG_* and Spring AOP INVITE_USER) visible in Operations audit.
 */
test.describe('Journey 46: Enterprise audit trail', () => {
  test.setTimeout(180_000);

  test('ops settings + invite appear in audit console', async ({ browser }) => {
    const owner = await contextForRole(browser, 'owner');
    const email = `audit.trail.${Date.now()}@demo.test`;
    try {
      await owner.page.goto('/settings?tab=operations');
      await expect(owner.page.getByTestId('operations-settings-panel')).toBeVisible({ timeout: 20_000 });
      await owner.page.getByTestId('ops-wave-max-lines').fill('47');
      const patchWait = owner.page.waitForResponse(
        (r) => r.url().includes('/api/v1/settings') && r.request().method() === 'PATCH',
        { timeout: 30_000 },
      );
      await owner.page.getByTestId('ops-settings-save').click();
      expect((await patchWait).ok()).toBeTruthy();

      await owner.page.goto('/settings?tab=users');
      await owner.page.getByTestId('invite-user-button').click();
      await owner.page.locator('#invite-email').fill(email);
      await selectInviteRoles(owner.page, 'VIEWER');
      const inviteWait = owner.page.waitForResponse(
        (r) =>
          r.url().includes('/api/v1/users/invitations') &&
          r.request().method() === 'POST' &&
          !r.url().includes('/resend'),
        { timeout: 30_000 },
      );
      await owner.page.getByTestId('invite-submit').click();
      expect((await inviteWait).ok()).toBeTruthy();

      const auditRes = await owner.page.request.get('/api/v1/audit/tenant?limit=100');
      expect(auditRes.ok(), await auditRes.text()).toBeTruthy();
      const page = (await auditRes.json()) as {
        items: Array<{
          action: string;
          entityType: string;
          diff?: Record<string, unknown>;
        }>;
      };
      const audit = page.items ?? [];
      expect(audit.some((a) => a.action === 'TG_UPDATE' && a.entityType === 'TENANT_SETTINGS')).toBeTruthy();
      expect(audit.some((a) => a.action === 'INVITE_USER' && a.entityType === 'INVITATION')).toBeTruthy();
      const aop = audit.find((a) => a.action === 'INVITE_USER');
      expect(aop?.diff?.source).toBe('spring_aop');
    } finally {
      await owner.close();
    }
  });
});
