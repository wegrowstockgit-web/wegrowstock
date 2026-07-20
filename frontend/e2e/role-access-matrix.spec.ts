import type { Page } from '@playwright/test';
import { expect, test } from './fixtures/roleFixture';

/**
 * Fuller role × surface matrix for nav visibility, route walls, and CTA affordances.
 * Complements rbac-boundaries / pillar-role-visibility / journey 08 with the gaps
 * those suites leave (VIEWER walls, Reports labor negatives, picker office deep-links).
 */

async function rail(page: Page) {
  return page.getByTestId('icon-rail');
}

async function expectNavVisible(page: Page, name: string | RegExp) {
  await expect((await rail(page)).getByRole('link', { name, exact: true })).toBeVisible();
}

async function expectNavHidden(page: Page, name: string | RegExp) {
  await expect((await rail(page)).getByRole('link', { name, exact: true })).toHaveCount(0);
}

async function expectOrgSettingsVisible(page: Page) {
  await expect((await rail(page)).locator('a[href="/settings"]')).toBeVisible();
}

async function expectOrgSettingsHidden(page: Page) {
  await expect((await rail(page)).locator('a[href="/settings"]')).toHaveCount(0);
}

/** Settles after ProtectedRoute Navigate (picker → fulfillment, others → dashboard/showroom). */
async function gotoAndSettle(page: Page, path: string) {
  await page.goto(path);
  await page.waitForLoadState('domcontentloaded');
  await expect(page.getByText('Loading session')).toHaveCount(0, { timeout: 15_000 }).catch(() => undefined);
}

async function expectRedirectAway(page: Page, deniedPath: string | RegExp, allowed: RegExp) {
  await expect(page).not.toHaveURL(deniedPath, { timeout: 15_000 });
  await expect(page).toHaveURL(allowed, { timeout: 15_000 });
}

test.describe('Role access matrix', () => {
  test.describe('Office nav visibility', () => {
    test('owner sees ops, reports, settings entry points', async ({ ownerPage }) => {
      await gotoAndSettle(ownerPage, '/dashboard');
      await expectNavVisible(ownerPage, 'Reports');
      await expectNavVisible(ownerPage, 'Exceptions');
      await expectNavVisible(ownerPage, 'Import');
      await expectNavVisible(ownerPage, 'Lot Trace');
      await expectNavVisible(ownerPage, 'Issue Supplies');
      await expectNavVisible(ownerPage, 'Fulfillment');
      await expectOrgSettingsVisible(ownerPage);
    });

    test('admin sees reports + settings; manager does not', async ({ adminPage, managerPage }) => {
      await gotoAndSettle(adminPage, '/dashboard');
      await expectNavVisible(adminPage, 'Reports');
      await expectOrgSettingsVisible(adminPage);

      await gotoAndSettle(managerPage, '/dashboard');
      await expectNavHidden(managerPage, 'Reports');
      await expectOrgSettingsHidden(managerPage);
      await expectNavVisible(managerPage, 'Exceptions');
      await expectNavVisible(managerPage, 'Fulfillment');
      await expectNavVisible(managerPage, 'Purchase Orders');
    });

    test('viewer: Lot Trace + office reads; no floor ops / reports / settings', async ({
      viewerPage,
    }) => {
      await gotoAndSettle(viewerPage, '/dashboard');
      await expectNavVisible(viewerPage, 'Lot Trace');
      await expectNavVisible(viewerPage, 'Dashboard');
      await expectNavVisible(viewerPage, 'Products');
      await expectNavVisible(viewerPage, 'Customers');
      await expectNavVisible(viewerPage, 'Purchase Orders');

      await expectNavHidden(viewerPage, 'Issue Supplies');
      await expectNavHidden(viewerPage, 'Fulfillment');
      await expectNavHidden(viewerPage, 'Exceptions');
      await expectNavHidden(viewerPage, 'Import');
      await expectNavHidden(viewerPage, 'Reports');
      await expectNavHidden(viewerPage, 'Returns');
      await expectNavHidden(viewerPage, 'Manufacturing');
      await expectOrgSettingsHidden(viewerPage);
    });

    test('picker on office shell: floor links kept; commercial nav hidden', async ({
      pickerPage,
    }) => {
      await gotoAndSettle(pickerPage, '/dashboard');
      // Exclusive pickers may still deep-link into AppShell; rail must not advertise office commerce.
      await expectNavVisible(pickerPage, 'Fulfillment');
      await expectNavVisible(pickerPage, 'Lot Trace');
      await expectNavHidden(pickerPage, 'Purchase Orders');
      await expectNavHidden(pickerPage, 'Sales Orders');
      await expectNavHidden(pickerPage, 'Customers');
      await expectNavHidden(pickerPage, 'Reports');
      await expectNavHidden(pickerPage, 'Exceptions');
      await expectOrgSettingsHidden(pickerPage);
    });
  });

  test.describe('Route walls', () => {
    test('viewer bounced from settings, reports, exceptions, import, floor issue', async ({
      viewerPage,
    }) => {
      await gotoAndSettle(viewerPage, '/settings');
      await expectRedirectAway(viewerPage, /\/settings\/?$/, /\/dashboard/);

      await gotoAndSettle(viewerPage, '/settings/billing');
      await expectRedirectAway(viewerPage, /\/settings\/billing/, /\/dashboard/);

      await gotoAndSettle(viewerPage, '/reports');
      await expectRedirectAway(viewerPage, /\/reports/, /\/dashboard/);

      await gotoAndSettle(viewerPage, '/reports?tab=labor');
      await expectRedirectAway(viewerPage, /\/reports/, /\/dashboard/);
      await expect(viewerPage.getByTestId('reports-labor-panel')).toHaveCount(0);

      await gotoAndSettle(viewerPage, '/exceptions');
      await expectRedirectAway(viewerPage, /\/exceptions/, /\/dashboard/);

      await gotoAndSettle(viewerPage, '/import');
      await expectRedirectAway(viewerPage, /\/import/, /\/dashboard/);

      await gotoAndSettle(viewerPage, '/issue-supplies');
      await expectRedirectAway(viewerPage, /\/issue-supplies/, /\/dashboard/);

      await gotoAndSettle(viewerPage, '/fulfillment');
      await expectRedirectAway(viewerPage, /\/fulfillment/, /\/dashboard/);

      // Allowed compliance / read surfaces
      await gotoAndSettle(viewerPage, '/compliance/lot-trace');
      await expect(viewerPage).toHaveURL(/\/compliance\/lot-trace/);
      await expect(viewerPage.getByRole('heading', { name: /Lot genealogy/i })).toBeVisible({
        timeout: 15_000,
      });

      await gotoAndSettle(viewerPage, '/products');
      await expect(viewerPage).toHaveURL(/\/products/);
    });

    test('manager blocked from settings/reports/fintech; allowed exceptions + floor', async ({
      managerPage,
    }) => {
      await gotoAndSettle(managerPage, '/settings');
      await expectRedirectAway(managerPage, /\/settings\/?$/, /\/dashboard/);

      await gotoAndSettle(managerPage, '/settings/fintech');
      await expectRedirectAway(managerPage, /\/settings\/fintech/, /\/dashboard/);

      await gotoAndSettle(managerPage, '/reports?tab=labor');
      await expectRedirectAway(managerPage, /\/reports/, /\/dashboard/);
      await expect(managerPage.getByTestId('reports-labor-panel')).toHaveCount(0);

      await gotoAndSettle(managerPage, '/exceptions');
      await expect(managerPage).toHaveURL(/\/exceptions/);

      await gotoAndSettle(managerPage, '/fulfillment');
      await expect(managerPage).toHaveURL(/\/fulfillment/);
      await expect(managerPage.getByText('Floor ops')).toBeVisible();
    });

    test('picker blocked from admin surfaces; allowed floor + returns receive', async ({
      pickerPage,
    }) => {
      await gotoAndSettle(pickerPage, '/settings');
      await expectRedirectAway(pickerPage, /\/settings/, /\/fulfillment/);

      await gotoAndSettle(pickerPage, '/reports?tab=labor');
      await expectRedirectAway(pickerPage, /\/reports/, /\/fulfillment/);
      await expect(pickerPage.getByTestId('reports-labor-panel')).toHaveCount(0);

      await gotoAndSettle(pickerPage, '/exceptions');
      await expectRedirectAway(pickerPage, /\/exceptions/, /\/fulfillment/);

      await gotoAndSettle(pickerPage, '/import');
      await expectRedirectAway(pickerPage, /\/import/, /\/fulfillment/);

      await gotoAndSettle(pickerPage, '/returns/receive');
      await expect(pickerPage).toHaveURL(/\/returns\/receive/);
      await expect(pickerPage.getByText('Floor ops')).toBeVisible();

      // Ungated office deep-links remain reachable (nav-hidden only) — document current product.
      await gotoAndSettle(pickerPage, '/products');
      await expect(pickerPage).toHaveURL(/\/products/);
    });

    test('admin can open reports labor; owner can open fintech; admin cannot', async ({
      adminPage,
      ownerPage,
    }) => {
      await gotoAndSettle(adminPage, '/reports?tab=labor');
      await expect(adminPage).toHaveURL(/\/reports/);
      await expect(adminPage.getByTestId('reports-labor-panel')).toBeVisible({ timeout: 20_000 });
      await expect(adminPage.getByRole('heading', { name: 'Reports', exact: true })).toBeVisible();

      await gotoAndSettle(adminPage, '/settings/fintech');
      await expectRedirectAway(adminPage, /\/settings\/fintech/, /\/(dashboard|settings)/);
      await expect(adminPage.getByTestId('fintech-settings-page')).toHaveCount(0);

      await gotoAndSettle(ownerPage, '/settings/fintech');
      await expect(ownerPage.getByTestId('fintech-settings-page')).toBeVisible({ timeout: 20_000 });

      await gotoAndSettle(ownerPage, '/reports?tab=labor');
      await expect(ownerPage.getByTestId('reports-labor-panel')).toBeVisible({ timeout: 20_000 });
    });

    test('B2B customer stays in showroom; office + floor rejected', async ({ b2bPage }) => {
      await gotoAndSettle(b2bPage, '/showroom/catalog');
      await expect(b2bPage).toHaveURL(/\/showroom\/catalog/);

      await gotoAndSettle(b2bPage, '/dashboard');
      await expectRedirectAway(b2bPage, /\/dashboard/, /\/showroom/);

      await gotoAndSettle(b2bPage, '/settings');
      await expectRedirectAway(b2bPage, /\/settings/, /\/showroom/);

      await gotoAndSettle(b2bPage, '/fulfillment');
      await expectRedirectAway(b2bPage, /\/fulfillment/, /\/showroom/);

      await gotoAndSettle(b2bPage, '/reports');
      await expectRedirectAway(b2bPage, /\/reports/, /\/showroom/);
    });
  });

  test.describe('CTA affordances', () => {
    test('Add customer: owner/admin yes; manager/viewer no', async ({
      ownerPage,
      adminPage,
      managerPage,
      viewerPage,
    }) => {
      for (const page of [ownerPage, adminPage]) {
        await gotoAndSettle(page, '/customers');
        await expect(page.getByRole('heading', { name: 'Customers', exact: true })).toBeVisible();
        await expect(page.getByRole('button', { name: 'Add customer' }).first()).toBeVisible();
      }
      for (const page of [managerPage, viewerPage]) {
        await gotoAndSettle(page, '/customers');
        await expect(page.getByRole('heading', { name: 'Customers', exact: true })).toBeVisible();
        await expect(page.getByRole('button', { name: 'Add customer' })).toHaveCount(0);
      }
    });
  });

  test.describe('Command palette vs floor route', () => {
    test('picker can open Receive Returns via palette when on office shell', async ({
      pickerPage,
    }) => {
      await gotoAndSettle(pickerPage, '/dashboard');
      await pickerPage.keyboard.press('Control+k');
      const palette = pickerPage.getByRole('dialog', { name: /command palette/i });
      await expect(palette).toBeVisible({ timeout: 10_000 });
      await palette.getByPlaceholder(/Search pages/i).fill('Receive Returns');
      const item = palette.getByRole('button', { name: /Receive Returns/i });
      await expect(item).toBeVisible({ timeout: 10_000 });
      await item.click();
      await expect(pickerPage).toHaveURL(/\/returns\/receive/, { timeout: 15_000 });
    });
  });
});
