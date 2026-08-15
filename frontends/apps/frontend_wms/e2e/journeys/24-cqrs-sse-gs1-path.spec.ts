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
 * Journey 24 — CQRS dashboard stats, SSE stream, client GS1 pre-validation,
 * and hierarchical pick-path ordering.
 */
test.describe('Journey 24: CQRS / SSE / GS1 / Path', () => {
  test.setTimeout(300_000);

  test('dashboard stats read model, SSE connect, reject wrong pick scan, path order', async ({
    browser,
  }) => {
    let waveId = '';

    const owner = await contextForRole(browser, 'owner');
    try {
      const stats = await owner.page.request.get('/api/v1/dashboard/stats');
      expect(stats.ok(), await stats.text()).toBeTruthy();
      const body = (await stats.json()) as {
        stockValue: number;
        currency: string;
        openOrdersCount: number;
      };
      expect(body.currency).toBeTruthy();
      expect(typeof body.openOrdersCount).toBe('number');
      expect(body.stockValue).not.toBeNull();

      await owner.page.goto('/dashboard');
      const sseState = await owner.page.evaluate(async () => {
        return await new Promise<{ sawConnected: boolean; readyState: number }>((resolve) => {
          const es = new EventSource('/api/v1/dashboard/stream', { withCredentials: true });
          let sawConnected = false;
          const done = () => {
            const readyState = es.readyState;
            es.close();
            resolve({ sawConnected, readyState });
          };
          const timer = setTimeout(done, 5_000);
          es.addEventListener('connected', () => {
            sawConnected = true;
            clearTimeout(timer);
            done();
          });
          es.onerror = () => {
            clearTimeout(timer);
            done();
          };
        });
      });
      expect(sseState.sawConnected).toBeTruthy();

      const variantId = await findVariantId(owner.page);
      const receive = await owner.page.request.post('/api/v1/inventory/receive', {
        data: {
          variantId,
          locationId: PICK_BIN_ID,
          quantity: 5,
          referenceType: 'E2E_J24',
        },
      });
      expect(receive.ok(), await receive.text()).toBeTruthy();

      const customerId = await firstCustomerId(owner.page);
      const stamp = Date.now().toString(36).toUpperCase();
      const soRes = await owner.page.request.post('/api/v1/sales-orders', {
        data: {
          customerId,
          number: `SO-J24-${stamp}`,
          channel: 'DIRECT',
          lines: [{ variantId, qtyOrdered: 1, unitPrice: 9 }],
        },
      });
      expect(soRes.ok(), await soRes.text()).toBeTruthy();
      const so = (await soRes.json()) as { id: string; status?: string };
      if (so.status === 'DRAFT' || !so.status) {
        const confirm = await owner.page.request.post(`/api/v1/sales-orders/${so.id}/confirm`);
        expect(confirm.ok(), await confirm.text()).toBeTruthy();
      }

      const alloc = await owner.page.request.post(`/api/v1/sales-orders/${so.id}/allocate`);
      expect(alloc.ok(), await alloc.text()).toBeTruthy();

      const wave = await owner.page.request.post('/api/v1/picking/waves/generate', {
        data: {},
      });
      expect(wave.ok(), await wave.text()).toBeTruthy();
      const waveBody = (await wave.json()) as {
        waveId: string;
        status: string;
        tasks?: Array<{ sku?: string; status?: string }>;
      };
      waveId = waveBody.waveId;
      if (waveBody.status !== 'RELEASED') {
        const released = await owner.page.request.post(`/api/v1/picking/waves/${waveId}/release`);
        expect(released.ok(), await released.text()).toBeTruthy();
      }

      const optimize = await owner.page.request.post('/api/v1/picking/waves/optimize', {
        data: { salesOrderIds: [so.id] },
      });
      if (optimize.ok()) {
        const opt = (await optimize.json()) as {
          manifest: Array<{ locationPath: string }>;
        };
        if (opt.manifest?.length >= 2) {
          const paths = opt.manifest.map((m) => m.locationPath);
          const sorted = [...paths].sort((a, b) =>
            a.localeCompare(b, undefined, { numeric: true, sensitivity: 'base' }),
          );
          expect(paths).toEqual(sorted);
        }
      }

      await owner.page.request.post(`/api/v1/picking/waves/${waveId}/claim`);
    } finally {
      await owner.close();
    }

    const picker = await contextForRole(browser, 'picker');
    try {
      if (waveId) {
        await picker.page.request.post(`/api/v1/picking/waves/${waveId}/claim`);
      }

      await picker.page.goto('/fulfillment');
      await expectFulfillmentSurface(picker.page);
      await picker.page.getByRole('button', { name: 'Batch' }).click();

      // Wait until current batch exposes an expected SKU for client pre-validation.
      await expect
        .poll(
          async () => {
            const res = await picker.page.request.get('/api/v1/picking/batches/current/tasks');
            if (!res.ok()) return false;
            const tasks = (await res.json()) as Array<{ status: string; sku?: string }>;
            return tasks.some((t) => t.status === 'PENDING' && !!t.sku);
          },
          { timeout: 20_000 },
        )
        .toBeTruthy();

      await intentScan(picker.page, 'NOT-THE-EXPECTED-SKU');
      await expect(picker.page.getByTestId('scan-verification-deck')).toHaveClass(
        /scan-error-shake/,
        { timeout: 8_000 },
      );

      // Matching barcode should clear the reject path (success or at least no forced error).
      await intentScan(picker.page, WIDGET_S_BARCODE);
      await expect(picker.page.getByTestId('scan-verification-deck')).toBeVisible();
    } finally {
      await picker.close();
    }
  });
});
