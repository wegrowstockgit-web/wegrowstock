import { expect, sessionAccessToken, test } from './fixtures/roleFixture';

const WH_01 = 'a0000000-0000-4000-8000-000000000601';
const WH_02 = 'a0000000-0000-4000-8000-000000000611';

test.describe('LBAC warehouse boundaries', () => {
  test('picker warehouse dropdown only lists authorized facility', async ({ pickerPage }) => {
    await pickerPage.goto('/fulfillment');
    await expect(pickerPage.getByText('Floor ops')).toBeVisible();

    const select = pickerPage.getByLabel('Active warehouse');
    await expect(select).toBeVisible();
    const options = select.locator('option');
    await expect(options).toHaveCount(1);
    await expect(options.first()).toHaveAttribute('value', WH_01);
  });

  test('picker forging X-Warehouse-Id for WH-02 is forbidden', async ({ pickerPage }) => {
    await pickerPage.goto('/fulfillment');
    await expect(pickerPage.getByText('Floor ops')).toBeVisible();
    const token = await sessionAccessToken(pickerPage);

    const forbidden = await pickerPage.request.get('/api/v1/locations?type=WAREHOUSE', {
      headers: {
        Authorization: `Bearer ${token}`,
        'X-Warehouse-Id': WH_02,
      },
    });
    expect(forbidden.status()).toBe(403);

    const allowed = await pickerPage.request.get('/api/v1/locations?type=WAREHOUSE', {
      headers: {
        Authorization: `Bearer ${token}`,
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
    await expect(ownerPage.getByText(/Good (morning|afternoon|evening)/i)).toBeVisible({
      timeout: 15_000,
    });
    const select = ownerPage.getByLabel('Active warehouse');
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
