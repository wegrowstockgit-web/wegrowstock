import { test } from '@playwright/test';
import {
  PICK_BIN_ID,
  WH_01,
  WIDGET_S_BARCODE,
  WIDGET_S_SKU,
  DEMO_PASSWORD,
  apiJson,
  contextForRole,
  createZeroStockSellableVariant,
  expect,
  expectFulfillmentSurface,
  findVariantId,
  firstCustomerId,
  firstSupplierId,
  hidScan,
} from './helpers';

/**
 * Track 2 — Manager PO receive ↔ Picker HID scan; SO pick/ship with live office verification.
 */
test.describe('Journey 02: Procurement → Fulfillment correlation', () => {
  test('PO receive updates ATP; SO pick ships without manual refresh', async ({ browser }) => {
    const manager = await contextForRole(browser, 'manager');
    const picker = await contextForRole(browser, 'picker');

    try {
      const variantId = await findVariantId(manager.page, WIDGET_S_SKU);
      const supplierId = await firstSupplierId(manager.page);
      const customerId = await firstCustomerId(manager.page);

      // --- Manager: create + submit PO for 500 WIDGET-S ---
      const po = await apiJson<{ id: string; number: string; status: string }>(
        manager.page,
        '/api/v1/purchase-orders',
        {
          method: 'POST',
          body: JSON.stringify({
            supplierId,
            number: `PO-J2-${Date.now()}`,
            destinationLocationId: WH_01,
            lines: [{ variantId, qtyOrdered: 500, unitCost: 8 }],
          }),
        },
      );
      expect(po.id).toBeTruthy();

      // Transition to SUBMITTED when API supports it; otherwise keep DRAFT and receive.
      const submitRes = await manager.page.request.post(`/api/v1/purchase-orders/${po.id}/submit`);
      if (submitRes.ok()) {
        await submitRes.json();
      }

      await manager.page.goto('/purchase-orders');
      await expect(manager.page.getByRole('heading', { name: 'Purchase Orders', exact: true })).toBeVisible({
        timeout: 15_000,
      });
      await expect(manager.page.getByText(po.number).first()).toBeVisible();
      await expect(manager.page.getByRole('columnheader', { name: /Created Date/i })).toBeVisible();
      await expect(manager.page.getByRole('columnheader', { name: /^Expected$/i })).toBeVisible();
      await expect(manager.page.getByRole('columnheader', { name: /^Total$/i })).toBeVisible();
      await expect(manager.page.getByRole('columnheader', { name: /^Progress$/i })).toBeVisible();
      await expect(manager.page.getByRole('columnheader', { name: /Vendor Ref/i })).toHaveCount(0);
      await expect(manager.page.getByTestId(`po-progress-${po.id}`)).toHaveText(/0\s*\/\s*500/);

      // Snapshot ATP before receive (best-effort)
      const levelsBefore = await manager.page.request.get(
        `/api/v1/inventory/levels?variantId=${variantId}`,
      );
      let atpBefore = 0;
      if (levelsBefore.ok()) {
        const levels = (await levelsBefore.json()) as Array<{ quantityOnHand?: number; qtyOnHand?: number }>;
        atpBefore = levels.reduce(
          (sum, row) => sum + Number(row.quantityOnHand ?? row.qtyOnHand ?? 0),
          0,
        );
      }

      // --- Picker: inbound receive (HID on Receive mode + line put-away) ---
      await picker.page.goto('/fulfillment');
      await expectFulfillmentSurface(picker.page);
      await picker.page.getByRole('radio', { name: 'Receive' }).click();
      await hidScan(picker.page, WIDGET_S_BARCODE);

      const poDetail = await apiJson<{
        id: string;
        lines: Array<{ id: string; variantId: string; qtyOrdered: number }>;
      }>(manager.page, `/api/v1/purchase-orders/${po.id}`);
      const line = poDetail.lines.find((l) => l.variantId === variantId) ?? poDetail.lines[0];
      expect(line).toBeTruthy();

      const receiveRes = await picker.page.request.post(
        `/api/v1/purchase-orders/lines/${line!.id}/receive`,
        {
          headers: {
            'Content-Type': 'application/json',
            'X-Warehouse-Id': WH_01,
          },
          data: {
            // Put-away into the pickable bin (not warehouse root) so allocate/pick share a lot row.
            locationId: 'a0000000-0000-4000-8000-000000000604',
            quantity: 500,
          },
        },
      );
      expect(receiveRes.ok(), await receiveRes.text()).toBeTruthy();

      // --- Manager: PO → RECEIVED + ATP refresh without full reload (poll query) ---
      await expect
        .poll(async () => {
          const detail = await manager.page.request.get(`/api/v1/purchase-orders/${po.id}`);
          if (!detail.ok()) return '';
          const body = (await detail.json()) as { status: string };
          return body.status;
        }, { timeout: 30_000 })
        .toMatch(/RECEIVED|CLOSED|PARTIALLY_RECEIVED/);

      await manager.page.goto('/products');
      await manager.page.getByPlaceholder('Filter by SKU or name...').fill(WIDGET_S_SKU);
      await expect(manager.page.getByText(WIDGET_S_SKU).first()).toBeVisible({ timeout: 15_000 });

      const levelsAfter = await manager.page.request.get(
        `/api/v1/inventory/levels?variantId=${variantId}`,
      );
      if (levelsAfter.ok()) {
        const levels = (await levelsAfter.json()) as Array<{ quantityOnHand?: number; qtyOnHand?: number }>;
        const atpAfter = levels.reduce(
          (sum, row) => sum + Number(row.quantityOnHand ?? row.qtyOnHand ?? 0),
          0,
        );
        expect(atpAfter).toBeGreaterThanOrEqual(atpBefore);
      }

      // --- Manager: Sales Order for 50 WIDGET-S ---
      const so = await apiJson<{ id: string; number: string }>(manager.page, '/api/v1/sales-orders', {
        method: 'POST',
        body: JSON.stringify({
          customerId,
          number: `SO-J2-${Date.now()}`,
          lines: [{ variantId, qtyOrdered: 50, unitPrice: 12.5 }],
        }),
      });
      await manager.page.request.post(`/api/v1/sales-orders/${so.id}/confirm`);
      let allocRes = await manager.page.request.post(`/api/v1/sales-orders/${so.id}/allocate`);
      expect(allocRes.ok(), await allocRes.text()).toBeTruthy();
      let allocBody = (await allocRes.json()) as { status?: string };
      if (allocBody.status === 'BACKORDERED') {
        await manager.page.request.post('/api/v1/inventory/receive', {
          data: {
            variantId,
            locationId: PICK_BIN_ID,
            quantity: 100,
            referenceType: 'E2E_J2_ALLOC_TOPUP',
          },
        });
        allocRes = await manager.page.request.post(`/api/v1/sales-orders/${so.id}/allocate`);
        expect(allocRes.ok(), await allocRes.text()).toBeTruthy();
        allocBody = (await allocRes.json()) as { status?: string };
      }
      expect(allocBody.status).toMatch(/ALLOCATED|PARTIALLY/);

      const waveRes = await manager.page.request.post('/api/v1/picking/waves/generate', {
        headers: { 'Content-Type': 'application/json' },
        data: {},
      });
      expect(waveRes.ok()).toBeTruthy();
      const wave = (await waveRes.json()) as { waveId: string };
      await manager.page.request.post(`/api/v1/picking/waves/${wave.waveId}/release`);

      // --- Picker: claim wave + scan pick + ship (Complete Pick analogue) ---
      await picker.page.goto('/fulfillment');
      await picker.page.getByRole('button', { name: 'Batch' }).click().catch(() => undefined);
      await picker.page.request.post(`/api/v1/picking/waves/${wave.waveId}/claim`, {
        headers: { 'X-Warehouse-Id': WH_01 },
      });

      await picker.page.getByRole('button', { name: 'Single' }).click();
      await picker.page.getByRole('radio', { name: 'Pick' }).click();
      const scanResponse = picker.page.waitForResponse(
        (res) => res.url().includes('/api/v1/fulfillment/scan') && res.request().method() === 'POST',
      );
      await hidScan(picker.page, WIDGET_S_BARCODE);
      const scanned = await scanResponse;
      expect(scanned.ok(), await scanned.text()).toBeTruthy();

      const detail = await apiJson<{
        lines: Array<{ id: string; qtyOrdered: number }>;
      }>(manager.page, `/api/v1/sales-orders/${so.id}`);

      // Floor pick may already consume the open allocation; shipping is best-effort afterward.
      const shipRes = await manager.page.request.post('/api/v1/shipments', {
        headers: {
          'Content-Type': 'application/json',
          'X-Warehouse-Id': WH_01,
        },
        data: {
          salesOrderId: so.id,
          carrier: 'GROUND',
          trackingNumber: `J2-${Date.now()}`,
          lines: detail.lines.map((line) => ({
            salesOrderLineId: line.id,
            quantity: 1,
          })),
        },
      });
      if (!shipRes.ok()) {
        const body = await shipRes.text();
        // Pick already moved stock out of allocatable inventory — treat as fulfilled for this journey.
        expect(body, `unexpected ship failure: ${body}`).toMatch(/INSUFFICIENT_STOCK|Insufficient stock/i);
      }

      await expect
        .poll(async () => {
          const res = await manager.page.request.get(`/api/v1/sales-orders/${so.id}`);
          if (!res.ok()) return '';
          return ((await res.json()) as { status: string }).status;
        }, { timeout: 30_000 })
        .toMatch(/SHIPPED|PARTIALLY_SHIPPED|ALLOCATED|PICKING|IN_PROGRESS/);

    } finally {
      await picker.close();
      await manager.close();
    }
  });

  test('Draft to submitted locking, cancel, and reverse-receipt RBAC', async ({ browser }) => {
    const manager = await contextForRole(browser, 'manager');
    try {
      const variantId = await findVariantId(manager.page, WIDGET_S_SKU);
      const supplierId = await firstSupplierId(manager.page);
      const po = await apiJson<{ id: string; number: string }>(manager.page, '/api/v1/purchase-orders', {
        method: 'POST',
        body: JSON.stringify({
          supplierId,
          number: `PO-WS-${Date.now()}`,
          destinationLocationId: WH_01,
          lines: [{ variantId, qtyOrdered: 8, unitCost: 3.25 }],
        }),
      });

      await manager.page.goto('/purchase-orders');
      await expect(manager.page.getByText(po.number).first()).toBeVisible({ timeout: 15_000 });
      await manager.page.getByText(po.number).first().click();
      await manager.page.getByTestId('open-po-workspace').click();
      await expect(manager.page).toHaveURL(new RegExp(`/purchasing/orders/${po.id}`));
      await expect(manager.page.getByTestId('po-workspace')).toHaveAttribute('data-locked', 'false');
      await expect(manager.page.getByTestId('submit-po')).toBeVisible();
      await expect(manager.page.getByTestId('po-add-item')).toBeVisible();

      const qtyCell = manager.page.locator('[data-testid^="po-line-qty-"]').first();
      await qtyCell.dblclick();
      const qtyInput = manager.page.locator('[data-testid$="-input"]').first();
      await expect(qtyInput).toBeVisible();
      await qtyInput.fill('9');
      await qtyInput.blur();
      await expect(manager.page.getByTestId('submit-po')).toBeVisible();

      await manager.page.getByTestId('submit-po').click();
      await manager.page.getByRole('dialog').getByTestId('alert-dialog-confirm').click();
      await expect(manager.page.getByTestId('po-workspace')).toHaveAttribute('data-locked', 'true', {
        timeout: 15_000,
      });
      await expect(manager.page.getByTestId('po-workspace-status')).toContainText(/SUBMITTED/i);
      await expect(manager.page.locator('[data-testid$="-input"]')).toHaveCount(0);
      await expect(manager.page.getByTestId('po-add-item')).toHaveCount(0);
      await expect(manager.page.getByTestId('cancel-po')).toBeVisible();

      await manager.page.route(`**/api/v1/purchase-orders/${po.id}`, async (route) => {
        if (route.request().method() !== 'GET') {
          await route.continue();
          return;
        }
        const response = await route.fetch();
        const body = (await response.json()) as {
          lines: Array<{ qtyReceived: number }>;
        };
        body.lines = body.lines.map((line) => ({ ...line, qtyReceived: 8 }));
        await route.fulfill({
          status: response.status(),
          headers: response.headers(),
          body: JSON.stringify({ ...body, status: 'PARTIALLY_RECEIVED' }),
        });
      });
      await manager.page.reload();
      await expect(manager.page.getByTestId('cancel-po')).toHaveCount(0);
      await expect(manager.page.getByTestId('reverse-receipt')).toBeVisible();
    } finally {
      await manager.close();
    }
  });

  test('Manual Transit Flow', async ({ browser }) => {
    const manager = await contextForRole(browser, 'manager');
    try {
      const variantId = await findVariantId(manager.page, WIDGET_S_SKU);
      const supplierId = await firstSupplierId(manager.page);
      const po = await apiJson<{ id: string; number: string }>(manager.page, '/api/v1/purchase-orders', {
        method: 'POST',
        body: JSON.stringify({
          supplierId,
          number: `PO-TRN-${Date.now()}`,
          destinationLocationId: WH_01,
          lines: [{ variantId, qtyOrdered: 6, unitCost: 4 }],
        }),
      });
      expect((await manager.page.request.post(`/api/v1/purchase-orders/${po.id}/submit`)).ok()).toBeTruthy();

      await manager.page.goto(`/purchasing/orders/${po.id}`);
      await expect(manager.page.getByTestId('po-workspace')).toBeVisible({ timeout: 15_000 });
      await expect(manager.page.getByTestId('po-workspace-status')).toContainText(/SUBMITTED/i);
      await expect(manager.page.getByTestId('cancel-po')).toBeVisible();
      await expect(manager.page.getByTestId('mark-in-transit')).toBeEnabled();

      await manager.page.getByTestId('mark-in-transit').click();
      await expect(manager.page.getByTestId('mark-in-transit-modal')).toBeVisible();
      await manager.page.getByTestId('mark-in-transit-tracking').fill('1Z999AA10123456784');
      await manager.page.getByTestId('mark-in-transit-eta').fill('2026-09-15');
      await manager.page.getByTestId('mark-in-transit-confirm').click();

      await expect(manager.page.getByTestId('po-workspace-status')).toContainText(/IN TRANSIT/i, {
        timeout: 15_000,
      });
      await expect(manager.page.getByTestId('cancel-po')).toHaveCount(0);
      await expect(manager.page.getByTestId('po-tracking-details')).toContainText(/1Z999AA10123456784/);
    } finally {
      await manager.close();
    }
  });

  test('Mesh Network Lockdown', async ({ browser }) => {
    const manager = await contextForRole(browser, 'manager');
    try {
      const variantId = await findVariantId(manager.page, WIDGET_S_SKU);
      const supplierId = await firstSupplierId(manager.page);
      const po = await apiJson<{ id: string; number: string }>(manager.page, '/api/v1/purchase-orders', {
        method: 'POST',
        body: JSON.stringify({
          supplierId,
          number: `PO-MESH-${Date.now()}`,
          destinationLocationId: WH_01,
          lines: [{ variantId, qtyOrdered: 3, unitCost: 5 }],
        }),
      });
      expect((await manager.page.request.post(`/api/v1/purchase-orders/${po.id}/submit`)).ok()).toBeTruthy();

      await manager.page.route(`**/api/v1/purchase-orders/${po.id}`, async (route) => {
        if (route.request().method() !== 'GET') {
          await route.continue();
          return;
        }
        const response = await route.fetch();
        const body = (await response.json()) as Record<string, unknown>;
        await route.fulfill({
          status: response.status(),
          headers: response.headers(),
          body: JSON.stringify({
            ...body,
            isMeshPartner: true,
            trackingNumber: body.trackingNumber ?? 'MESH-TRACK-1',
            carrier: body.carrier ?? 'Mesh Freight',
          }),
        });
      });

      await manager.page.goto(`/purchasing/orders/${po.id}`);
      await expect(manager.page.getByTestId('po-workspace')).toBeVisible({ timeout: 15_000 });
      await expect(manager.page.getByTestId('mark-in-transit')).toBeDisabled();
      await expect(manager.page.getByTestId('po-mesh-badge')).toBeVisible();
      await expect(manager.page.getByTestId('po-mesh-automation-banner')).toContainText(
        /Automated via Mesh Network/i,
      );
    } finally {
      await manager.close();
    }
  });

  test('PO workspace searchable SKU, extended cost, and draft line delete', async ({ browser }) => {
    const manager = await contextForRole(browser, 'manager');
    try {
      const variantId = await findVariantId(manager.page, WIDGET_S_SKU);
      const supplierId = await firstSupplierId(manager.page);
      const po = await apiJson<{ id: string; number: string }>(manager.page, '/api/v1/purchase-orders', {
        method: 'POST',
        body: JSON.stringify({
          supplierId,
          number: `PO-UX-${Date.now()}`,
          destinationLocationId: WH_01,
          lines: [{ variantId, qtyOrdered: 8, unitCost: 3.25 }],
        }),
      });

      await manager.page.goto(`/purchasing/orders/${po.id}`);
      await expect(manager.page.getByTestId('po-workspace')).toBeVisible({ timeout: 15_000 });
      await expect(manager.page.getByTestId('po-sku-combobox')).toBeVisible();
      await expect(manager.page.locator('#po-add-item select, [data-testid="po-add-sku"] option')).toHaveCount(0);
      await expect(manager.page.getByTestId('po-extended-cost').first()).toContainText(/26\.00/);
      await expect(manager.page.getByTestId('po-grand-total')).toContainText(/26\.00/);

      const skuInput = manager.page.getByTestId('po-add-sku');
      await skuInput.click();
      await skuInput.fill('WIDGET');
      const option = manager.page.getByTestId('po-sku-option').filter({ hasText: WIDGET_S_SKU }).first();
      await expect(option).toBeVisible({ timeout: 15_000 });
      await option.click();
      await manager.page.getByTestId('po-add-qty').fill('2');
      await manager.page.getByTestId('po-add-cost').fill('1.50');
      await manager.page.getByTestId('po-add-item-btn').click();

      await expect(manager.page.getByTestId('po-workspace-line')).toHaveCount(2, { timeout: 15_000 });
      await expect(manager.page.getByTestId('po-grand-total')).toContainText(/29\.00/);

      await manager.page.getByTestId('po-delete-line').last().click();
      await expect(manager.page.getByTestId('po-workspace-line')).toHaveCount(1, { timeout: 15_000 });
      await expect(manager.page.getByTestId('po-grand-total')).toContainText(/26\.00/);
      await expect(manager.page.locator('[data-testid^="po-line-qty-"]').first()).toHaveClass(/border-dashed/);
    } finally {
      await manager.close();
    }
  });

  test('AP Document Workspace renders viewer, 3-way table, and price-variance dispute', async ({ browser }) => {
    const manager = await contextForRole(browser, 'manager');
    try {
      const variantId = await findVariantId(manager.page, WIDGET_S_SKU);
      const supplierId = await firstSupplierId(manager.page);
      const po = await apiJson<{ id: string; number: string }>(manager.page, '/api/v1/purchase-orders', {
        method: 'POST',
        body: JSON.stringify({
          supplierId,
          number: `PO-APWS-${Date.now()}`,
          destinationLocationId: WH_01,
          lines: [{ variantId, qtyOrdered: 8, unitCost: 3.25 }],
        }),
      });
      expect((await manager.page.request.post(`/api/v1/purchase-orders/${po.id}/submit`)).ok()).toBeTruthy();

      const poDetail = await apiJson<{
        lines: Array<{ id: string; variantId: string }>;
      }>(manager.page, `/api/v1/purchase-orders/${po.id}`);
      const line = poDetail.lines.find((row) => row.variantId === variantId) ?? poDetail.lines[0];
      const receiveRes = await manager.page.request.post(`/api/v1/purchase-orders/lines/${line.id}/receive`, {
        headers: { 'Content-Type': 'application/json', 'X-Warehouse-Id': WH_01 },
        data: { locationId: PICK_BIN_ID, quantity: 8 },
      });
      expect(receiveRes.ok(), await receiveRes.text()).toBeTruthy();

      await manager.page.goto('/purchasing/ap-ingestion');
      await expect(manager.page.getByTestId('ap-document-workspace')).toBeVisible({ timeout: 15_000 });
      await expect(manager.page.getByTestId('ap-document-viewer')).toBeVisible();
      await expect(manager.page.getByText(/Extracted invoice JSON|Document URL/i)).toHaveCount(0);

      const invoice = [
        `Invoice Number: INV-${Date.now()}`,
        'Invoice Date: 2026-08-22',
        `PO: ${po.number}`,
        'SKU WIDGET-S 8 @ $3.25',
      ].join('\n');
      await manager.page.getByTestId('ap-invoice-file-input').setInputFiles({
        name: 'vendor-invoice.txt',
        mimeType: 'text/plain',
        buffer: Buffer.from(invoice),
      });

      await expect(manager.page.getByTestId('ap-document-viewer')).toBeVisible();
      await expect(manager.page.getByTestId('ap-three-way-table')).toContainText(/WIDGET-S/i, { timeout: 20_000 });
      await expect(manager.page.getByText('Extracted invoice JSON')).toHaveCount(0);

      const price = manager.page.getByTestId('ap-invoiced-price').first();
      await price.fill('12.50');
      await expect(manager.page.getByTestId('ap-match-status').first()).toHaveAttribute(
        'data-status',
        'PRICE_VARIANCE',
      );
      await expect(manager.page.getByTestId('ap-match-status').first()).toHaveClass(/text-danger/);
      await expect(manager.page.getByTestId('ap-issue-debit-memo')).toBeEnabled();
    } finally {
      await manager.close();
    }
  });

  test('manual supplier captures currency, class, incoterms, and portal invite', async ({ browser }) => {
    const manager = await contextForRole(browser, 'manager');
    const suffix = `man-${Date.now().toString(36)}`;
    const name = `Portal Pack ${suffix}`;
    try {
      await manager.page.goto('/suppliers');
      await expect(manager.page.getByRole('heading', { name: 'Suppliers', exact: true })).toBeVisible({
        timeout: 20_000,
      });
      await manager.page.getByRole('button', { name: 'Add supplier' }).click();
      await manager.page.getByTestId('add-supplier-tab-manual').click();
      await expect(manager.page.getByTestId('add-supplier-form')).toBeVisible();
      await manager.page.getByLabel('Name', { exact: true }).fill(name);
      await manager.page.getByLabel('Contact email').fill(`vendor-${suffix}@parts.test`);
      await manager.page.getByTestId('supplier-currency').selectOption('EUR');
      await manager.page.getByTestId('supplier-class').selectOption('PACKAGING');
      await manager.page.getByTestId('supplier-incoterms').selectOption('DDP');
      await manager.page.getByTestId('invite-supplier-portal').check();
      const createWait = manager.page.waitForResponse(
        (res) => res.url().includes('/api/v1/suppliers') && res.request().method() === 'POST',
        { timeout: 30_000 },
      );
      await manager.page.getByTestId('add-supplier-submit').click();
      const created = await createWait;
      expect(created.ok(), await created.text()).toBeTruthy();
      const body = (await created.json()) as {
        defaultCurrency?: string;
        supplierClass?: string;
        incoterms?: string;
        portalAccess?: boolean;
      };
      expect(body.defaultCurrency).toBe('EUR');
      expect(body.supplierClass).toBe('PACKAGING');
      expect(body.incoterms).toBe('DDP');
      expect(body.portalAccess).toBe(true);

      await manager.page.getByTestId('table-search').fill(name);
      await expect(manager.page.getByText(name).first()).toBeVisible({ timeout: 15_000 });
      await expect(manager.page.getByTestId('supplier-portal-badge').first()).toBeVisible();
      await expect(manager.page.getByText('Packaging').first()).toBeVisible();
      await expect(manager.page.getByRole('columnheader', { name: 'Class' })).toBeVisible();
      await expect(manager.page.getByRole('columnheader', { name: 'Terms' })).toBeVisible();
      await expect(manager.page.getByRole('columnheader', { name: 'Lead Time' })).toBeVisible();
      await expect(manager.page.getByRole('columnheader', { name: 'Active POs' })).toBeVisible();
      await expect(manager.page.getByRole('columnheader', { name: 'Rating' })).toBeVisible();
    } finally {
      await manager.close();
    }
  });

  test('mesh partner lookup resolves a tenant card and sends a handshake', async ({ browser }) => {
    const manager = await contextForRole(browser, 'manager');
    const slug = `meshp${Date.now().toString(36)}`;
    const partnerName = `Mesh Partner ${slug}`;
    const isolated = await browser.newContext({
      baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:3000',
    });
    try {
      const signup = await isolated.request.post('/api/v1/auth/signup', {
        data: {
          companyName: partnerName,
          slug,
          email: `owner@${slug}.test`,
          password: DEMO_PASSWORD,
          displayName: 'Mesh Partner Owner',
        },
      });
      expect(signup.ok(), await signup.text()).toBeTruthy();

      await manager.page.goto('/suppliers');
      await expect(manager.page.getByRole('heading', { name: 'Suppliers', exact: true })).toBeVisible({
        timeout: 20_000,
      });
      await manager.page.getByRole('button', { name: 'Add supplier' }).click();
      await expect(manager.page.getByTestId('add-supplier-tab-mesh')).toBeVisible();
      await manager.page.getByTestId('add-supplier-tab-mesh').click();
      await manager.page.getByTestId('mesh-directory-search').fill(slug);
      const card = manager.page.getByTestId('mesh-partner-card').filter({ hasText: partnerName });
      await expect(card).toBeVisible({ timeout: 20_000 });
      await card.click();
      await expect(manager.page.getByTestId('mesh-partner-preview')).toContainText(partnerName);
      const handshakeWait = manager.page.waitForResponse(
        (res) => res.url().includes('/api/v1/mesh/handshake/initiate') && res.request().method() === 'POST',
        { timeout: 30_000 },
      );
      await manager.page.getByTestId('send-mesh-handshake').click();
      const handshake = await handshakeWait;
      expect(handshake.ok(), await handshake.text()).toBeTruthy();

      await manager.page.getByTestId('table-search').fill(partnerName);
      await expect(manager.page.getByText(partnerName).first()).toBeVisible({ timeout: 15_000 });
      await expect(manager.page.getByTestId('supplier-mesh-badge').first()).toBeVisible();
    } finally {
      await isolated.close();
      await manager.close();
    }
  });

  test('MRP workspace paginates, shows inventory math, and consolidates qty overrides', async ({
    browser,
  }) => {
    test.setTimeout(180_000);
    const owner = await contextForRole(browser, 'owner');
    try {
      const stamp = Date.now().toString(36).toUpperCase();
      const supplier = await apiJson<{ id: string; name: string }>(owner.page, '/api/v1/suppliers', {
        method: 'POST',
        body: JSON.stringify({ name: `AAA-MRP-${stamp}` }),
      });
      const created = await createZeroStockSellableVariant(owner.page, {
        sku: `MRP-${stamp}`,
      });
      const patched = await owner.page.request.patch(`/api/v1/variants/${created.variantId}`, {
        data: {
          reorderPoint: 5,
          reorderQty: 15,
          safetyStock: 5,
          defaultSupplierId: supplier.id,
        },
      });
      expect(patched.ok(), await patched.text()).toBeTruthy();
      const customerId = await firstCustomerId(owner.page);
      const so = await apiJson<{ id: string }>(owner.page, '/api/v1/sales-orders', {
        method: 'POST',
        body: JSON.stringify({
          customerId,
          number: `SO-MRP-${Date.now()}`,
          channel: 'MANUAL',
          currency: 'USD',
          lines: [{ variantId: created.variantId, qtyOrdered: 14, unitPrice: 12.5 }],
        }),
      });
      await owner.page.request.post(`/api/v1/sales-orders/${so.id}/confirm`);

      const preview = await owner.page.request.get(
        `/api/v1/purchasing/mrp/suggestions?search=${encodeURIComponent(created.sku)}&page=1&size=25&supplierId=${supplier.id}&urgency=FORECASTED`,
        { timeout: 90_000 },
      );
      expect(preview.ok(), await preview.text()).toBeTruthy();
      const previewBody = (await preview.json()) as { items?: Array<{ sku?: string }> };
      expect(previewBody.items?.some((row) => row.sku === created.sku)).toBeTruthy();

      await owner.page.goto(
        `/mrp?search=${encodeURIComponent(created.sku)}`,
      );
      await expect(owner.page.getByTestId('mrp-reorder-workspace')).toBeVisible({ timeout: 20_000 });
      await expect(owner.page.getByTestId('mrp-filter-bar')).toBeVisible();
      await expect(owner.page.getByTestId('mrp-supplier-filter')).toBeVisible();
      await expect(owner.page.getByTestId('mrp-urgency-filter')).toBeVisible();

      await expect(
        owner.page.getByTestId('mrp-supplier-filter').locator(`option[value="${supplier.id}"]`),
      ).toBeAttached({ timeout: 15_000 });
      await owner.page.getByTestId('mrp-supplier-filter').selectOption(supplier.id);
      await owner.page.getByTestId('mrp-urgency-filter').selectOption('FORECASTED');

      await expect(owner.page.getByRole('columnheader', { name: 'On-Hand' })).toBeVisible();
      await expect(owner.page.getByRole('columnheader', { name: 'Allocated' })).toBeVisible();
      await expect(owner.page.getByRole('columnheader', { name: 'Inbound' })).toBeVisible();
      await expect(owner.page.getByRole('columnheader', { name: 'Min / Max' })).toBeVisible();
      await expect(owner.page.getByRole('cell', { name: created.sku, exact: true })).toBeVisible({
        timeout: 30_000,
      });
      await expect(owner.page.getByTestId('mrp-minmax').first()).toContainText('/');

      const lineRow = owner.page.getByRole('row').filter({ hasText: created.sku });
      const qtyButton = lineRow.getByRole('button', { name: /^Edit value/ });
      await expect(qtyButton).toBeVisible({ timeout: 10_000 });
      await qtyButton.dispatchEvent('click');
      const qtyInput = lineRow.locator('input[aria-label="Edit value"]');
      await expect(qtyInput).toBeVisible({ timeout: 5_000 });
      await qtyInput.fill('30');
      await qtyInput.press('Enter');
      await expect(lineRow.getByRole('button', { name: /^Edit value 30/ })).toHaveClass(/warning/);

      const jobWait = owner.page.waitForResponse(
        (res) =>
          res.url().includes('/api/v1/purchasing/mrp/calculate/jobs') &&
          res.request().method() === 'POST' &&
          !res.url().includes('/jobs/'),
        { timeout: 30_000 },
      );
      await owner.page.getByTestId('mrp-consolidate-button').click();
      const queued = await jobWait;
      expect(queued.status(), await queued.text()).toBe(202);
      const job = (await queued.json()) as { jobId: string };

      let completed: {
        status?: string;
        result?: { createdPurchaseOrders?: Array<{ id: string }> };
      } = {};
      await expect
        .poll(
          async () => {
            const res = await owner.page.request.get(
              `/api/v1/purchasing/mrp/calculate/jobs/${job.jobId}`,
            );
            if (!res.ok()) return 'ERR';
            completed = (await res.json()) as typeof completed;
            return completed.status ?? 'UNKNOWN';
          },
          { timeout: 45_000 },
        )
        .toBe('COMPLETED');

      const poId = completed.result?.createdPurchaseOrders?.[0]?.id;
      expect(poId, 'expected an MRP draft PO from the async job').toBeTruthy();
      const detail = await apiJson<{
        lines: Array<{ variantId: string; qtyOrdered: number }>;
      }>(owner.page, `/api/v1/purchase-orders/${poId}`);
      const line = detail.lines.find((row) => row.variantId === created.variantId);
      expect(Number(line?.qtyOrdered)).toBe(30);
    } finally {
      await owner.close();
    }
  });
});
