import { dismissOnboardingTourIfPresent, expect, test } from '../fixtures/roleFixture';
import { contextForRole } from './helpers';

/**
 * Journey 90 — Paginated mobile cards, settings nav, RTLS/map guardrails, peek sheet.
 */
test.describe('Journey 90: Mobile responsiveness, settings, and pagination', () => {
  test.setTimeout(240_000);

  test('phone lists stay on one page of cards with pagination', async ({ browser }) => {
    const owner = await contextForRole(browser, 'owner');
    try {
      await owner.page.setViewportSize({ width: 390, height: 844 });
      await owner.page.goto('/sales-orders');
      await dismissOnboardingTourIfPresent(owner.page);
      await expect(owner.page.getByRole('heading', { name: /sales orders/i })).toBeVisible({
        timeout: 30_000,
      });
      await expect(owner.page.getByTestId('sales-orders-mobile-list')).toBeVisible({
        timeout: 20_000,
      });
      await expect(owner.page.getByTestId('sales-orders-table-view')).toHaveCount(0);
      await expect(owner.page.getByTestId('pagination')).toBeVisible();
      const cards = owner.page.locator('[data-testid^="sales-order-mobile-card-"]');
      await expect.poll(async () => cards.count()).toBeLessThanOrEqual(25);

      await owner.page.goto('/invoices');
      await expect(owner.page.getByTestId('invoices-page')).toBeVisible({ timeout: 20_000 });
      await expect(owner.page.getByTestId('invoices-mobile-list')).toBeVisible();
      await expect(owner.page.getByTestId('pagination')).toBeVisible();

      await owner.page.goto('/purchasing/suppliers');
      await expect(owner.page.getByTestId('suppliers-page')).toBeVisible({ timeout: 20_000 });
      await expect(owner.page.getByTestId('suppliers-mobile-list')).toBeVisible();
      await expect(owner.page.getByTestId('pagination')).toBeVisible();

      await owner.page.goto('/purchase-orders');
      await expect(owner.page.getByTestId('purchase-orders-page')).toBeVisible({ timeout: 20_000 });
      await expect(owner.page.getByRole('heading', { name: 'Purchase Orders' })).toBeVisible();
      await expect(owner.page.getByTestId('purchase-orders-header-actions')).toBeVisible();
      await expect(owner.page.getByRole('button', { name: 'Floor receive' })).toBeVisible();
      await expect(owner.page.getByRole('button', { name: 'New PO' })).toBeVisible();
      await expect(owner.page.getByTestId('purchase-orders-mobile-list')).toBeVisible();
      await expect(owner.page.getByTestId('purchase-orders-table-view')).toHaveCount(0);
      await expect(owner.page.getByTestId('pagination')).toBeVisible();
      const poCards = owner.page.locator('[data-testid^="purchase-order-mobile-card-"]');
      await expect.poll(async () => poCards.count()).toBeLessThanOrEqual(25);
      if ((await poCards.count()) > 0) {
        await poCards.first().click();
        await expect(owner.page.getByTestId('right-peek-drawer')).toBeVisible();
        await expect(owner.page.getByTestId('open-po-workspace')).toBeVisible();
      }
    } finally {
      await owner.close();
    }
  });

  test('customers, returns, exceptions, and manufacturing use mobile cards', async ({
    browser,
  }) => {
    const owner = await contextForRole(browser, 'owner');
    try {
      await owner.page.setViewportSize({ width: 390, height: 844 });
      await owner.page.goto('/customers');
      await dismissOnboardingTourIfPresent(owner.page);
      await expect(owner.page.getByTestId('customers-page')).toBeVisible({ timeout: 20_000 });
      await expect(owner.page.getByTestId('customers-mobile-list')).toBeVisible();
      await expect(owner.page.getByTestId('customers-table-view')).toHaveCount(0);
      await expect(owner.page.getByTestId('pagination')).toBeVisible();

      await owner.page.goto('/returns');
      await expect(owner.page.getByTestId('returns-page')).toBeVisible({ timeout: 20_000 });
      await expect(owner.page.getByTestId('returns-desktop-list')).toHaveCount(0);
      const returnsMobile = owner.page.getByTestId('returns-mobile-list');
      const returnsEmpty = owner.page.getByText(/No returns/i);
      await expect(returnsMobile.or(returnsEmpty).first()).toBeAttached();

      await owner.page.goto('/exceptions');
      await expect(owner.page.getByTestId('action-required-hub')).toBeVisible({ timeout: 20_000 });
      await expect(owner.page.getByTestId('exceptions-tab-holds')).toBeVisible();
      await expect(owner.page.getByTestId('exceptions-tab-sync')).toBeVisible();

      await owner.page.goto('/manufacturing/orders');
      await expect(owner.page.getByRole('heading', { name: /production orders/i })).toBeVisible({
        timeout: 20_000,
      });
      await expect(owner.page.getByTestId('manufacturing-orders-table-view')).toHaveCount(0);
      const moList = owner.page.getByTestId('manufacturing-orders-mobile-list');
      const moEmpty = owner.page.getByText(/No production orders/i);
      await expect(moList.or(moEmpty).first()).toBeVisible();
    } finally {
      await owner.close();
    }
  });

  test('settings uses a top select on phones; users render paginated cards', async ({
    browser,
  }) => {
    const owner = await contextForRole(browser, 'owner');
    try {
      await owner.page.setViewportSize({ width: 390, height: 844 });
      await owner.page.goto('/settings?tab=users');
      await dismissOnboardingTourIfPresent(owner.page);
      await expect(owner.page.getByTestId('settings-page')).toBeVisible({ timeout: 30_000 });
      await expect(owner.page.getByTestId('settings-nav-select')).toBeVisible();
      await expect(owner.page.getByTestId('users-mobile-list')).toBeVisible();
      await expect(owner.page.getByTestId('pagination')).toBeVisible();
      await expect(owner.page.getByTestId('role-matrix-desktop-banner')).toBeVisible();
    } finally {
      await owner.close();
    }
  });

  test('warehouse map and RTLS canvas stay off phones; peek drawer is a bottom sheet', async ({
    browser,
  }) => {
    const owner = await contextForRole(browser, 'owner');
    try {
      await owner.page.setViewportSize({ width: 390, height: 844 });
      await owner.page.goto('/settings?tab=warehouses');
      await dismissOnboardingTourIfPresent(owner.page);
      await expect(owner.page.getByTestId('warehouse-visualizer')).toBeVisible({ timeout: 30_000 });
      await expect(owner.page.getByTestId('warehouse-location-search')).toBeVisible();
      await expect(owner.page.getByTestId('digital-twin-map')).toHaveCount(0);
      await expect(owner.page.getByTestId('warehouse-view-toggle')).toBeHidden();

      await owner.page.goto('/rtls');
      await expect(owner.page.getByTestId('rtls-mobile-empty')).toBeVisible({ timeout: 20_000 });
      await expect(owner.page.getByTestId('rtls-vector-canvas')).toHaveCount(0);

      await owner.page.setViewportSize({ width: 1440, height: 900 });
      await owner.page.goto('/sales-orders');
      await expect(owner.page.getByTestId('sales-orders-table-view')).toBeVisible({
        timeout: 20_000,
      });
      const firstRow = owner.page.locator('table tbody tr').first();
      if (await firstRow.count()) {
        await firstRow.click();
        const drawer = owner.page.getByTestId('right-peek-drawer');
        await expect(drawer).toBeVisible();
        await expect(drawer).toHaveClass(/md:right-0/);
      }
    } finally {
      await owner.close();
    }
  });
});
