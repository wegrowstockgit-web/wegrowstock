import { test } from '@playwright/test';
import {
  WIDGET_S_SKU,
  apiJson,
  contextForRole,
  expect,
  findVariantId,
  firstCustomerId,
} from './helpers';

test.describe('Journey 76: B2B sales order workspace', () => {
  test.setTimeout(180_000);

  test('new order workspace searches customer/SKU, shows ATP, and enriches the grid', async ({
    browser,
  }) => {
    const admin = await contextForRole(browser, 'admin');
    try {
      const customerId = await firstCustomerId(admin.page);
      const customers = await apiJson<{
        items?: Array<{ id: string; name: string }>;
        content?: Array<{ id: string; name: string }>;
      }>(admin.page, '/api/v1/customers?page=1&size=100');
      const customer = (customers.items ?? customers.content ?? []).find((row) => row.id === customerId);
      expect(customer?.name).toBeTruthy();
      const customerName = customer!.name;
      await findVariantId(admin.page, WIDGET_S_SKU);

      await admin.page.goto('/sales-orders');
      await expect(admin.page.getByRole('heading', { name: /Sales orders/i }).first()).toBeVisible({
        timeout: 20_000,
      });
      await expect(admin.page.getByRole('columnheader', { name: 'Customer PO' })).toBeVisible();
      await expect(admin.page.getByRole('columnheader', { name: 'Total Amount' })).toBeVisible();
      await expect(admin.page.getByRole('columnheader', { name: 'Progress' })).toBeVisible();
      await expect(admin.page.getByRole('columnheader', { name: 'Requested Ship Date' })).toBeVisible();

      await admin.page.getByTestId('new-sales-order-button').click();
      await expect(admin.page).toHaveURL(/\/sales\/orders\/new/);
      await expect(admin.page.getByTestId('new-sales-order-page')).toBeVisible();

      const customerSearch = admin.page.getByTestId('so-customer-search');
      await customerSearch.click();
      await customerSearch.fill(customerName.slice(0, Math.min(12, customerName.length)));
      await expect(admin.page.getByTestId(`so-customer-option-${customerName}`)).toBeVisible({
        timeout: 15_000,
      });
      await admin.page.getByTestId(`so-customer-option-${customerName}`).click();
      await expect(admin.page.getByTestId('so-customer-credit')).toBeVisible({ timeout: 15_000 });
      await expect(admin.page.getByTestId('so-customer-credit')).toContainText(/Available credit|Price tier/i);

      const skuInput = admin.page.locator('[data-testid^="so-add-sku-"]').first();
      await skuInput.click();
      await skuInput.fill(WIDGET_S_SKU);
      const skuOption = admin.page.getByTestId('so-sku-option').filter({ hasText: WIDGET_S_SKU }).first();
      await expect(skuOption).toBeVisible({ timeout: 15_000 });
      await skuOption.click();
      await expect(admin.page.getByText(/On-Hand:.*Available:/)).toBeVisible({ timeout: 15_000 });

      const poNumber = `PO-E2E-${Date.now()}`;
      await admin.page.getByTestId('so-customer-po').fill(poNumber);
      await admin.page.getByTestId('so-ship-date').fill('2026-09-15');

      const qty = admin.page.locator('[data-testid^="so-qty-"]').first();
      await qty.fill('2');
      const priceInput = admin.page.locator('[data-testid^="so-price-"]').first();
      await expect(priceInput).not.toHaveValue('', { timeout: 10_000 });
      const unitPrice = Number(await priceInput.inputValue());
      expect(unitPrice).toBeGreaterThan(0);
      const extended = admin.page.locator('[data-testid^="so-extended-"]').first();
      await expect(extended).toContainText(/\$/);
      await expect(admin.page.getByTestId('so-grand-total')).toHaveText(await extended.innerText());

      const createWait = admin.page.waitForResponse(
        (res) => res.url().includes('/api/v1/sales-orders') && res.request().method() === 'POST',
        { timeout: 20_000 },
      );
      await admin.page.getByTestId('so-create-order').click();
      const created = await createWait;
      expect(created.ok(), await created.text()).toBeTruthy();
      const body = (await created.json()) as { number?: string };
      await expect(admin.page).toHaveURL(/\/sales-orders/, { timeout: 20_000 });

      if (body.number) {
        const search = admin.page.getByPlaceholder(/Search orders or customers/i);
        await search.fill(body.number);
        await expect(admin.page.getByText(body.number).first()).toBeVisible({ timeout: 20_000 });
        await expect(admin.page.getByTestId(`so-po-${body.number}`)).toHaveText(poNumber);
        await expect(admin.page.getByTestId(`so-total-${body.number}`)).toContainText(/\$/);
        await expect(admin.page.getByTestId(`so-progress-${body.number}`)).toHaveText(/0\/1 lines shipped/);
      } else {
        await expect(admin.page.getByText(poNumber).first()).toBeVisible({ timeout: 20_000 });
      }
    } finally {
      await admin.close();
    }
  });
});
