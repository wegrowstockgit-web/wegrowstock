import { expect, test } from '@playwright/test';

const DEMO_PASSWORD = process.env.E2E_DEMO_PASSWORD ?? 'password123';

const TENANT_OWNERS = [
  { email: 'owner@demo.test', expectUrl: /\/dashboard/ },
  { email: 'owner@acme.test', expectUrl: /\/dashboard/ },
  { email: 'owner@northwind.test', expectUrl: /\/dashboard/ },
  { email: 'owner@pacific.test', expectUrl: /\/dashboard/ },
] as const;

test.describe('Multi-tenant slugless login matrix', () => {
  for (const account of TENANT_OWNERS) {
    test(`${account.email} reaches office dashboard`, async ({ page }) => {
      await page.goto('/login');
      await expect(page.getByLabel('Company slug')).toHaveCount(0);
      await page.getByLabel('Email').fill(account.email);
      await page.getByLabel('Password').fill(DEMO_PASSWORD);
      await page.getByRole('button', { name: 'Sign in' }).click();
      await expect(page).toHaveURL(account.expectUrl, { timeout: 15_000 });
    });
  }

  test('picker@northwind.test is LBAC-scoped to Seattle DC only', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Email').fill('picker@northwind.test');
    await page.getByLabel('Password').fill(DEMO_PASSWORD);
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page).toHaveURL(/\/fulfillment/, { timeout: 15_000 });
    await expect(page.getByText('Floor ops')).toBeVisible();

    // Single warehouse claim → terminal lockdown (no switcher)
    await expect(page.getByLabel('Active warehouse')).toHaveCount(0);
    const locked = page.locator('[data-terminal-locked="true"]');
    await expect(locked).toBeVisible();
    await expect(locked).toContainText(/Seattle|SEA/i);
  });
});