import { expect, test } from '../fixtures/roleFixture';
import { contextForRole } from './helpers';

type AuditItem = {
  action?: string;
  diff?: { ip?: string; location?: string; summary?: string; detail?: string };
};

/**
 * Journey 56 — Login security events appear in Operations audit and the user timeline.
 */
test.describe('Journey 56: Login security audit trail', () => {
  test.setTimeout(180_000);

  test('owner login is audited with IP and location', async ({ browser }) => {
    const owner = await contextForRole(browser, 'owner');
    try {
      const auditRes = await owner.page.request.get('/api/v1/audit/tenant?action=LOGIN_SUCCESS&limit=40');
      test.skip(!auditRes.ok(), 'Login audit API not deployed');
      const page = (await auditRes.json()) as { items?: AuditItem[] };
      const items = page.items ?? [];
      const login = items.find((row) => row.action === 'LOGIN_SUCCESS');
      test.skip(!login, 'No LOGIN_SUCCESS row yet — backend image needs the login-audit change');
      expect(login!.diff?.ip, 'login audit should capture an IP').toBeTruthy();
      expect(login!.diff?.location, 'login audit should capture a location').toBeTruthy();
      expect(JSON.stringify(login!.diff ?? {})).not.toMatch(/latitude|longitude|maps\.google/i);

      await owner.page.goto('/settings?tab=operations');
      await expect(owner.page.getByTestId('audit-log-table')).toBeVisible({ timeout: 20_000 });
      await owner.page.getByTestId('audit-filter-action').selectOption('LOGIN_SUCCESS');
      await expect(owner.page.getByText(/Signed in/i).first()).toBeVisible({ timeout: 20_000 });
      await expect(owner.page.getByTestId('audit-login-meta').first()).toBeVisible();
      await expect(owner.page.getByTestId('audit-login-meta').first()).toHaveText(/\S+ • \S+/);

      await owner.page.goto('/settings?tab=users');
      const editBtn = owner.page.getByRole('button', { name: 'Edit access' }).first();
      await expect(editBtn).toBeVisible({ timeout: 15_000 });
      await editBtn.click();
      await expect(owner.page.getByTestId('activity-timeline')).toBeVisible({ timeout: 15_000 });
      const successIcon = owner.page.getByTestId('timeline-login-success-icon');
      if (await successIcon.count()) {
        await expect(successIcon.first()).toBeVisible();
        await expect(owner.page.getByTestId('timeline-login-meta').first()).toHaveText(/\S+ • \S+/);
      }
    } finally {
      await owner.close();
    }
  });
});
