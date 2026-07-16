import { expect, test } from './fixtures/roleFixture';

const API_BASE = process.env.E2E_API_URL ?? 'http://localhost:8080';

test.describe('Observability', () => {
  test('API echoes X-Request-Id for correlation', async ({ request }) => {
    const res = await request.get(`${API_BASE}/actuator/health`, {
      headers: { 'X-Request-Id': 'obs-e2e-req-1' },
    });
    expect(res.ok()).toBeTruthy();
    expect(res.headers()['x-request-id']).toBe('obs-e2e-req-1');
  });

  test('operations console loads for owner', async ({ ownerPage: page }) => {
    await page.goto('/settings');
    await page.getByRole('button', { name: 'Operations' }).click();
    await expect(page.getByTestId('operations-console')).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole('heading', { name: 'Failed outbox events' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Failed sync logs' })).toBeVisible();
    await expect(page.getByText(/Last X-Request-Id/)).toBeVisible();
  });
});

test.describe('Synthetic order smoke', () => {
  test('critical office order surfaces remain reachable', async ({ ownerPage: page }) => {
    for (const path of ['/purchase-orders', '/sales-orders', '/invoices'] as const) {
      await page.goto(path);
      await expect(page).not.toHaveURL(/\/login/, { timeout: 20_000 });
      await expect(page).toHaveURL(new RegExp(path.replace('/', '\\/')), { timeout: 20_000 });
    }
    await expect(page.getByRole('heading', { name: 'Invoices', exact: true })).toBeVisible({
      timeout: 15_000,
    });
  });
});
