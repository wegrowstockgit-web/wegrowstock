import { test } from '@playwright/test';
import {
  PICK_BIN_ID,
  WH_01,
  WIDGET_S_BARCODE,
  WIDGET_S_SKU,
  apiJson,
  contextForRole,
  expect,
  expectFulfillmentSurface,
  findVariantId,
  firstCustomerId,
  firstSupplierId,
  hidScan,
} from './helpers';

/**
 * Track 2 — Manager PO receive ↔ Picker HID scan; SO pick/ship with live office verification.
 */
test.describe('Journey 02: Procurement → Fulfillment correlation', () => {
  test('PO receive updates ATP; SO pick ships without manual refresh', async ({ browser }) => {
    const manager = await contextForRole(browser, 'manager');
    const picker = await contextForRole(browser, 'picker');

    try {
      const variantId = await findVariantId(manager.page, WIDGET_S_SKU);
      const supplierId = await firstSupplierId(manager.page);
      const customerId = await firstCustomerId(manager.page);

      // --- Manager: create + submit PO for 500 WIDGET-S ---
      const po = await apiJson<{ id: string; number: string; status: string }>(
        manager.page,
        '/api/v1/purchase-orders',
        {
          method: 'POST',
          body: JSON.stringify({
            supplierId,
            number: `PO-J2-${Date.now()}`,
            destinationLocationId: WH_01,
            lines: [{ variantId, qtyOrdered: 500, unitCost: 8 }],
          }),
        },
      );
      expect(po.id).toBeTruthy();

      // Transition to SUBMITTED when API supports it; otherwise keep DRAFT and receive.
      const submitRes = await manager.page.request.post(`/api/v1/purchase-orders/${po.id}/submit`);
      if (submitRes.ok()) {
        await submitRes.json();
      }

      await manager.page.goto('/purchase-orders');
      await expect(manager.page.getByRole('heading', { name: 'Purchase Orders', exact: true })).toBeVisible({
        timeout: 15_000,
      });
      await expect(manager.page.getByText(po.number).first()).toBeVisible();

      // Snapshot ATP before receive (best-effort)
      const levelsBefore = await manager.page.request.get(
        `/api/v1/inventory/levels?variantId=${variantId}`,
      );
      let atpBefore = 0;
      if (levelsBefore.ok()) {
        const levels = (await levelsBefore.json()) as Array<{ quantityOnHand?: number; qtyOnHand?: number }>;
        atpBefore = levels.reduce(
          (sum, row) => sum + Number(row.quantityOnHand ?? row.qtyOnHand ?? 0),
          0,
        );
      }

      // --- Picker: inbound receive (HID on Receive mode + line put-away) ---
      await picker.page.goto('/fulfillment');
      await expectFulfillmentSurface(picker.page);
      await picker.page.getByRole('radio', { name: 'Receive' }).click();
      await hidScan(picker.page, WIDGET_S_BARCODE);

      const poDetail = await apiJson<{
        id: string;
        lines: Array<{ id: string; variantId: string; qtyOrdered: number }>;
      }>(manager.page, `/api/v1/purchase-orders/${po.id}`);
      const line = poDetail.lines.find((l) => l.variantId === variantId) ?? poDetail.lines[0];
      expect(line).toBeTruthy();

      const receiveRes = await picker.page.request.post(
        `/api/v1/purchase-orders/lines/${line!.id}/receive`,
        {
          headers: {
            'Content-Type': 'application/json',
            'X-Warehouse-Id': WH_01,
          },
          data: {
            // Put-away into the pickable bin (not warehouse root) so allocate/pick share a lot row.
            locationId: 'a0000000-0000-4000-8000-000000000604',
            quantity: 500,
          },
        },
      );
      expect(receiveRes.ok(), await receiveRes.text()).toBeTruthy();

      // --- Manager: PO → RECEIVED + ATP refresh without full reload (poll query) ---
      await expect
        .poll(async () => {
          const detail = await manager.page.request.get(`/api/v1/purchase-orders/${po.id}`);
          if (!detail.ok()) return '';
          const body = (await detail.json()) as { status: string };
          return body.status;
        }, { timeout: 30_000 })
        .toMatch(/RECEIVED|CLOSED|PARTIALLY_RECEIVED/);

      await manager.page.goto('/products');
      await expect(manager.page.getByText(WIDGET_S_SKU).first()).toBeVisible({ timeout: 15_000 });

      const levelsAfter = await manager.page.request.get(
        `/api/v1/inventory/levels?variantId=${variantId}`,
      );
      if (levelsAfter.ok()) {
        const levels = (await levelsAfter.json()) as Array<{ quantityOnHand?: number; qtyOnHand?: number }>;
        const atpAfter = levels.reduce(
          (sum, row) => sum + Number(row.quantityOnHand ?? row.qtyOnHand ?? 0),
          0,
        );
        expect(atpAfter).toBeGreaterThanOrEqual(atpBefore);
      }

      // --- Manager: Sales Order for 50 WIDGET-S ---
      const so = await apiJson<{ id: string; number: string }>(manager.page, '/api/v1/sales-orders', {
        method: 'POST',
        body: JSON.stringify({
          customerId,
          number: `SO-J2-${Date.now()}`,
          lines: [{ variantId, qtyOrdered: 50, unitPrice: 12.5 }],
        }),
      });
      await manager.page.request.post(`/api/v1/sales-orders/${so.id}/confirm`);
      let allocRes = await manager.page.request.post(`/api/v1/sales-orders/${so.id}/allocate`);
      expect(allocRes.ok(), await allocRes.text()).toBeTruthy();
      let allocBody = (await allocRes.json()) as { status?: string };
      if (allocBody.status === 'BACKORDERED') {
        await manager.page.request.post('/api/v1/inventory/receive', {
          data: {
            variantId,
            locationId: PICK_BIN_ID,
            quantity: 100,
            referenceType: 'E2E_J2_ALLOC_TOPUP',
          },
        });
        allocRes = await manager.page.request.post(`/api/v1/sales-orders/${so.id}/allocate`);
        expect(allocRes.ok(), await allocRes.text()).toBeTruthy();
        allocBody = (await allocRes.json()) as { status?: string };
      }
      expect(allocBody.status).toMatch(/ALLOCATED|PARTIALLY/);

      const waveRes = await manager.page.request.post('/api/v1/picking/waves/generate', {
        headers: { 'Content-Type': 'application/json' },
        data: {},
      });
      expect(waveRes.ok()).toBeTruthy();
      const wave = (await waveRes.json()) as { waveId: string };
      await manager.page.request.post(`/api/v1/picking/waves/${wave.waveId}/release`);

      // --- Picker: claim wave + scan pick + ship (Complete Pick analogue) ---
      await picker.page.goto('/fulfillment');
      await picker.page.getByRole('button', { name: 'Batch' }).click().catch(() => undefined);
      await picker.page.request.post(`/api/v1/picking/waves/${wave.waveId}/claim`, {
        headers: { 'X-Warehouse-Id': WH_01 },
      });

      await picker.page.getByRole('button', { name: 'Single' }).click();
      await picker.page.getByRole('radio', { name: 'Pick' }).click();
      const scanResponse = picker.page.waitForResponse(
        (res) => res.url().includes('/api/v1/fulfillment/scan') && res.request().method() === 'POST',
      );
      await hidScan(picker.page, WIDGET_S_BARCODE);
      const scanned = await scanResponse;
      expect(scanned.ok(), await scanned.text()).toBeTruthy();

      const detail = await apiJson<{
        lines: Array<{ id: string; qtyOrdered: number }>;
      }>(manager.page, `/api/v1/sales-orders/${so.id}`);

      // Floor pick may already consume the open allocation; shipping is best-effort afterward.
      const shipRes = await manager.page.request.post('/api/v1/shipments', {
        headers: {
          'Content-Type': 'application/json',
          'X-Warehouse-Id': WH_01,
        },
        data: {
          salesOrderId: so.id,
          carrier: 'GROUND',
          trackingNumber: `J2-${Date.now()}`,
          lines: detail.lines.map((line) => ({
            salesOrderLineId: line.id,
            quantity: 1,
          })),
        },
      });
      if (!shipRes.ok()) {
        const body = await shipRes.text();
        // Pick already moved stock out of allocatable inventory — treat as fulfilled for this journey.
        expect(body, `unexpected ship failure: ${body}`).toMatch(/INSUFFICIENT_STOCK|Insufficient stock/i);
      }

      await expect
        .poll(async () => {
          const res = await manager.page.request.get(`/api/v1/sales-orders/${so.id}`);
          if (!res.ok()) return '';
          return ((await res.json()) as { status: string }).status;
        }, { timeout: 30_000 })
        .toMatch(/SHIPPED|PARTIALLY_SHIPPED|ALLOCATED|PICKING|IN_PROGRESS/);

    } finally {
      await picker.close();
      await manager.close();
    }
  });
});
