import { test, expect } from '@playwright/test';

/**
 * E2E: RFC 7807 Problem Details surface for business validation failures.
 * Manager adjusts stock below available → 409 with human-readable detail (no stack leak).
 */
test.describe('Observability: Problem Details', () => {
  test('insufficient stock returns client-safe ProblemDetail', async ({ request }) => {
    const login = await request.post('/api/v1/auth/login', {
      data: { email: 'manager@demo.test', password: 'password123' },
    });
    expect(login.ok()).toBeTruthy();

    const variants = await request.get('/api/v1/variants?limit=5');
    expect(variants.ok()).toBeTruthy();
    const body = await variants.json();
    const variantId = body.items?.[0]?.id ?? body[0]?.id;
    expect(variantId).toBeTruthy();

    const locations = await request.get('/api/v1/locations');
    expect(locations.ok()).toBeTruthy();
    const locationList = await locations.json();
    const list = Array.isArray(locationList) ? locationList : (locationList.items ?? []);
    const location =
      list.find((l: { type?: string }) => l.type === 'BIN' || l.type === 'WAREHOUSE') ?? list[0];
    expect(location?.id).toBeTruthy();

    const adjust = await request.post('/api/v1/inventory/adjust', {
      headers: {
        'Content-Type': 'application/json',
        'X-Request-Id': 'e2e-obs-problem-1',
        'X-Warehouse-Id': location.id,
      },
      data: {
        variantId,
        locationId: location.id,
        delta: -999999999,
        reasonCode: 'E2E_OBS_PROBE',
      },
    });

    expect(adjust.status()).toBe(409);
    expect(adjust.headers()['x-request-id']).toBe('e2e-obs-problem-1');

    const problem = await adjust.json();
    expect(problem.title).toBe('INSUFFICIENT_STOCK');
    expect(problem.status).toBe(409);
    expect(String(problem.detail)).toMatch(/Insufficient/i);
    expect(String(problem.detail)).not.toMatch(/at com\.invsys|Exception|Caused by/i);
    expect(problem.code).toBe('INSUFFICIENT_STOCK');
  });
});
