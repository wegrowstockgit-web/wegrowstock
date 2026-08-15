import { expect, test } from '../fixtures/roleFixture';
import { contextForRole, apiJson, DEMO_PICKER_USER_ID } from './helpers';

/**
 * Journey 35 — Two-tier user management:
 * self-service personal profile vs admin org-scope (RBAC + audit correlation).
 */
test.describe('Journey 35: User profile two-tier', () => {
  test.setTimeout(240_000);

  test('picker edits personal profile; cannot PATCH org-scope; owner audits org change', async ({
    browser,
  }) => {
    const picker = await contextForRole(browser, 'picker');
    const owner = await contextForRole(browser, 'owner');
    const stamp = Date.now().toString(36);
    try {
      // Self-service route available to non-admin
      await picker.page.goto('/settings/profile');
      await expect(picker.page.getByTestId('profile-settings-page')).toBeVisible({ timeout: 20_000 });
      await expect(picker.page.getByTestId('personal-profile-form')).toBeVisible();
      await expect(picker.page.getByTestId('org-scope-readonly')).toBeVisible();

      await picker.page.getByLabel('Phone').fill('555-2222');
      await picker.page.getByLabel('City').fill('Chicago');
      const savePersonal = picker.page.waitForResponse(
        (r) =>
          r.url().includes('/api/v1/users/me/profile') && r.request().method() === 'PATCH',
        { timeout: 30_000 },
      );
      await picker.page.getByRole('button', { name: /Save personal settings/i }).click();
      expect((await savePersonal).ok()).toBeTruthy();

      // Picker blocked from admin settings users → redirected to fulfillment
      await picker.page.goto('/settings?tab=users');
      await expect(picker.page).toHaveURL(/\/fulfillment/, { timeout: 15_000 });
      await expect(picker.page.getByTestId('invite-user-button')).toHaveCount(0);

      // API: picker forbidden on org-scope
      const forbidden = await picker.page.request.patch(
        `/api/v1/users/${DEMO_PICKER_USER_ID}/org-scope`,
        {
          data: { corporateDepartment: 'ShouldFail', timezonePreference: 'UTC' },
        },
      );
      expect(forbidden.status()).toBe(403);

      // Owner edits picker org scope via UI
      await owner.page.goto('/settings?tab=users');
      await expect(owner.page.getByTestId('invite-user-button')).toBeVisible({ timeout: 20_000 });
      const editAccess = owner.page.getByTestId(`edit-access-${DEMO_PICKER_USER_ID}`);
      if ((await editAccess.count()) > 0) {
        await editAccess.click();
      } else {
        const row = owner.page.getByRole('row').filter({ hasText: 'picker@demo.test' });
        await row.getByRole('button', { name: 'Edit access' }).click();
      }
      await expect(owner.page.getByTestId('org-scope-section')).toBeVisible();
      await expect(owner.page.getByTestId('warehouse-multiselect')).toBeVisible();
      await owner.page.getByLabel('Department').fill(`Floor Ops ${stamp}`);
      await owner.page.getByLabel('Timezone').fill('America/Chicago');
      await owner.page.getByLabel('Shift schedule').selectOption('DAY');

      // Ensure WH-01 checked for LBAC multi-select
      const whCheckbox = owner.page
        .getByTestId('warehouse-multiselect')
        .locator('label')
        .filter({ hasText: /WH-01/i })
        .locator('input[type="checkbox"]');
      if ((await whCheckbox.count()) > 0 && !(await whCheckbox.isChecked())) {
        await whCheckbox.check();
      }

      const orgWait = owner.page.waitForResponse(
        (r) => r.url().includes('/org-scope') && r.request().method() === 'PATCH',
        { timeout: 30_000 },
      );
      await owner.page.getByTestId('save-org-scope').click();
      const orgRes = await orgWait;
      expect(orgRes.ok(), await orgRes.text()).toBeTruthy();

      // Correlate audit (actor = owner, entity = picker)
      const ownerMe = await apiJson<{ userId: string }>(owner.page, '/api/v1/auth/me');
      const audit = await apiJson<
        Array<{
          action: string;
          actorUserId?: string;
          entityId?: string;
          diff?: Record<string, unknown>;
        }>
      >(owner.page, '/api/v1/operations/audit');
      const hit = audit.find(
        (a) =>
          a.action === 'USER_ORG_UPDATE' &&
          a.entityId === DEMO_PICKER_USER_ID &&
          JSON.stringify(a.diff ?? {}).includes(stamp),
      );
      expect(hit, 'USER_ORG_UPDATE audit missing').toBeTruthy();
      expect(hit!.actorUserId).toBe(ownerMe.userId);

      // Personal profile reflects org badges (read-only)
      await picker.page.goto('/settings/profile');
      await expect(picker.page.getByTestId('org-scope-readonly')).toContainText(
        new RegExp(`Floor Ops ${stamp}|America/Chicago|DAY`),
      );
    } finally {
      await picker.close();
      await owner.close();
    }
  });
});
