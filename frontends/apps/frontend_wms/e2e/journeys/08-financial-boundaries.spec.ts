import { test } from '@playwright/test';
import {
  WH_01,
  WIDGET_S_SKU,
  apiJson,
  contextForRole,
  expect,
  findVariantId,
  firstCustomerId,
} from './helpers';

/**
 * Track 8 — Owner-gated fintech / billing boundaries.
 * Fintech cockpit is OWNER-only; billing remains ADMIN+OWNER; capital drawdown is OWNER-only.
 */
test.describe('Journey 08: Strict owner-gated financial boundaries', () => {
  test('admin masked from fintech; owner sees financing cockpit', async ({ browser }) => {
    const admin = await contextForRole(browser, 'admin');
    const owner = await contextForRole(browser, 'owner');
    const manager = await contextForRole(browser, 'manager');

    try {
      // --- Admin: Cash Flow nav masked; direct fintech URL blocked ---
      await admin.page.goto('/settings');
      await expect(admin.page.getByRole('heading', { name: 'Settings', exact: true })).toBeVisible({
        timeout: 15_000,
      });
      await expect(admin.page.getByRole('link', { name: 'Cash Flow & Financing' })).toHaveCount(0);
      // Billing still available to ADMIN (Stripe + carriers)
      await expect(admin.page.getByRole('link', { name: 'Billing' })).toBeVisible();

      await admin.page.goto('/settings/fintech');
      await expect(admin.page).not.toHaveURL(/\/settings\/fintech/, { timeout: 15_000 });
      await expect(admin.page.getByTestId('fintech-settings-page')).toHaveCount(0);

      const fintechApi = await admin.page.request.get('/api/v1/fintech/dashboard');
      expect([401, 403]).toContain(fintechApi.status());

      const drawdown = await admin.page.request.post('/api/v1/fintech/drawdown', {
        headers: {
          'Content-Type': 'application/json',
          'Idempotency-Key': `j8-admin-${Date.now()}`,
        },
        data: { amount: 100 },
      });
      expect([401, 403]).toContain(drawdown.status());

      // Manager cannot enter billing / fintech surfaces
      await manager.page.goto('/settings/billing');
      await expect(manager.page).not.toHaveURL(/\/settings\/billing/, { timeout: 15_000 });
      const billingApi = await manager.page.request.get('/api/v1/billing/stripe/status');
      expect([401, 403]).toContain(billingApi.status());

      // --- Owner: fintech cockpit + Stripe-connected billing ---
      await owner.page.goto('/settings/fintech');
      await expect(owner.page.getByTestId('fintech-settings-page')).toBeVisible({ timeout: 20_000 });
      await expect(owner.page.getByRole('heading', { name: /Cash flow & financing/i })).toBeVisible();
      await expect(owner.page.getByText('Financing Cockpit')).toBeVisible({ timeout: 20_000 });
      await expect(owner.page.getByText(/GMV|Credit limit|Capital drawdown/i).first()).toBeVisible();

      const ownerDash = await owner.page.request.get('/api/v1/fintech/dashboard');
      expect(ownerDash.ok()).toBeTruthy();
      const dash = (await ownerDash.json()) as { creditLine?: { creditLimit?: number } };
      expect(dash.creditLine).toBeTruthy();

      await owner.page.goto('/settings/billing');
      await expect(owner.page.getByTestId('billing-settings-page')).toBeVisible({ timeout: 15_000 });
      await expect(
        owner.page.getByText(/Billing|Connect Stripe|Stripe/i).first(),
      ).toBeVisible();
    } finally {
      await manager.close();
      await admin.close();
      await owner.close();
    }
  });

  test('Sales order draft locks after submit', async ({ browser }) => {
    const manager = await contextForRole(browser, 'manager');
    try {
      const variantId = await findVariantId(manager.page, WIDGET_S_SKU);
      const customerId = await firstCustomerId(manager.page);
      const so = await apiJson<{ id: string; number: string }>(manager.page, '/api/v1/sales-orders', {
        method: 'POST',
        body: JSON.stringify({
          customerId,
          number: `SO-WS-${Date.now()}`,
          sourceLocationId: WH_01,
          lines: [{ variantId, qtyOrdered: 6, unitPrice: 9 }],
        }),
      });

      await manager.page.goto('/sales-orders');
      await expect(manager.page.getByText(so.number).first()).toBeVisible({ timeout: 15_000 });
      await manager.page.getByText(so.number).first().click();
      await manager.page.getByTestId('open-so-workspace').click();
      await expect(manager.page).toHaveURL(new RegExp(`/sales/orders/${so.id}`));
      await expect(manager.page.getByTestId('so-workspace')).toHaveAttribute('data-locked', 'false');

      const qtyCell = manager.page.locator('[data-testid^="so-line-qty-"]').first();
      await qtyCell.dblclick();
      const qtyInput = manager.page.locator('[data-testid$="-input"]').first();
      await expect(qtyInput).toBeVisible();
      await qtyInput.fill('7');
      await qtyInput.blur();

      await manager.page.getByTestId('submit-so').click();
      await manager.page.getByRole('dialog').getByTestId('alert-dialog-confirm').click();
      await expect(manager.page.getByTestId('so-workspace')).toHaveAttribute('data-locked', 'true', {
        timeout: 15_000,
      });
      await expect(manager.page.locator('[data-testid$="-input"]')).toHaveCount(0);
      await expect(manager.page.getByTestId('cancel-so')).toBeVisible();
    } finally {
      await manager.close();
    }
  });

  test('Void credit memo is manager-only', async ({ browser }) => {
    const manager = await contextForRole(browser, 'manager');
    const picker = await contextForRole(browser, 'picker');
    const invoiceId = 'a0000000-0000-4000-8000-00000000eeee';
    const issued = {
      id: invoiceId,
      number: 'INV-WS-LOCK',
      customerName: 'Acme',
      status: 'OPEN',
      subtotal: 40,
      tax: 0,
      total: 40,
      currency: 'USD',
      lines: [{ id: 'il-1', description: 'Widget', qty: 2, unitPrice: 20, amount: 40, kind: 'ITEM' }],
    };
    try {
      await manager.page.route(`**/api/v1/invoices/${invoiceId}`, async (route) => {
        if (route.request().method() !== 'GET') {
          await route.continue();
          return;
        }
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(issued) });
      });
      await picker.page.route(`**/api/v1/invoices/${invoiceId}`, async (route) => {
        if (route.request().method() !== 'GET') {
          await route.continue();
          return;
        }
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(issued) });
      });

      await manager.page.goto(`/invoices/${invoiceId}`);
      await expect(manager.page.getByTestId('invoice-workspace')).toBeVisible({ timeout: 15_000 });
      await expect(manager.page.getByTestId('void-credit-memo')).toBeVisible();

      await picker.page.goto(`/invoices/${invoiceId}`);
      await expect(picker.page.getByTestId('void-credit-memo')).toHaveCount(0);
    } finally {
      await picker.close();
      await manager.close();
    }
  });

  test('Manager approves a variance and posts a ledger correction', async ({ browser }) => {
    const manager = await contextForRole(browser, 'manager');
    const lineId = 'a0000000-0000-4000-8000-00000000ffff';
    let approved = false;
    try {
      await manager.page.route('**/api/v1/cycle-counts/pending-variances', async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(
            approved
              ? []
              : [
                  {
                    lineId,
                    cycleCountId: 'cc-1',
                    locationId: 'loc-1',
                    locationPath: 'WH-01/Z-A/A-1/B-02',
                    variantId: 'var-1',
                    sku: WIDGET_S_SKU,
                    expectedQty: 10,
                    countedQty: 0,
                    financialDelta: 250,
                    varianceStatus: 'PENDING_MANAGER_REVIEW',
                    updatedAt: new Date().toISOString(),
                  },
                ],
          ),
        });
      });
      await manager.page.route(`**/api/v1/cycle-counts/lines/${lineId}/approve-adjustment`, async (route) => {
        approved = true;
        await route.fulfill({ status: 200, contentType: 'application/json', body: '{}' });
      });

      await manager.page.goto('/inventory/variances');
      await expect(manager.page.getByTestId('variance-workspace')).toBeVisible({ timeout: 15_000 });
      await expect(manager.page.getByTestId(`variance-expected-${lineId}`)).toHaveText('10');
      await expect(manager.page.getByTestId(`variance-counted-${lineId}`)).toHaveText('0');
      await manager.page.getByTestId(`approve-variance-${lineId}`).click();
      await manager.page.getByRole('dialog').getByTestId('alert-dialog-confirm').click();
      await expect(manager.page.getByTestId('variance-workspace-empty')).toBeVisible({ timeout: 15_000 });
    } finally {
      await manager.close();
    }
  });
});
