import { expect, test } from '@playwright/test';
import { completeScannerPin, loginAsDemo } from './fixtures/roleFixture';
import { clickNavLink } from './fixtures/nav';

test.describe('Authentication', () => {
  test('demo login reaches dashboard', async ({ page }) => {
    await loginAsDemo(page);
    await expect(page).toHaveURL(/\/dashboard/);
    await expect(page.getByText('Stock value')).toBeVisible({ timeout: 15_000 });
  });
});

test.describe('Dashboard', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsDemo(page);
    await expect(page).toHaveURL(/\/dashboard/);
  });

  test('shows KPI cards without crashing', async ({ page }) => {
    const kpis = page.getByTestId('floating-kpi-row');
    await expect(kpis).toBeVisible();
    await expect(page.getByTestId('kpi-stock-value')).toBeVisible();
    await expect(page.getByTestId('kpi-open-orders')).toBeVisible();
    await expect(page.getByTestId('kpi-low-stock-items')).toBeVisible();
    await expect(page.getByTestId('kpi-unpaid-invoices')).toBeVisible();
  });

  test('shows activity feed timeline', async ({ page }) => {
    await expect(page.getByTestId('activity-feed')).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText('Activity feed')).toBeVisible();
  });

  test('shows actionable work queue', async ({ page }) => {
    await expect(page.getByTestId('work-queue')).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText('Do this next')).toBeVisible();
    await expect(page.getByTestId('work-queue-needsAllocation')).toBeVisible();
  });
});

test.describe('Navigation', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsDemo(page);
    await expect(page).toHaveURL(/\/dashboard/);
  });

  test('manufacturing and settings routes load', async ({ page }) => {
    await expect(page.getByTestId('icon-rail')).toBeVisible();
    await clickNavLink(page, 'Manufacturing');
    await expect(page).toHaveURL(/\/manufacturing/);

    await clickNavLink(page, 'Organization');
    await expect(page).toHaveURL(/\/settings/);
    await expect(page.getByRole('button', { name: 'Integrations' })).toBeVisible();
  });

  test('settings billing and tax configuration load', async ({ page }) => {
    await page.goto('/settings/billing');
    await completeScannerPin(page);
    await expect(page).toHaveURL(/\/settings\/billing/);
    await expect(page.getByText('Billing & payments')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Connect Stripe' })).toBeVisible();
    await expect(page.getByText('Shipping accounts')).toBeVisible();

    await page.goto('/settings');
    await completeScannerPin(page);
    await page.getByRole('button', { name: 'Inventory Rules' }).click();
    await expect(page.getByRole('heading', { name: 'Stacked tax schemes' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Legacy single tax rates' })).toBeVisible();
  });

  test('cycle counts warehouse page loads', async ({ page }) => {
    await clickNavLink(page, 'Cycle counts');
    await expect(page).toHaveURL(/\/cycle-counts/);
    await expect(page.getByRole('heading', { name: 'Priority audits' })).toBeVisible();
  });

  test('purchase order form includes warehouse and freight fields', async ({ page }) => {
    await clickNavLink(page, 'Purchase Orders');
    await page.getByRole('button', { name: 'New PO' }).click();
    await expect(page.getByText('Destination warehouse')).toBeVisible();
    await expect(page.getByLabel('Freight amount')).toBeVisible();
  });

  test('sales order form includes customer PO and ship date', async ({ page }) => {
    await clickNavLink(page, 'Sales Orders');
    await page.getByRole('button', { name: 'New order' }).click();
    await expect(page.getByLabel('Customer PO number')).toBeVisible();
    await expect(page.getByLabel('Requested ship date')).toBeVisible();
    await expect(page.getByText('Ship-from warehouse')).toBeVisible();
  });

  test('fulfillment pack mode shows scale workflow', async ({ page }) => {
    await clickNavLink(page, 'Fulfillment');
    await expect(page.getByText('Floor ops')).toBeVisible();
    await page.getByRole('button', { name: 'Pack' }).click();
    await expect(page.getByText('Packing & shipping label')).toBeVisible();
    await expect(page.getByText('Sales order')).toBeVisible();
  });

  test('products page shows UoM editor control', async ({ page }) => {
    await clickNavLink(page, 'Products');
    await expect(page).toHaveURL(/\/products/);
    await expect(page.getByRole('button', { name: /Edit UoM for/ }).first()).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText('On hand', { exact: true })).toBeVisible();
    await expect(page.getByRole('tab', { name: 'All' })).toBeVisible();
    await expect(page.getByRole('tab', { name: 'Low stock' })).toBeVisible();

    await expect(page.getByRole('button', { name: /Edit UoM for/ }).first()).toBeVisible();
    await expect(page.locator('tbody tr').first()).toBeVisible();
    await expect(page.locator('tbody tr').first().getByText(/^[0-9.—]+$/).first()).toBeVisible();
  });

  test('products header opens Import wizard (not rail nav)', async ({ page }) => {
    await clickNavLink(page, 'Products');
    await expect(page).toHaveURL(/\/products/);
    await expect(page.getByTestId('icon-rail').getByRole('link', { name: 'Import' })).toHaveCount(0);
    await page.getByTestId('products-import-button').click();
    await expect(page.getByTestId('products-import-dialog')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Import catalog' })).toBeVisible();
  });

  test('products save view uses toast instead of alert', async ({ page }) => {
    await clickNavLink(page, 'Products');
    await expect(page).toHaveURL(/\/products/);
    await page.getByRole('tab', { name: 'Low stock' }).click();
    await page.getByRole('button', { name: 'Save view' }).click();
    await page.getByLabel('Filter view name').fill('My low stock');
    await page.getByRole('button', { name: 'Save', exact: true }).click();
    await expect(page.getByRole('status').filter({ hasText: /Saved view/ })).toBeVisible();
    await expect(page.getByRole('tab', { name: 'My low stock' })).toBeVisible();
  });

  test('fulfillment receive mode is available', async ({ page }) => {
    await clickNavLink(page, 'Fulfillment');
    await page.getByRole('radio', { name: 'Receive' }).click();
    await expect(page.getByText(/Scan to receive/i)).toBeVisible();
  });

  test('production orders page shows disassemble action', async ({ page }) => {
    await clickNavLink(page, 'Production Orders');
    await expect(page).toHaveURL(/\/manufacturing\/orders/);
    await expect(page.getByRole('button', { name: 'Disassemble' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'New order' })).toBeVisible();
  });

  test('reports demand sensing tab loads chart', async ({ page }) => {
    await clickNavLink(page, 'Reports');
    await page.getByRole('button', { name: 'Demand sensing' }).click();
    await expect(page.getByText('30-day velocity by SKU')).toBeVisible({ timeout: 15_000 });
  });

  test('dashboard shows low stock velocity chart', async ({ page }) => {
    await expect(page.getByText('Low stock velocity')).toBeVisible({ timeout: 15_000 });
  });

  test('sales orders peek drawer opens on row click', async ({ page }) => {
    await clickNavLink(page, 'Sales Orders');
    const firstRow = page.locator('table tbody tr').first();
    await firstRow.click({ timeout: 15_000 });
    await expect(page.getByTestId('right-peek-drawer')).toBeVisible();
  });

  test('purchase orders peek drawer opens on row click', async ({ page }) => {
    await clickNavLink(page, 'Purchase Orders');
    const firstRow = page.locator('table tbody tr').first();
    await firstRow.click({ timeout: 15_000 });
    await expect(page.getByTestId('right-peek-drawer')).toBeVisible();
  });

  test('cycle counts uses warehouse floor chrome', async ({ page }) => {
    await clickNavLink(page, 'Cycle counts');
    await expect(page).toHaveURL(/\/cycle-counts/);
    await expect(page.getByText('Floor ops')).toBeVisible();
    await expect(page.getByTestId('icon-rail')).toHaveCount(0);
  });

  test('settings billing page does not embed financing cockpit', async ({ page }) => {
    await page.goto('/settings/billing');
    await completeScannerPin(page);
    await expect(page).toHaveURL(/\/settings\/billing/);
    await expect(page.getByText('Billing & payments')).toBeVisible();
    await expect(page.getByText('Financing Cockpit')).toHaveCount(0);
  });

  test('settings fintech page loads cockpit', async ({ page }) => {
    await page.goto('/settings/fintech');
    await completeScannerPin(page);
    await expect(page).toHaveURL(/\/settings\/fintech/);
    await expect(page.getByTestId('fintech-settings-page')).toBeVisible();
    await expect(page.getByText('Financing Cockpit')).toBeVisible();
    await expect(page.getByText('Eligible factoring invoices')).toBeVisible();
  });

  test('purchase orders AP ingestion panel loads', async ({ page }) => {
    await clickNavLink(page, 'Purchase Orders');
    await expect(page.getByText('AP invoice ingestion')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Upload & reconcile' })).toBeVisible();
  });
});
