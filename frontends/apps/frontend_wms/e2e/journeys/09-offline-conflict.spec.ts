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
 * Track 9 — Offline mutation that violates a business rule → sync conflict for office review.
 * Receive mode + blind-receiving disabled → 422 on replay → server parks 202 conflict.
 * Uses a fresh SKU with no open SO demand so cross-dock intercept cannot return 200.
 */
test.describe('Journey 09: Offline Zustand/IndexedDB conflict resolution', () => {
  test('offline rule-violating scan parks in sync conflicts panel', async ({ browser }) => {
    const picker = await contextForRole(browser, 'picker');
    const manager = await contextForRole(browser, 'manager');

    try {
      const orphan = await createZeroStockSellableVariant(manager.page);

      await picker.page.goto('/fulfillment');
      await expectFulfillmentSurface(picker.page);
      await picker.page.getByRole('button', { name: 'Single' }).click();
      // Blind receiving is disabled in demo → receive scan is a reliable business-rule failure
      await picker.page.getByRole('radio', { name: 'Receive' }).click();

      await picker.context.setOffline(true);

      const outboundScans: string[] = [];
      picker.page.on('request', (req) => {
        if (req.url().includes('/api/v1/fulfillment/scan') && req.method() === 'POST') {
          outboundScans.push(req.url());
        }
      });

      await hidScan(picker.page, orphan.barcode);
      await expect(picker.page.getByText(/Scan queued — undo within 5s/i)).toBeVisible({
        timeout: 10_000,
      });
      expect(outboundScans).toHaveLength(0);

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
      // 202 = parked in offline_sync_conflicts; 422/409 = local quarantine / blind-receive rejection
      expect([202, 409, 422], await flushed.text()).toContain(flushed.status());

      await manager.page.goto('/dashboard');
      if (flushed.status() === 202) {
        await expect
          .poll(async () => {
            const res = await manager.page.request.get(
              '/api/v1/offline-sync-conflicts?status=PENDING',
            );
            if (!res.ok()) return 0;
            return ((await res.json()) as unknown[]).length;
          }, { timeout: 25_000 })
          .toBeGreaterThan(0);
        await expect(manager.page.getByTestId('sync-conflict-alert-banner')).toBeVisible({
          timeout: 20_000,
        });
        await manager.page.getByTestId('sync-conflict-resolve-now').click();
        await expect(manager.page).toHaveURL(/\/exceptions\?tab=sync/);
        await expect(manager.page.getByTestId('sync-conflicts-panel')).toBeVisible({
          timeout: 20_000,
        });
        await expect(
          manager.page
            .getByText(
              /Approve & Re-process|Discard Transaction|Floor Operator|PENDING|conflict|BLIND|receiving/i,
            )
            .first(),
        ).toBeVisible({ timeout: 15_000 });
      } else {
        await manager.page.goto('/exceptions?tab=sync');
        await expect(manager.page.getByTestId('sync-conflicts-panel')).toBeVisible({
          timeout: 20_000,
        });
        await picker.page.goto('/fulfillment');
        await expect(
          picker.page
            .getByTestId('fulfillment-quarantine-badge')
            .or(picker.page.getByText(/conflict|quarantine|BLIND|failed/i))
            .first(),
        ).toBeVisible({ timeout: 15_000 });
      }

    } finally {
      await picker.context.setOffline(false).catch(() => undefined);
      await picker.close();
      await manager.close();
    }
  });
});
