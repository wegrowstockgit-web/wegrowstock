import { expect, test } from '../fixtures/roleFixture';
import { contextForRole, unwrapItems, WIDGET_S_BARCODE, WIDGET_S_SKU, WH_01 } from './helpers';

/**
 * Journey 49 — Mobile inbound receive + directed putaway (full-screen /inbound/receive).
 */
test.describe('Journey 49: Inbound receive & directed putaway', () => {
  test.setTimeout(240_000);

  test('picker walks PO → item → qty → directed putaway confirm', async ({ browser }) => {
    const manager = await contextForRole(browser, 'manager');
    let poNumber = '';
    let binCode = '';
    try {
      const suppliers = await manager.page.request.get('/api/v1/suppliers');
      expect(suppliers.ok()).toBeTruthy();
      const supplierList = unwrapItems<{ id: string }>(await suppliers.json());
      const supplierId = supplierList[0]?.id;
      expect(supplierId).toBeTruthy();

      const variants = await manager.page.request.get('/api/v1/variants');
      expect(variants.ok()).toBeTruthy();
      const variantPayload = await variants.json();
      const variantList = (Array.isArray(variantPayload) ? variantPayload : variantPayload.items ?? []) as Array<{
        id: string;
        sku: string;
      }>;
      const widget = variantList.find((v) => v.sku === WIDGET_S_SKU);
      expect(widget?.id).toBeTruthy();

      poNumber = `PO-J49-${Date.now()}`;
      const poRes = await manager.page.request.post('/api/v1/purchase-orders', {
        data: {
          supplierId,
          number: poNumber,
          destinationLocationId: WH_01,
          lines: [{ variantId: widget!.id, qtyOrdered: 5, unitCost: 1.25 }],
        },
      });
      expect(poRes.ok()).toBeTruthy();
      const po = (await poRes.json()) as { id: string; number: string };

      const submitRes = await manager.page.request.post(`/api/v1/purchase-orders/${po.id}/submit`);
      expect(submitRes.ok()).toBeTruthy();
      const transitRes = await manager.page.request.post(`/api/v1/purchase-orders/${po.id}/mark-in-transit`);
      expect(transitRes.ok()).toBeTruthy();

      const suggest = await manager.page.request.get(
        `/api/v1/inbound/receive/putaway-suggestion?variantId=${widget!.id}`,
        { headers: { 'X-Warehouse-Id': WH_01 } },
      );
      expect(suggest.ok()).toBeTruthy();
      const directive = (await suggest.json()) as { code: string; locationId: string };
      binCode = directive.code;
      expect(binCode).toBeTruthy();
    } finally {
      await manager.close();
    }

    const picker = await contextForRole(browser, 'picker');
    try {
      await picker.page.goto('/inbound/receive');
      await expect(picker.page.getByTestId('inbound-receive-page')).toBeVisible({ timeout: 20_000 });
      await expect(picker.page.getByTestId('inbound-step-po')).toBeVisible();

      await picker.page.getByTestId('scanner-keyboard-entry').first().click();
      await picker.page.getByTestId('scanner-manual-input').fill(poNumber);
      await picker.page.getByTestId('scanner-manual-input').press('Enter');
      await expect(picker.page.getByTestId('inbound-step-item')).toBeVisible({ timeout: 20_000 });
      await expect(picker.page.getByTestId('inbound-expected-lines')).toContainText(WIDGET_S_SKU);

      await picker.page.getByTestId('scanner-keyboard-entry').first().click();
      await picker.page.getByTestId('scanner-manual-input').fill(WIDGET_S_BARCODE);
      await picker.page.getByTestId('scanner-manual-input').press('Enter');
      await expect(picker.page.getByTestId('inbound-step-qty')).toBeVisible({ timeout: 20_000 });

      await picker.page.getByTestId('inbound-receive-all').click();
      await picker.page.getByTestId('inbound-qty-continue').click();
      await expect(picker.page.getByTestId('inbound-step-putaway')).toBeVisible({ timeout: 15_000 });
      await expect(picker.page.getByTestId('putaway-bin-label')).toBeVisible();
      await expect(picker.page.getByTestId('putaway-strategy')).toBeVisible();

      const confirmWait = picker.page.waitForResponse(
        (r) => r.url().includes('/api/v1/inbound/receive/confirm') && r.request().method() === 'POST',
        { timeout: 30_000 },
      );
      await picker.page.getByTestId('scanner-keyboard-entry').first().click();
      await picker.page.getByTestId('scanner-manual-input').fill(binCode);
      await picker.page.getByTestId('scanner-manual-input').press('Enter');
      const confirmRes = await confirmWait;
      expect(confirmRes.ok()).toBeTruthy();
      const body = (await confirmRes.json()) as { action: string; poNumber: string };
      expect(body.action).toBe('PO_RECEIPT');
      expect(body.poNumber).toBe(poNumber);
      await expect(picker.page.getByTestId('inbound-step-done')).toBeVisible({ timeout: 15_000 });
    } finally {
      await picker.close();
    }
  });
});
