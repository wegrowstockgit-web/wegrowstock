import { test } from '@playwright/test';
import { contextForRole, expect } from './helpers';

test.describe('Journey 77: B2B customer master', () => {
  test.setTimeout(180_000);

  test('KPIs, tabbed create, portal invite, workspace, and credit hold', async ({ browser }) => {
    const admin = await contextForRole(browser, 'admin');
    try {
      const suffix = Date.now().toString(36);
      const company = `Northwind ${suffix}`;
      const email = `buyer-${suffix}@demo.test`;

      await admin.page.goto('/customers');
      await expect(admin.page.getByTestId('customers-page')).toBeVisible({ timeout: 20_000 });
      await expect(admin.page.getByTestId('customer-kpi-row')).toBeVisible();
      await expect(admin.page.getByTestId('kpi-active-b2b')).toBeVisible();
      await expect(admin.page.getByRole('columnheader', { name: 'Name & Email' })).toBeVisible();
      await expect(admin.page.getByRole('columnheader', { name: 'Price Tier' })).toBeVisible();
      await expect(admin.page.getByRole('columnheader', { name: 'Credit Available' })).toBeVisible();
      await expect(admin.page.getByRole('columnheader', { name: 'Portal Status' })).toBeVisible();

      await admin.page.getByRole('button', { name: 'Add customer' }).first().click();
      await expect(admin.page.getByTestId('add-customer-form')).toBeVisible();
      await admin.page.getByLabel('Company Name').fill(company);
      await admin.page.getByLabel('Primary Contact Email').fill(email);
      await admin.page.getByLabel('Tax ID / EIN').fill('98-7654321');

      await admin.page.getByTestId('add-customer-tab-financials').click();
      await admin.page.getByLabel('Credit Limit').fill('15000');
      await admin.page.getByTestId('customer-price-tier-search').click();
      const wholesale = admin.page.getByTestId('customer-tier-option-Wholesale');
      if (await wholesale.isVisible({ timeout: 8_000 }).catch(() => false)) {
        await wholesale.click();
      } else {
        await admin.page.getByTestId('customer-price-tier-results').locator('button').nth(1).click();
      }

      await admin.page.getByTestId('add-customer-tab-addresses').click();
      await admin.page.getByLabel('Street').first().fill('100 Market St');
      await admin.page.getByTestId('customer-add-shipping').click();

      await admin.page.getByTestId('add-customer-tab-portal').click();
      await admin.page.getByTestId('customer-provision-showroom').check();

      const createWait = admin.page.waitForResponse(
        (res) => res.url().includes('/api/v1/customers') && res.request().method() === 'POST',
        { timeout: 20_000 },
      );
      await admin.page.getByTestId('add-customer-submit').click();
      const created = await createWait;
      expect(created.ok(), await created.text()).toBeTruthy();
      const body = (await created.json()) as { id: string };
      expect(body.id).toBeTruthy();

      const search = admin.page.getByPlaceholder(/Search customers/i);
      await search.fill(company);
      await expect(admin.page.getByText(company).first()).toBeVisible({ timeout: 20_000 });
      await expect(admin.page.getByTestId(`customer-credit-${body.id}`)).toContainText(/\$/);
      await expect(admin.page.getByTestId(`customer-portal-${body.id}`)).toHaveText(/Pending|Active/i);

      await admin.page.getByTestId(`customer-row-actions-${body.id}`).click();
      await admin.page.getByTestId(`customer-open-workspace-${body.id}`).click();
      await expect(admin.page).toHaveURL(new RegExp(`/sales/customers/${body.id}`));
      await expect(admin.page.getByTestId('customer-workspace-page')).toBeVisible();
      await expect(admin.page.getByTestId('customer-detail')).toBeVisible();

      await admin.page.goto('/customers');
      await search.fill(company);
      await expect(admin.page.getByText(company).first()).toBeVisible({ timeout: 20_000 });
      await admin.page.getByTestId(`customer-row-actions-${body.id}`).click();
      const hold = admin.page.getByTestId(`customer-credit-hold-${body.id}`);
      if (await hold.isVisible()) {
        const holdWait = admin.page.waitForResponse(
          (res) => res.url().includes('/credit-hold') && res.request().method() === 'POST',
        );
        await hold.click();
        expect((await holdWait).ok()).toBeTruthy();
      }
    } finally {
      await admin.close();
    }
  });
});
