import { expect, test } from '../fixtures/roleFixture';
import { contextForRole } from './helpers';

/**
 * Journey 47 — Hybrid audit UI: user drawer timeline + operations compliance grid.
 */
test.describe('Journey 47: Hybrid audit trail UI', () => {
  test.setTimeout(180_000);

  test('user timeline + operations audit grid filters (owner only)', async ({ browser }) => {
    const owner = await contextForRole(browser, 'owner');
    try {
      await owner.page.goto('/settings?tab=users');
      await expect(owner.page.getByTestId('invite-user-button')).toBeVisible({ timeout: 20_000 });

      const editBtn = owner.page.getByRole('button', { name: 'Edit access' }).first();
      await expect(editBtn).toBeVisible({ timeout: 15_000 });
      await editBtn.click();
      await expect(owner.page.getByTestId('user-detail-drawer')).toBeVisible();
      await expect(owner.page.getByTestId('activity-timeline')).toBeVisible({ timeout: 15_000 });

      const timelineWait = owner.page.waitForResponse(
        (r) => r.url().includes('/api/v1/audit/entity/USER/') && r.request().method() === 'GET',
        { timeout: 20_000 },
      );
      // Drawer already mounted — ensure request completed (may have raced; refetch via reopen if needed)
      try {
        await timelineWait;
      } catch {
        await owner.page.getByRole('button', { name: 'Cancel' }).click();
        await owner.page.getByRole('button', { name: 'Edit access' }).first().click();
        await owner.page.waitForResponse(
          (r) => r.url().includes('/api/v1/audit/entity/USER/') && r.ok(),
          { timeout: 20_000 },
        );
      }
      await expect(owner.page.getByTestId('activity-timeline')).toBeVisible();

      await owner.page.getByRole('button', { name: 'Cancel' }).click();

      await owner.page.goto('/settings?tab=operations');
      await expect(owner.page.getByTestId('audit-log-table')).toBeVisible({ timeout: 20_000 });
      const auditGrid = owner.page.getByTestId('audit-log-grid');
      await expect(auditGrid).toBeVisible();
      await expect(auditGrid.getByRole('columnheader', { name: 'Timestamp' })).toBeVisible();
      await expect(auditGrid.getByRole('columnheader', { name: 'Actor' })).toBeVisible();
      await expect(auditGrid.getByRole('columnheader', { name: 'Action', exact: true })).toBeVisible();
      await expect(auditGrid.getByRole('columnheader', { name: 'Entity Type' })).toBeVisible();
      await expect(auditGrid.getByRole('columnheader', { name: 'Entity ID' })).toBeVisible();
      await expect(auditGrid.getByRole('columnheader', { name: 'Changes (Diff)' })).toBeVisible();

      const filterWait = owner.page.waitForResponse(
        (r) =>
          r.url().includes('/api/v1/audit/tenant') &&
          r.url().includes('entityType=TENANT_SETTINGS') &&
          r.ok(),
        { timeout: 20_000 },
      );
      await owner.page.getByTestId('audit-filter-entity-type').selectOption('TENANT_SETTINGS');
      await filterWait;
    } finally {
      await owner.close();
    }
  });

  test('warehouse manager cannot open settings audit surfaces', async ({ browser }) => {
    const manager = await contextForRole(browser, 'manager');
    try {
      await manager.page.goto('/settings?tab=operations');
      await expect(manager.page).not.toHaveURL(/\/settings/, { timeout: 15_000 });
      const denied = await manager.page.request.get('/api/v1/audit/tenant');
      expect(denied.status()).toBe(403);
    } finally {
      await manager.close();
    }
  });
});
