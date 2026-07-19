import { completeScannerPin, expect, test } from '../../e2e/fixtures/roleFixture';
import {
  contextForRole,
  WIDGET_S_BARCODE,
  WIDGET_S_SKU,
  WH_01,
} from '../../e2e/journeys/helpers';
import type { Page, Request } from '@playwright/test';

async function hardwareWedgeScan(page: Page, barcode: string): Promise<void> {
  await page.evaluate((code) => {
    window.dispatchEvent(new CustomEvent('hardwareScan', { detail: { barcode: code } }));
  }, barcode);
}

function isUuid(value: string): boolean {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(
    value,
  );
}

async function walkToPutaway(page: Page, poNumber: string): Promise<string> {
  await hardwareWedgeScan(page, poNumber);
  await expect(page.getByTestId('inbound-step-item')).toBeVisible({ timeout: 20_000 });
  await hardwareWedgeScan(page, WIDGET_S_BARCODE);
  await expect(page.getByTestId('inbound-step-qty')).toBeVisible({ timeout: 20_000 });
  await page.getByTestId('inbound-qty-input').fill('1');
  await page.getByTestId('inbound-qty-continue').click();
  await expect(page.getByTestId('inbound-step-putaway')).toBeVisible({ timeout: 20_000 });
  const binCode = ((await page.getByTestId('putaway-code').textContent()) ?? '').trim();
  expect(binCode.length).toBeGreaterThan(0);
  return binCode;
}

/**
 * Mobile-Scanner — offline inbound receive: Wi-Fi drop, duplicate item scans,
 * optimistic UI, then reconnect with distinct Idempotency-Key UUIDs.
 */
test.describe('Offline inbound network-drop simulation', () => {
  test.setTimeout(240_000);

  test('caches two offline putaway scans and flushes unique idempotency keys', async ({
    browser,
  }, testInfo) => {
    expect(testInfo.project.name).toBe('Mobile-Scanner');

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

      poNumber = `PO-OFFLINE-${Date.now()}`;
      const poRes = await manager.page.request.post('/api/v1/purchase-orders', {
        data: {
          supplierId,
          number: poNumber,
          destinationLocationId: WH_01,
          lines: [{ variantId: widget!.id, qtyOrdered: 4, unitCost: 1.1 }],
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
      const context = picker.context;
      const page = picker.page;

      await page.goto('/inbound/receive');
      await expect(page.getByTestId('inbound-receive-page')).toBeVisible({ timeout: 20_000 });
      await completeScannerPin(page);

      const binCode = await walkToPutaway(page, poNumber);

      const confirmRequests: Request[] = [];
      page.on('response', (res) => {
        if (
          res.url().includes('/api/v1/inbound/receive/confirm') &&
          res.request().method() === 'POST' &&
          res.ok()
        ) {
          confirmRequests.push(res.request());
        }
      });

      // --- First offline scan ---
      await context.setOffline(true);
      await page.evaluate(() => window.dispatchEvent(new Event('offline')));
      await expect(page.getByTestId('network-status-badge')).toContainText('Offline - Caching Scans', {
        timeout: 10_000,
      });

      await hardwareWedgeScan(page, binCode);
      await expect(page.getByTestId('inbound-scanned-count')).toContainText(/Scanned\s+[1-9]/, {
        timeout: 8_000,
      });
      await expect(page.getByTestId('inbound-step-done')).toBeVisible({ timeout: 8_000 });
      expect(confirmRequests).toHaveLength(0);

      // Hold confirms while we re-walk the receive UI for a second offline scan.
      await page.route('**/api/v1/inbound/receive/confirm', (route) => route.abort());
      await context.setOffline(false);
      await page.evaluate(() => window.dispatchEvent(new Event('online')));

      if (await page.getByTestId('inbound-receive-another').isVisible().catch(() => false)) {
        await page.getByTestId('inbound-receive-another').click();
      } else {
        await page.goto('/inbound/receive');
        await completeScannerPin(page);
      }

      const binCode2 = await walkToPutaway(page, poNumber);

      await context.setOffline(true);
      await page.evaluate(() => window.dispatchEvent(new Event('offline')));
      await page.unroute('**/api/v1/inbound/receive/confirm');

      await hardwareWedgeScan(page, binCode2);
      await expect(page.getByTestId('inbound-scanned-count')).toContainText(/Scanned\s+[1-9]/, {
        timeout: 8_000,
      });
      await expect(page.getByTestId('network-status-badge')).toContainText('Offline - Caching Scans');
      // Aborted mid-walk attempts must not count as successful confirms.
      expect(confirmRequests).toHaveLength(0);

      // --- Wi-Fi restore — both queued confirms should flush with distinct keys ---
      await context.setOffline(false);
      await page.evaluate(() => window.dispatchEvent(new Event('online')));

      await expect
        .poll(() => confirmRequests.length, { timeout: 45_000 })
        .toBeGreaterThanOrEqual(2);

      const keys = confirmRequests.slice(0, 2).map((req) => req.headers()['idempotency-key'] ?? '');
      expect(keys[0]).toBeTruthy();
      expect(keys[1]).toBeTruthy();
      expect(isUuid(keys[0]!)).toBe(true);
      expect(isUuid(keys[1]!)).toBe(true);
      expect(keys[0]).not.toBe(keys[1]);
    } finally {
      await picker.close();
    }
  });
});
