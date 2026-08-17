import { expect, test } from '@playwright/test';

const apiBase = process.env.E2E_API_URL ?? 'http://localhost:8080';

test('signed-in register reads POS entitlement, language, and currency from WMS', async ({
  page,
  request,
}) => {
  const login = await request.post(`${apiBase}/api/v1/auth/login`, {
    data: { email: 'owner@demo.test', password: 'password123' },
    failOnStatusCode: false,
  });
  if (!login.ok()) {
    test.skip(true, 'API is not available for live POS session testing');
  }
  const tokens = await login.json();
  const accessToken = tokens.accessToken as string;

  await page.goto('/login');
  await page.getByTestId('pos-login-email').fill('owner@demo.test');
  await page.getByTestId('pos-login-password').fill('password123');
  await page.getByRole('button', { name: /open register|abrir caja|ouvrir la caisse/i }).click();

  await expect(page.getByTestId('register-page')).toBeVisible();
  const locked = page.getByTestId('pos-locked');
  const search = page.getByTestId('pos-upc-search');
  await expect(locked.or(search)).toBeVisible();

  const session = await request.get(`${apiBase}/api/v1/pos/session`, {
    headers: { Authorization: `Bearer ${accessToken}` },
    failOnStatusCode: false,
  });
  if (session.ok()) {
    const body = await session.json();
    expect(body).toHaveProperty('posEnabled');
    expect(['en', 'es', 'fr']).toContain(body.language);
    expect(String(body.currency)).toMatch(/^[A-Z]{3}$/);
    if (body.posEnabled) {
      await expect(search).toBeVisible();
      await expect(page.getByTestId('pos-grand-total')).toBeVisible();

      const pin = await request.post(`${apiBase}/api/v1/auth/terminal-pin`, {
        headers: { Authorization: `Bearer ${accessToken}` },
        data: { pin: '1234' },
        failOnStatusCode: false,
      });
      const overrides = await request.get(`${apiBase}/api/v1/pos/managers/sync-pins`, {
        headers: { Authorization: `Bearer ${accessToken}` },
        failOnStatusCode: false,
      });
      if (overrides.ok()) {
        expect(body.cashierId).toBeTruthy();
        expect(body.tenantId).toBeTruthy();
        const vault = await overrides.json();
        expect(Array.isArray(vault.managers)).toBe(true);
        if (pin.ok() || pin.status() === 204) {
          expect(vault.managers.length).toBeGreaterThan(0);
        }

        const eventId = crypto.randomUUID();
        const sync = await request.post(`${apiBase}/api/v1/pos/audit-sync`, {
          headers: { Authorization: `Bearer ${accessToken}` },
          data: [{
            id: eventId,
            timestamp: Date.now(),
            cashierId: body.cashierId ?? tokens.userId,
            eventType: 'LINE_VOID',
            orderId: crypto.randomUUID(),
            valueVoided: 12.5,
          }],
        });
        expect(sync.ok()).toBeTruthy();
        const ingested = await sync.json();
        expect(ingested.accepted + ingested.duplicates).toBeGreaterThan(0);
      }
    } else {
      await expect(locked).toBeVisible();
    }
  }
});
