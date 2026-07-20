import { completeScannerPin, expect, test } from '../../e2e/fixtures/roleFixture';
import { clickNavLink, expandNavCategory } from '../../e2e/fixtures/nav';
import { contextForRole } from '../../e2e/journeys/helpers';

const DEMO_VIEWER_USER_ID = 'a0000000-0000-4000-8000-000000000205';

/**
 * Desktop-Admin persona — ADMIN on 1920×1080 office chrome.
 * Office rail is grouped (Inbound / Outbound / Inventory / …); deep-links use
 * `page.goto`. Prefer `clickNavLink` from `e2e/fixtures/nav` when clicking the rail.
 */
test.describe('Admin security & settings suite', () => {
  test.setTimeout(180_000);

  test('operations grids, role mutation toast, fintech OWNER boundary', async ({ browser }, testInfo) => {
    expect(testInfo.project.name).toBe('Desktop-Admin');
    const viewport = testInfo.project.use.viewport;
    expect(viewport?.width).toBe(1920);
    expect(viewport?.height).toBe(1080);

    const admin = await contextForRole(browser, 'admin');
    try {
      await admin.page.goto('/dashboard');
      await completeScannerPin(admin.page);
      await expect(admin.page.getByTestId('icon-rail')).toBeVisible({ timeout: 20_000 });

      // Grouped sidebar: expand parents before nested leaf clicks.
      await expandNavCategory(admin.page, 'Inbound');
      await expandNavCategory(admin.page, 'Inventory');
      await clickNavLink(admin.page, 'Purchase Orders');
      await expect(admin.page).toHaveURL(/\/purchase-orders/, { timeout: 15_000 });
      await clickNavLink(admin.page, 'Products');
      await expect(admin.page).toHaveURL(/\/products/, { timeout: 15_000 });

      await admin.page.goto('/settings/operations');
      // Full navigations wipe the in-memory AES key — re-unlock the shift PIN gate.
      await completeScannerPin(admin.page);
      await expect(admin.page).toHaveURL(/\/settings(\?tab=operations)?/, { timeout: 20_000 });
      await expect(admin.page.getByTestId('app-shell')).toBeVisible({ timeout: 20_000 });
      await expect(admin.page.getByTestId('operations-console')).toBeVisible({ timeout: 20_000 });
      await expect(admin.page.getByTestId('audit-log-table')).toBeVisible({ timeout: 20_000 });

      const auditGrid = admin.page.getByTestId('audit-log-grid');
      await expect(auditGrid).toBeVisible();
      await expect(auditGrid.getByRole('columnheader', { name: 'Timestamp' })).toBeVisible();
      await expect(auditGrid.getByRole('columnheader', { name: 'Actor' })).toBeVisible();
      await expect(auditGrid.getByRole('columnheader', { name: 'Action', exact: true })).toBeVisible();

      const filterWait = admin.page.waitForResponse(
        (r) =>
          r.url().includes('/api/v1/audit/tenant') &&
          r.url().includes('entityType=USER') &&
          r.ok(),
        { timeout: 20_000 },
      );
      await admin.page.getByTestId('audit-filter-entity-type').selectOption('USER');
      await filterWait;

      // Role mutation on seeded viewer — restore VIEWER in finally.
      await admin.page.goto('/settings?tab=users');
      await completeScannerPin(admin.page);
      await expect(admin.page.getByTestId('invite-user-button')).toBeVisible({ timeout: 20_000 });

      const editAccess = admin.page.getByTestId(`edit-access-${DEMO_VIEWER_USER_ID}`);
      if ((await editAccess.count()) > 0) {
        await editAccess.click();
      } else {
        await admin.page
          .getByRole('row')
          .filter({ hasText: 'viewer@demo.test' })
          .getByRole('button', { name: 'Edit access' })
          .click();
      }
      await expect(admin.page.getByTestId('user-detail-drawer')).toBeVisible();
      await expect(admin.page.getByTestId('org-scope-section')).toBeVisible();

      await admin.page.getByTestId('org-scope-section').getByLabel('Role').selectOption('PICKER');

      const orgWait = admin.page.waitForResponse(
        (r) => r.url().includes('/org-scope') && r.request().method() === 'PATCH',
        { timeout: 30_000 },
      );
      // Save control sits below the activity timeline — invoke via DOM click inside the scrollport.
      await admin.page.getByTestId('save-org-scope').evaluate((el) => (el as HTMLButtonElement).click());
      expect((await orgWait).ok()).toBeTruthy();
      await expect(admin.page.getByTestId('app-toast')).toContainText(/Access updated successfully/i, {
        timeout: 10_000,
      });

      // SECURITY: fintech is OWNER-only — ADMIN must be bounced off the route.
      await admin.page.goto('/settings/fintech');
      await completeScannerPin(admin.page);
      await expect(admin.page).not.toHaveURL(/\/settings\/fintech/, { timeout: 15_000 });
      await expect(admin.page.getByTestId('fintech-settings-page')).toHaveCount(0);
      await expect(admin.page).toHaveURL(/\/(dashboard|settings|fulfillment)/, { timeout: 15_000 });
    } finally {
      // Restore viewer role so other suites stay stable.
      try {
        await admin.page.request.patch(`/api/v1/users/${DEMO_VIEWER_USER_ID}/org-scope`, {
          data: { role: 'VIEWER' },
        });
      } catch {
        // ignore restore failures
      }
      await admin.close();
    }
  });
});
