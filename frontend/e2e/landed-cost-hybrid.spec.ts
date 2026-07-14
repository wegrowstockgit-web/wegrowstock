import { expect, sessionAccessToken, test } from './fixtures/roleFixture';

/**
 * Smoke: hybrid landed-cost endpoint rejects ValueStrategy for FREIGHT (customs-only).
 */
test.describe('Hybrid landed cost API', () => {
  test('FREIGHT with VALUE strategy is rejected', async ({ ownerPage }) => {
    await ownerPage.goto('/dashboard');
    await expect(ownerPage).toHaveURL(/\/dashboard/);
    const token = await sessionAccessToken(ownerPage);
    const res = await ownerPage.request.post(
      '/api/v1/purchasing/invoices/00000000-0000-4000-8000-000000000001/landed-costs',
      {
        headers: { Authorization: `Bearer ${token}` },
        data: {
          freightTotal: 100,
          eventType: 'FREIGHT',
          strategy: 'VALUE',
        },
      }
    );
    expect(res.status()).toBe(400);
    const body = await res.json();
    expect(JSON.stringify(body)).toMatch(/VALUE_RESERVED_FOR_CUSTOMS|Customs/i);
  });
});
