import { expect, test } from './fixtures/roleFixture';

const DEMO_PASSWORD = process.env.E2E_DEMO_PASSWORD ?? 'password123';

/** Ensure owner session is live without an extra login when the fixture already authenticated. */
async function signInOwner(page: import('@playwright/test').Page) {
  await page.goto('/dashboard');
  if (page.url().includes('/login')) {
    await page.getByLabel('Email').fill('owner@demo.test');
    await page.getByLabel('Password').fill(DEMO_PASSWORD);
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page).toHaveURL(/\/dashboard/, { timeout: 25_000 });
  }
  await expect(page.getByTestId('floating-kpi-row')).toBeVisible({ timeout: 25_000 });
}

test.describe('Production tracks (conflicts, time-travel, migration wizard)', () => {
  test('dashboard exposes sync conflicts panel for owners', async ({ ownerPage: page }) => {
    await signInOwner(page);
    await expect(page.getByTestId('sync-conflicts-panel')).toBeVisible({ timeout: 20_000 });
    await expect(
      page.getByText(/No pending sync conflicts|Force Retry|Could not load sync conflicts/i).first(),
    ).toBeVisible();
  });

  test('settings sync conflicts tab loads', async ({ ownerPage: page }) => {
    await signInOwner(page);
    await page.goto('/settings');
    await expect(page.getByRole('heading', { name: 'Settings', exact: true })).toBeVisible({
      timeout: 15_000,
    });
    const tab = page.getByRole('button', { name: 'Sync Conflicts' });
    await expect(tab).toBeVisible({ timeout: 15_000 });
    await tab.click();
    await expect(page.getByTestId('sync-conflicts-panel')).toBeVisible();
  });

  test('reports time-travel valuation tab loads chart controls', async ({ ownerPage: page }) => {
    await signInOwner(page);
    await page.goto('/reports');
    await expect(page.getByRole('heading', { name: 'Reports' })).toBeVisible({ timeout: 20_000 });
    await page.getByRole('button', { name: 'Time-travel valuation' }).click();
    await expect(page.getByTestId('time-travel-valuation')).toBeVisible({ timeout: 15_000 });
    await expect(page.getByTestId('as-of-date')).toBeVisible();
  });

  test('import wizard supports legacy ERP migration mode', async ({ ownerPage: page }) => {
    await signInOwner(page);
    await page.goto('/import');
    await expect(page.getByTestId('import-wizard')).toBeVisible({ timeout: 20_000 });
    const legacy = page.getByTestId('legacy-migration-mode');
    await expect(legacy).toBeVisible({ timeout: 15_000 });
    await legacy.click();
    await expect(page.getByTestId('import-wizard').getByRole('heading', { level: 1 })).toHaveText(
      'Legacy ERP migration',
      { timeout: 15_000 },
    );
    await expect(page.getByText(/INITIAL_MIGRATION receives/i)).toBeVisible();
  });

  test('settings accounting + integrations vault forms mount', async ({ ownerPage: page }) => {
    await signInOwner(page);
    await page.goto('/settings');
    await expect(page.getByRole('heading', { name: 'Settings', exact: true })).toBeVisible({
      timeout: 15_000,
    });
    await page.getByRole('button', { name: 'Accounting Sync' }).click();
    await expect(page.getByTestId('accounting-sync')).toBeVisible({ timeout: 15_000 });
    await page.getByRole('button', { name: 'Integrations' }).click();
    await expect(page.getByTestId('integrations-settings')).toBeVisible({ timeout: 15_000 });
    await expect(page.getByTestId('system-alerts-card')).toBeVisible();
    await expect(page.getByTestId('shopify-integration')).toBeVisible();
    await page.goto('/settings/billing');
    await expect(page.getByTestId('billing-settings-page')).toBeVisible({ timeout: 15_000 });
    await expect(page.getByTestId('carrier-credentials')).toBeVisible();
  });
});
