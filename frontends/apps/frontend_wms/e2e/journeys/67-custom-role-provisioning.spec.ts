import { test } from '@playwright/test';
import { contextForRole, expect } from './helpers';

const COST_VIEW = 'inventory:cost:view';

type RoleRow = {
  id: string;
  name: string;
  isSystemRole?: boolean;
};

/**
 * Tenant admins can provision custom roles. System roles stay read-only.
 */
test.describe('Journey 67: Dynamic custom role provisioning', () => {
  test('owner creates, edits, and deletes a custom role without mutating system roles', async ({
    browser,
  }) => {
    test.setTimeout(180_000);
    const owner = await contextForRole(browser, 'owner');
    const roleName = `E2E Buyer ${Date.now().toString(36)}`;
    let createdCode: string | undefined;

    const deleteIfPresent = async (code: string) => {
      const list = await owner.page.request.get('/api/v1/roles');
      if (!list.ok()) return;
      const roles = (await list.json()) as RoleRow[];
      const match = roles.find((role) => role.name === code && role.isSystemRole === false);
      if (match) {
        await owner.page.request.delete(`/api/v1/roles/${match.id}`);
      }
    };

    try {
      const rolesRes = await owner.page.request.get('/api/v1/roles');
      test.skip(rolesRes.status() === 404, 'Roles API not deployed — rebuild API (deploy.bat apis)');
      expect(rolesRes.ok(), await rolesRes.text()).toBeTruthy();
      const roles = (await rolesRes.json()) as RoleRow[];
      const picker = roles.find((role) => role.name === 'PICKER');
      const admin = roles.find((role) => role.name === 'ADMIN');
      expect(picker, 'PICKER role missing').toBeTruthy();
      expect(admin?.isSystemRole, 'ADMIN should be a system role').toBe(true);
      expect(picker?.isSystemRole, 'PICKER should be a system role').toBe(true);

      await owner.page.goto('/settings?tab=users');
      const matrixEl = owner.page.getByTestId('role-permissions-matrix');
      await expect(matrixEl).toBeVisible({ timeout: 20_000 });
      await matrixEl.scrollIntoViewIfNeeded();

      const createBtn = owner.page.getByTestId('create-custom-role');
      await expect(createBtn).toBeVisible({ timeout: 15_000 });
      await createBtn.click();

      await expect(owner.page.getByTestId('create-role-dialog')).toBeVisible();
      await owner.page.getByTestId('create-role-name').fill(roleName);
      await owner.page.getByTestId('create-role-clone').selectOption({ label: 'Picker' });

      const createdWait = owner.page.waitForResponse(
        (res) =>
          res.url().includes('/api/v1/roles') &&
          res.request().method() === 'POST' &&
          res.status() === 201,
        { timeout: 20_000 },
      );
      await owner.page.getByTestId('create-role-submit').click();
      const created = (await (await createdWait).json()) as RoleRow;
      createdCode = created.name;
      expect(created.isSystemRole).toBe(false);

      await expect(owner.page.getByTestId(`delete-role-${created.name}`)).toBeVisible({
        timeout: 15_000,
      });
      await expect(owner.page.getByTestId(`perm-PICKER-${COST_VIEW}`)).toBeDisabled();
      await expect(owner.page.getByTestId(`perm-ADMIN-${COST_VIEW}`)).toBeDisabled();

      const customToggle = owner.page.getByTestId(`perm-${created.name}-${COST_VIEW}`);
      await expect(customToggle).toBeEnabled();
      await customToggle.click();
      await expect(customToggle).toHaveAttribute('aria-checked', 'true', { timeout: 10_000 });

      const locked = await owner.page.request.patch('/api/v1/settings/permissions', {
        data: { roleId: admin!.id, permissionKey: COST_VIEW, granted: false },
      });
      expect(locked.status()).toBe(422);

      await owner.page.getByTestId(`delete-role-${created.name}`).click();
      await expect(owner.page.getByTestId(`delete-role-${created.name}`)).toHaveCount(0, {
        timeout: 15_000,
      });
      createdCode = undefined;
    } finally {
      if (createdCode) {
        await deleteIfPresent(createdCode);
      }
      await owner.close();
    }
  });
});
