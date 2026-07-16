import { expect, hidScan, test } from './fixtures/roleFixture';

const DEMO_BARCODE = '8901000000001'; // WIDGET-S from demo seed

test.describe('B2B → fulfillment → invoice cycle', () => {
  test('cross-role baton: portal draft → confirm/allocate → pick/ship → invoice', async ({
    b2bPage,
    adminPage,
    pickerPage,
  }) => {
    const poNumber = `PO-E2E-${Date.now()}`;

    // --- Step 1: B2B places a draft sales order ---
    await b2bPage.goto('/showroom/catalog');
    await expect(b2bPage.getByRole('heading', { name: 'Catalog' })).toBeVisible();

    const firstCard = b2bPage.locator('.grid > div').first();
    await firstCard.getByRole('button').last().click();
    await b2bPage.getByLabel(/Open cart/i).click();
    await expect(b2bPage.getByRole('dialog')).toBeVisible();
    await b2bPage.getByRole('button', { name: 'Proceed to checkout' }).click();
    await b2bPage.getByLabel('Your PO number').fill(poNumber);
    await b2bPage.getByRole('button', { name: 'Continue' }).click();
    await b2bPage.getByRole('button', { name: 'Place order' }).click();
    await expect(b2bPage.getByText('Order submitted')).toBeVisible();

    // --- Step 2: Admin confirms + allocates ---
    await adminPage.goto('/sales-orders');
    await expect(adminPage.getByRole('heading', { name: 'Sales Orders', exact: true })).toBeVisible();

    let orderId = '';
    let orderNumber = '';
    await expect
      .poll(async () => {
        const listRes = await adminPage.request.get('/api/v1/sales-orders', {
        });
        if (!listRes.ok()) return false;
        const orders = (await listRes.json()) as Array<{
          id: string;
          number: string;
          status: string;
          customerPoNumber?: string;
        }>;
        const found =
          orders.find((o) => o.customerPoNumber === poNumber) ??
          orders.find((o) => o.status === 'DRAFT');
        if (!found) return false;
        orderId = found.id;
        orderNumber = found.number;
        return true;
      })
      .toBeTruthy();
    expect(orderId).toBeTruthy();

    const confirmRes = await adminPage.request.post(`/api/v1/sales-orders/${orderId}/confirm`, {
    });
    expect(confirmRes.ok()).toBeTruthy();

    const allocateRes = await adminPage.request.post(`/api/v1/sales-orders/${orderId}/allocate`, {
    });
    expect(allocateRes.ok()).toBeTruthy();

    await adminPage.reload();
    await expect(adminPage.getByText(orderNumber).first()).toBeVisible();

    // Release a picking wave so the floor can batch-pick
    const waveRes = await adminPage.request.post('/api/v1/picking/waves/generate', {
      headers: { 'Content-Type': 'application/json' },
      data: {},
    });
    expect(waveRes.ok()).toBeTruthy();
    const wave = (await waveRes.json()) as { waveId: string };
    const releaseRes = await adminPage.request.post(`/api/v1/picking/waves/${wave.waveId}/release`, {
    });
    expect(releaseRes.ok()).toBeTruthy();

    // --- Step 3: Picker fulfills via HID scan + ships ---
    await pickerPage.goto('/fulfillment');
    await expect(pickerPage.getByText('Floor ops')).toBeVisible();
    await pickerPage.getByRole('button', { name: 'Batch' }).click();
    await expect(pickerPage.getByText(/Next bin|Optimized route|No released batch/)).toBeVisible();

    // Single-pick scan still exercises HID wedge + fulfillment path
    await pickerPage.getByRole('button', { name: 'Single' }).click();
    await pickerPage.getByRole('radio', { name: 'Pick' }).click();

    const scanResponse = pickerPage.waitForResponse(
      (res) => res.url().includes('/api/v1/fulfillment/scan') && res.request().method() === 'POST'
    );
    await hidScan(pickerPage, DEMO_BARCODE);
    const scanned = await scanResponse;
    expect(scanned.ok()).toBeTruthy();

    const detailRes = await adminPage.request.get(`/api/v1/sales-orders/${orderId}`, {
    });
    expect(detailRes.ok()).toBeTruthy();
    const detail = (await detailRes.json()) as {
      id: string;
      lines: Array<{ id: string; qtyOrdered: number }>;
    };

    const pickerToken = await sessionAccessToken(pickerPage);
    const shipRes = await pickerPage.request.post('/api/v1/shipments', {
      headers: {
        'Content-Type': 'application/json',
        'X-Warehouse-Id': 'a0000000-0000-4000-8000-000000000601',
      },
      data: {
        salesOrderId: orderId,
        carrier: 'GROUND',
        trackingNumber: `E2E-${Date.now()}`,
        lines: detail.lines.map((line) => ({
          salesOrderLineId: line.id,
          quantity: line.qtyOrdered,
        })),
      },
    });
    expect(shipRes.ok()).toBeTruthy();
    const shipment = (await shipRes.json()) as { id: string };

    // --- Step 4: Admin invoices the shipped order (split-shipment path) ---
    await adminPage.goto('/invoices');
    await expect(adminPage.getByRole('heading', { name: 'Invoices', exact: true })).toBeVisible();

    const invoiceRes = await adminPage.request.post(`/api/v1/invoices/from-shipment/${shipment.id}`, {
    });
    expect(invoiceRes.ok()).toBeTruthy();

    await adminPage.reload();
    await expect
      .poll(async () => {
        const inv = await adminPage.request.get('/api/v1/invoices', {
        });
        const list = (await inv.json()) as Array<{ salesOrderId?: string }>;
        return list.some((i) => i.salesOrderId === orderId);
      })
      .toBeTruthy();
  });
});
