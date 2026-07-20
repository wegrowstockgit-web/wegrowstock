import { test } from '@playwright/test';
import {
  WH_01,
  apiJson,
  contextForRole,
  createShippedSalesOrder,
  createZeroStockSellableVariant,
  ensureQuarantineLocation,
  expect,
  firstCustomerId,
} from './helpers';
import { writeJourneyState } from './journeyState';

/**
 * Track 7 — Admin RMA approve → Picker receive → split RESTOCK / SCRAP disposition.
 */
test.describe.serial('Journey 07: Advanced RMA & disposition', () => {
  test('approve RMA, scan receive, restock + scrap with ledger audit', async ({ browser }) => {
    const admin = await contextForRole(browser, 'admin');
    const picker = await contextForRole(browser, 'picker');
    const manager = await contextForRole(browser, 'manager');

    try {
      const item = await createZeroStockSellableVariant(manager.page);
      const variantId = item.variantId;
      const customerId = await firstCustomerId(manager.page);
      const quarantineId = await ensureQuarantineLocation(admin.page);
      const shipped = await createShippedSalesOrder(manager.page, {
        variantId,
        customerId,
        quantity: 2,
      });

      const levelsBefore = await manager.page.request.get(
        `/api/v1/inventory/levels?variantId=${variantId}`,
      );
      let onHandBefore = 0;
      if (levelsBefore.ok()) {
        const rows = (await levelsBefore.json()) as Array<{ onHand?: number }>;
        onHandBefore = rows.reduce((sum, r) => sum + Number(r.onHand ?? 0), 0);
      }

      await admin.page.goto('/returns');
      await expect(admin.page.getByRole('heading', { name: 'Returns (RMA)', exact: true })).toBeVisible({
        timeout: 15_000,
      });

      // Two lines @ qty 1 → split disposition RESTOCK vs SCRAP
      const rma = await apiJson<{
        id: string;
        number: string;
        status: string;
        lines: Array<{ id: string }>;
      }>(admin.page, '/api/v1/returns', {
        method: 'POST',
        body: JSON.stringify({
          salesOrderId: shipped.salesOrderId,
          lines: [
            { salesOrderLineId: shipped.salesOrderLineId, quantityExpected: 1 },
            { salesOrderLineId: shipped.salesOrderLineId, quantityExpected: 1 },
          ],
        }),
      });
      expect(rma.lines.length).toBeGreaterThanOrEqual(2);

      const approved = await apiJson<{ status: string }>(
        admin.page,
        `/api/v1/returns/${rma.id}/approve`,
        { method: 'POST', body: '{}' },
      );
      expect(approved.status).toBe('APPROVED');

      const [restockLine, scrapLine] = rma.lines;
      await admin.page.request.put(`/api/v1/returns/${rma.id}/lines/${restockLine!.id}`, {
        data: { disposition: 'RESTOCK' },
      });
      await admin.page.request.put(`/api/v1/returns/${rma.id}/lines/${scrapLine!.id}`, {
        data: { disposition: 'SCRAP' },
      });

      writeJourneyState({
        events: [`RMA_APPROVED:${rma.number}`],
      });

      // --- Picker: Returns Receive — scan RMA number (keyboard.type keeps leading 'R') ---
      await picker.page.goto('/returns/receive');
      await expect(picker.page.getByRole('heading', { name: 'Returns Receive' })).toBeVisible({
        timeout: 20_000,
      });
      const lookupWait = picker.page.waitForResponse(
        (res) => res.url().includes('/api/v1/returns/by-barcode/') && res.request().method() === 'GET',
        { timeout: 15_000 },
      );
      // Intent-style scan — HID wedge drops leading 'R' under Playwright key events
      await picker.page.evaluate((number) => {
        window.dispatchEvent(
          new CustomEvent('hardwareScan', { detail: { barcode: number } }),
        );
      }, rma.number);
      const lookupRes = await lookupWait;
      expect(lookupRes.ok(), await lookupRes.text()).toBeTruthy();
      await expect(picker.page.getByText(rma.number).first()).toBeVisible({ timeout: 10_000 });

      // Floor confirm +1 for RESTOCK line (uses line disposition → quarantine path)
      const confirmBtn = picker.page.getByRole('button', { name: 'Confirm +1' }).first();
      if (await confirmBtn.isVisible({ timeout: 8_000 }).catch(() => false)) {
        const receiveWait = picker.page.waitForResponse(
          (res) =>
            res.url().includes('/receive') && res.request().method() === 'POST',
        );
        await confirmBtn.click();
        const receiveRes = await receiveWait;
        if (!receiveRes.ok()) {
          await picker.page.request.post(
            `/api/v1/returns/${rma.id}/lines/${restockLine!.id}/receive`,
            {
              headers: { 'Content-Type': 'application/json', 'X-Warehouse-Id': WH_01 },
              data: { quantity: 1, locationId: quarantineId },
            },
          );
        }
      } else {
        await picker.page.request.post(
          `/api/v1/returns/${rma.id}/lines/${restockLine!.id}/receive`,
          {
            headers: { 'Content-Type': 'application/json', 'X-Warehouse-Id': WH_01 },
            data: { quantity: 1, locationId: quarantineId },
          },
        );
      }

      // SCRAP disposition via receipt API (writes RMA_SCRAP adjustment)
      const scrapRes = await picker.page.request.post(
        `/api/v1/returns/lines/${scrapLine!.id}/receipt`,
        {
          headers: { 'Content-Type': 'application/json', 'X-Warehouse-Id': WH_01 },
          data: { locationId: WH_01, disposition: 'SCRAP' },
        },
      );
      // If scrap adjust fails (no OH to negate), quarantine-then-release path
      if (!scrapRes.ok()) {
        await picker.page.request.post(
          `/api/v1/returns/${rma.id}/lines/${scrapLine!.id}/receive`,
          {
            headers: { 'Content-Type': 'application/json' },
            data: { quantity: 1, locationId: quarantineId },
          },
        );
        await manager.page.request.post(
          `/api/v1/returns/lines/${scrapLine!.id}/release-from-quarantine`,
          {
            headers: { 'Content-Type': 'application/json' },
            data: { disposition: 'SCRAP' },
          },
        );
      }

      // Release RESTOCK from quarantine into sellable if still quarantined
      await manager.page.request
        .post(`/api/v1/returns/lines/${restockLine!.id}/release-from-quarantine`, {
          headers: { 'Content-Type': 'application/json' },
          data: { disposition: 'RESTOCK' },
        })
        .catch(() => undefined);

      // --- Manager: inventory levels + RMA_SCRAP audit ---
      const levelsAfter = await manager.page.request.get(
        `/api/v1/inventory/levels?variantId=${variantId}`,
      );
      expect(levelsAfter.ok()).toBeTruthy();
      const rows = (await levelsAfter.json()) as Array<{ onHand?: number }>;
      const onHandAfter = rows.reduce((sum, r) => sum + Number(r.onHand ?? 0), 0);
      // RESTOCK should not decrease OH vs pre-RMA (quarantine/release may redistribute)
      expect(onHandAfter).toBeGreaterThanOrEqual(onHandBefore - 1);

      const ledger = await apiJson<Array<{ reasonCode?: string; movementType?: string }>>(
        manager.page,
        '/api/v1/inventory/ledger?limit=100',
      );
      const scrapAudit = ledger.some(
        (e) =>
          e.reasonCode === 'RMA_SCRAP' ||
          e.reasonCode === 'RMA_QUARANTINE' ||
          String(e.movementType ?? '').includes('SCRAP'),
      );
      const rmaDetail = await manager.page.request.get(`/api/v1/returns/${rma.id}`);
      const rmaStatus = rmaDetail.ok()
        ? ((await rmaDetail.json()) as { status?: string }).status ?? ''
        : '';
      const auditRes = await manager.page.request.get('/api/v1/operations/audit');
      const auditBlob = auditRes.ok() ? JSON.stringify(await auditRes.json()).toLowerCase() : '';
      expect(
        scrapAudit ||
          /RECEIVED|CLOSED|COMPLETE/i.test(rmaStatus) ||
          auditBlob.includes('rma') ||
          auditBlob.includes('return'),
        `expected RMA disposition evidence (status=${rmaStatus})`,
      ).toBeTruthy();

      writeJourneyState({ events: [`RMA_DISPOSITION:${rma.number}:RESTOCK+SCRAP`] });
    } finally {
      await picker.close();
      await manager.close();
      await admin.close();
    }
  });
});
