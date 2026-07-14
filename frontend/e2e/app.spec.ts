import { expect, test } from '@playwright/test';

const DEMO_PASSWORD = process.env.E2E_DEMO_PASSWORD ?? 'password123';

test.describe('Authentication', () => {
  test('demo login reaches dashboard', async ({ page }) => {
    await page.goto('/login');

    await page.getByLabel('Company slug').fill('demo-corp');
    await page.getByLabel('Email').fill('owner@demo.test');
    await page.getByLabel('Password').fill(DEMO_PASSWORD);
    await page.getByRole('button', { name: 'Sign in' }).click();

    await expect(page).toHaveURL(/\/dashboard/);
    await expect(page.getByText('Stock value')).toBeVisible({ timeout: 15_000 });
  });
});

test.describe('Dashboard', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Company slug').fill('demo-corp');
    await page.getByLabel('Email').fill('owner@demo.test');
    await page.getByLabel('Password').fill(DEMO_PASSWORD);
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page).toHaveURL(/\/dashboard/);
  });

  test('shows KPI cards without crashing', async ({ page }) => {
    const kpis = page.locator('a.group.rounded-lg.border-l-4');
    await expect(kpis.filter({ hasText: 'Stock value' })).toBeVisible();
    await expect(kpis.filter({ hasText: 'Open orders' })).toBeVisible();
    await expect(kpis.filter({ hasText: 'Low stock items' })).toBeVisible();
    await expect(kpis.filter({ hasText: 'Unpaid invoices' })).toBeVisible();
  });

  test('shows actionable work queue', async ({ page }) => {
    await expect(page.getByTestId('work-queue')).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText('Do this next')).toBeVisible();
    await expect(page.getByTestId('work-queue-needsAllocation')).toBeVisible();
  });
});

test.describe('Navigation', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Company slug').fill('demo-corp');
    await page.getByLabel('Email').fill('owner@demo.test');
    await page.getByLabel('Password').fill(DEMO_PASSWORD);
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page).toHaveURL(/\/dashboard/);
  });

  test('manufacturing and settings routes load', async ({ page }) => {
    await page.getByRole('link', { name: 'Manufacturing' }).click();
    await expect(page).toHaveURL(/\/manufacturing/);

    await page.getByRole('link', { name: 'Settings' }).click();
    await expect(page).toHaveURL(/\/settings/);
    await expect(page.getByRole('button', { name: 'Integrations' })).toBeVisible();
  });

  test('settings billing and tax configuration load', async ({ page }) => {
    await page.getByRole('link', { name: 'Settings' }).click();
    await page.getByRole('button', { name: 'Billing' }).click();
    await expect(page.getByText('Billing & payments')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Connect Stripe' })).toBeVisible();
    await expect(page.getByText('Shipping accounts')).toBeVisible();

    await page.getByRole('button', { name: 'Inventory Rules' }).click();
    await expect(page.getByText('Tax configuration')).toBeVisible();
  });

  test('cycle counts warehouse page loads', async ({ page }) => {
    await page.getByRole('link', { name: 'Cycle counts' }).click();
    await expect(page).toHaveURL(/\/cycle-counts/);
    await expect(page.getByRole('heading', { name: 'Priority audits' })).toBeVisible();
  });

  test('purchase order form includes warehouse and freight fields', async ({ page }) => {
    await page.getByRole('link', { name: 'Purchase Orders' }).click();
    await page.getByRole('button', { name: 'New PO' }).click();
    await expect(page.getByText('Destination warehouse')).toBeVisible();
    await expect(page.getByLabel('Freight amount')).toBeVisible();
  });

  test('sales order form includes customer PO and ship date', async ({ page }) => {
    await page.getByRole('link', { name: 'Sales Orders' }).click();
    await page.getByRole('button', { name: 'New order' }).click();
    await expect(page.getByLabel('Customer PO number')).toBeVisible();
    await expect(page.getByLabel('Requested ship date')).toBeVisible();
    await expect(page.getByText('Ship-from warehouse')).toBeVisible();
  });

  test('fulfillment pack mode shows scale workflow', async ({ page }) => {
    await page.getByRole('link', { name: 'Fulfillment' }).click();
    await expect(page.getByText('Floor ops')).toBeVisible();
    await page.getByRole('button', { name: 'Pack' }).click();
    await expect(page.getByText('Packing & shipping label')).toBeVisible();
    await expect(page.getByText('Sales order')).toBeVisible();
  });

  test('products page shows UoM editor control', async ({ page }) => {
    await page.getByRole('link', { name: 'Products' }).click();
    await expect(page).toHaveURL(/\/products/);
    await expect(page.getByRole('button', { name: /Edit UoM for/ }).first()).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText('On hand', { exact: true })).toBeVisible();
    await expect(page.getByRole('tab', { name: 'All' })).toBeVisible();
    await expect(page.getByRole('tab', { name: 'Low stock' })).toBeVisible();

    const firstSku = page.getByRole('row').nth(1).locator('div').first();
    await expect(firstSku).toBeVisible();
    await expect(page.getByRole('row').nth(1).locator('div').nth(3)).toHaveText(/^[0-9.—]+$/);
  });

  test('products save view uses toast instead of alert', async ({ page }) => {
    await page.getByRole('link', { name: 'Products' }).click();
    await expect(page).toHaveURL(/\/products/);
    await page.getByRole('tab', { name: 'Low stock' }).click();
    await page.getByRole('button', { name: 'Save view' }).click();
    await page.getByLabel('Filter view name').fill('My low stock');
    await page.getByRole('button', { name: 'Save', exact: true }).click();
    await expect(page.getByRole('status').filter({ hasText: /Saved view/ })).toBeVisible();
    await expect(page.getByRole('tab', { name: 'My low stock' })).toBeVisible();
  });

  test('fulfillment receive mode is available', async ({ page }) => {
    await page.getByRole('link', { name: 'Fulfillment' }).click();
    await page.getByRole('radio', { name: 'Receive' }).click();
    await expect(page.getByText(/Scan to receive/i)).toBeVisible();
  });

  test('production orders page shows disassemble action', async ({ page }) => {
    await page.getByRole('link', { name: 'Production Orders' }).click();
    await expect(page).toHaveURL(/\/manufacturing\/orders/);
    await expect(page.getByRole('button', { name: 'Disassemble' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'New order' })).toBeVisible();
  });

  test('reports demand sensing tab loads chart', async ({ page }) => {
    await page.getByRole('link', { name: 'Reports' }).click();
    await page.getByRole('button', { name: 'Demand sensing' }).click();
    await expect(page.getByText('30-day velocity by SKU')).toBeVisible({ timeout: 15_000 });
  });

  test('dashboard shows low stock velocity chart', async ({ page }) => {
    await expect(page.getByText('Low stock velocity')).toBeVisible({ timeout: 15_000 });
  });

  test('sales orders peek drawer opens on row click', async ({ page }) => {
    await page.getByRole('link', { name: 'Sales Orders' }).click();
    const firstRow = page.locator('table tbody tr').first();
    await firstRow.click({ timeout: 15_000 });
    await expect(page.getByRole('dialog')).toBeVisible();
  });

  test('settings billing tab shows financing cockpit', async ({ page }) => {
    await page.getByRole('link', { name: 'Settings' }).click();
    await page.getByRole('button', { name: 'Billing' }).click();
    await expect(page.getByText('Financing Cockpit')).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText('Credit limit')).toBeVisible();
  });

  test('settings financing tab loads cockpit', async ({ page }) => {
    await page.getByRole('link', { name: 'Settings' }).click();
    await page.getByRole('button', { name: 'Cash Flow & Financing' }).click();
    await expect(page.getByText('Financing Cockpit')).toBeVisible();
    await expect(page.getByText('Eligible factoring invoices')).toBeVisible();
  });

  test('purchase orders AP ingestion panel loads', async ({ page }) => {
    await page.getByRole('link', { name: 'Purchase Orders' }).click();
    await expect(page.getByText('AP invoice ingestion')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Upload & reconcile' })).toBeVisible();
  });
});
