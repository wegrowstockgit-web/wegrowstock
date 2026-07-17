import { expect, test } from '../fixtures/roleFixture';
import {
  contextForRole,
  expectFulfillmentSurface,
  findVariantId,
  firstCustomerId,
  PICK_BIN_B02_ID,
  PICK_BIN_ID,
  WH_01,
  WIDGET_S_BARCODE,
} from './helpers';

async function intentScan(page: import('@playwright/test').Page, barcode: string): Promise<void> {
  await page.evaluate((code) => {
    window.dispatchEvent(new CustomEvent('hardwareScan', { detail: { barcode: code } }));
  }, barcode);
}

/**
 * Journey 21 — Functional LPN move, MIB tote assignment, and next-best-action.
 */
test.describe('Journey 21: LPN / Tote / Interleaving', () => {
  test.setTimeout(300_000);

  test('mint LPN, pack stock, move via scanner, and fetch next-best-action', async ({ browser }) => {
    const owner = await contextForRole(browser, 'owner');
    let lpnBarcode = '';
    try {
      const variantId = await findVariantId(owner.page);
      const receive = await owner.page.request.post('/api/v1/inventory/receive', {
        data: {
          variantId,
          locationId: PICK_BIN_ID,
          quantity: 3,
          referenceType: 'E2E_LPN_MOVE',
        },
      });
      expect(receive.ok(), await receive.text()).toBeTruthy();

      const levelsRes = await owner.page.request.get(
        `/api/v1/inventory/levels?variantId=${variantId}`,
      );
      expect(levelsRes.ok(), await levelsRes.text()).toBeTruthy();
      const levels = (await levelsRes.json()) as Array<{
        id: string;
        locationId: string;
        onHand: number;
        lpnId?: string | null;
      }>;
      const loose = levels.find(
        (l) => l.locationId === PICK_BIN_ID && Number(l.onHand) > 0 && !l.lpnId,
      );
      expect(loose, 'loose inventory level at pick bin').toBeTruthy();

      const mint = await owner.page.request.post('/api/v1/inventory/lpns/mint', {
        data: { locationId: PICK_BIN_ID },
      });
      expect(mint.ok(), await mint.text()).toBeTruthy();
      const minted = (await mint.json()) as { lpnBarcode: string; zpl: string };
      lpnBarcode = minted.lpnBarcode;
      expect(lpnBarcode).toMatch(/^LPN-/);
      expect(minted.zpl).toContain('^XA');

      const pack = await owner.page.request.post(
        `/api/v1/inventory/lpns/${encodeURIComponent(lpnBarcode)}/pack`,
        { data: { inventoryLevelIds: [loose!.id] } },
      );
      expect(pack.ok(), await pack.text()).toBeTruthy();
      const packed = (await pack.json()) as {
        itemCount: number;
        linesPacked: number;
        lines?: Array<{ quantity: number }>;
      };
      expect(packed.linesPacked).toBeGreaterThanOrEqual(1);
      expect(packed.itemCount).toBeGreaterThanOrEqual(1);
      expect(Number(packed.lines?.[0]?.quantity ?? 0)).toBeGreaterThan(0);

      const contents = await owner.page.request.get(
        `/api/v1/inventory/lpns/${encodeURIComponent(lpnBarcode)}`,
      );
      expect(contents.ok(), await contents.text()).toBeTruthy();
      const body = (await contents.json()) as { lineCount: number };
      expect(body.lineCount).toBeGreaterThanOrEqual(1);
    } finally {
      await owner.close();
    }

    const picker = await contextForRole(browser, 'picker');
    try {
      await picker.page.goto('/fulfillment');
      await expectFulfillmentSurface(picker.page);
      await picker.page.getByRole('button', { name: 'Single' }).click();
      await picker.page.getByTestId('lpn-move-mode').click();
      await expect(picker.page.getByTestId('lpn-move-panel')).toBeVisible();

      await intentScan(picker.page, lpnBarcode);
      await expect(picker.page.getByTestId('lpn-move-panel')).toContainText('Scan destination bin', {
        timeout: 10_000,
      });

      const moveWait = picker.page.waitForResponse(
        (res) =>
          res.url().includes('/api/v1/inventory/lpns/move') && res.request().method() === 'POST',
        { timeout: 30_000 },
      );
      await intentScan(picker.page, 'B-02');
      const moveRes = await moveWait;
      expect(moveRes.ok(), await moveRes.text()).toBeTruthy();
      const moved = (await moveRes.json()) as {
        destinationLocationId: string;
        linesMoved: number;
      };
      expect(moved.destinationLocationId).toBe(PICK_BIN_B02_ID);
      expect(moved.linesMoved).toBeGreaterThanOrEqual(1);

      const nba = await picker.page.request.get('/api/v1/tasks/next-best-action', {
        params: { currentLocationId: PICK_BIN_B02_ID },
      });
      expect(nba.ok(), await nba.text()).toBeTruthy();
      const action = (await nba.json()) as { summary: string };
      expect(action.summary).toBeTruthy();
    } finally {
      await picker.close();
    }
  });

  test('wave generate assigns tote identifiers for multi-order batch', async ({ browser }) => {
    const owner = await contextForRole(browser, 'owner');
    try {
      const variantId = await findVariantId(owner.page);
      const customerId = await firstCustomerId(owner.page);

      const receive = await owner.page.request.post('/api/v1/inventory/receive', {
        data: {
          variantId,
          locationId: PICK_BIN_ID,
          quantity: 10,
          referenceType: 'E2E_TOTE_WAVE',
        },
      });
      expect(receive.ok(), await receive.text()).toBeTruthy();

      const stamp = Date.now().toString(36).toUpperCase();
      for (const suffix of ['A', 'B'] as const) {
        const so = await owner.page.request.post('/api/v1/sales-orders', {
          data: {
            customerId,
            number: `SO-TOTE-${stamp}-${suffix}`,
            channel: 'DIRECT',
            lines: [{ variantId, qtyOrdered: 1, unitPrice: 9.99 }],
          },
        });
        expect(so.ok(), await so.text()).toBeTruthy();
        const order = (await so.json()) as { id: string };
        const confirm = await owner.page.request.post(`/api/v1/sales-orders/${order.id}/confirm`);
        expect(confirm.ok(), await confirm.text()).toBeTruthy();
        const allocate = await owner.page.request.post(`/api/v1/sales-orders/${order.id}/allocate`);
        expect(allocate.ok(), await allocate.text()).toBeTruthy();
      }

      const wave = await owner.page.request.post('/api/v1/picking/waves/generate', {
        data: {},
      });
      expect(wave.ok(), await wave.text()).toBeTruthy();
      const body = (await wave.json()) as {
        tasks: Array<{ toteIdentifier?: string | null }>;
      };
      expect(body.tasks.length).toBeGreaterThanOrEqual(2);
      const totes = new Set(body.tasks.map((t) => t.toteIdentifier).filter(Boolean));
      expect(totes.size).toBeGreaterThanOrEqual(1);
      expect([...totes].some((t) => String(t).startsWith('Tote '))).toBeTruthy();

      await owner.page.goto('/fulfillment');
      await expectFulfillmentSurface(owner.page);
      await owner.page.getByRole('button', { name: 'Batch' }).click();
      // Release path may vary; tote banner appears when a PENDING task with tote is current.
      const releaseBtn = owner.page.getByRole('button', { name: /Release to floor/i });
      if (await releaseBtn.isVisible().catch(() => false)) {
        await releaseBtn.click();
      }
    } finally {
      await owner.close();
    }

    // WH_01 referenced so LBAC context stays intentional for demo tenant.
    expect(WH_01).toBeTruthy();
    expect(WIDGET_S_BARCODE).toBeTruthy();
  });
});
