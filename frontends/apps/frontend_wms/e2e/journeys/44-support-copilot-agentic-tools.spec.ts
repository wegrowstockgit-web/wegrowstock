import { expect, test } from '../fixtures/roleFixture';
import { completeScannerPin, dismissOnboardingTourIfPresent } from '../fixtures/roleFixture';
import { apiJson, contextForRole, findVariantId, WIDGET_S_SKU } from './helpers';

/**
 * Live CQRS tool grounding: ask about a real SKU / sales order and expect
 * available-to-promise / status facts in the Operations Instructor reply.
 */
test.describe('Support copilot agentic CQRS tools', () => {
  test.setTimeout(180_000);

  test('manager ATP question returns live on-hand facts for a real SKU', async ({ browser }) => {
    const manager = await contextForRole(browser, 'manager');
    try {
      let sku = WIDGET_S_SKU;
      try {
        await findVariantId(manager.page, sku);
      } catch {
        const page = await apiJson<{ items?: Array<{ sku?: string }> }>(
          manager.page,
          '/api/v1/variants?limit=20',
        );
        sku = page.items?.find((v) => v.sku)?.sku ?? '';
      }
      test.skip(!sku, 'No seeded variants available for ATP grounding');

      await manager.page.goto('/products');
      await completeScannerPin(manager.page);
      await dismissOnboardingTourIfPresent(manager.page);

      await manager.page.getByTestId('support-assistant-fab').click();
      await expect(manager.page.getByTestId('support-assistant-panel')).toBeVisible();

      await manager.page
        .getByTestId('support-assistant-input')
        .fill(`What is the available-to-promise for SKU ${sku}?`);
      await manager.page.getByTestId('support-assistant-send').click();

      const reply = manager.page.getByTestId('support-assistant-reply').last();
      await expect(reply).toContainText(new RegExp(sku!.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'i'), {
        timeout: 45_000,
      });
      await expect(reply).toContainText(/available-to-promise|on-hand|reserved|ATP/i, {
        timeout: 15_000,
      });
      await expect(reply).not.toContainText(/\/api\/v1|SalesOrderService|inventory_levels/i);
    } finally {
      await manager.close();
    }
  });

  test('manager order-status question surfaces hold guidance without jargon', async ({ browser }) => {
    const manager = await contextForRole(browser, 'manager');
    try {
      const payload = await apiJson<
        { items?: Array<{ number?: string; status?: string }>; content?: Array<{ number?: string; status?: string }> } | Array<{ number?: string; status?: string }>
      >(manager.page, '/api/v1/sales-orders?page=1&size=20');
      const orders = Array.isArray(payload) ? payload : (payload.items ?? payload.content ?? []);
      const order = orders.find((o) => o.number);
      test.skip(!order?.number, 'No seeded sales orders for status grounding');

      await manager.page.goto('/sales-orders');
      await completeScannerPin(manager.page);
      await dismissOnboardingTourIfPresent(manager.page);

      await manager.page.getByTestId('support-assistant-fab').click();
      await manager.page
        .getByTestId('support-assistant-input')
        .fill(`What is the status of ${order!.number} and is it on hold?`);
      await manager.page.getByTestId('support-assistant-send').click();

      const reply = manager.page.getByTestId('support-assistant-reply').last();
      await expect(reply).toContainText(new RegExp(order!.number!.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'i'), {
        timeout: 45_000,
      });
      await expect(reply).toContainText(/status|hold|allocate|backorder|draft|shipped|confirmed/i, {
        timeout: 15_000,
      });
      await expect(reply).not.toContainText(/HTTP\s*\d{3}|\/api\/v1|CQRS/i);

      const chip = manager.page.getByTestId('support-action-chip').first();
      await expect(chip).toBeVisible({ timeout: 15_000 });
    } finally {
      await manager.close();
    }
  });
});
