import { expect, test } from '../fixtures/roleFixture';
import {
  contextForRole,
  findVariantId,
  firstCustomerId,
  PICK_BIN_ID,
} from './helpers';

/**
 * Journey 25 — async inventory_level delta flush, FFD packing placements,
 * and media cache-first service-worker routing (prod SW when registered).
 */
test.describe('Journey 25: Async levels / FFD packing / media SW', () => {
  test.setTimeout(300_000);

  test('receive materializes levels; cartonize packing; media cache policy', async ({
    browser,
  }) => {
    const owner = await contextForRole(browser, 'owner');
    try {
      const variantId = await findVariantId(owner.page);
      const stamp = Date.now().toString(36).toUpperCase();

      const receive = await owner.page.request.post('/api/v1/inventory/receive', {
        data: {
          variantId,
          locationId: PICK_BIN_ID,
          quantity: 3,
          referenceType: 'E2E_J25',
        },
      });
      expect(receive.ok(), await receive.text()).toBeTruthy();

      await expect
        .poll(
          async () => {
            const levelsRes = await owner.page.request.get(
              `/api/v1/inventory/levels?variantId=${variantId}`,
            );
            if (!levelsRes.ok()) return 0;
            const levels = (await levelsRes.json()) as Array<{ onHand: number; locationId: string }>;
            return levels
              .filter((l) => l.locationId === PICK_BIN_ID)
              .reduce((sum, l) => sum + Number(l.onHand ?? 0), 0);
          },
          { timeout: 15_000 },
        )
        .toBeGreaterThanOrEqual(3);

      const customerId = await firstCustomerId(owner.page);
      const soRes = await owner.page.request.post('/api/v1/sales-orders', {
        data: {
          customerId,
          number: `SO-J25-${stamp}`,
          channel: 'MANUAL',
          currency: 'USD',
          lines: [{ variantId, qtyOrdered: 1, unitPrice: 9.99 }],
        },
      });
      expect(soRes.ok(), await soRes.text()).toBeTruthy();
      const so = (await soRes.json()) as { id: string };
      await owner.page.request.post(`/api/v1/sales-orders/${so.id}/confirm`);

      const previewRes = await owner.page.request.get(
        `/api/v1/shipments/cartonize-preview?salesOrderId=${so.id}`,
      );
      expect(previewRes.ok(), await previewRes.text()).toBeTruthy();
      const preview = (await previewRes.json()) as {
        cartonName: string;
        packing?: Array<{ xIn: number; yIn: number; zIn: number; lengthIn: number }>;
      };
      expect(preview.cartonName).toBeTruthy();
      expect(Array.isArray(preview.packing)).toBeTruthy();
      expect(preview.packing!.length).toBeGreaterThanOrEqual(1);
      expect(preview.packing![0]).toMatchObject({
        xIn: expect.any(Number),
        yIn: expect.any(Number),
        zIn: expect.any(Number),
      });

      // Service worker script ships cache-first for /api/v1/media/* (prod registration).
      const swRes = await owner.page.request.get('/sw.js');
      expect(swRes.ok()).toBeTruthy();
      const swText = await swRes.text();
      expect(swText).toContain('/api/v1/media/');
      expect(swText).toContain('invsys-media-v1');
      // Workbox CacheFirst is minified in prod — assert media route + dedicated cache name.

      await owner.page.goto('/fulfillment');
      await expect(owner.page.getByRole('button', { name: 'Pack', exact: true })).toBeVisible({
        timeout: 30_000,
      });
      await owner.page.getByRole('button', { name: 'Pack', exact: true }).click();
      await expect(
        owner.page.getByText(/Serial or Bluetooth|shipping-bay scale|Weight override/i).first(),
      ).toBeVisible({ timeout: 20_000 });
    } finally {
      await owner.close();
    }
  });
});
