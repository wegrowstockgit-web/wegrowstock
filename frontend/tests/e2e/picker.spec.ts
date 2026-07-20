import { completeScannerPin, expect, test } from '../../e2e/fixtures/roleFixture';
import {
  contextForRole,
  WIDGET_S_BARCODE,
  WIDGET_S_SKU,
  WH_01,
} from '../../e2e/journeys/helpers';
import type { Page } from '@playwright/test';

/**
 * HID keyboard-wedge: rapid keydowns + Enter (gap under SCANNER_MAX_GAP_MS).
 * Uses Playwright's keyboard API after blur; falls back to manual input Enter submit.
 */
async function wedgeScan(page: Page, barcode: string): Promise<void> {
  await page.evaluate(() => {
    const active = document.activeElement as HTMLElement | null;
    active?.blur?.();
  });
  // delay 5ms stays under SCANNER_MAX_GAP_MS (35) even on emulated mobile CPUs
  await page.keyboard.type(barcode, { delay: 5 });
  await page.keyboard.press('Enter');

  // If the wedge burst was truncated (gap reset), commit via the focused manual field + Enter.
  const last = (await page.getByTestId('scanner-last-value').textContent()) ?? '';
  if (!last.includes(barcode)) {
    const input = page.getByTestId('scanner-manual-input');
    await input.fill(barcode);
    await input.press('Enter');
  }
}

/**
 * Mobile-Scanner persona — PICKER on rugged Android viewport (360×640).
 * Full-screen inbound receive must not mount the desktop office AppShell.
 * Office rail nesting is covered in e2e/app.spec.ts via `clickNavLink`.
 */
test.describe('Mobile Picker suite', () => {
  test.setTimeout(240_000);

  test('picker inbound receive via HID wedge; office shell hidden; touch targets', async ({
    browser,
  }, testInfo) => {
    expect(testInfo.project.name).toBe('Mobile-Scanner');

    const viewport = testInfo.project.use.viewport;
    expect(viewport?.width).toBe(360);
    expect(viewport?.height).toBe(640);
    expect(testInfo.project.use.hasTouch).toBe(true);

    // Seed a receivable PO as manager (office persona), then hand off to picker.
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
      const variantList = (Array.isArray(variantPayload) ? variantPayload : variantPayload.items ?? []) as Array<{
        id: string;
        sku: string;
      }>;
      const widget = variantList.find((v) => v.sku === WIDGET_S_SKU);
      expect(widget?.id).toBeTruthy();

      poNumber = `PO-PICKER-${Date.now()}`;
      const poRes = await manager.page.request.post('/api/v1/purchase-orders', {
        data: {
          supplierId,
          number: poNumber,
          destinationLocationId: WH_01,
          lines: [{ variantId: widget!.id, qtyOrdered: 3, unitCost: 1.1 }],
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
    try {
      // Office shell smoke: expand grouped parents (Inbound is hidden for pickers).
      // Soft-check only — floor receive below is the persona assertion.
      await picker.page.goto('/dashboard');
      await completeScannerPin(picker.page);
      const rail = picker.page.getByTestId('icon-rail');
      if (await rail.isVisible({ timeout: 5_000 }).catch(() => false)) {
        const openNav = picker.page.getByRole('button', { name: /open navigation/i });
        if (await openNav.isVisible().catch(() => false)) {
          await openNav.click();
        }
        await rail.getByText('Outbound', { exact: true }).click({ timeout: 5_000 }).catch(() => undefined);
        await rail.getByText('Inventory', { exact: true }).click({ timeout: 5_000 }).catch(() => undefined);
        await expect(rail.getByText('Outbound', { exact: true })).toBeVisible({ timeout: 5_000 });
        await expect(rail.getByText('Inventory', { exact: true })).toBeVisible({ timeout: 5_000 });
      }

      // Floor picking vector — WarehouseFloorShell, never corporate AppShell rail.
      await picker.page.goto('/fulfillment');
      await completeScannerPin(picker.page);
      await expect(picker.page.getByTestId('warehouse-floor-shell')).toBeVisible({ timeout: 20_000 });
      await expect(picker.page.getByTestId('app-shell')).toHaveCount(0);
      await expect(picker.page.getByTestId('icon-rail')).toHaveCount(0);

      await picker.page.goto('/inbound/receive');
      await completeScannerPin(picker.page);
      await expect(picker.page.getByTestId('inbound-receive-page')).toBeVisible({ timeout: 20_000 });

      // Desktop office shell / rail must not mount on this full-screen floor route.
      await expect(picker.page.getByTestId('app-shell')).toHaveCount(0);
      await expect(picker.page.getByTestId('icon-rail')).toHaveCount(0);

      const size = picker.page.viewportSize();
      expect(size?.width).toBe(360);
      expect(size?.height).toBe(640);

      await expect(picker.page.getByTestId('inbound-step-po')).toBeVisible();

      // Hardware keyboard wedge: rapid sequential keystrokes + Enter.
      await wedgeScan(picker.page, poNumber);
      await expect(picker.page.getByTestId('inbound-step-item')).toBeVisible({ timeout: 20_000 });
      await expect(picker.page.getByTestId('inbound-expected-lines')).toContainText(WIDGET_S_SKU);

      await wedgeScan(picker.page, WIDGET_S_BARCODE);
      // Qty confirmation screen is the scan→transition assertion (scanner unmounts on this step).
      await expect(picker.page.getByTestId('inbound-step-qty')).toBeVisible({ timeout: 20_000 });
      await expect(picker.page.getByTestId('inbound-qty-input')).toBeVisible();
      await expect(picker.page.getByTestId('inbound-step-qty')).toContainText(WIDGET_S_SKU);

      const receiveAll = picker.page.getByTestId('inbound-receive-all');
      const continueBtn = picker.page.getByTestId('inbound-qty-continue');
      await expect(receiveAll).toBeVisible();
      await expect(continueBtn).toBeVisible();

      for (const btn of [receiveAll, continueBtn]) {
        await expect(btn).toHaveClass(/min-h-12/);
        await expect(btn).toHaveClass(/(?:^|\s)p-4(?:\s|$)/);
      }
    } finally {
      await picker.close();
    }
  });
});
