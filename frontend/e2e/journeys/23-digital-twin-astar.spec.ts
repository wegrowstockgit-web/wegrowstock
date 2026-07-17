import { expect, test } from '../fixtures/roleFixture';
import {
  contextForRole,
  expectFulfillmentSurface,
  PICK_BIN_B02_ID,
  PICK_BIN_ID,
  WH_01,
} from './helpers';

/**
 * Journey 23 — Digital Twin coordinates, heatmap, A* wayfinding, scanner mini-map.
 */
test.describe('Journey 23: Digital Twin & A* Wayfinding', () => {
  test.setTimeout(300_000);

  test('patch bin coordinates, heatmap, wayfinding polyline, and mini-map UI', async ({
    browser,
  }) => {
    const owner = await contextForRole(browser, 'owner');
    try {
      const patch = await owner.page.request.patch(
        `/api/v1/locations/${PICK_BIN_ID}/coordinates`,
        {
          data: { coordX: 12.5, coordY: 8.25, coordZ: 0 },
        },
      );
      expect(patch.ok(), await patch.text()).toBeTruthy();
      const bin = (await patch.json()) as { coordX: number; coordY: number; id: string };
      expect(Number(bin.coordX)).toBeCloseTo(12.5, 1);
      expect(Number(bin.coordY)).toBeCloseTo(8.25, 1);

      const patchB = await owner.page.request.patch(
        `/api/v1/locations/${PICK_BIN_B02_ID}/coordinates`,
        {
          data: { coordX: 22.5, coordY: 8.25 },
        },
      );
      expect(patchB.ok(), await patchB.text()).toBeTruthy();

      const heat = await owner.page.request.get('/api/v1/locations/heatmap', {
        params: { days: 7 },
      });
      expect(heat.ok(), await heat.text()).toBeTruthy();
      const cells = (await heat.json()) as Array<{
        locationId: string;
        intensity: number;
        movementCount: number;
      }>;
      expect(cells.length).toBeGreaterThan(0);
      expect(cells.some((c) => c.locationId === PICK_BIN_ID)).toBeTruthy();

      const path = await owner.page.request.get('/api/v1/picking/wayfinding', {
        params: {
          fromLocationId: PICK_BIN_ID,
          toLocationId: PICK_BIN_B02_ID,
        },
      });
      expect(path.ok(), await path.text()).toBeTruthy();
      const way = (await path.json()) as {
        travelCost: number;
        points: Array<{ x: number; y: number }>;
      };
      expect(way.points.length).toBeGreaterThanOrEqual(2);
      expect(way.travelCost).toBeGreaterThan(0);

      await owner.page.goto('/settings?tab=warehouses');
      await expect(owner.page.getByTestId('warehouse-visualizer')).toBeVisible({
        timeout: 30_000,
      });
      await expect(owner.page.getByTestId('digital-twin-map')).toBeVisible();
      await owner.page.getByTestId('heatmap-toggle').click();
      await expect(owner.page.getByTestId('heatmap-toggle')).toContainText(/Heatmap on/i);
      await expect(owner.page.getByTestId('digital-twin-svg')).toBeVisible();
    } finally {
      await owner.close();
    }

    const picker = await contextForRole(browser, 'picker');
    try {
      // Seed a released wave task so Batch shows next bin + mini-map (best-effort).
      const wave = await picker.page.request.post('/api/v1/picking/waves/generate', { data: {} });
      if (wave.ok()) {
        const body = (await wave.json()) as { waveId: string };
        await picker.page.request.post(`/api/v1/picking/waves/${body.waveId}/release`);
      }

      await picker.page.goto('/fulfillment');
      await expectFulfillmentSurface(picker.page);
      await picker.page.getByRole('button', { name: 'Batch' }).click();

      const miniMap = picker.page.getByTestId('wayfinding-open');
      if (await miniMap.isVisible({ timeout: 15_000 }).catch(() => false)) {
        await miniMap.click();
        await expect(picker.page.getByTestId('wayfinding-overlay')).toBeVisible();
        await expect(picker.page.getByTestId('wayfinding-svg')).toBeVisible({ timeout: 20_000 });
        await picker.page.getByTestId('wayfinding-close').click();
      } else {
        // No active pick stop — still prove wayfinding API from picker session.
        const path = await picker.page.request.get('/api/v1/picking/wayfinding', {
          params: {
            fromLocationId: WH_01,
            toLocationId: PICK_BIN_ID,
          },
        });
        expect(path.ok(), await path.text()).toBeTruthy();
      }
    } finally {
      await picker.close();
    }
  });
});
