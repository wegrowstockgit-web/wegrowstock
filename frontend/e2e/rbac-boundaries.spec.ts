import { expect, test } from './fixtures/roleFixture';

test.describe('RBAC dual-axis boundaries', () => {
  test('visual affordance: owner sees Add customer CTA, viewer does not', async ({
    ownerPage,
    viewerPage,
  }) => {
    await ownerPage.goto('/customers');
    await expect(ownerPage.getByRole('heading', { name: 'Customers', exact: true })).toBeVisible();
    await expect(ownerPage.getByRole('button', { name: 'Add customer' }).first()).toBeVisible();

    await viewerPage.goto('/customers');
    await expect(viewerPage.getByRole('heading', { name: 'Customers', exact: true })).toBeVisible();
    await expect(viewerPage.getByRole('button', { name: 'Add customer' })).toHaveCount(0);
  });

  test('route boundary: picker is redirected from /settings to /fulfillment', async ({
    pickerPage,
  }) => {
    await pickerPage.goto('/fulfillment');
    await expect(pickerPage.getByText('Floor ops')).toBeVisible();
    await pickerPage.goto('/settings');
    await expect(pickerPage).toHaveURL(/\/fulfillment/);
    await expect(pickerPage.getByText('Floor ops')).toBeVisible();
  });

  test('API boundary: picker cannot POST admin-only /customers (403)', async ({ pickerPage }) => {
    await pickerPage.goto('/fulfillment');
    await expect(pickerPage.getByText('Floor ops')).toBeVisible();

    const responsePromise = pickerPage.waitForResponse(
      (res) =>
        res.url().includes('/api/v1/customers') &&
        res.request().method() === 'POST'
    );

    await pickerPage.evaluate(async () => {
      const raw = localStorage.getItem('invsys-session');
      const token = raw ? (JSON.parse(raw) as { state?: { accessToken?: string } }).state?.accessToken : null;
      await fetch('/api/v1/customers', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify({
          name: 'Forbidden Customer',
          email: 'forbidden@example.test',
        }),
      });
    });

    const response = await responsePromise;
    expect(response.status()).toBe(403);
  });
});
