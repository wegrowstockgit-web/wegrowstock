import { expect, test } from '../fixtures/roleFixture';
import { contextForRole, expectFulfillmentSurface } from './helpers';

/**
 * Journey 27 — Enterprise master-data forms + Settings office layout + hardware note.
 */
test.describe('Journey 27: Enterprise master data & Settings layout', () => {
  test.setTimeout(300_000);

  test('settings shell, warehouse form fields, customer/supplier create', async ({ browser }) => {
    const owner = await contextForRole(browser, 'owner');
    try {
      await owner.page.goto('/settings?tab=warehouses');
      await expect(owner.page.getByRole('heading', { name: 'Settings' })).toBeVisible({
        timeout: 30_000,
      });
      await expect(owner.page.locator('.settings-shell')).toBeVisible();
      await expect(owner.page.getByTestId('floor-hardware-compat')).toBeVisible();

      await owner.page.getByRole('button', { name: /Add warehouse/i }).first().click();
      await expect(owner.page).toHaveURL(/\/warehouses\/add/);
      await expect(owner.page.getByTestId('warehouses-add-page')).toBeVisible();
      await expect(owner.page.getByTestId('add-warehouse-form')).toBeVisible();
      await expect(owner.page.getByLabel('Clear height (ft)')).toBeVisible();
      await expect(owner.page.getByLabel('Dock doors')).toBeVisible();
      await owner.page.getByRole('button', { name: 'Cancel' }).click();
      await expect(owner.page).toHaveURL(/\/settings\?tab=warehouses/);

      await owner.page.goto('/settings/profile');
      await expect(owner.page.getByTestId('personal-profile-form')).toBeVisible({ timeout: 20_000 });
      await owner.page.getByLabel('Phone').fill('555-0100');
      const profileWait = owner.page.waitForResponse(
        (res) =>
          res.url().includes('/api/v1/users/me/profile') && res.request().method() === 'PATCH',
        { timeout: 30_000 },
      );
      await owner.page.getByRole('button', { name: /Save personal settings/i }).click();
      expect((await profileWait).ok()).toBeTruthy();

      await owner.page.goto('/settings?tab=users');
      await expect(owner.page.getByTestId('invite-user-button')).toBeVisible({ timeout: 20_000 });
      const selfEdit = owner.page.getByTestId(`edit-access-${(await owner.page.evaluate(() => {
        const raw = localStorage.getItem('invsys-session');
        if (!raw) return '';
        try {
          return JSON.parse(raw)?.state?.user?.id ?? '';
        } catch {
          return '';
        }
      }))}`);
      // Fall back: first Edit access button for org-scope save
      const editBtn = (await selfEdit.count()) > 0 ? selfEdit : owner.page.getByRole('button', { name: 'Edit access' }).first();
      await editBtn.click();
      await expect(owner.page.getByTestId('org-scope-section')).toBeVisible();
      await owner.page.getByLabel('Department').fill('Outbound');
      const orgWait = owner.page.waitForResponse(
        (res) =>
          res.url().includes('/org-scope') && res.request().method() === 'PATCH',
        { timeout: 30_000 },
      );
      await owner.page.getByTestId('save-org-scope').click();
      expect((await orgWait).ok()).toBeTruthy();

      const suffix = Date.now().toString(36);
      await owner.page.goto('/customers');
      await expect(owner.page.getByRole('heading', { name: 'Customers', exact: true })).toBeVisible({
        timeout: 20_000,
      });
      await owner.page.getByRole('button', { name: 'Add customer' }).click();
      await expect(owner.page.getByTestId('add-customer-form')).toBeVisible();
      await owner.page.getByLabel('Name', { exact: true }).fill(`Cust ${suffix}`);
      await owner.page.getByLabel('Tax ID / EIN').fill('12-3456789');
      const custWait = owner.page.waitForResponse(
        (res) => res.url().includes('/api/v1/customers') && res.request().method() === 'POST',
        { timeout: 30_000 },
      );
      await owner.page.getByTestId('add-customer-submit').click();
      const custRes = await custWait;
      expect(custRes.ok(), await custRes.text()).toBeTruthy();
      const custBody = (await custRes.json()) as { paymentTerms?: string; customerStatus?: string };
      expect(custBody.paymentTerms).toBe('NET30');
      expect(custBody.customerStatus).toBe('ACTIVE');

      await owner.page.goto('/suppliers');
      await expect(owner.page.getByRole('heading', { name: 'Suppliers', exact: true })).toBeVisible({
        timeout: 20_000,
      });
      await owner.page.getByRole('button', { name: 'Add supplier' }).click();
      await expect(owner.page.getByTestId('add-supplier-form')).toBeVisible();
      await owner.page.getByLabel('Name', { exact: true }).fill(`Supp ${suffix}`);
      await owner.page.getByLabel('Default lead time (days)').fill('10');
      const suppWait = owner.page.waitForResponse(
        (res) => res.url().includes('/api/v1/suppliers') && res.request().method() === 'POST',
        { timeout: 30_000 },
      );
      await owner.page.getByTestId('add-supplier-submit').click();
      const suppRes = await suppWait;
      expect(suppRes.ok(), await suppRes.text()).toBeTruthy();
      const suppBody = (await suppRes.json()) as { defaultLeadTimeDays?: number };
      expect(suppBody.defaultLeadTimeDays).toBe(10);

      // Office route still healthy after master-data creates
      await owner.page.goto('/fulfillment');
      await expectFulfillmentSurface(owner.page);
    } finally {
      await owner.close();
    }
  });
});
