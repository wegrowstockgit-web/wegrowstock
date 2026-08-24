import { test } from '@playwright/test';
import {
  PICK_BIN_ID,
  apiJson,
  contextForRole,
  createShippedSalesOrder,
  createZeroStockSellableVariant,
  expect,
  firstCustomerId,
} from './helpers';

test.describe('Journey 81: RMA exception toasts and restock bin cues', () => {
  test.setTimeout(180_000);

  test('restock bin is required and escalate surfaces Problem Details', async ({ browser }) => {
    const admin = await contextForRole(browser, 'admin');
    const manager = await contextForRole(browser, 'manager');
    try {
      const item = await createZeroStockSellableVariant(manager.page);
      const customerId = await firstCustomerId(manager.page);
      const shipped = await createShippedSalesOrder(manager.page, {
        variantId: item.variantId,
        customerId,
        quantity: 1,
        numberPrefix: 'SO-RMAT',
      });

      const rma = await apiJson<{
        id: string;
        number: string;
        lines: Array<{ id: string }>;
      }>(admin.page, '/api/v1/returns', {
        method: 'POST',
        body: JSON.stringify({
          salesOrderId: shipped.salesOrderId,
          lines: [{ salesOrderLineId: shipped.salesOrderLineId, quantityExpected: 1 }],
        }),
      });
      await apiJson(admin.page, `/api/v1/returns/${rma.id}/approve`, {
        method: 'POST',
        body: '{}',
      });
      await admin.page.request.put(`/api/v1/returns/${rma.id}/lines/${rma.lines[0]!.id}`, {
        data: { disposition: 'RESTOCK' },
      });

      await admin.page.goto('/returns');
      await expect(admin.page.getByTestId('returns-page')).toBeVisible({ timeout: 20_000 });
      const skipTour = admin.page.getByRole('button', { name: 'Not now' });
      if (await skipTour.isVisible({ timeout: 3_000 }).catch(() => false)) {
        await skipTour.click();
      }

      const card = admin.page.getByTestId(`rma-card-${rma.number}`);
      await expect(card).toBeVisible({ timeout: 20_000 });
      await card.click();

      const bin = admin.page.getByTestId(`rma-restock-bin-${item.sku}`);
      await expect(bin).toBeVisible({ timeout: 10_000 });
      await expect(bin).toHaveAttribute('aria-invalid', 'true');
      await expect(admin.page.getByTestId('rma-complete-disposition')).toBeDisabled();

      const escalateWait = admin.page.waitForResponse(
        (res) =>
          res.url().includes(`/api/v1/returns/${rma.id}/escalate-rtv`) &&
          res.request().method() === 'POST',
      );
      await admin.page.getByTestId('rma-escalate-rtv').click();
      const escalateRes = await escalateWait;
      expect(escalateRes.status()).toBe(422);
      const body = (await escalateRes.json()) as { detail?: string };
      expect(body.detail).toBeTruthy();
      const toast = admin.page.getByTestId('app-toast');
      await expect(toast).toContainText(body.detail!);
      await expect(admin.page.getByTestId('toast-region')).toBeVisible();

      const viewport = admin.page.viewportSize();
      expect(viewport).toBeTruthy();
      const toastBox = await toast.boundingBox();
      expect(toastBox).toBeTruthy();
      expect(toastBox!.y).toBeLessThan(viewport!.height * 0.35);
      expect(toastBox!.x).toBeGreaterThan(viewport!.width * 0.45);

      const search = admin.page.getByRole('button', { name: /search/i });
      if (await search.isVisible().catch(() => false)) {
        const searchBox = await search.boundingBox();
        if (searchBox && toastBox) {
          const overlapX =
            toastBox.x < searchBox.x + searchBox.width && searchBox.x < toastBox.x + toastBox.width;
          const overlapY =
            toastBox.y < searchBox.y + searchBox.height && searchBox.y < toastBox.y + toastBox.height;
          expect(overlapX && overlapY).toBeFalsy();
        }
      }

      const binWait = admin.page.waitForResponse(
        (res) => res.url().includes(`/api/v1/returns/${rma.id}/lines/`) && res.request().method() === 'PUT',
      );
      const listRefresh = admin.page.waitForResponse(
        (res) =>
          res.url().includes('/api/v1/returns') &&
          res.request().method() === 'GET' &&
          !res.url().includes('PENDING_REVIEW') &&
          !res.url().includes('/lines/'),
      );
      await bin.selectOption(PICK_BIN_ID);
      expect((await binWait).ok()).toBeTruthy();
      expect((await listRefresh).ok()).toBeTruthy();
      await expect(bin).toHaveAttribute('aria-invalid', 'false', { timeout: 15_000 });
      await expect(admin.page.getByTestId('rma-complete-disposition')).toBeEnabled();
    } finally {
      await manager.close();
      await admin.close();
    }
  });
});
