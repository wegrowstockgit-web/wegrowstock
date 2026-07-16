import { test } from '@playwright/test';
import {
  BOLT_BARCODE,
  BOX_MED_BARCODE,
  GADGET_BLK_SKU,
  WIDGET_S_BARCODE,
  apiJson,
  contextForRole,
  expect,
  findVariantId,
  hidScan,
} from './helpers';
import { writeJourneyState } from './journeyState';

/**
 * Track 6 — Manager BOM production order → Picker terminal scan → Complete build → ledger.
 */
test.describe.serial('Journey 06: Manufacturing BOM → Terminal assembly', () => {
  test('allocate components, scan recipe, complete build with ASSEMBLY ledger', async ({
    browser,
  }) => {
    const manager = await contextForRole(browser, 'manager');
    const picker = await contextForRole(browser, 'picker');

    try {
      const parentVariantId = await findVariantId(manager.page, GADGET_BLK_SKU);

      await manager.page.goto('/manufacturing/orders');
      await expect(
        manager.page.getByRole('heading', { name: 'Production Orders', exact: true }),
      ).toBeVisible({
        timeout: 15_000,
      });

      const order = await apiJson<{ id: string; number: string; status: string }>(
        manager.page,
        '/api/v1/manufacturing/orders',
        {
          method: 'POST',
          body: JSON.stringify({ parentVariantId, qtyTarget: 1 }),
        },
      );
      expect(order.id).toBeTruthy();

      const allocated = await apiJson<{ id: string; status: string }>(
        manager.page,
        `/api/v1/manufacturing/orders/${order.id}/allocate`,
        { method: 'POST', body: '{}' },
      );
      expect(allocated.status).toMatch(/COMPONENTS_ALLOCATED|WIP/);

      writeJourneyState({
        events: [`MO_ALLOCATED:${order.number ?? order.id}`],
      });

      // --- Picker: Production Terminal — verify recipe mix via HID ---
      await picker.page.goto('/manufacturing/terminal');
      await expect(picker.page.getByRole('heading', { name: 'Production Terminal' })).toBeVisible({
        timeout: 20_000,
      });

      const orderCard = picker.page.getByText(order.number ?? order.id).first();
      await expect(orderCard).toBeVisible({ timeout: 15_000 });
      await orderCard.click();

      for (const barcode of [WIDGET_S_BARCODE, BOLT_BARCODE, BOX_MED_BARCODE]) {
        await hidScan(picker.page, barcode);
      }
      await expect(picker.page.getByText(WIDGET_S_BARCODE).first()).toBeVisible({ timeout: 10_000 });

      const assembleWait = picker.page.waitForResponse(
        (res) =>
          res.url().includes(`/api/v1/manufacturing/orders/${order.id}/assemble`) &&
          res.request().method() === 'POST',
      );
      await picker.page.getByRole('button', { name: 'Complete build' }).click();
      const assembleRes = await assembleWait;
      expect(assembleRes.ok(), await assembleRes.text()).toBeTruthy();

      // --- Ledger: ASSEMBLY_OUT components + ASSEMBLY_IN finished good ---
      const ledger = await apiJson<
        Array<{ movementType?: string; reasonCode?: string; variantId?: string }>
      >(manager.page, '/api/v1/inventory/ledger?limit=100');

      const outs = ledger.filter((e) => e.movementType === 'ASSEMBLY_OUT');
      const ins = ledger.filter((e) => e.movementType === 'ASSEMBLY_IN');
      expect(outs.length, 'expected ASSEMBLY_OUT ledger rows').toBeGreaterThan(0);
      expect(ins.length, 'expected ASSEMBLY_IN ledger rows').toBeGreaterThan(0);

      await expect
        .poll(async () => {
          const res = await manager.page.request.get(`/api/v1/manufacturing/orders/${order.id}`);
          if (!res.ok()) return '';
          return ((await res.json()) as { status: string }).status;
        }, { timeout: 20_000 })
        .toMatch(/COMPLETED|WIP/);

      writeJourneyState({ events: [`MO_ASSEMBLED:${order.id}`] });
    } finally {
      await picker.close();
      await manager.close();
    }
  });
});
