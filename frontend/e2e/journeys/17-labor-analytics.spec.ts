import { test } from '@playwright/test';
import {
  DEMO_PICKER_USER_ID,
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
  hidScan,
} from './helpers';

/**
 * Track 17 — HID burst picks → backdated 1h shift → Labor Velocity Leaderboard metrics.
 */
test.describe.serial('Journey 17: Floor Labor Velocity (LMS)', () => {
  test('picker burst scans; manager sees Active PPH + Utilization on leaderboard', async ({
    browser,
  }) => {
    test.setTimeout(180_000);
    const manager = await contextForRole(browser, 'manager');
    const picker = await contextForRole(browser, 'picker');

    try {
      const variantId = await findVariantId(manager.page, WIDGET_S_SKU);
      const customerId = await firstCustomerId(manager.page);

      const topup = await manager.page.request.post('/api/v1/inventory/receive', {
        data: {
          variantId,
          locationId: PICK_BIN_ID,
          quantity: 40,
          referenceType: 'E2E_LMS_TOPUP',
        },
      });
      expect(topup.ok(), await topup.text()).toBeTruthy();

      const so = await apiJson<{ id: string; number: string }>(manager.page, '/api/v1/sales-orders', {
        method: 'POST',
        body: JSON.stringify({
          customerId,
          number: `SO-LMS-${Date.now()}`,
          lines: [{ variantId, qtyOrdered: 10, unitPrice: 12.5 }],
        }),
      });
      await manager.page.request.post(`/api/v1/sales-orders/${so.id}/confirm`);
      await manager.page.request.post(`/api/v1/sales-orders/${so.id}/allocate`);

      const waveRes = await manager.page.request.post('/api/v1/picking/waves/generate', {
        headers: { 'Content-Type': 'application/json' },
        data: {},
      });
      expect(waveRes.ok(), await waveRes.text()).toBeTruthy();
      const wave = (await waveRes.json()) as { waveId: string };
      await manager.page.request.post(`/api/v1/picking/waves/${wave.waveId}/release`);

      await picker.page.goto('/fulfillment');
      await expectFulfillmentSurface(picker.page);
      await picker.page.getByRole('button', { name: 'Batch' }).click().catch(() => undefined);
      const claim = await picker.page.request.post(`/api/v1/picking/waves/${wave.waveId}/claim`, {
        headers: { 'X-Warehouse-Id': WH_01 },
      });
      expect(claim.ok(), await claim.text()).toBeTruthy();

      await picker.page.getByRole('button', { name: 'Single' }).click();
      await picker.page.getByRole('radio', { name: 'Pick' }).click();

      // HID burst — 10 scans @ ~50ms gaps
      for (let i = 0; i < 10; i += 1) {
        const scanWait = picker.page.waitForResponse(
          (res) =>
            res.url().includes('/api/v1/fulfillment/scan') && res.request().method() === 'POST',
          { timeout: 15_000 },
        );
        await hidScan(picker.page, WIDGET_S_BARCODE);
        const scanned = await scanWait;
        // Some scans may no-op once qty exhausted — tolerate after first successes
        if (!scanned.ok() && i >= 3) break;
        await picker.page.waitForTimeout(50);
      }

      const me = await apiJson<{ userId?: string; id?: string }>(picker.page, '/api/v1/auth/me');
      const pickerUserId = me.userId ?? me.id ?? DEMO_PICKER_USER_ID;

      // Fast-forward mock clock: 1-hour claimed wave / shift window
      const backdate = await apiJson<{ shiftStart: string; shiftEnd: string }>(
        manager.page,
        '/api/v1/admin/test/labor/backdate-shift',
        {
          method: 'POST',
          body: JSON.stringify({ userId: pickerUserId, shiftHours: 1 }),
        },
      );
      expect(backdate.shiftStart).toBeTruthy();

      const velocity = await apiJson<{
        operators: Array<{
          userId: string;
          operatorName: string;
          totalPicks: number;
          activePph: number;
          shiftPph: number;
          utilizationPercent: number;
        }>;
      }>(manager.page, '/api/v1/dashboard/labor-velocity');

      const row =
        velocity.operators.find((o) => o.userId === pickerUserId && Number(o.totalPicks) > 0) ??
        velocity.operators.find((o) => Number(o.totalPicks) > 0);
      expect(row, 'at least one picker with picks on labor velocity board').toBeTruthy();
      expect(Number(row!.activePph)).toBeGreaterThanOrEqual(0);
      expect(Number(row!.utilizationPercent)).toBeGreaterThanOrEqual(0);

      await manager.page.goto('/dashboard');
      await expect(manager.page.getByTestId('labor-velocity-leaderboard')).toBeVisible({
        timeout: 20_000,
      });
      await expect(manager.page.getByTestId('labor-velocity-view-full')).toBeVisible();
      await manager.page.getByTestId('labor-velocity-view-full').click();
      await expect(manager.page).toHaveURL(/\/reports\?tab=labor/);
      await expect(manager.page.getByTestId('reports-labor-panel')).toBeVisible({ timeout: 20_000 });
      await expect(manager.page.getByText(/Labor Velocity|Active PPH|Utilization/i).first()).toBeVisible({
        timeout: 15_000,
      });
      const uiRow = manager.page.getByTestId(`labor-row-${row!.userId}`);
      if (await uiRow.isVisible({ timeout: 8_000 }).catch(() => false)) {
        await expect(uiRow.getByText(/%/).first()).toBeVisible();
        await uiRow.click();
      }
    } finally {
      await picker.close();
      await manager.close();
    }
  });
});
