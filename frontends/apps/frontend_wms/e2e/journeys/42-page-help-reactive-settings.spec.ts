import { completeScannerPin, dismissOnboardingTourIfPresent, expect, test } from '../fixtures/roleFixture';
import { contextForRole } from './helpers';

/**
 * Granular Page Info — stays open across settings tabs and surfaces status enums.
 */
test.describe('Reactive page help + granular components', () => {
  test.setTimeout(120_000);

  test('settings tabs cross-fade help while drawer stays open', async ({ browser }) => {
    const owner = await contextForRole(browser, 'owner');
    try {
      await owner.page.setViewportSize({ width: 1280, height: 800 });
      await owner.page.goto('/settings?tab=users');
      await completeScannerPin(owner.page);
      await dismissOnboardingTourIfPresent(owner.page);

      await owner.page.getByTestId('page-help-trigger').click();
      await expect(owner.page.getByTestId('page-help-panel')).toBeVisible({ timeout: 15_000 });
      await expect(owner.page.getByTestId('page-help-title')).toContainText(/Users/i);
      await expect(owner.page.getByTestId('page-help-route')).toHaveText('/settings?tab=users');
      await expect(owner.page.getByTestId('page-help-body')).toContainText(
        /Owner|Admin|Picker|warehouse|WAREHOUSE_MANAGER/i,
      );

      const settingsNav = owner.page.getByTestId('settings-nav');
      await settingsNav.getByRole('button', { name: 'Operations', exact: true }).click();
      await expect(owner.page).toHaveURL(/tab=operations/, { timeout: 10_000 });
      await expect(owner.page.getByTestId('page-help-panel')).toBeVisible();
      await expect(owner.page.getByTestId('page-help-title')).toContainText(/Operations/i, {
        timeout: 10_000,
      });
      await expect(owner.page.getByTestId('page-help-route')).toHaveText('/settings?tab=operations');
      await expect(owner.page.getByTestId('page-help-body')).toContainText(/Audit|adjustment/i);

      await settingsNav.getByRole('button', { name: 'Integrations', exact: true }).click();
      await expect(owner.page).toHaveURL(/tab=integrations/);
      await expect(owner.page.getByTestId('page-help-title')).toContainText(/Integration/i, {
        timeout: 10_000,
      });
      await expect(owner.page.getByTestId('page-help-body')).toContainText(
        /Shopify|Xero|accounting|E-commerce|storefront/i,
      );
    } finally {
      await owner.close();
    }
  });

  test('sales orders help lists ALLOCATED status and data origin', async ({ browser }) => {
    const manager = await contextForRole(browser, 'manager');
    try {
      await manager.page.setViewportSize({ width: 1280, height: 800 });
      await manager.page.goto('/sales-orders');
      await completeScannerPin(manager.page);
      await dismissOnboardingTourIfPresent(manager.page);

      await manager.page.getByTestId('page-help-trigger').click();
      await expect(manager.page.getByTestId('page-help-body')).toBeVisible({ timeout: 15_000 });
      await expect(manager.page.getByTestId('page-help-statuses').first()).toBeVisible();
      await expect(manager.page.getByTestId('page-help-body')).toContainText(/ALLOCATED/i);
      await expect(manager.page.getByTestId('page-help-body')).toContainText(/Where this comes from/i);
      await expect(manager.page.getByTestId('page-help-component').first()).toBeVisible();
    } finally {
      await manager.close();
    }
  });

  test('dashboard help documents KPI snapshot origin', async ({ browser }) => {
    const owner = await contextForRole(browser, 'owner');
    try {
      await owner.page.goto('/dashboard');
      await completeScannerPin(owner.page);
      await dismissOnboardingTourIfPresent(owner.page);

      await owner.page.getByTestId('page-help-trigger').click();
      await expect(owner.page.getByTestId('page-help-body')).toContainText(/Stock Value|Low Stock|Open Orders/i);
      await expect(owner.page.getByTestId('page-help-body')).toContainText(
        /Where this comes from|floor and office activity|Live warehouse totals/i,
      );
      await expect(owner.page.getByTestId('page-help-body')).not.toContainText(/CQRS|DashboardKpiSnapshot|\/api\//i);
      await expect(owner.page.getByTestId('page-help-body')).toContainText(/Needs Allocation|Ready to Invoice/i);
    } finally {
      await owner.close();
    }
  });
});
