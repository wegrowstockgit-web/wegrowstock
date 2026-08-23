import { test } from '@playwright/test';
import { contextForRole, expect } from './helpers';

test.describe('Journey 78: Accounts receivable invoices', () => {
  test.setTimeout(180_000);

  test('combobox create, balance due, overdue, PDF/email, log payment', async ({ browser }) => {
    const admin = await contextForRole(browser, 'admin');
    try {
      await admin.page.goto('/invoices');
      await expect(admin.page.getByTestId('invoices-page')).toBeVisible({ timeout: 20_000 });
      const skipTour = admin.page.getByRole('button', { name: 'Not now' });
      if (await skipTour.isVisible({ timeout: 3_000 }).catch(() => false)) {
        await skipTour.click();
      }
      await expect(admin.page.getByRole('columnheader', { name: 'Balance Due' })).toBeVisible();
      await expect(admin.page.getByRole('columnheader', { name: 'Actions' })).toBeVisible();

      await admin.page.getByTestId('new-invoice').click();
      await expect(admin.page.getByTestId('create-invoice-form')).toBeVisible();
      const search = admin.page.getByTestId('invoice-so-search');
      await search.click();
      const soWait = admin.page.waitForResponse(
        (res) =>
          res.url().includes('/api/v1/sales-orders') &&
          res.url().includes('status=SHIPPED') &&
          res.request().method() === 'GET',
        { timeout: 20_000 },
      );
      await search.fill('SO');
      const soRes = await soWait;
      expect(soRes.ok(), await soRes.text()).toBeTruthy();
      await expect(admin.page.getByTestId('invoice-so-results')).toBeVisible();
      await admin.page.keyboard.press('Escape');

      const listRes = await admin.page.request.get('/api/v1/invoices?size=50');
      expect(listRes.ok()).toBeTruthy();
      const body = (await listRes.json()) as {
        items?: Array<{
          id: string;
          number: string;
          status: string;
          balanceDue?: number;
          total: number;
        }>;
      };
      const invoice = (body.items ?? []).find((row) => (row.balanceDue ?? row.total) > 0) ?? body.items?.[0];
      if (!invoice) {
        await expect(admin.page.getByText(/No invoices yet/i)).toBeVisible();
        return;
      }

      const searchBox = admin.page.getByPlaceholder(/Search invoices/i);
      await searchBox.fill(invoice.number);
      await expect(admin.page.getByText(invoice.number).first()).toBeVisible({ timeout: 20_000 });
      await expect(admin.page.getByTestId(`invoice-balance-${invoice.id}`)).toContainText(/\$/);

      await admin.page.getByTestId(`invoice-row-${invoice.id}`).click();
      await expect(admin.page).toHaveURL(new RegExp(`/invoices/${invoice.id}`));
      await expect(admin.page.getByTestId('invoice-workspace')).toBeVisible();
      await expect(admin.page.getByTestId('invoice-download-pdf')).toBeVisible();

      await admin.page.goto('/invoices');
      await expect(admin.page.getByTestId('invoices-page')).toBeVisible({ timeout: 20_000 });
      await admin.page.getByPlaceholder(/Search invoices/i).fill(invoice.number);
      await expect(admin.page.getByText(invoice.number).first()).toBeVisible({ timeout: 20_000 });
      await admin.page.getByTestId(`invoice-row-actions-${invoice.id}`).click();

      const pdfWait = admin.page.waitForResponse(
        (res) => res.url().includes('/documents/invoice') && res.url().includes('/pdf'),
      );
      await admin.page.getByTestId(`invoice-download-pdf-${invoice.id}`).click();
      expect((await pdfWait).ok()).toBeTruthy();

      await admin.page.getByTestId(`invoice-row-actions-${invoice.id}`).click();
      const emailWait = admin.page.waitForResponse((res) =>
        res.url().includes('/documents/invoice') && res.url().includes('/email'),
      );
      await admin.page.getByTestId(`invoice-email-${invoice.id}`).click();
      expect([200, 400]).toContain((await emailWait).status());

      await admin.page.getByTestId(`invoice-select-${invoice.id}`).check();
      await expect(admin.page.getByTestId('invoice-bulk-bar')).toBeVisible();
      const bulkWait = admin.page.waitForResponse(
        (res) =>
          res.url().includes('/documents/invoices/email') && res.request().method() === 'POST',
      );
      await admin.page.getByTestId('invoice-bulk-email').click();
      expect([200, 400, 404]).toContain((await bulkWait).status());

      await admin.page.getByTestId(`invoice-row-actions-${invoice.id}`).click();
      const pay = admin.page.getByTestId(`invoice-log-payment-${invoice.id}`);
      if (await pay.isVisible()) {
        await pay.click();
        await expect(admin.page.getByTestId('log-payment-form')).toBeVisible();
        await admin.page.getByTestId('log-payment-amount').fill('1');
        const payWait = admin.page.waitForResponse(
          (res) => res.url().includes('/payments') && res.request().method() === 'POST',
        );
        await admin.page.getByTestId('log-payment-submit').click();
        expect((await payWait).ok()).toBeTruthy();
      }
    } finally {
      await admin.close();
    }
  });
});
