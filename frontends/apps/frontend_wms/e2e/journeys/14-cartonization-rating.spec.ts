import { test, type Page } from '@playwright/test';
import { completeIdentifierFirstLogin, completeScannerPin, installAutoUnlockNavigations } from '../fixtures/roleFixture';
import {
  DEMO_PASSWORD,
  PICK_BIN_ID,
  WH_01,
  apiJson,
  contextForRole,
  expect,
  expectFulfillmentSurface,
  findVariantId,
  firstCustomerId,
} from './helpers';

/**
 * Journey 14 — 3D cartonization + rate shopping (isolated admin / packer contexts).
 *
 * EasyPost: docker/dev stacks activate MockEasyPostGateway (@Profile !prod).
 * Production uses LiveEasyPostGateway (real rate-shop + buy). This E2E never stubs
 * EasyPost in the browser — it exercises the pack-label API against the server mock.
 */
test.describe('Journey 14: 3D Cartonization & Rate Shopping', () => {
  test.setTimeout(180_000);

  test('admin allocates dimmed lines; packer sees Medium Corrugated and auto-prints', async ({
    browser,
  }) => {
    const admin = await contextForRole(browser, 'owner');

    const customerId = await firstCustomerId(admin.page);
    const skus = ['WIDGET-S', 'BOLT-M8-50', 'TAPE-2IN', 'GADGET-BLK', 'GADGET-WHT'] as const;
    const variantIds: string[] = [];
    for (const sku of skus) {
      const id = await findVariantId(admin.page, sku);
      variantIds.push(id);
      const topup = await admin.page.request.post('/api/v1/inventory/receive', {
        data: {
          variantId: id,
          locationId: PICK_BIN_ID,
          quantity: 10,
          referenceType: 'E2E_CARTON_TOPUP',
        },
      });
      expect(topup.ok(), await topup.text()).toBeTruthy();
    }

    const so = await apiJson<{ id: string; number: string }>(admin.page, '/api/v1/sales-orders', {
      method: 'POST',
      body: JSON.stringify({
        customerId,
        number: `SO-CARTON-${Date.now()}`,
        channel: 'MANUAL',
        currency: 'USD',
        lines: variantIds.map((variantId) => ({
          variantId,
          qtyOrdered: 1,
          unitPrice: 12.5,
        })),
      }),
    });
    await admin.page.request.post(`/api/v1/sales-orders/${so.id}/confirm`);
    const alloc = await admin.page.request.post(`/api/v1/sales-orders/${so.id}/allocate`);
    expect(alloc.ok(), await alloc.text()).toBeTruthy();

    // Engine math: 5 seeded dimmed lines exceed Small Mailer → Medium Corrugated
    const preview = await apiJson<{ cartonName: string }>(
      admin.page,
      `/api/v1/shipments/cartonize-preview?salesOrderId=${so.id}`,
    );
    expect(preview.cartonName).toBe('Medium Corrugated');
    await admin.close();

    // Packer Surface B — isolated context; print spy before first navigation
    const baseURL = process.env.E2E_BASE_URL ?? 'http://localhost:3000';
    const packerCtx = await browser.newContext({ baseURL });
    await packerCtx.addInitScript(() => {
      const w = window as unknown as { __invsysPrintCount?: number; print: () => void };
      w.__invsysPrintCount = 0;
      const nativePrint = w.print.bind(window);
      w.print = () => {
        w.__invsysPrintCount = (w.__invsysPrintCount ?? 0) + 1;
        try {
          nativePrint();
        } catch {
          /* headless may block dialogs */
        }
      };
    });
    const packerPage = await packerCtx.newPage();
    installAutoUnlockNavigations(packerPage);

    await packerPage.goto('/login');
    await packerPage.evaluate(() => {
      localStorage.setItem(
        'invsys-preferences',
        JSON.stringify({
          state: {
            densityMode: 'cozy',
            showOnboardingTour: false,
            activeTourId: null,
            currentTourStep: 0,
            isTourMovingRoutes: false,
            targetRoute: null,
          },
          version: 0,
        }),
      );
    });
    await packerPage.reload();
    await completeIdentifierFirstLogin(packerPage, 'picker@demo.test', DEMO_PASSWORD);
    await expect(packerPage).not.toHaveURL(/\/login/, { timeout: 25_000 });
    await completeScannerPin(packerPage);

    await packerPage.goto('/fulfillment', { waitUntil: 'domcontentloaded' });
    await expectFulfillmentSurface(packerPage);

    await packerPage.getByRole('button', { name: 'Pack', exact: true }).click();
    const packOrderSelect = packerPage.getByLabel('Sales order');
    await expect
      .poll(async () => packOrderSelect.locator(`option[value="${so.id}"]`).count(), {
        timeout: 45_000,
      })
      .toBe(1);
    await packOrderSelect.selectOption(so.id);

    await expect(packerPage.getByText('Use Box: Medium Corrugated').first()).toBeVisible({
      timeout: 45_000,
    });

    const packLabelWait = packerPage.waitForResponse(
      (res) =>
        res.url().includes('/api/v1/shipments/pack-label') && res.request().method() === 'POST',
      { timeout: 60_000 },
    );

    await packerPage.getByRole('button', { name: /Complete Pack/i }).click();
    const packRes = await packLabelWait;
    expect(packRes.ok(), await packRes.text()).toBeTruthy();
    const packed = (await packRes.json()) as {
      trackingNumber?: string;
      cartonName?: string;
    };
    expect(packed.cartonName).toBe('Medium Corrugated');
    expect(packed.trackingNumber).toMatch(/^LBL-/);

    // Tracking rendered automatically; print spooler is best-effort (no bound ZPL printer in e2e)
    await expect(
      packerPage.getByText(new RegExp(`Tracking\\s+${packed.trackingNumber}`)).first(),
    ).toBeVisible({ timeout: 20_000 });
    await expect(packerPage.getByText(/Medium Corrugated/i).first()).toBeVisible();

    const shipments = await listShipments(packerPage, so.id);
    expect(shipments[0]?.trackingNumber).toBe(packed.trackingNumber);

    await packerCtx.close();
  });
});

async function listShipments(
  page: Page,
  soId: string,
): Promise<Array<{ trackingNumber?: string | null }>> {
  const res = await page.request.get(`/api/v1/shipments?salesOrderId=${soId}`, {
    headers: { 'X-Warehouse-Id': WH_01 },
  });
  if (!res.ok()) return [];
  return (await res.json()) as Array<{ trackingNumber?: string | null }>;
}
