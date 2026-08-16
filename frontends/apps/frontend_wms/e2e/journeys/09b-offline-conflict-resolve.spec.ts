import { test } from '@playwright/test';
import { peekMutationQueue } from '../fixtures/roleFixture';
import {
  contextForRole,
  createZeroStockSellableVariant,
  expect,
  expectFulfillmentSurface,
  hidScan,
} from './helpers';

/**
 * Full hybrid conflict engine: floor park → human-readable panel → Approve & Re-process.
 */
test.describe('Journey 09b: Offline conflict metadata resolve', () => {
  test('manager resolves parked conflict via schema-driven form', async ({ browser }) => {
    const picker = await contextForRole(browser, 'picker');
    const manager = await contextForRole(browser, 'manager');

    try {
      const orphan = await createZeroStockSellableVariant(manager.page);

      await picker.page.goto('/fulfillment');
      await expectFulfillmentSurface(picker.page);
      await picker.page.getByRole('button', { name: 'Single' }).click();
      await picker.page.getByRole('radio', { name: 'Receive' }).click();
      await picker.context.setOffline(true);

      await hidScan(picker.page, orphan.barcode);
      await expect(picker.page.getByText(/Scan queued — undo within 5s/i)).toBeVisible({
        timeout: 10_000,
      });
      await expect(picker.page.getByText(/Scan queued — undo within 5s/i)).toBeHidden({
        timeout: 8_000,
      });
      await expect
        .poll(async () => (await peekMutationQueue(picker.page)).length, { timeout: 10_000 })
        .toBeGreaterThan(0);

      const flushPromise = picker.page.waitForResponse(
        (res) =>
          res.url().includes('/api/v1/fulfillment/scan') && res.request().method() === 'POST',
        { timeout: 25_000 },
      );
      await picker.context.setOffline(false);
      await picker.page.evaluate(() => window.dispatchEvent(new Event('online')));
      const flushed = await flushPromise;

      // Prefer server DLQ (HTTP 202). Fall back to any PENDING row for this tenant.
      let conflictId: string | null = null;
      if (flushed.status() === 202) {
        const body = (await flushed.json()) as { conflictId?: string };
        conflictId = body.conflictId ?? null;
      }

      await expect
        .poll(async () => {
          const res = await manager.page.request.get(
            '/api/v1/offline-sync-conflicts?status=PENDING',
          );
          if (!res.ok()) return 0;
          const rows = (await res.json()) as Array<{ id: string }>;
          if (!conflictId && rows[0]?.id) conflictId = rows[0].id;
          return rows.length;
        }, { timeout: 30_000 })
        .toBeGreaterThan(0);

      expect(conflictId).toBeTruthy();

      await manager.page.goto('/exceptions?tab=sync');
      await expect(manager.page.getByTestId('sync-conflicts-panel')).toBeVisible({
        timeout: 20_000,
      });
      await expect(manager.page.getByTestId('sync-conflict-human-summary')).toBeVisible({
        timeout: 15_000,
      });
      await expect(manager.page.getByTestId('sync-conflict-human-summary')).toContainText(
        /Floor Operator|failed to process/i,
      );
      await expect(manager.page.getByTestId('sync-conflict-resolution-form')).toBeVisible();

      // No raw serialization jargon for managers.
      await expect(manager.page.getByText(/payload_json|schema_metadata_json/i)).toHaveCount(0);

      const qty = manager.page.getByTestId('conflict-input-quantity');
      if (await qty.isVisible().catch(() => false)) {
        await qty.fill('1');
      }

      await manager.page.getByTestId(`approve-conflict-${conflictId}`).click();
      await manager.page.getByTestId('confirm-approve-conflict').click();

      await expect
        .poll(async () => {
          const res = await manager.page.request.get(
            `/api/v1/offline-sync-conflicts?status=PENDING`,
          );
          if (!res.ok()) return -1;
          const rows = (await res.json()) as Array<{ id: string }>;
          return rows.some((r) => r.id === conflictId) ? 1 : 0;
        }, { timeout: 25_000 })
        .toBe(0);

    } finally {
      await picker.context.setOffline(false).catch(() => undefined);
      await picker.close();
      await manager.close();
    }
  });
});
