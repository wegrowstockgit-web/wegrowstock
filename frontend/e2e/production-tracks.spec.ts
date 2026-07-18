import { expect, test } from './fixtures/roleFixture';
import { contextForRole } from './journeys/helpers';

const DEMO_PASSWORD = process.env.E2E_DEMO_PASSWORD ?? 'password123';

/** Lightweight session check for fixtures that already authenticated via ownerPage. */
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
  test('action-required hub exposes sync conflicts for owners', async ({ ownerPage: page }) => {
    await page.goto('/exceptions?tab=sync');
    await expect(page.getByTestId('action-required-hub')).toBeVisible({ timeout: 25_000 });
    await expect(page.getByTestId('exceptions-tab-sync')).toBeVisible({ timeout: 20_000 });
    await expect(page.getByTestId('sync-conflicts-panel')).toBeVisible({ timeout: 20_000 });
    await expect(
      page.getByText(/No pending sync conflicts|Force Retry|Could not load sync conflicts/i).first(),
    ).toBeVisible();
  });

  test('settings sync conflicts tab loads', async ({ ownerPage: page }) => {
    await signInOwner(page);
    await page.goto('/settings?tab=syncConflicts');
    await expect(page.getByTestId('settings-page')).toBeVisible({ timeout: 20_000 });
    await expect(page.getByTestId('sync-conflicts-panel')).toBeVisible({ timeout: 20_000 });
  });

  test('reports time-travel valuation tab loads chart controls', async ({ ownerPage: page }) => {
    await signInOwner(page);
    await page.goto('/reports');
    await expect(page.getByRole('heading', { name: 'Reports' })).toBeVisible({ timeout: 20_000 });
    await page.getByRole('button', { name: 'Time-travel valuation' }).click();
    await expect(page.getByTestId('time-travel-valuation')).toBeVisible({ timeout: 15_000 });
    await expect(page.getByTestId('as-of-date')).toBeVisible();
  });

  test('import wizard supports legacy ERP migration mode', async ({ browser }) => {
    const owner = await contextForRole(browser, 'owner');
    try {
      await owner.page.goto('/import');
      await expect(owner.page.getByTestId('import-wizard')).toBeVisible({ timeout: 20_000 });
      await expect(owner.page.getByTestId('download-import-template')).toBeVisible();
      await expect(owner.page.getByTestId('validation-preview')).toBeVisible();
      await expect(owner.page.getByLabel('HS code')).toBeVisible();
      await expect(owner.page.getByLabel('Location path')).toBeVisible();
      await expect(owner.page.getByLabel('Lot #')).toBeVisible();
      await expect(owner.page.getByLabel('Temp zone')).toBeVisible();
      const legacy = owner.page.getByTestId('legacy-migration-mode');
      await expect(legacy).toBeVisible({ timeout: 15_000 });
      await legacy.click();
      await expect(owner.page.getByTestId('import-wizard').getByRole('heading', { level: 1 })).toHaveText(
        'Legacy ERP migration',
        { timeout: 15_000 },
      );
      await expect(owner.page.getByText(/INITIAL_MIGRATION receives/i)).toBeVisible();
    } finally {
      await owner.close();
    }
  });

  test('settings accounting + integrations vault forms mount', async ({ browser }) => {
    let owner = await contextForRole(browser, 'owner');
    try {
      await owner.page.goto('/settings?tab=accounting');
      await expect(owner.page.getByTestId('settings-content')).toBeVisible({ timeout: 20_000 });
      await expect(owner.page.getByTestId('accounting-sync')).toBeVisible({ timeout: 15_000 });
      await owner.page.getByRole('button', { name: 'Integrations', exact: true }).click();
      await expect(owner.page.getByTestId('integrations-settings')).toBeVisible({ timeout: 15_000 });
      await expect(owner.page.getByTestId('system-alerts-card')).toBeVisible();
      await expect(owner.page.getByTestId('shopify-integration')).toBeVisible();
      await owner.page.goto('/settings/billing');
      if (owner.page.url().includes('/login')) {
        await owner.close();
        owner = await contextForRole(browser, 'owner');
        await owner.page.goto('/settings/billing');
      }
      await expect(owner.page.getByTestId('billing-settings-page')).toBeVisible({ timeout: 20_000 });
      await expect(owner.page.getByTestId('carrier-credentials')).toBeVisible();
    } finally {
      await owner.close();
    }
  });
});
