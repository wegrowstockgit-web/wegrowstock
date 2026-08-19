import { expect, test } from '../fixtures/roleFixture';
import { contextForRole } from './helpers';

type RoleRow = {
  id: string;
  name: string;
  description?: string | null;
};

/**
 * Journey 71 — Role descriptions come from the API, not hardcoded frontend copy.
 */
test.describe('Journey 71: Dynamic role descriptions', () => {
  test.setTimeout(180_000);

  test('edit-access drawer shows live PICKER description', async ({ browser }) => {
    const owner = await contextForRole(browser, 'owner');
    try {
      const rolesRes = await owner.page.request.get('/api/v1/roles');
      test.skip(!rolesRes.ok(), 'Roles API not deployed');
      const roles = (await rolesRes.json()) as RoleRow[];
      const picker = roles.find((role) => role.name === 'PICKER');
      test.skip(!picker?.description, 'Role description column not migrated yet');

      await owner.page.goto('/settings?tab=users');
      const editBtn = owner.page.getByRole('button', { name: 'Edit access' }).first();
      await expect(editBtn).toBeVisible({ timeout: 20_000 });
      await editBtn.click();
      await expect(owner.page.getByTestId('role-multiselect')).toBeVisible({ timeout: 15_000 });
      await expect(owner.page.getByTestId('role-option-PICKER')).toContainText(picker.description!);
    } finally {
      await owner.close();
    }
  });
});
