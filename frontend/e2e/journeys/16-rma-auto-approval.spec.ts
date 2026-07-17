import fs from 'node:fs';
import path from 'node:path';
import { test } from '@playwright/test';
import {
  CLIENT_A_METRO_ID,
  WIDGET_S_SKU,
  apiJson,
  contextForRole,
  createShippedSalesOrder,
  expect,
  findVariantId,
} from './helpers';

const FIXTURE_PNG = path.join(process.cwd(), 'e2e', 'fixtures', 'pixel.png');

/**
 * Track 16 — Self-serve Auto-RMA: low-value auto-approve vs DAMAGED pending review.
 * Estimates use EasyPostGateway.estimateCheapestRate (rate-only). Docker/dev = MockEasyPostGateway.
 */
test.describe.serial('Journey 16: Customer Self-Serve Auto-RMA Rules', () => {
  test('auto-approve $50 return; damaged → pending → approve without label', async ({
    browser,
  }) => {
    test.setTimeout(180_000);
    const manager = await contextForRole(browser, 'manager');
    const b2b = await contextForRole(browser, 'b2b');

    try {
      const variantId = await findVariantId(manager.page, WIDGET_S_SKU);

      const cheap = await createShippedSalesOrder(manager.page, {
        variantId,
        customerId: CLIENT_A_METRO_ID,
        quantity: 1,
        unitPrice: 50,
        numberPrefix: 'SO-RMA-LO',
      });

      await b2b.page.goto('/showroom/orders');
      await expect
        .poll(async () => {
          const res = await b2b.page.request.get('/api/v1/portal/orders');
          if (!res.ok()) return false;
          return ((await res.json()) as Array<{ number: string }>).some(
            (o) => o.number === cheap.number,
          );
        }, { timeout: 20_000 })
        .toBeTruthy();
      await b2b.page.reload();
      await expect(b2b.page.getByText(cheap.number).first()).toBeVisible({ timeout: 15_000 });
      await b2b.page
        .getByRole('row', { name: new RegExp(cheap.number) })
        .getByRole('button', { name: /Return Items/i })
        .click();
      await expect(b2b.page.getByTestId('returns-wizard')).toBeVisible();
      await b2b.page.getByLabel(/Qty/i).first().fill('1');
      await b2b.page.getByRole('button', { name: 'Continue' }).click();
      await b2b.page.getByLabel('Return reason').selectOption('CHANGED_MIND');
      await b2b.page.getByRole('button', { name: 'Submit return' }).click();
      await expect(b2b.page.getByTestId('rma-approved-banner')).toBeVisible({ timeout: 20_000 });
      await expect(b2b.page.getByRole('button', { name: 'Download Return Label' })).toBeVisible();
      await b2b.page.getByRole('button', { name: 'Done' }).click();

      // DAMAGED path: B2B uploads own evidence (uploader-scoped), then submits portal RMA
      const damagedOrder = await createShippedSalesOrder(manager.page, {
        variantId,
        customerId: CLIENT_A_METRO_ID,
        quantity: 1,
        unitPrice: 40,
        numberPrefix: 'SO-RMA-DMG',
      });
      const eligible = await apiJson<Array<{ salesOrderLineId: string }>>(
        b2b.page,
        `/api/v1/showroom/returns/eligible/${damagedOrder.salesOrderId}`,
      );
      expect(eligible.length).toBeGreaterThan(0);

      expect(fs.existsSync(FIXTURE_PNG)).toBeTruthy();
      const upload = await b2b.page.request.post('/api/v1/media/uploads?kind=EVIDENCE', {
        multipart: {
          file: {
            name: 'damage.png',
            mimeType: 'image/png',
            buffer: fs.readFileSync(FIXTURE_PNG),
          },
        },
      });
      expect(upload.ok(), await upload.text()).toBeTruthy();
      const mediaId = ((await upload.json()) as { id: string }).id;

      const pendingRma = await apiJson<{ id: string; status: string; number: string }>(
        b2b.page,
        '/api/v1/showroom/returns',
        {
          method: 'POST',
          body: JSON.stringify({
            salesOrderId: damagedOrder.salesOrderId,
            reasonCode: 'DAMAGED',
            lines: [
              {
                salesOrderLineId: eligible[0]!.salesOrderLineId,
                quantity: 1,
                mediaObjectId: mediaId,
              },
            ],
          }),
        },
      );
      expect(pendingRma.status).toBe('PENDING_REVIEW');

      await manager.page.goto('/returns');
      await expect(manager.page.getByTestId('rma-review-queue')).toBeVisible({ timeout: 20_000 });
      await expect(manager.page.getByText(pendingRma.number).first()).toBeVisible({
        timeout: 15_000,
      });
      await expect(manager.page.locator('img[alt="RMA evidence"]').first()).toBeVisible({
        timeout: 15_000,
      });
      await manager.page.getByRole('button', { name: 'Approve without Label' }).first().click();

      await expect
        .poll(async () => {
          const res = await b2b.page.request.get(`/api/v1/showroom/returns/${pendingRma.id}`);
          if (!res.ok()) return '';
          const body = (await res.json()) as {
            status: string;
            labelPurchaseMode?: string;
            shippingInstruction?: string;
          };
          return `${body.status}|${body.labelPurchaseMode}|${body.shippingInstruction ?? ''}`;
        }, { timeout: 30_000 })
        .toMatch(/APPROVED\|CUSTOMER\|.*own expense/i);
    } finally {
      await b2b.close();
      await manager.close();
    }
  });
});
