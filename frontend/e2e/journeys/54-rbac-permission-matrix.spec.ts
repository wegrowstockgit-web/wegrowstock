import { test } from '@playwright/test';
import { contextForRole, DEMO_PASSWORD, expect } from './helpers';

const COST_VIEW = 'inventory:cost:view';

type MatrixResponse = {
  roles: Array<{ id: string; name: string }>;
  permissionKeys: string[];
  grants: Array<{ roleId: string; permissionKey: string; granted: boolean }>;
};

type MeResponse = {
  userId: string;
  roles: string[];
  grantedPermissions?: string[];
};

type UserRow = {
  id: string;
  email: string;
  roles?: string[];
};

/**
 * Real functional e2e for composite multi-role RBAC:
 * 1) Matrix baseline (PICKER cost=false, MANAGER cost=true)
 * 2) Session grantedPermissions for picker vs manager
 * 3) PATCH toggle updates picker grants
 * 4) Multi-role union: PICKER + WAREHOUSE_MANAGER → cost view true
 * 5) Settings UI matrix smoke
 */
test.describe('Journey 54: Granular RBAC permission matrix', () => {
  test('matrix, session grants, PATCH toggle, and multi-role union', async ({ browser }) => {
    test.setTimeout(180_000);

    const owner = await contextForRole(browser, 'owner');
    try {
      const matrixRes = await owner.page.request.get('/api/v1/settings/permissions');
      test.skip(
        matrixRes.status() === 404,
        'Permissions API not deployed — rebuild API (deploy.bat)',
      );
      expect(matrixRes.ok(), await matrixRes.text()).toBeTruthy();
      const matrix = (await matrixRes.json()) as MatrixResponse;
      expect(matrix.permissionKeys).toContain(COST_VIEW);
      expect(matrix.permissionKeys).toContain('so:discount:override');

      const pickerRole = matrix.roles.find((r) => r.name === 'PICKER');
      const managerRole = matrix.roles.find((r) => r.name === 'WAREHOUSE_MANAGER');
      expect(pickerRole, 'PICKER role missing').toBeTruthy();
      expect(managerRole, 'WAREHOUSE_MANAGER role missing').toBeTruthy();

      const grantFor = (roleId: string) =>
        matrix.grants.find((g) => g.roleId === roleId && g.permissionKey === COST_VIEW)?.granted ??
        false;

      // Ensure baseline: picker denied, manager granted (seed or repair via PATCH)
      if (grantFor(managerRole!.id) !== true) {
        const repair = await owner.page.request.patch('/api/v1/settings/permissions', {
          data: { roleId: managerRole!.id, permissionKey: COST_VIEW, granted: true },
        });
        expect(repair.ok(), await repair.text()).toBeTruthy();
      }
      if (grantFor(pickerRole!.id) !== false) {
        const repair = await owner.page.request.patch('/api/v1/settings/permissions', {
          data: { roleId: pickerRole!.id, permissionKey: COST_VIEW, granted: false },
        });
        expect(repair.ok(), await repair.text()).toBeTruthy();
      }

      const matrixAfter = (await (
        await owner.page.request.get('/api/v1/settings/permissions')
      ).json()) as MatrixResponse;
      const grantAfter = (roleId: string) =>
        matrixAfter.grants.find((g) => g.roleId === roleId && g.permissionKey === COST_VIEW)
          ?.granted ?? false;
      expect(grantAfter(pickerRole!.id)).toBe(false);
      expect(grantAfter(managerRole!.id)).toBe(true);

      // --- Session grants for single-role users ---
      const manager = await contextForRole(browser, 'manager');
      try {
        const me = (await (await manager.page.request.get('/api/v1/auth/me')).json()) as MeResponse;
        expect(me.roles).toContain('WAREHOUSE_MANAGER');
        expect(me.grantedPermissions ?? []).toContain(COST_VIEW);
      } finally {
        await manager.close();
      }

      const picker = await contextForRole(browser, 'picker');
      let pickerUserId: string;
      try {
        const me = (await (await picker.page.request.get('/api/v1/auth/me')).json()) as MeResponse;
        pickerUserId = me.userId;
        expect(me.roles).toEqual(expect.arrayContaining(['PICKER']));
        expect(me.grantedPermissions ?? []).not.toContain(COST_VIEW);
      } finally {
        await picker.close();
      }

      // --- PATCH: temporarily grant cost view to PICKER role ---
      const grantPatch = await owner.page.request.patch('/api/v1/settings/permissions', {
        data: { roleId: pickerRole!.id, permissionKey: COST_VIEW, granted: true },
      });
      expect(grantPatch.ok(), await grantPatch.text()).toBeTruthy();

      const pickerGranted = await contextForRole(browser, 'picker');
      try {
        const me = (await (
          await pickerGranted.page.request.get('/api/v1/auth/me')
        ).json()) as MeResponse;
        expect(me.grantedPermissions ?? []).toContain(COST_VIEW);
      } finally {
        await pickerGranted.close();
      }

      // Restore picker role grant to false
      const revokePatch = await owner.page.request.patch('/api/v1/settings/permissions', {
        data: { roleId: pickerRole!.id, permissionKey: COST_VIEW, granted: false },
      });
      expect(revokePatch.ok(), await revokePatch.text()).toBeTruthy();

      // --- Multi-role union: add WAREHOUSE_MANAGER onto picker user ---
      const usersRes = await owner.page.request.get('/api/v1/users');
      expect(usersRes.ok()).toBeTruthy();
      const users = (await usersRes.json()) as UserRow[];
      const pickerUser =
        users.find((u) => u.email === 'picker@demo.test') ??
        users.find((u) => u.id === pickerUserId);
      expect(pickerUser, 'picker@demo.test not found').toBeTruthy();

      const addRoleRes = await owner.page.request.post(`/api/v1/users/${pickerUser!.id}/roles`, {
        data: { role: 'WAREHOUSE_MANAGER' },
      });
      test.skip(
        addRoleRes.status() === 404,
        'POST /users/{id}/roles not deployed — rebuild API',
      );
      expect(addRoleRes.ok(), await addRoleRes.text()).toBeTruthy();
      const added = (await addRoleRes.json()) as { roles: string[] };
      expect(added.roles).toEqual(
        expect.arrayContaining(['PICKER', 'WAREHOUSE_MANAGER']),
      );

      const hybrid = await contextForRole(browser, 'picker');
      try {
        const me = (await (await hybrid.page.request.get('/api/v1/auth/me')).json()) as MeResponse;
        expect(me.roles).toEqual(expect.arrayContaining(['PICKER', 'WAREHOUSE_MANAGER']));
        // PICKER alone denies cost view; manager grant makes union true
        expect(me.grantedPermissions ?? []).toContain(COST_VIEW);
      } finally {
        await hybrid.close();
      }

      // Restore picker to single role (PATCH replaces all roles)
      const restore = await owner.page.request.patch(`/api/v1/users/${pickerUser!.id}/role`, {
        data: { role: 'PICKER' },
      });
      if (!restore.ok()) {
        // If already single-role or race, verify via /users rather than failing the assertion.
        const usersAfter = (await (await owner.page.request.get('/api/v1/users')).json()) as UserRow[];
        const restored = usersAfter.find((u) => u.id === pickerUser!.id);
        expect(
          restored?.roles ?? [],
          `restore failed: ${await restore.text()}`,
        ).toEqual(['PICKER']);
      } else {
        expect(restore.ok()).toBeTruthy();
      }

      // --- UI matrix smoke ---
      await owner.page.goto('/settings?tab=users');
      const matrixEl = owner.page.getByTestId('role-permissions-matrix');
      await expect(matrixEl).toBeVisible({ timeout: 20_000 });
      await matrixEl.scrollIntoViewIfNeeded();
      // Prefer human label; fall back to raw key if an older build is served
      const costLabel = owner.page.getByText(/View Unit Costs|inventory:cost:view/);
      await expect(costLabel.first()).toBeVisible({ timeout: 15_000 });
      const pickerCostToggle = owner.page.getByTestId(`perm-PICKER-${COST_VIEW}`);
      await expect(pickerCostToggle).toBeVisible();
      await expect(pickerCostToggle).toHaveAttribute('aria-checked', 'false');

      // Toggle on via UI then off (leave baseline intact)
      await pickerCostToggle.click();
      await expect(pickerCostToggle).toHaveAttribute('aria-checked', 'true', { timeout: 10_000 });
      await pickerCostToggle.click();
      await expect(pickerCostToggle).toHaveAttribute('aria-checked', 'false', { timeout: 10_000 });
    } finally {
      // Best-effort restore if test aborted mid-union
      try {
        const usersRes = await owner.page.request.get('/api/v1/users');
        if (usersRes.ok()) {
          const users = (await usersRes.json()) as UserRow[];
          const pickerUser = users.find((u) => u.email === 'picker@demo.test');
          if (pickerUser) {
            await owner.page.request.patch(`/api/v1/users/${pickerUser.id}/role`, {
              data: { role: 'PICKER' },
            });
          }
        }
        const matrixRes = await owner.page.request.get('/api/v1/settings/permissions');
        if (matrixRes.ok()) {
          const matrix = (await matrixRes.json()) as MatrixResponse;
          const pickerRole = matrix.roles.find((r) => r.name === 'PICKER');
          if (pickerRole) {
            await owner.page.request.patch('/api/v1/settings/permissions', {
              data: { roleId: pickerRole.id, permissionKey: COST_VIEW, granted: false },
            });
          }
        }
      } catch {
        /* ignore cleanup errors */
      }
      await owner.close();
    }
  });

  test('login session payload includes grantedPermissions', async ({ page }) => {
    test.setTimeout(60_000);
    const login = await page.request.post('/api/v1/auth/login', {
      data: { email: 'manager@demo.test', password: DEMO_PASSWORD },
    });
    test.skip(!login.ok(), 'Demo API not reachable');
    const session = (await login.json()) as { grantedPermissions?: string[]; roles?: string[] };
    // Prefer /auth/me (always hydrated); fall back to login body when present.
    const meRes = await page.request.get('/api/v1/auth/me');
    expect(meRes.ok(), await meRes.text()).toBeTruthy();
    const me = (await meRes.json()) as { grantedPermissions?: string[]; roles?: string[] };
    const roles = me.roles ?? session.roles ?? [];
    const grants = me.grantedPermissions ?? session.grantedPermissions ?? [];
    expect(roles).toContain('WAREHOUSE_MANAGER');
    expect(grants).toContain(COST_VIEW);
  });
});