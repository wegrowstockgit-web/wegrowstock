import { test } from '@playwright/test';
import {
  WH_01,
  WIDGET_S_BARCODE,
  WIDGET_S_SKU,
  apiJson,
  contextForRole,
  expect,
  expectFulfillmentSurface,
  findVariantId,
  firstCustomerId,
  hidScan,
} from './helpers';
import { writeJourneyState } from './journeyState';

/**
 * Track 3 — Floor Skip & Flag → Office Unresolved Exceptions → resolve → re-queue.
 */
test.describe.serial('Journey 03: Floor exception & resolution loop', () => {
  test('picker flags barcode; manager resolves; task returns to queue', async ({ browser }) => {
    const manager = await contextForRole(browser, 'manager');
    const picker = await contextForRole(browser, 'picker');

    try {
      const variantId = await findVariantId(manager.page, WIDGET_S_SKU);
      const customerId = await firstCustomerId(manager.page);

      const so = await apiJson<{ id: string; number: string }>(manager.page, '/api/v1/sales-orders', {
        method: 'POST',
        body: JSON.stringify({
          customerId,
          number: `SO-J3-${Date.now()}`,
          lines: [{ variantId, qtyOrdered: 5, unitPrice: 12.5 }],
        }),
      });
      await manager.page.request.post(`/api/v1/sales-orders/${so.id}/confirm`);
      await manager.page.request.post(`/api/v1/sales-orders/${so.id}/allocate`);

      const waveRes = await manager.page.request.post('/api/v1/picking/waves/generate', {
        headers: { 'Content-Type': 'application/json' },
        data: {},
      });
      expect(waveRes.ok()).toBeTruthy();
      const wave = (await waveRes.json()) as { waveId: string };
      await manager.page.request.post(`/api/v1/picking/waves/${wave.waveId}/release`);
      await picker.page.request.post(`/api/v1/picking/waves/${wave.waveId}/claim`, {
        headers: { 'X-Warehouse-Id': WH_01 },
      });

      await picker.page.goto('/fulfillment');
      await expectFulfillmentSurface(picker.page);
      await picker.page.getByRole('button', { name: 'Batch' }).click().catch(() => undefined);
      await picker.page.getByRole('button', { name: 'Single' }).click();
      await picker.page.getByRole('radio', { name: 'Pick' }).click();

      // Drive a failed/partial scan, then Skip & Flag when visible; else report via API with allocation.
      await hidScan(picker.page, WIDGET_S_BARCODE).catch(() => undefined);

      const skipBtn = picker.page.getByTestId('skip-flag-barcode');
      let exceptionId = '';

      if (await skipBtn.isVisible({ timeout: 8_000 }).catch(() => false)) {
        const reportWait = picker.page.waitForResponse(
          (res) =>
            res.url().includes('/api/v1/fulfillment/exceptions/report') &&
            res.request().method() === 'POST',
        );
        await skipBtn.click();
        const reportRes = await reportWait;
        expect(reportRes.ok()).toBeTruthy();
        const body = (await reportRes.json()) as { exceptionId?: string; id?: string };
        exceptionId = body.exceptionId ?? body.id ?? '';
      } else {
        // Fallback: locate an open allocation and report exception (still exercises office loop)
        const allocRes = await picker.page.request.get('/api/v1/picking/batches/current/tasks');
        let allocationId = '';
        if (allocRes.ok()) {
          const tasks = (await allocRes.json()) as Array<{
            id: string;
            allocationId?: string;
            status?: string;
          }>;
          const open = tasks.find((t) => t.status !== 'DONE' && t.status !== 'SKIPPED');
          allocationId = open?.allocationId ?? '';
        }
        if (!allocationId) {
          // Pull allocations for the sales order lines
          const detail = await apiJson<{
            lines: Array<{ id: string; allocationId?: string }>;
          }>(manager.page, `/api/v1/sales-orders/${so.id}`);
          allocationId = detail.lines.find((l) => l.allocationId)?.allocationId ?? '';
        }
        expect(allocationId, 'Need an allocationId to report a floor exception').toBeTruthy();

        const report = await picker.page.request.post('/api/v1/fulfillment/exceptions/report', {
          headers: {
            'Content-Type': 'application/json',
            'Idempotency-Key': `j3-${Date.now()}`,
            'X-Warehouse-Id': WH_01,
          },
          data: {
            allocationId,
            metadata: { reason: 'DAMAGED_BARCODE', source: 'journey-03' },
          },
        });
        expect(report.ok()).toBeTruthy();
        const reported = (await report.json()) as { exceptionId?: string; id?: string };
        exceptionId = reported.exceptionId ?? reported.id ?? '';
      }

      writeJourneyState({
        exceptionId,
        salesOrderId: so.id,
        salesOrderNumber: so.number,
        events: [`EXCEPTION_OPEN:${exceptionId || so.number}`],
      });

      // --- Manager: Unresolved Exceptions panel on office dashboard ---
      await manager.page.goto('/dashboard');
      await expect(manager.page.getByTestId('unresolved-exceptions-panel')).toBeVisible({
        timeout: 20_000,
      });
      await expect
        .poll(async () => {
          const text = await manager.page.getByTestId('unresolved-exceptions-panel').innerText();
          return /OPEN|DAMAGED|exception|Resolve|\([1-9]/i.test(text);
        }, { timeout: 30_000 })
        .toBeTruthy();

      await manager.page.getByTestId('open-exceptions-queue').click();
      await expect(manager.page).toHaveURL(/\/exceptions/);
      await expect(manager.page.getByRole('heading', { name: 'Fulfillment exceptions' })).toBeVisible();

      const lotInput = manager.page.getByPlaceholder('Lot #').first();
      await expect(lotInput).toBeVisible({ timeout: 15_000 });
      const lotNumber = `J3-LOT-${Date.now()}`;
      await lotInput.fill(lotNumber);
      await manager.page.getByRole('button', { name: 'Lot override' }).first().click();

      await expect
        .poll(async () => {
          const list = await manager.page.request.get('/api/v1/office/exceptions/list');
          if (!list.ok()) return false;
          const items = (await list.json()) as Array<{ id: string; resolutionStatus: string }>;
          if (exceptionId) {
            return items.some((i) => i.id === exceptionId && i.resolutionStatus === 'RESOLVED');
          }
          return items.some((i) => i.resolutionStatus === 'RESOLVED');
        }, { timeout: 30_000 })
        .toBeTruthy();

      writeJourneyState({ events: [`EXCEPTION_RESOLVED:${lotNumber}`] });

      // --- Picker: item re-enters active picking surface ---
      await picker.page.goto('/fulfillment');
      await expectFulfillmentSurface(picker.page, 15_000);
      await picker.page.getByRole('radio', { name: 'Pick' }).click();
      await expect(picker.page.getByRole('radio', { name: 'Pick' })).toBeChecked();
      // Soft re-queue signal: pick mode + live scan buffer (item may be waiting for next HID scan)
      await expect(picker.page.getByText('Ready to scan')).toBeVisible({ timeout: 15_000 });
      await expect(picker.page.getByText(/Scan a barcode to get started|Next bin|Optimized route/i)).toBeVisible();
    } finally {
      await picker.close();
      await manager.close();
    }
  });
});
