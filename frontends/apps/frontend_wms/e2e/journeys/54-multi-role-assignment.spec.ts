import { expect, test } from '../fixtures/roleFixture';
import { contextForRole, DEMO_PICKER_USER_ID } from './helpers';

test.describe('Journey 54: Additive multi-role assignment', () => {
  test.setTimeout(180_000);

  test('owner assigns PICKER + RETAIL_CASHIER and sees stacked badges', async ({ browser }) => {
    const owner = await contextForRole(browser, 'owner');
    try {
      await owner.page.goto('/settings?tab=users');
      await expect(owner.page.getByTestId('invite-user-button')).toBeVisible({ timeout: 20_000 });

      const editAccess = owner.page.getByTestId(`edit-access-${DEMO_PICKER_USER_ID}`);
      if ((await editAccess.count()) > 0) {
        await editAccess.click();
      } else {
        await owner.page.getByRole('row').filter({ hasText: 'picker@demo.test' }).getByRole('button', { name: 'Edit access' }).click();
      }
      await expect(owner.page.getByTestId('role-multiselect')).toBeVisible();

      const roles = owner.page.getByTestId('role-multiselect');
      const pickerBox = roles.getByTestId('role-option-PICKER').locator('input[type="checkbox"]');
      const cashierBox = roles.getByTestId('role-option-RETAIL_CASHIER').locator('input[type="checkbox"]');
      if (!(await pickerBox.isChecked())) {
        await pickerBox.check();
      }
      if (!(await cashierBox.isChecked())) {
        await cashierBox.check();
      }

      const orgWait = owner.page.waitForResponse(
        (r) => r.url().includes('/org-scope') && r.request().method() === 'PATCH',
        { timeout: 30_000 },
      );
      await owner.page.getByTestId('save-org-scope').click();
      const orgRes = await orgWait;
      expect(orgRes.ok(), await orgRes.text()).toBeTruthy();
      const body = (await orgRes.json()) as { roles: string[] };
      expect(body.roles).toEqual(expect.arrayContaining(['PICKER', 'RETAIL_CASHIER']));

      await expect(owner.page.getByTestId(`user-roles-${DEMO_PICKER_USER_ID}`)).toContainText(/PICKER/i);
      await expect(owner.page.getByTestId(`user-roles-${DEMO_PICKER_USER_ID}`)).toContainText(/RETAIL CASHIER/i);
    } finally {
      try {
        await owner.page.request.patch(`/api/v1/users/${DEMO_PICKER_USER_ID}/org-scope`, {
          data: { role: 'PICKER' },
        });
      } catch {
        // restore best-effort
      }
      await owner.close();
    }
  });
});
