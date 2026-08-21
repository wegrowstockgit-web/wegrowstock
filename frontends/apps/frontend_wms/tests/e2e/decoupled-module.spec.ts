import {
  completeScannerPin,
  dismissOnboardingTourIfPresent,
  expect,
  test,
} from '../../e2e/fixtures/roleFixture';
import { contextForRole, unwrapItems, WIDGET_S_BARCODE, WIDGET_S_SKU, WH_01 } from '../../e2e/journeys/helpers';
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
    const keyboard = page.getByTestId('scanner-keyboard-entry');
    if ((await keyboard.count()) > 0) {
      await keyboard.first().click();
    }
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
      const supplierList = unwrapItems<{ id: string }>(await suppliers.json());
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

  test('Test C — interactive Action Draft card Approve & Execute', async ({ browser }, testInfo) => {
    test.skip(
      testInfo.project.name === 'Mobile-Scanner',
      'Desktop Support Co-Pilot generative UI',
    );

    const manager = await contextForRole(browser, 'manager');
    try {
      let draftExecuteHits = 0;
      await manager.page.route('**/api/v1/support/chat', async (route) => {
        const body = [
          'event:token',
          'data: I can start that cycle count.',
          '',
          'event:done',
          'data: {"ok":true,"actionDraft":{"title":"Generate cycle count for Aisle-4","description":"Creates a count worksheet for Aisle-4.","targetEndpoint":"/api/v1/cycle-counts","httpMethod":"POST","payload":{"supportAction":"generateCycleCount","zoneId":"Aisle-4"}}}',
          '',
        ].join('\n');
        await route.fulfill({
          status: 200,
          contentType: 'text/event-stream',
          body,
        });
      });
      await manager.page.route('**/api/v1/support/actions/draft-execute', async (route) => {
        draftExecuteHits += 1;
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ ok: true, cycleCountId: 'cc-e2e-1', message: 'Cycle count ready' }),
        });
      });

      await manager.page.goto('/cycle-counts');
      await completeScannerPin(manager.page);
      await dismissOnboardingTourIfPresent(manager.page);

      await manager.page.getByTestId('support-assistant-fab').click();
      await expect(manager.page.getByTestId('support-assistant-panel')).toBeVisible();
      await manager.page.getByTestId('support-assistant-input').fill('Generate cycle count for zone Aisle-4');
      await manager.page.getByTestId('support-assistant-send').click();

      const draft = manager.page.getByTestId('support-action-draft');
      await expect(draft).toBeVisible({ timeout: 20_000 });
      await expect(draft).toContainText(/Aisle-4/i);
      await manager.page.getByTestId('support-draft-approve').click();
      await expect(manager.page.getByTestId('support-draft-approved')).toBeVisible({ timeout: 15_000 });
      await expect.poll(() => draftExecuteHits).toBeGreaterThan(0);
    } finally {
      await manager.close();
    }
  });
});
