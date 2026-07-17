import { expect, test } from '../fixtures/roleFixture';
import {
  contextForRole,
  expectFulfillmentSurface,
  findVariantId,
  firstCustomerId,
  PICK_BIN_ID,
  WIDGET_S_BARCODE,
} from './helpers';

async function intentScan(page: import('@playwright/test').Page, barcode: string): Promise<void> {
  await page.evaluate((code) => {
    window.dispatchEvent(new CustomEvent('hardwareScan', { detail: { barcode: code } }));
  }, barcode);
}

/**
 * Journey 22 — On-the-fly palletization: mint → print ZPL → pack → ship by LPN.
 */
test.describe('Journey 22: Pallet Builder', () => {
  test.setTimeout(300_000);

  test('mint LPN from Build Pallet UI, pack barcode, ship by LPN', async ({ browser }) => {
    const owner = await contextForRole(browser, 'owner');
    try {
      const variantId = await findVariantId(owner.page);
      const receive = await owner.page.request.post('/api/v1/inventory/receive', {
        data: {
          variantId,
          locationId: PICK_BIN_ID,
          quantity: 5,
          referenceType: 'E2E_PALLET',
        },
      });
      expect(receive.ok(), await receive.text()).toBeTruthy();
    } finally {
      await owner.close();
    }

    const picker = await contextForRole(browser, 'picker');
    let lpnBarcode = '';
    try {
      await picker.page.goto('/fulfillment');
      await expectFulfillmentSurface(picker.page);
      await picker.page.getByRole('button', { name: 'Single' }).click();
      await picker.page.getByTestId('build-pallet-mode').click();
      await expect(picker.page.getByTestId('pallet-builder')).toBeVisible();

      const mintWait = picker.page.waitForResponse(
        (res) =>
          res.url().includes('/api/v1/inventory/lpns/mint') && res.request().method() === 'POST',
        { timeout: 30_000 },
      );
      await picker.page.getByTestId('mint-new-lpn').click();
      const mintRes = await mintWait;
      expect(mintRes.ok(), await mintRes.text()).toBeTruthy();
      const minted = (await mintRes.json()) as { lpnBarcode: string; zpl: string };
      lpnBarcode = minted.lpnBarcode;
      expect(lpnBarcode).toMatch(/^LPN-/);
      expect(minted.zpl).toContain('^XA');
      await expect(picker.page.getByTestId('active-lpn-barcode')).toHaveText(lpnBarcode);

      const packWait = picker.page.waitForResponse(
        (res) =>
          res.url().includes(`/lpns/${encodeURIComponent(lpnBarcode)}/pack`) &&
          res.request().method() === 'POST',
        { timeout: 30_000 },
      );
      await intentScan(picker.page, WIDGET_S_BARCODE);
      const packRes = await packWait;
      expect(packRes.ok(), await packRes.text()).toBeTruthy();
      const packed = (await packRes.json()) as { itemCount: number };
      expect(packed.itemCount).toBeGreaterThanOrEqual(1);
      await expect(picker.page.getByTestId('pallet-item-count')).toContainText(String(packed.itemCount), {
        timeout: 10_000,
      });

      const contents = await picker.page.request.get(
        `/api/v1/inventory/lpns/${encodeURIComponent(lpnBarcode)}`,
      );
      expect(contents.ok(), await contents.text()).toBeTruthy();
      const body = (await contents.json()) as { lineCount: number; status: string };
      expect(body.lineCount).toBeGreaterThanOrEqual(1);
      expect(body.status).toBe('OPEN');
    } finally {
      await picker.close();
    }

    const shipper = await contextForRole(browser, 'owner');
    try {
      const variantId = await findVariantId(shipper.page);
      const customerId = await firstCustomerId(shipper.page);
      const stamp = Date.now().toString(36).toUpperCase();
      const soRes = await shipper.page.request.post('/api/v1/sales-orders', {
        data: {
          customerId,
          number: `SO-PAL-${stamp}`,
          channel: 'DIRECT',
          lines: [{ variantId, qtyOrdered: 1, unitPrice: 11 }],
        },
      });
      expect(soRes.ok(), await soRes.text()).toBeTruthy();
      const so = (await soRes.json()) as { id: string };

      const ship = await shipper.page.request.post('/api/v1/shipments', {
        data: {
          salesOrderId: so.id,
          carrier: 'UPS',
          trackingNumber: `1ZE2E${stamp}`,
          lpnBarcode,
          lines: [],
        },
      });
      expect(ship.ok(), await ship.text()).toBeTruthy();
      const shipment = (await ship.json()) as { status: string };
      expect(shipment.status).toBe('SHIPPED');

      const lpn = await shipper.page.request.get(
        `/api/v1/inventory/lpns/${encodeURIComponent(lpnBarcode)}`,
      );
      expect(lpn.ok(), await lpn.text()).toBeTruthy();
      const after = (await lpn.json()) as { status: string; totalQuantity: number };
      expect(after.status).toBe('DISPATCHED');
      expect(Number(after.totalQuantity)).toBe(0);
    } finally {
      await shipper.close();
    }
  });
});
