import { expect, test } from '@playwright/test';

const DEMO_PASSWORD = process.env.E2E_DEMO_PASSWORD ?? 'password123';
const uniqueSlug = `e2e-${Date.now()}`;

test.describe('Onboarding funnel', () => {
  test('signup reaches dashboard with checklist', async ({ page }) => {
    await page.goto('/signup');

    await page.getByLabel('Company name').fill(`E2E Corp ${uniqueSlug}`);
    await page.getByLabel('Your name').fill('E2E Tester');
    await page.getByLabel('Work email').fill(`owner-${uniqueSlug}@e2e.test`);
    await page.getByLabel('Password').fill(DEMO_PASSWORD);
    await page.getByRole('button', { name: 'Create account' }).click();

    await expect(page).toHaveURL(/\/dashboard/, { timeout: 20_000 });
    await expect(page.getByText('Stock value')).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText('Getting started')).toBeVisible();
  });
});
