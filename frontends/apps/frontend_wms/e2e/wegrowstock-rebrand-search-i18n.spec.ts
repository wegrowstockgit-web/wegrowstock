import { expect, test } from './fixtures/roleFixture';

const DEMO_PASSWORD = process.env.E2E_DEMO_PASSWORD ?? 'password123';

test.describe('weGrowStock rebrand, warehouse scope, global search, i18n', () => {
  test('login page brands as weGrowStock', async ({ browser }) => {
    const context = await browser.newContext({
      baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:3000',
    });
    const page = await context.newPage();
    await page.goto('/login');
    await expect(page).toHaveTitle(/weGrowStock/i);
    await expect(page.getByTestId('brand-logo').first()).toBeVisible();
    await expect(page.getByText('weGrowStock').first()).toBeVisible();
    await expect(page.getByTestId('login-email')).toBeVisible();
    await expect(page.getByTestId('login-continue')).toBeVisible();
    await expect(page.getByTestId('login-password')).toHaveCount(0);
    await context.close();
  });

  test('office chrome shows brand, tier badge, and tenant-global scope', async ({ ownerPage }) => {
    await ownerPage.goto('/dashboard');
    await expect(ownerPage.getByTestId('brand-logo').first()).toBeVisible({ timeout: 15_000 });
    await expect(ownerPage.getByText('weGrowStock').first()).toBeVisible();
    await expect(ownerPage.getByTestId('tier-badge').first()).toBeVisible({ timeout: 15_000 });
    await expect(ownerPage.getByTestId('tier-badge').first()).toHaveText(/BASIC|INTERMEDIATE|ENTERPRISE/i);

    await expect(ownerPage.getByLabel('Active warehouse')).toHaveCount(0);
    await expect(ownerPage.getByTestId('global-tenant-scope')).toBeVisible();
    await expect(ownerPage.getByTestId('global-tenant-scope')).toHaveText('Global Tenant Scope');

    await ownerPage.goto('/products');
    await expect(ownerPage.getByLabel('Active warehouse')).toHaveCount(0);
    await expect(ownerPage.getByTestId('global-tenant-scope')).toBeVisible();
  });

  test('warehouse-scoped routes keep the active warehouse dropdown', async ({ ownerPage }) => {
    await ownerPage.goto('/fulfillment');
    await expect(ownerPage.getByLabel('Active warehouse')).toBeVisible({ timeout: 15_000 });
    await expect(ownerPage.getByTestId('global-tenant-scope')).toHaveCount(0);
  });

  test('owner command palette searches catalog via GET /api/v1/search/global', async ({
    ownerPage,
  }) => {
    await ownerPage.goto('/dashboard');
    await expect(ownerPage.getByTestId('app-shell')).toBeVisible({ timeout: 15_000 });

    const searchWait = ownerPage.waitForResponse(
      (r) => r.url().includes('/api/v1/search/global') && r.request().method() === 'GET' && r.ok(),
      { timeout: 20_000 },
    );
    await ownerPage.keyboard.press('Control+k');
    const palette = ownerPage.getByRole('dialog', { name: /command palette/i });
    await expect(palette).toBeVisible({ timeout: 10_000 });
    await palette.getByPlaceholder(/Search pages/i).fill('WIDGET-S');
    const searchRes = await searchWait;
    expect(searchRes.url()).toMatch(/q=WIDGET-S/i);

    await expect(palette.getByText('Catalog', { exact: true })).toBeVisible({ timeout: 10_000 });
    await expect(palette.getByRole('button', { name: /WIDGET-S/i }).first()).toBeVisible();
    await palette.getByRole('button', { name: /WIDGET-S/i }).first().click();
    await expect(ownerPage).toHaveURL(/\/products/, { timeout: 15_000 });
  });

  test('picker global search omits customer records', async ({ pickerPage, ownerPage }) => {
    await ownerPage.goto('/dashboard');
    await ownerPage.keyboard.press('Control+k');
    const ownerPalette = ownerPage.getByRole('dialog', { name: /command palette/i });
    await expect(ownerPalette).toBeVisible();
    await ownerPalette.getByPlaceholder(/Search pages/i).fill('Retail Partners');
    await expect(ownerPalette.getByText('Customer', { exact: true })).toBeVisible({ timeout: 10_000 });
    await ownerPage.keyboard.press('Escape');

    await pickerPage.goto('/dashboard');
    await expect(pickerPage.getByTestId('app-shell')).toBeVisible({ timeout: 15_000 });
    const pickerSearch = pickerPage.waitForResponse(
      (r) => r.url().includes('/api/v1/search/global') && r.request().method() === 'GET' && r.ok(),
      { timeout: 20_000 },
    );
    await pickerPage.keyboard.press('Control+k');
    const pickerPalette = pickerPage.getByRole('dialog', { name: /command palette/i });
    await expect(pickerPalette).toBeVisible();
    await pickerPalette.getByPlaceholder(/Search pages/i).fill('Retail Partners');
    const pickerBody = (await (await pickerSearch).json()) as Array<{ category?: string }>;
    expect(pickerBody.some((row) => row.category === 'Customer')).toBeFalsy();
    await expect(pickerPalette.getByText('Customer', { exact: true })).toHaveCount(0);
  });

  test('profile language selector persists Spanish then restores English', async ({ ownerPage }) => {
    const restore = async () => {
      await ownerPage.request.patch('/api/v1/users/me/profile', {
        data: { preferredLanguage: 'en', localeLanguage: 'en' },
      });
    };

    try {
      await restore();
      await ownerPage.goto('/settings/profile');
      await expect(ownerPage.getByTestId('profile-settings-page')).toBeVisible({ timeout: 20_000 });
      const form = ownerPage.getByTestId('personal-profile-form');
      await expect(form.getByTestId('language-select')).toBeVisible();
      await form.getByTestId('language-select').selectOption('es');
      const savePersonal = ownerPage.waitForResponse(
        (r) =>
          r.url().includes('/api/v1/users/me/profile') && r.request().method() === 'PATCH',
        { timeout: 30_000 },
      );
      await ownerPage.getByRole('button', { name: /Save personal settings|Guardar ajustes personales|Enregistrer les paramètres personnels/i }).click();
      expect((await savePersonal).ok()).toBeTruthy();

      await ownerPage.reload();
      await expect(ownerPage.getByTestId('profile-settings-page')).toBeVisible({ timeout: 20_000 });
      await expect(ownerPage.getByTestId('personal-profile-form').getByTestId('language-select')).toHaveValue('es');
      await expect(ownerPage.getByRole('link', { name: 'Panel', exact: true })).toBeVisible({
        timeout: 15_000,
      });
    } finally {
      await restore();
    }
  });

  test('language preference survives logout and login for nav, page info, and copilot', async ({
    browser,
  }) => {
    const context = await browser.newContext({
      baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:3000',
    });
    const page = await context.newPage();

    const persistLanguage = async (language: 'en' | 'es' | 'fr') => {
      const login = await page.request.post('/api/v1/auth/login', {
        data: { email: 'owner@demo.test', password: DEMO_PASSWORD },
      });
      expect(login.ok()).toBeTruthy();
      const patch = await page.request.patch('/api/v1/users/me/profile', {
        data: { preferredLanguage: language, localeLanguage: language },
      });
      expect(patch.ok()).toBeTruthy();
      await page.request.post('/api/v1/auth/logout').catch(() => undefined);
    };

    const loginUi = async () => {
      await page.goto('/login');
      await page.getByTestId('login-email').fill('owner@demo.test');
      await page.getByTestId('login-continue').click();
      await expect(page.getByTestId('login-password')).toBeVisible({ timeout: 15_000 });
      await page.getByTestId('login-password').fill(DEMO_PASSWORD);
      await page.getByTestId('login-submit').click();
      await expect(page.getByTestId('app-shell')).toBeVisible({ timeout: 20_000 });
    };

    try {
      await persistLanguage('es');
      await loginUi();
      await expect(page.getByRole('link', { name: 'Panel', exact: true })).toBeVisible({
        timeout: 15_000,
      });
      await page.getByTestId('page-help-trigger').click();
      await expect(page.getByTestId('page-help-title')).toHaveTextContent(/Centro de mando/i);
      await page.keyboard.press('Escape');
      await page.getByTestId('support-assistant-fab').click();
      await expect(page.getByTestId('support-assistant-panel')).toBeVisible();
      await expect(page.getByTestId('support-assistant-panel')).toHaveTextContent(
        /Copiloto de operaciones/i,
      );
      await page.getByTestId('support-assistant-close').click();

      await page.getByTestId('header-user-trigger').click();
      await page.getByRole('menuitem', { name: /Cerrar sesión/i }).click();
      await expect(page).toHaveURL(/\/login/, { timeout: 15_000 });

      await persistLanguage('fr');
      await loginUi();
      await expect(page.getByRole('link', { name: 'Tableau de bord', exact: true })).toBeVisible({
        timeout: 15_000,
      });
      await page.getByTestId('page-help-trigger').click();
      await expect(page.getByTestId('page-help-title')).toHaveTextContent(/Centre de commande/i);
      await page.keyboard.press('Escape');
      await page.getByTestId('support-assistant-fab').click();
      await expect(page.getByTestId('support-assistant-panel')).toHaveTextContent(/Copilote/i);
    } finally {
      await persistLanguage('en');
      await context.close();
    }
  });

  test('owner can still sign in with English labels after language restore', async ({
    browser,
  }) => {
    const context = await browser.newContext({
      baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:3000',
    });
    const page = await context.newPage();
    const login = await page.request.post('/api/v1/auth/login', {
      data: { email: 'owner@demo.test', password: DEMO_PASSWORD },
    });
    expect(login.ok()).toBeTruthy();
    const me = await page.request.get('/api/v1/auth/me');
    expect(me.ok()).toBeTruthy();
    const body = (await me.json()) as { localeLanguage?: string; tier?: string };
    expect(body.localeLanguage === 'en' || !body.localeLanguage).toBeTruthy();
    expect(body.tier).toMatch(/BASIC|INTERMEDIATE|ENTERPRISE/i);
    await context.close();
  });
});
