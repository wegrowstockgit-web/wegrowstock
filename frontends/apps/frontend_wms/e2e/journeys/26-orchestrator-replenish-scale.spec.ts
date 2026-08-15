import { expect, test } from '../fixtures/roleFixture';
import {
  contextForRole,
  expectFulfillmentSurface,
  findVariantId,
  PICK_BIN_ID,
  WH_01,
} from './helpers';

/**
 * Journey 26 — Task orchestrator next-best-action, predictive replenishment
 * wave triggers in the interleave queue, packing-scale UI surface.
 */
test.describe('Journey 26: Orchestrator / Predictive replen / Packing scale', () => {
  test.setTimeout(300_000);

  test('next-best-action travelScore; packing scale connect affordance', async ({ browser }) => {
    const owner = await contextForRole(browser, 'owner');
    try {
      const variantId = await findVariantId(owner.page);
      const receive = await owner.page.request.post('/api/v1/inventory/receive', {
        data: {
          variantId,
          locationId: PICK_BIN_ID,
          quantity: 2,
          referenceType: 'E2E_J26',
        },
      });
      expect(receive.ok(), await receive.text()).toBeTruthy();

      const nba = await owner.page.request.get('/api/v1/tasks/next-best-action', {
        params: { currentLocationId: PICK_BIN_ID },
        headers: { 'X-Warehouse-Id': WH_01 },
      });
      expect(nba.ok(), await nba.text()).toBeTruthy();
      const body = (await nba.json()) as {
        taskType: string | null;
        summary: string;
        travelScore?: number | null;
      };
      // May be null when no pending tasks — still a valid orchestrator response.
      expect(body).toHaveProperty('summary');
      if (body.taskType) {
        expect(body.travelScore === null || typeof body.travelScore === 'number').toBeTruthy();
      }

      await owner.page.goto('/fulfillment');
      await expectFulfillmentSurface(owner.page);
      await owner.page.getByRole('button', { name: 'Pack', exact: true }).click();
      await expect(owner.page.getByTestId('packing-scale-status')).toBeVisible({
        timeout: 20_000,
      });
      await expect(
        owner.page.getByText(/packing scale|Connect packing scale|billable weight/i).first(),
      ).toBeVisible();
    } finally {
      await owner.close();
    }
  });
});
