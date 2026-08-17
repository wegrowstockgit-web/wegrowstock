import { expect, type Page } from '@playwright/test';

const DEMO_SESSION = {
  posEnabled: true,
  module: 'RETAIL_POS',
  tier: 'ENTERPRISE',
  language: 'en',
  languageSource: 'ORGANIZATION',
  currency: 'USD',
  currencySource: 'PLACE',
  placeLanguage: 'en',
  placeCurrency: 'USD',
  localeTag: 'en-US',
  taxRegionHint: 'US',
  timezone: 'America/New_York',
  companyName: 'Demo Corp',
  cashierId: 'a0000000-0000-4000-8000-000000000201',
  tenantId: 'a0000000-0000-4000-8000-000000000001',
  tenantBaseCurrency: 'USD',
  liveExchangeRate: 1,
};

export async function mockPosAuthApis(page: Page): Promise<void> {
  let signedIn = false;
  await page.route('**/api/v1/auth/login', async (route) => {
    signedIn = true;
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ accessToken: 'pos-e2e' }),
    });
  });
  await page.route('**/api/v1/pos/session**', async (route) => {
    if (!signedIn) {
      await route.fulfill({ status: 401, contentType: 'application/json', body: '{}' });
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(DEMO_SESSION),
    });
  });
  await page.route('**/api/v1/pos/managers/sync-pins', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ tenantId: DEMO_SESSION.tenantId, managers: [] }),
    });
  });
  await page.route('**/api/v1/pos/customers', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([
        {
          id: 'a0000000-0000-4000-8000-000000001101',
          name: 'Retail Partners LLC',
          email: 'ap@retailpartners.com',
        },
      ]),
    });
  });
  const catalog = [
    {
      variantId: 'a0000000-0000-4000-8000-000000000701',
      upc: '7501234567890',
      sku: 'AGUA',
      name: 'Agua 600ml',
      retailPrice: 12.5,
      imageUrl: '/catalog/agua.svg',
    },
    {
      variantId: 'a0000000-0000-4000-8000-000000000702',
      upc: '049000042566',
      sku: 'COLA',
      name: 'Cola 355ml',
      retailPrice: 18,
      imageUrl: '/catalog/cola.svg',
    },
    {
      variantId: 'a0000000-0000-4000-8000-000000000703',
      upc: '022000001234',
      sku: 'BREAD',
      name: 'Bread loaf',
      retailPrice: 29.9,
      imageUrl: '/catalog/bread.svg',
    },
  ];
  await page.route('**/api/v1/pos/catalog-sync', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(catalog),
    });
  });
  await page.route('**/api/v1/pos/catalog/lookup**', async (route) => {
    const upc = new URL(route.request().url()).searchParams.get('upc');
    const item = catalog.find((row) => row.upc === upc);
    if (!item) {
      await route.fulfill({ status: 404, contentType: 'application/json', body: '{"code":"VARIANT_NOT_FOUND"}' });
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(item),
    });
  });
}

async function enterShiftPin(page: Page): Promise<void> {
  await expect(page.getByTestId('pos-pin-gate')).toBeVisible();
  await page.getByTestId('scanner-pin-digit-1').click();
  await page.getByTestId('scanner-pin-digit-2').click();
  await page.getByTestId('scanner-pin-digit-3').click();
  await page.getByTestId('scanner-pin-digit-4').click();
}

export async function signInAndUnlockRegister(page: Page): Promise<void> {
  await page.goto('/login');
  await expect(page.getByTestId('pos-login')).toBeVisible();
  await page.getByTestId('pos-login-email').fill('owner@demo.test');
  await page.getByTestId('pos-login-password').fill('password123');
  await page.getByRole('button', { name: /sign in|iniciar sesión|connexion/i }).click();
  await enterShiftPin(page);
  await expect(page.getByTestId('register-page')).toBeVisible();
}
