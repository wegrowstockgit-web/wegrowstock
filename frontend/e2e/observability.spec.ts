import { expect, test } from '@playwright/test';

const DEMO_PASSWORD = process.env.E2E_DEMO_PASSWORD ?? 'password123';
const API_BASE = process.env.E2E_API_URL ?? 'http://localhost:8080';

async function officeLogin(page: import('@playwright/test').Page) {
  await page.goto('/login');
  await page.getByLabel('Company slug').fill('demo-corp');
  await page.getByLabel('Email').fill('owner@demo.test');
  await page.getByLabel('Password').fill(DEMO_PASSWORD);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page).toHaveURL(/\/dashboard/);
}

test.describe('Observability', () => {
  test('API echoes X-Request-Id for correlation', async ({ request }) => {
    const res = await request.get(`${API_BASE}/actuator/health`, {
      headers: { 'X-Request-Id': 'obs-e2e-req-1' },
    });
    expect(res.ok()).toBeTruthy();
    expect(res.headers()['x-request-id']).toBe('obs-e2e-req-1');
  });

  test('operations console loads for owner', async ({ page }) => {
    await officeLogin(page);
    await page.getByRole('link', { name: 'Settings' }).click();
    await page.getByRole('button', { name: 'Operations' }).click();
    await expect(page.getByTestId('operations-console')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Failed outbox events' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Failed sync logs' })).toBeVisible();
    await expect(page.getByText(/Last X-Request-Id/)).toBeVisible();
  });
});

test.describe('Synthetic order smoke', () => {
  test('critical office order surfaces remain reachable', async ({ page }) => {
    await officeLogin(page);

    await page.goto('/purchase-orders');
    await expect(page).toHaveURL(/\/purchase-orders/);

    await page.goto('/fulfillment');
    await expect(page).toHaveURL(/\/fulfillment/);
    await page.getByRole('radio', { name: 'Receive' }).click();
    await expect(page.getByText(/Scan to receive/i)).toBeVisible();

    await page.goto('/sales-orders');
    await expect(page).toHaveURL(/\/sales-orders/);

    await page.goto('/invoices');
    await expect(page).toHaveURL(/\/invoices/);
  });
});
