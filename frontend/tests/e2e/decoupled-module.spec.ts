import {
  completeScannerPin,
  dismissOnboardingTourIfPresent,
  expect,
  test,
} from '../../e2e/fixtures/roleFixture';
import { contextForRole, WIDGET_S_BARCODE, WIDGET_S_SKU, WH_01 } from '../../e2e/journeys/helpers';
import type { Page } from '@playwright/test';

async function wedgeScan(page: Page, barcode: string): Promise<void> {
  await page.evaluate(() => {
    const active = document.activeElement as HTMLElement | null;
    active?.blur?.();
  });
  await page.keyboard.type(barcode, { delay: 5 });
  await page.keyboard.press('Enter');
  const last = (await page.getByTestId('scanner-last-value').textContent()) ?? '';
  if (!last.includes(barcode)) {
    const input = page.getByTestId('scanner-manual-input');
    await input.fill(barcode);
    await input.press('Enter');
  }
}

/**
 * Optional chatbot / training module — active vs disabled surfaces.
 * Test B simulates {@code VITE_ENABLE_CHATBOT=false} via {@code window.__INVSYS_CHATBOT__}
 * (same gate as the Vite env flag in {@code featureFlags.ts}).
 */
test.describe('Decoupled chatbot module', () => {
  test.setTimeout(240_000);

  test('Test A — chatbot active: launcher + tool-calling reply', async ({ browser }, testInfo) => {
    test.skip(
      testInfo.project.name === 'Mobile-Scanner',
      'Desktop / chromium surfaces for Support FAB',
    );

    const manager = await contextForRole(browser, 'manager');
    try {
      await manager.page.goto('/products');
      await completeScannerPin(manager.page);
      await dismissOnboardingTourIfPresent(manager.page);

      await expect(manager.page.getByTestId('support-assistant-fab')).toBeVisible({
        timeout: 30_000,
      });
      await manager.page.getByTestId('support-assistant-fab').click();
      await expect(manager.page.getByTestId('support-assistant-panel')).toBeVisible();

      await manager.page
        .getByTestId('support-assistant-input')
        .fill(`What is the available-to-promise for SKU ${WIDGET_S_SKU}?`);
      await manager.page.getByTestId('support-assistant-send').click();

      const reply = manager.page.getByTestId('support-assistant-reply').last();
      await expect(reply).toContainText(/available-to-promise|on-hand|reserved|ATP|SKU/i, {
        timeout: 45_000,
      });
    } finally {
      await manager.close();
    }
  });

  test('Test B — chatbot disabled: no FAB; picker inbound + wave surface clean', async ({
    browser,
  }, testInfo) => {
    test.skip(testInfo.project.name !== 'Mobile-Scanner', 'Picker persona on Mobile-Scanner');

    const manager = await contextForRole(browser, 'manager');
    let poNumber = '';
    try {
      const suppliers = await manager.page.request.get('/api/v1/suppliers');
      expect(suppliers.ok()).toBeTruthy();
      const supplierList = (await suppliers.json()) as Array<{ id: string }>;
      const supplierId = supplierList[0]?.id;
      expect(supplierId).toBeTruthy();

      const variants = await manager.page.request.get('/api/v1/variants');
      expect(variants.ok()).toBeTruthy();
      const variantPayload = await variants.json();
      const variantList = (
        Array.isArray(variantPayload) ? variantPayload : variantPayload.items ?? []
      ) as Array<{ id: string; sku: string }>;
      const widget = variantList.find((v) => v.sku === WIDGET_S_SKU);
      expect(widget?.id).toBeTruthy();

      poNumber = `PO-DECOUPLE-${Date.now()}`;
      const poRes = await manager.page.request.post('/api/v1/purchase-orders', {
        data: {
          supplierId,
          number: poNumber,
          destinationLocationId: WH_01,
          lines: [{ variantId: widget!.id, qtyOrdered: 2, unitCost: 1.1 }],
        },
      });
      expect(poRes.ok()).toBeTruthy();
      const po = (await poRes.json()) as { id: string };
      expect((await manager.page.request.post(`/api/v1/purchase-orders/${po.id}/submit`)).ok()).toBeTruthy();
      expect(
        (await manager.page.request.post(`/api/v1/purchase-orders/${po.id}/mark-in-transit`)).ok(),
      ).toBeTruthy();
    } finally {
      await manager.close();
    }

    const picker = await contextForRole(browser, 'picker');
    await picker.context.addInitScript(() => {
      window.__INVSYS_CHATBOT__ = false;
    });

    const consoleErrors: string[] = [];
    picker.page.on('console', (msg) => {
      if (msg.type() === 'error') consoleErrors.push(msg.text());
    });
    picker.page.on('pageerror', (err) => consoleErrors.push(String(err)));

    try {
      // Full reload so featureFlags re-evaluates with the init script.
      await picker.page.goto('/fulfillment');
      await completeScannerPin(picker.page);
      await expect(picker.page.getByTestId('support-assistant-fab')).toHaveCount(0);
      await expect(picker.page.getByTestId('warehouse-floor-shell')).toBeVisible({
        timeout: 20_000,
      });

      await picker.page.goto('/inbound/receive');
      await completeScannerPin(picker.page);
      await expect(picker.page.getByTestId('support-assistant-fab')).toHaveCount(0);
      await expect(picker.page.getByTestId('inbound-receive-page')).toBeVisible({
        timeout: 20_000,
      });

      await wedgeScan(picker.page, poNumber);
      await expect(picker.page.getByTestId('inbound-step-item')).toBeVisible({ timeout: 20_000 });
      await wedgeScan(picker.page, WIDGET_S_BARCODE);
      await expect(picker.page.getByTestId('inbound-step-qty')).toBeVisible({ timeout: 20_000 });

      await picker.page.goto('/fulfillment');
      await completeScannerPin(picker.page);
      await expect(picker.page.getByTestId('warehouse-floor-shell')).toBeVisible({
        timeout: 20_000,
      });
      await expect(picker.page.getByTestId('support-assistant-fab')).toHaveCount(0);

      expect(consoleErrors, `console errors: ${consoleErrors.join('\n')}`).toEqual([]);
    } finally {
      await picker.close();
    }
  });
});
