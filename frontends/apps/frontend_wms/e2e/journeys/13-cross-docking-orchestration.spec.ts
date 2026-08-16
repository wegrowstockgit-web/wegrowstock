import { test } from '@playwright/test';
import { installAutoUnlockNavigations } from '../fixtures/roleFixture';
import {
  RESERVE_BIN_PATH,
  STAGING_BARCODE,
  STAGING_PATH,
  STAGING_S01,
  WH_01,
  apiJson,
  contextForRole,
  createZeroStockSellableVariant,
  expect,
  expectFulfillmentSurface,
  firstCustomerId,
  firstSupplierId,
  hidScan,
} from './helpers';

/**
 * Track 13 — Cross-Docking Orchestration & Intercept Matrix
 *
 * Manager (Surface A): backorder SO + replenishment PO.
 * Picker (Surface B, mobile): receive scan intercepts to Z-SHIP/S-01, not reserve put-away.
 * Ledger: RECEIVE @ staging with CROSS_DOCK_ROUTING; SO flips BACKORDERED → ALLOCATED.
 *
 * Uses a fresh zero-OH SKU (seed WIDGET-L is a kit; demo WIDGET-S stock is polluted by prior E2E).
 */
test.describe('Journey 13: Cross-docking orchestration', () => {
  test('inbound receipt bypasses storage when open backorder exists', async ({ browser }) => {
    const manager = await contextForRole(browser, 'manager');

    // Surface B — simulated handheld (isolated mobile context)
    const pickerMobile = await browser.newContext({
      baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:3000',
      viewport: { width: 390, height: 844 },
      isMobile: true,
      hasTouch: true,
    });
    const pickerPage = await pickerMobile.newPage();
    installAutoUnlockNavigations(pickerPage);
    {
      const loginRes = await pickerPage.request.post('/api/v1/auth/login', {
        data: { email: 'picker@demo.test', password: 'password123' },
      });
      expect(loginRes.ok()).toBeTruthy();
      const session = (await loginRes.json()) as {
        userId: string;
        tenantId: string;
        roles: string[];
        warehouseIds?: string[];
      };
      const meRes = await pickerPage.request.get('/api/v1/auth/me');
      const me = meRes.ok()
        ? ((await meRes.json()) as {
            userId: string;
            tenantId: string;
            email: string;
            displayName: string;
            roles: string[];
            warehouseIds?: string[];
          })
        : null;
      await pickerPage.goto('/login');
      await pickerPage.evaluate(
        ({ user }) => {
          localStorage.setItem(
            'invsys-session',
            JSON.stringify({
              state: {
                authenticated: true,
                user,
                lastRequestId: null,
                primarySession: null,
              },
              version: 0,
            }),
          );
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
        },
        {
          user: {
            id: me?.userId ?? session.userId,
            email: me?.email ?? 'picker@demo.test',
            displayName: me?.displayName ?? 'picker',
            roles: me?.roles ?? session.roles ?? [],
            warehouseIds: me?.warehouseIds ?? session.warehouseIds ?? [],
            tenantId: me?.tenantId ?? session.tenantId,
          },
        },
      );
    }

    try {
      const supplierId = await firstSupplierId(manager.page);
      const customerId = await firstCustomerId(manager.page);
      const item = await createZeroStockSellableVariant(manager.page);

      // --- Manager Surface A: Sales Order for high-velocity SKU with 0 OH ---
      await manager.page.goto('/sales-orders');
      await expect(manager.page.getByRole('heading', { name: 'Sales Orders', exact: true })).toBeVisible({
        timeout: 15_000,
      });

      const so = await apiJson<{ id: string; number: string; status: string }>(
        manager.page,
        '/api/v1/sales-orders',
        {
          method: 'POST',
          body: JSON.stringify({
            customerId,
            number: `SO-XD-${Date.now()}`,
            lines: [{ variantId: item.variantId, qtyOrdered: 25, unitPrice: 12.5 }],
          }),
        },
      );
      await manager.page.request.post(`/api/v1/sales-orders/${so.id}/confirm`);
      const allocateRes = await manager.page.request.post(`/api/v1/sales-orders/${so.id}/allocate`);
      expect(allocateRes.ok(), await allocateRes.text()).toBeTruthy();
      const allocated = (await allocateRes.json()) as { status: string };
      expect(allocated.status).toBe('BACKORDERED');

      const soDetail = await apiJson<{
        status: string;
        lines: Array<{ qtyOrdered: number; qtyAllocated?: number }>;
      }>(manager.page, `/api/v1/sales-orders/${so.id}`);
      expect(soDetail.status).toBe('BACKORDERED');
      const lineAllocated = soDetail.lines.reduce(
        (sum, l) => sum + Number(l.qtyAllocated ?? 0),
        0,
      );
      expect(lineAllocated).toBe(0);

      await manager.page.goto('/sales-orders');
      await expect(manager.page.getByText(so.number).first()).toBeVisible({ timeout: 15_000 });
      await expect(manager.page.getByText(/BACKORDERED/i).first()).toBeVisible();

      // --- Manager: PO for 50 units to replenish ---
      await manager.page.goto('/purchase-orders');
      await expect(
        manager.page.getByRole('heading', { name: 'Purchase Orders', exact: true }),
      ).toBeVisible({ timeout: 15_000 });

      const po = await apiJson<{ id: string; number: string; status: string }>(
        manager.page,
        '/api/v1/purchase-orders',
        {
          method: 'POST',
          body: JSON.stringify({
            supplierId,
            number: `PO-XD-${Date.now()}`,
            destinationLocationId: WH_01,
            lines: [{ variantId: item.variantId, qtyOrdered: 50, unitCost: 8 }],
          }),
        },
      );
      const submitRes = await manager.page.request.post(`/api/v1/purchase-orders/${po.id}/submit`);
      expect(submitRes.ok() || submitRes.status() === 409).toBeTruthy();

      await manager.page.goto('/purchase-orders');
      await expect(manager.page.getByText(po.number).first()).toBeVisible({ timeout: 15_000 });

      // --- Receiver Surface B: Receiving Scanner + HID intercept ---
      await pickerPage.goto('/fulfillment');
      await expectFulfillmentSurface(pickerPage);
      await pickerPage.getByRole('radio', { name: 'Receive' }).click();

      // PO barcode (number) then product barcode — product triggers cross-dock overlay
      const poScanSettled = pickerPage.waitForResponse(
        (res) =>
          res.url().includes('/api/v1/fulfillment/scan') && res.request().method() === 'POST',
      );
      await hidScan(pickerPage, po.number);
      await poScanSettled;

      const scanResponsePromise = pickerPage.waitForResponse((res) => {
        if (!res.url().includes('/api/v1/fulfillment/scan') || res.request().method() !== 'POST') {
          return false;
        }
        try {
          const body = res.request().postDataJSON() as { barcode?: string };
          return body?.barcode === item.barcode || body?.barcode === item.sku;
        } catch {
          return false;
        }
      });
      await hidScan(pickerPage, item.barcode);
      const scanRes = await scanResponsePromise;
      expect(scanRes.ok(), await scanRes.text()).toBeTruthy();
      const scanBody = (await scanRes.json()) as {
        crossDock?: boolean;
        stagingPath?: string;
        crossDockInstruction?: string;
        putawayTarget?: string;
        message?: string;
      };
      expect(scanBody.crossDock).toBe(true);
      expect(scanBody.stagingPath ?? scanBody.putawayTarget ?? '').toMatch(/Z-SHIP|S-01|STAGE/i);
      expect(scanBody.crossDockInstruction ?? scanBody.message ?? '').toMatch(/CROSS-DOCK|Staging/i);

      await expect(pickerPage.getByTestId('cross-dock-overlay')).toBeVisible({ timeout: 10_000 });
      await expect(pickerPage.getByTestId('cross-dock-instruction')).toBeVisible();
      await expect(pickerPage.getByTestId('cross-dock-staging-path')).toContainText(/Z-SHIP\/S-01|S-01/);
      await expect(pickerPage.getByTestId('cross-dock-bypass-bin')).toContainText(RESERVE_BIN_PATH);
      await expect(pickerPage.getByTestId('cross-dock-overlay')).not.toContainText(
        new RegExp(`take.*${RESERVE_BIN_PATH}`, 'i'),
      );

      // Confirm staging drop-off via location barcode
      await hidScan(pickerPage, STAGING_BARCODE);
      await expect(pickerPage.getByText('S-01').first()).toBeVisible({ timeout: 8_000 });
      await expect(pickerPage.getByText(/Drop-off confirmed at/i)).toBeAttached({ timeout: 8_000 });

      // Post the physical receive into staging (PO line) — engine routes + fulfills backorder
      const poDetail = await apiJson<{
        lines: Array<{ id: string; variantId: string }>;
      }>(manager.page, `/api/v1/purchase-orders/${po.id}`);
      const poLine = poDetail.lines.find((l) => l.variantId === item.variantId) ?? poDetail.lines[0];
      expect(poLine).toBeTruthy();

      const receiveRes = await pickerPage.request.post(
        `/api/v1/purchase-orders/lines/${poLine!.id}/receive`,
        {
          headers: {
            'Content-Type': 'application/json',
            'X-Warehouse-Id': WH_01,
          },
          data: {
            locationId: WH_01,
            quantity: 50,
          },
        },
      );
      expect(receiveRes.ok(), await receiveRes.text()).toBeTruthy();

      // --- Ledger integrity ---
      const ledger = await apiJson<
        Array<{
          movementType: string;
          reasonCode?: string;
          locationId: string;
          variantId: string;
          referenceId?: string;
        }>
      >(manager.page, '/api/v1/inventory/ledger?limit=80');

      const crossDockReceive = ledger.find(
        (row) =>
          row.variantId === item.variantId &&
          row.movementType === 'RECEIVE' &&
          row.reasonCode === 'CROSS_DOCK_ROUTING' &&
          row.locationId === STAGING_S01,
      );
      expect(
        crossDockReceive,
        `Expected RECEIVE @ ${STAGING_PATH} with CROSS_DOCK_ROUTING. Sample: ${JSON.stringify(ledger.slice(0, 5))}`,
      ).toBeTruthy();

      // --- Manager: SO dynamically becomes ALLOCATED ---
      await expect
        .poll(
          async () => {
            const res = await manager.page.request.get(`/api/v1/sales-orders/${so.id}`);
            if (!res.ok()) return '';
            return ((await res.json()) as { status: string }).status;
          },
          { timeout: 20_000 },
        )
        .toBe('ALLOCATED');

      await manager.page.goto('/sales-orders');
      await expect(manager.page.getByText(so.number).first()).toBeVisible({ timeout: 15_000 });
      await expect
        .poll(async () => {
          const row = manager.page.locator('tr', { hasText: so.number }).first();
          return (await row.textContent()) ?? '';
        }, { timeout: 20_000 })
        .toMatch(/ALLOCATED/i);
    } finally {
      await pickerMobile.close();
      await manager.close();
    }
  });
});
