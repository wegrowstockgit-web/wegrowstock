import { expect, test } from '../fixtures/roleFixture';
import { contextForRole, findVariantId, WIDGET_S_BARCODE, WIDGET_S_SKU } from './helpers';

async function intentScan(page: import('@playwright/test').Page, barcode: string): Promise<void> {
  await page.evaluate((code) => {
    window.dispatchEvent(new CustomEvent('hardwareScan', { detail: { barcode: code } }));
  }, barcode);
}

/**
 * Journey 20 — Internal lot minting escape hatch on receive scanner.
 */
test.describe('Journey 20: Internal Lot Minting', () => {
  test.setTimeout(240_000);

  test('Missing Vendor Lot mints INT- lot, prints ZPL, and injects lot field', async ({
    browser,
  }) => {
    const owner = await contextForRole(browser, 'owner');
    try {
      const variantId = await findVariantId(owner.page, WIDGET_S_SKU);
      const patch = await owner.page.request.patch(`/api/v1/variants/${variantId}`, {
        data: { isLotTracked: true },
      });
      expect(patch.ok(), await patch.text()).toBeTruthy();
    } finally {
      await owner.close();
    }

    const picker = await contextForRole(browser, 'picker');
    try {
      // Capture ZPL print attempts (QZ may be absent — browser fallback still calls executePrint).
      await picker.page.addInitScript(() => {
        (window as unknown as { __printedZpl?: string[] }).__printedZpl = [];
      });

      await picker.page.goto('/fulfillment');
      await expect(picker.page.getByText('Floor ops')).toBeVisible({ timeout: 30_000 });
      await picker.page.getByRole('button', { name: 'Single' }).click();
      await picker.page.getByRole('radio', { name: 'Receive' }).click();

      const scanWait = picker.page.waitForResponse(
        (res) =>
          res.url().includes('/api/v1/fulfillment/scan') && res.request().method() === 'POST',
        { timeout: 30_000 },
      );
      await intentScan(picker.page, WIDGET_S_BARCODE);
      const scanRes = await scanWait;
      expect(scanRes.ok(), await scanRes.text()).toBeTruthy();

      await expect(picker.page.getByTestId('mint-internal-lot')).toBeVisible({ timeout: 15_000 });

      const mintWait = picker.page.waitForResponse(
        (res) =>
          res.url().includes('/api/v1/inventory/lots/mint') && res.request().method() === 'POST',
        { timeout: 30_000 },
      );
      await picker.page.getByTestId('mint-internal-lot').click();
      const mintRes = await mintWait;
      expect(mintRes.ok(), await mintRes.text()).toBeTruthy();
      const minted = (await mintRes.json()) as {
        id: string;
        lotNumber: string;
        zpl: string;
      };
      expect(minted.lotNumber).toMatch(/^INT-/);
      expect(minted.zpl).toContain('^XA');
      expect(minted.zpl).toContain(minted.lotNumber);

      await expect(picker.page.getByTestId('gs1-lot')).toHaveValue(minted.lotNumber, {
        timeout: 10_000,
      });
      await expect(picker.page.getByTestId('mint-internal-lot')).toHaveCount(0);

      // Re-scan with injected lot — receive should bind the minted lot string.
      const rescanWait = picker.page.waitForResponse(
        (res) =>
          res.url().includes('/api/v1/fulfillment/scan') && res.request().method() === 'POST',
        { timeout: 30_000 },
      );
      await intentScan(picker.page, WIDGET_S_BARCODE);
      const rescanRes = await rescanWait;
      expect(rescanRes.ok(), await rescanRes.text()).toBeTruthy();
      const scanBody = rescanRes.request().postDataJSON() as { lotNumber?: string };
      expect(scanBody.lotNumber).toBe(minted.lotNumber);
    } finally {
      await picker.close();
    }
  });
});
