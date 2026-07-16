import { expect, sessionAccessToken, test } from './fixtures/roleFixture';

const WH_01 = 'a0000000-0000-4000-8000-000000000601';
const WH_02 = 'a0000000-0000-4000-8000-000000000611';

test.describe('LBAC warehouse boundaries', () => {
  test('picker warehouse is terminal-locked to authorized facility', async ({ pickerPage }) => {
    await pickerPage.goto('/fulfillment');
    await expect(pickerPage.getByText('Floor ops')).toBeVisible();

    // Single warehouse_ids claim → no switcher dropdown (kiosk lockdown)
    await expect(pickerPage.getByLabel('Active warehouse')).toHaveCount(0);
    const locked = pickerPage.locator('[data-terminal-locked="true"]');
    await expect(locked).toBeVisible();
    await expect(locked).toContainText(/Main Warehouse|WH-01|Warehouse/i);
  });

  test('picker forging X-Warehouse-Id for WH-02 is forbidden', async ({ pickerPage }) => {
    await pickerPage.goto('/fulfillment');
    await expect(pickerPage.getByText('Floor ops')).toBeVisible();
    const token = await sessionAccessToken(pickerPage);

    const forbidden = await pickerPage.request.get('/api/v1/locations/warehouses/assigned', {
      headers: {
        'X-Warehouse-Id': WH_02,
      },
    });
    expect(forbidden.status()).toBe(403);
    const problem = (await forbidden.json()) as { title?: string; status?: number; detail?: string };
    expect(problem.title).toBe('WAREHOUSE_FORBIDDEN');
    expect(problem.status).toBe(403);
    expect(problem.detail).toBeTruthy();

    const allowed = await pickerPage.request.get('/api/v1/locations/warehouses/assigned', {
      headers: {
        'X-Warehouse-Id': WH_01,
      },
    });
    expect(allowed.ok()).toBeTruthy();
    const list = (await allowed.json()) as Array<{ id: string; code: string }>;
    expect(list.every((w) => w.id === WH_01)).toBeTruthy();
    expect(list.some((w) => w.id === WH_02)).toBeFalsy();
  });

  test('owner can switch to Overflow Warehouse', async ({ ownerPage }) => {
    await ownerPage.goto('/dashboard');
    const select = ownerPage.getByLabel('Active warehouse');
    await expect(select).toBeVisible({ timeout: 15_000 });
    await expect(select.locator(`option[value="${WH_02}"]`)).toHaveCount(1);
    await select.selectOption(WH_02);
    await expect(select).toHaveValue(WH_02);
  });

  test('slugless login has no company slug field', async ({ browser }) => {
    const context = await browser.newContext({
      baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:3000',
    });
    const page = await context.newPage();
    await page.goto('/login');
    await expect(page.getByLabel('Company slug')).toHaveCount(0);
    await expect(page.getByLabel('Email')).toBeVisible();
    await page.getByLabel('Email').fill('owner@demo.test');
    await page.getByLabel('Password').fill(process.env.E2E_DEMO_PASSWORD ?? 'password123');
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page).toHaveURL(/\/dashboard/);
    await context.close();
  });
});
