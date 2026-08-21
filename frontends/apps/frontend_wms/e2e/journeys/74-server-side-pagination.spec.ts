import { expect, test } from '../fixtures/roleFixture';
import { unwrapItems } from './helpers';

/**
 * Journey 74 — Server-side offset pagination and table search.
 * Skips when the live API still returns a raw array (pre-V129 image).
 */
test.describe('Journey 74: Server-side pagination & search', () => {
  test.setTimeout(180_000);

  test('list endpoints return page envelopes and PO search updates the URL', async ({
    ownerPage,
  }) => {
    const list = await ownerPage.request.get('/api/v1/purchase-orders?page=1&size=25&sort=number,asc');
    test.skip(!list.ok(), 'Purchase order API is not reachable');
    const body = await list.json();
    test.skip(Array.isArray(body), 'offset pagination not deployed (needs V129 + list envelope)');
    expect(body.items).toBeDefined();
    expect(typeof body.totalElements).toBe('number');
    expect(body.page).toBe(1);
    expect(body.size).toBe(25);

    const searchRes = await ownerPage.request.get('/api/v1/purchase-orders?search=no-such-po-zzzz');
    expect(searchRes.ok()).toBeTruthy();
    const searched = await searchRes.json();
    expect(unwrapItems(searched)).toHaveLength(0);
    expect(searched.totalElements).toBe(0);

    const endpoints = [
      '/api/v1/suppliers?page=1&size=2',
      '/api/v1/sales-orders?page=1&size=25',
      '/api/v1/customers?page=1&size=2',
      '/api/v1/invoices?page=1&size=25',
      '/api/v1/products?page=1&size=25',
      '/api/v1/manufacturing/orders?page=1&size=25',
    ];
    for (const path of endpoints) {
      const res = await ownerPage.request.get(path);
      expect(res.ok(), path).toBeTruthy();
      const json = await res.json();
      expect(Array.isArray(json), `${path} should return a page envelope`).toBeFalsy();
      expect(json.items).toBeDefined();
      expect(typeof json.totalElements).toBe('number');
      expect(json.items.length).toBeLessThanOrEqual(Number(new URL(path, 'http://x').searchParams.get('size')));
    }

    await ownerPage.goto('/purchase-orders');
    await expect(ownerPage.getByRole('heading', { name: 'Purchase Orders' })).toBeVisible({
      timeout: 20_000,
    });
    await expect(ownerPage.getByTestId('pagination')).toBeVisible({ timeout: 15_000 });
    await ownerPage.getByTestId('page-size').selectOption('25');
    await expect(ownerPage).toHaveURL(/size=25/, { timeout: 10_000 });

    const search = ownerPage.getByTestId('table-search');
    await expect(search).toBeVisible({ timeout: 15_000 });
    await search.fill('Acme');
    await expect(ownerPage).toHaveURL(/search=Acme/, { timeout: 10_000 });
    await expect(ownerPage).not.toHaveURL(/page=/, { timeout: 5_000 });
    await expect(ownerPage).toHaveURL(/size=25/);
  });
});
