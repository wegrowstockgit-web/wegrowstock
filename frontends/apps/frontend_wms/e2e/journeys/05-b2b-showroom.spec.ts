import { test } from '@playwright/test';
import { apiJson, contextForRole, expect } from './helpers';

/**
 * Track 5 — B2B showroom draft order → Admin confirm into allocation engine.
 * Uses browser.newContext() for isolated B2B + Admin sessions.
 */
test.describe('Journey 05: B2B Showroom → Admin fulfillment', () => {
  test('b2b drafts portal order; admin confirms', async ({ browser }) => {
    test.setTimeout(120_000);
    const b2b = await contextForRole(browser, 'b2b');
    const admin = await contextForRole(browser, 'admin');

    try {
      // --- B2B: restricted showroom shell ---
      await b2b.page.goto('/showroom/catalog');
      await expect(b2b.page).toHaveURL(/\/showroom/, { timeout: 20_000 });
      await expect(b2b.page.getByRole('heading', { name: 'Catalog' })).toBeVisible({
        timeout: 15_000,
      });
      await expect(b2b.page.getByText(/Tier pricing applied automatically/i)).toBeVisible();

      // Office / warehouse routes redirect (client RBAC); APIs return 403
      await b2b.page.goto('/dashboard');
      await expect(b2b.page).toHaveURL(/\/showroom/, { timeout: 15_000 });
      const officeApi = await b2b.page.request.get('/api/v1/sales-orders');
      expect([401, 403]).toContain(officeApi.status());

      // Custom pricing on catalog API
      const items = await apiJson<Array<{ variantId: string; unitPrice: number | string | null }>>(
        b2b.page,
        '/api/v1/portal/catalog',
      );
      expect(items.length).toBeGreaterThan(0);
      const priced = items.find((i) => Number(i.unitPrice) > 0);
      expect(priced, 'catalog should include at least one priced SKU').toBeTruthy();

      // UI cart add + checkout (mirrors b2b-portal.spec)
      await b2b.page.goto('/showroom/catalog');
      await b2b.page.locator('.grid > div').first().getByRole('button').last().click();
      await b2b.page.getByLabel(/Open cart/i).click();
      await expect(b2b.page.getByRole('heading', { name: 'Your cart' })).toBeVisible();
      await b2b.page.getByRole('button', { name: 'Proceed to checkout' }).click();
      const poNumber = `PO-J5-${Date.now()}`;
      await b2b.page.getByLabel('Your PO number').fill(poNumber);
      await b2b.page.getByRole('button', { name: 'Continue' }).click();
      await b2b.page.getByRole('button', { name: 'Instant Checkout' }).click();
      await expect(b2b.page.getByText('Order submitted')).toBeVisible({ timeout: 20_000 });

      // Resolve the new DRAFT portal SO (API is authoritative for admin hand-off)
      const portalOrders = await apiJson<Array<{ id: string; number?: string; status?: string }>>(
        b2b.page,
        '/api/v1/portal/orders',
      );
      const portalOrder = portalOrders[0];
      expect(portalOrder?.id).toBeTruthy();

      // --- Admin: Sales Orders desk + confirm ---
      await admin.page.goto('/sales-orders');
      await expect(admin.page.getByRole('heading', { name: 'Sales Orders', exact: true })).toBeVisible({
        timeout: 15_000,
      });

      const before = await apiJson<{ status: string }>(
        admin.page,
        `/api/v1/sales-orders/${portalOrder!.id}`,
      );
      expect(before.status).toBe('DRAFT');

      const confirmRes = await admin.page.request.post(
        `/api/v1/sales-orders/${portalOrder!.id}/confirm`,
      );
      expect(confirmRes.ok(), await confirmRes.text()).toBeTruthy();

      const after = await apiJson<{ status: string }>(
        admin.page,
        `/api/v1/sales-orders/${portalOrder!.id}`,
      );
      expect(after.status).toBe('CONFIRMED');

    } finally {
      await b2b.close().catch(() => undefined);
      await admin.close().catch(() => undefined);
    }
  });

  test('b2b requests quote; admin prices it; buyer accepts', async ({ browser }) => {
    test.setTimeout(120_000);
    const b2b = await contextForRole(browser, 'b2b');
    const admin = await contextForRole(browser, 'admin');

    try {
      await b2b.page.goto('/showroom/catalog');
      await expect(b2b.page.getByRole('heading', { name: 'Catalog' })).toBeVisible({
        timeout: 15_000,
      });
      await b2b.page.locator('.grid > div').first().getByRole('button').last().click();
      await b2b.page.getByLabel(/Open cart/i).click();
      await b2b.page.getByRole('button', { name: 'Proceed to checkout' }).click();
      await b2b.page.getByLabel(/Ship Complete/i).check();
      await b2b.page.getByRole('button', { name: 'Continue' }).click();
      await b2b.page.getByRole('button', { name: 'Request Custom Quote' }).click();
      await expect(b2b.page.getByText('Quote requested')).toBeVisible({ timeout: 20_000 });

      const portalOrders = await apiJson<Array<{ id: string; status?: string }>>(
        b2b.page,
        '/api/v1/portal/orders',
      );
      const quote = portalOrders.find((o) => o.status === 'PENDING_REP_APPROVAL');
      expect(quote?.id).toBeTruthy();

      const detail = await apiJson<{
        id: string;
        status: string;
        lines: Array<{ id: string; unitPrice: number | string }>;
      }>(admin.page, `/api/v1/sales-orders/${quote!.id}`);
      expect(detail.status).toBe('PENDING_REP_APPROVAL');

      const sendRes = await admin.page.request.post(`/api/v1/sales-orders/${quote!.id}/quote`, {
        data: {
          linePrices: detail.lines.map((line) => ({
            lineId: line.id,
            unitPrice: Number(line.unitPrice),
          })),
          manualDiscountTotal: 5,
          quoteExpiresAt: new Date(Date.now() + 14 * 86_400_000).toISOString(),
          quoteNotes: 'E2E courtesy',
        },
      });
      expect(sendRes.ok(), await sendRes.text()).toBeTruthy();

      await b2b.page.goto('/showroom/orders');
      await expect(b2b.page.getByRole('button', { name: 'Accept & Convert to Order' })).toBeVisible({
        timeout: 15_000,
      });
      await b2b.page.getByRole('button', { name: 'Accept & Convert to Order' }).click();
      await expect
        .poll(async () => {
          const orders = await apiJson<Array<{ id: string; status: string }>>(
            b2b.page,
            '/api/v1/portal/orders',
          );
          return orders.find((o) => o.id === quote!.id)?.status;
        })
        .not.toBe('QUOTE_READY');
    } finally {
      await b2b.close().catch(() => undefined);
      await admin.close().catch(() => undefined);
    }
  });
});
