import { expect, test } from '@playwright/test';
import { loginAsDemo } from './fixtures/roleFixture';

test.describe('Wholesale self-serve onboarding', () => {
  test('guest can browse catalog and open the apply form', async ({ page }) => {
    await page.goto('/showroom/catalog');
    await expect(page.getByRole('heading', { name: 'Catalog' })).toBeVisible({ timeout: 15_000 });
    await expect(page.getByTestId('showroom-gated-banner')).toBeVisible();
    await expect(page.getByText(/Login to see wholesale price|No products available/i).first()).toBeVisible();

    await page.goto('/showroom/apply');
    await expect(page.getByRole('heading', { name: 'Apply for Wholesale' })).toBeVisible();
    await expect(page.getByLabel('Company Name')).toBeVisible();
    await expect(page.getByLabel('Tax/VAT ID (RFC/EIN)')).toBeVisible();
  });

  test('showroom login is passwordless', async ({ page }) => {
    await page.goto('/showroom/login');
    await expect(page.getByRole('heading', { name: 'Wholesale sign in' })).toBeVisible();
    await expect(page.getByLabel('Email')).toBeVisible();
    await expect(page.getByLabel(/password/i)).toHaveCount(0);
    await page.getByLabel('Email').fill('buyer@example.test');
    await page.getByRole('button', { name: 'Email me a login link' }).click();
    await expect(page.getByText('Check your email for your login link')).toBeVisible({ timeout: 15_000 });
  });

  test('office login is identifier-first then password', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByTestId('login-email')).toBeVisible();
    await expect(page.getByTestId('login-password')).toHaveCount(0);
    await expect(page.getByTestId('login-continue')).toBeVisible();
    await page.getByTestId('login-continue').click();
    await expect(page.getByTestId('login-password')).toBeVisible({ timeout: 15_000 });
    await expect(page.getByTestId('login-submit')).toBeVisible();
    await expect(page.getByTestId('login-magic-link')).toBeVisible();
  });

  test('admin can open pending applications inbox', async ({ page }) => {
    await loginAsDemo(page, 'owner@demo.test');
    await page.goto('/customers');
    await expect(page.getByTestId('customers-page')).toBeVisible({ timeout: 15_000 });
    await page.getByTestId('pending-applications-tab').click();
    await expect(
      page.getByTestId('wholesale-applications-panel').or(page.getByText('No pending applications')),
    ).toBeVisible({ timeout: 15_000 });
  });
});
